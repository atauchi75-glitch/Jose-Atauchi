package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class LoadedImageFile(
    val bitmap: Bitmap,
    val localPath: String,
    val displayName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int
)

object ImageUtils {
    private const val MAX_DIMENSION = 1600

    suspend fun loadAndCacheSpecimenImage(
        context: Context,
        sourceUri: Uri
    ): LoadedImageFile = withContext(Dispatchers.IO) {
        val (queriedName, queriedSize) = queryUriMetadata(context, sourceUri)
        val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"

        val rawBitmap = decodeScaledBitmap(context, sourceUri)
        val normalized = ensureSoftwareArgb8888(scaleDownIfNeeded(rawBitmap, MAX_DIMENSION))

        val dir = File(context.filesDir, "specimens").apply { mkdirs() }
        val file = File(dir, "specimen_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            normalized.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val finalName = queriedName.ifBlank { "imagen_espécimen.jpg" }
        val finalSize = if (queriedSize > 0L) queriedSize else file.length()

        LoadedImageFile(
            bitmap = normalized,
            localPath = file.absolutePath,
            displayName = finalName,
            mimeType = mimeType,
            fileSizeBytes = finalSize,
            widthPx = normalized.width,
            heightPx = normalized.height
        )
    }

    suspend fun loadBitmapFromFile(path: String, maxDimension: Int = 1200): Bitmap? =
        withContext(Dispatchers.IO) {
            if (path.isBlank()) return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.let {
                ensureSoftwareArgb8888(scaleDownIfNeeded(it, maxDimension))
            }
        }

    fun ensureSoftwareArgb8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Tamaño N/D"
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.getDefault(), "%.0f KB", kb.coerceAtLeast(1.0))
        } else {
            String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0)
        }
    }

    fun shortFormatLabel(mimeType: String, fileName: String): String {
        val extFromName = fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
        if (extFromName in setOf("JPG", "JPEG", "PNG", "WEBP", "HEIC", "HEIF")) {
            return if (extFromName == "JPEG") "JPG" else extFromName
        }
        return when {
            mimeType.contains("png", ignoreCase = true) -> "PNG"
            mimeType.contains("webp", ignoreCase = true) -> "WEBP"
            mimeType.contains("heic", ignoreCase = true) || mimeType.contains("heif", ignoreCase = true) -> "HEIC"
            else -> "JPG"
        }
    }

    private fun queryUriMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var displayName = ""
        var size = -1L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx).orEmpty()
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return displayName to size
    }

    @SuppressLint("ExifInterface")
    private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxSide = maxOf(info.size.width, info.size.height)
                if (maxSide > MAX_DIMENSION) {
                    val ratio = MAX_DIMENSION.toFloat() / maxSide.toFloat()
                    decoder.setTargetSize(
                        (info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1)
                    )
                }
            }
        }

        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: throw IllegalStateException("No se pudo leer la imagen seleccionada.")

        val orientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        return rotateIfNeeded(decoded, orientation)
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxSide.toFloat()
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return bitmap.scale(w, h)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqMax: Int): Int {
        var inSampleSize = 1
        val maxSide = maxOf(width, height)
        if (maxSide > reqMax) {
            val half = maxSide / 2
            while (half / inSampleSize >= reqMax) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
