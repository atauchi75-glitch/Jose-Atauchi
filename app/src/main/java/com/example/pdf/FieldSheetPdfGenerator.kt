package com.example.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.example.model.FaunaDetails
import com.example.model.FloraDetails
import com.example.model.KingdomType
import com.example.model.ObservationRecord
import com.example.model.SheetOptions
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfExportResult(
    val fileName: String,
    val contentUri: Uri,
    val cacheShareUri: Uri,
    val savedLocationLabel: String
)

object FieldSheetPdfGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_H = 38f
    private const val TOP_MARGIN = 42f
    private const val BOTTOM_LIMIT = 795f
    private val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_H * 2

    fun buildFileName(record: ObservationRecord): String {
        val prefix = if (record.kingdom == KingdomType.FLORA) "Ficha_Flora" else "Ficha_Fauna"
        val cleanSpecies = record.displayScientificName
            .ifBlank { record.displayCommonName }
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "Especie" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "${prefix}_${cleanSpecies}_$stamp.pdf"
    }

    suspend fun exportToCache(
        context: Context,
        record: ObservationRecord
    ): Pair<File, Uri> = withContext(Dispatchers.IO) {
        val fileName = buildFileName(record)
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val outFile = File(dir, fileName)
        val doc = buildDocument(record)
        try {
            FileOutputStream(outFile).use { stream ->
                doc.writeTo(stream)
            }
        } finally {
            doc.close()
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        outFile to uri
    }

    suspend fun exportToUri(
        context: Context,
        record: ObservationRecord,
        targetUri: Uri
    ): PdfExportResult = withContext(Dispatchers.IO) {
        val (_, shareUri) = exportToCache(context, record)
        val doc = buildDocument(record)
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                doc.writeTo(out)
            } ?: throw IllegalStateException("No se pudo abrir el archivo de destino.")
        } finally {
            doc.close()
        }
        PdfExportResult(
            fileName = buildFileName(record),
            contentUri = targetUri,
            cacheShareUri = shareUri,
            savedLocationLabel = "Ubicación seleccionada por el usuario"
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun exportToDownloads(
        context: Context,
        record: ObservationRecord
    ): PdfExportResult = withContext(Dispatchers.IO) {
        val fileName = buildFileName(record)
        val (_, shareUri) = exportToCache(context, record)
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/FaunaGBIF"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("No se pudo crear el PDF en Descargas.")

        try {
            val doc = buildDocument(record)
            try {
                resolver.openOutputStream(itemUri)?.use { out: OutputStream ->
                    doc.writeTo(out)
                } ?: throw IllegalStateException("No se pudo escribir el archivo PDF.")
            } finally {
                doc.close()
            }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw e
        }

        PdfExportResult(
            fileName = fileName,
            contentUri = itemUri,
            cacheShareUri = shareUri,
            savedLocationLabel = "Descargas/FaunaGBIF/$fileName"
        )
    }

    private suspend fun buildDocument(record: ObservationRecord): PdfDocument {
        val specimenBitmap = ImageUtils.loadBitmapFromFile(record.imageLocalPath, maxDimension = 720)
        val doc = PdfDocument()
        try {
            val writer = PdfPageWriter(doc, record)
            writer.render(specimenBitmap)
            return doc
        } catch (t: Throwable) {
            doc.close()
            throw t
        }
    }

    private class PdfPageWriter(
        private val doc: PdfDocument,
        private val record: ObservationRecord
    ) {
        private val pages = mutableListOf<PdfDocument.Page>()
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas = Canvas()
        private var y = TOP_MARGIN

        private val isFlora = record.kingdom == KingdomType.FLORA
        private val accentColor = if (isFlora) Color.parseColor("#133A2A") else Color.parseColor("#1B4353")
        private val accentSoft = if (isFlora) Color.parseColor("#EAF4EE") else Color.parseColor("#E8F2F6")
        private val badgeSecondary = if (isFlora) Color.parseColor("#2E6F4E") else Color.parseColor("#B85D34")

        private val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val headerPaint = Paint().apply { color = accentColor; style = Paint.Style.FILL }
        private val cardFillPaint = Paint().apply { color = accentSoft; style = Paint.Style.FILL }
        private val cardStrokePaint = Paint().apply {
            color = Color.parseColor("#CFD8D3")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        private val dividerPaint = Paint().apply {
            color = Color.parseColor("#DCE3DF")
            strokeWidth = 0.8f
        }
        private val checkFillPaint = Paint().apply {
            color = badgeSecondary
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val checkStrokePaint = Paint().apply {
            color = Color.parseColor("#6D7B73")
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
            isAntiAlias = true
        }
        private val checkMarkPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCEAE2")
            textSize = 9.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val codeLinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5E7B8")
            textSize = 9.2f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        private val sectionHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 11.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B2620")
            textSize = 9.4f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A352F")
            textSize = 9.4f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val italicSciPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 13.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        }
        private val commonTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#16201B")
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7770")
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        fun render(specimenBitmap: Bitmap?) {
            startNewPage(firstPage = true)
            drawHeroSummaryCard(specimenBitmap)
            drawGbifTaxonomyStrip()

            when (record.kingdom) {
                KingdomType.FLORA -> record.flora?.let { renderFloraSections(it) }
                KingdomType.FAUNA -> record.fauna?.let { renderFaunaSections(it) }
            }

            // Finish last page and stamp footers on all pages
            finishCurrentPage()
            stampPageNumbers()
        }

        private fun startNewPage(firstPage: Boolean) {
            finishCurrentPage()
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH,
                PAGE_HEIGHT,
                pages.size + 1
            ).create()
            val page = doc.startPage(pageInfo)
            pages.add(page)
            currentPage = page
            canvas = page.canvas
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

            if (firstPage) {
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 84f, headerPaint)
                val sheetTitle = if (isFlora) {
                    "FICHA DE OBSERVACIÓN DE FLORA"
                } else {
                    "FICHA DE OBSERVACIÓN DE FAUNA"
                }
                val sheetSubtitle = if (isFlora) {
                    "Registro Botánico y Caracterización de Especies Vegetales · GBIF & UICN"
                } else {
                    "Registro Zoológico, Morfológico y Etológico de Especies Animales · GBIF & UICN"
                }
                canvas.drawText(sheetTitle, MARGIN_H, 28f, titlePaint)
                canvas.drawText(sheetSubtitle, MARGIN_H, 44f, subtitlePaint)
                val prefix = record.kingdom.codePrefix
                val codeDisplay = if (record.fieldMetadata.codigoFicha == "[Completar]") {
                    "$prefix-[Completar]"
                } else {
                    record.fieldMetadata.codigoFicha
                }
                canvas.drawText(
                    "CÓDIGO: $codeDisplay   |   FECHA: ${record.fieldMetadata.fecha}",
                    MARGIN_H,
                    66f,
                    codeLinePaint
                )
                y = 98f
            } else {
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 34f, headerPaint)
                val runningTitle = if (isFlora) {
                    "FICHA DE OBSERVACIÓN DE FLORA · ${record.displayScientificName}"
                } else {
                    "FICHA DE OBSERVACIÓN DE FAUNA · ${record.displayScientificName}"
                }
                canvas.drawText(runningTitle, MARGIN_H, 21f, subtitlePaint)
                y = 50f
            }
        }

        private fun finishCurrentPage() {
            currentPage?.let { page ->
                val pageCanvas = page.canvas
                val pageNum = pages.size
                pageCanvas.drawLine(
                    MARGIN_H,
                    PAGE_HEIGHT - 34f,
                    PAGE_WIDTH - MARGIN_H,
                    PAGE_HEIGHT - 34f,
                    dividerPaint
                )
                pageCanvas.drawText(
                    "FaunaGBIF · Ficha técnica generada con referencia GBIF y Lista Roja UICN",
                    MARGIN_H,
                    PAGE_HEIGHT - 20f,
                    footerPaint
                )
                val pText = "Página $pageNum"
                val pWidth = footerPaint.measureText(pText)
                pageCanvas.drawText(
                    pText,
                    PAGE_WIDTH - MARGIN_H - pWidth,
                    PAGE_HEIGHT - 20f,
                    footerPaint
                )
                doc.finishPage(page)
            }
            currentPage = null
        }

        private fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > BOTTOM_LIMIT) {
                startNewPage(firstPage = false)
            }
        }

        private fun drawHeroSummaryCard(specimenBitmap: Bitmap?) {
            val boxHeight = 104f
            ensureSpace(boxHeight + 12f)
            val rect = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + boxHeight)
            canvas.drawRoundRect(rect, 8f, 8f, cardFillPaint)
            canvas.drawRoundRect(rect, 8f, 8f, cardStrokePaint)

            var textX = MARGIN_H + 12f
            if (specimenBitmap != null) {
                val imgSize = 84f
                val imgRect = RectF(MARGIN_H + 10f, y + 10f, MARGIN_H + 10f + imgSize, y + 10f + imgSize)
                val srcRatio = specimenBitmap.width.toFloat() / specimenBitmap.height.toFloat()
                val dstRect = if (srcRatio > 1f) {
                    val h = imgSize / srcRatio
                    val top = imgRect.centerY() - h / 2f
                    RectF(imgRect.left, top, imgRect.right, top + h)
                } else {
                    val w = imgSize * srcRatio
                    val left = imgRect.centerX() - w / 2f
                    RectF(left, imgRect.top, left + w, imgRect.bottom)
                }
                canvas.drawBitmap(specimenBitmap, null, dstRect, null)
                canvas.drawRect(dstRect, cardStrokePaint)
                textX = MARGIN_H + 106f
            }

            val availW = (PAGE_WIDTH - MARGIN_H - 12f) - textX
            var innerY = y + 16f
            canvas.drawText(record.displayCommonName.take(46), textX, innerY, commonTitlePaint)
            innerY += 18f
            canvas.drawText(record.displayScientificName.take(48), textX, innerY, italicSciPaint)
            innerY += 15f

            val uicnCode = record.displayConservationCode
            val uicnPaint = TextPaint(labelPaint).apply {
                color = iucnColorInt(uicnCode)
                textSize = 9.2f
            }
            canvas.drawText("Estado UICN: $uicnCode (${iucnLabelEs(uicnCode)})", textX, innerY, uicnPaint)
            innerY += 14f

            val gbifLine = if (record.gbif.isVerifiedInGbif && record.gbif.usageKey != null) {
                val countFormatted = record.gbif.occurrenceCount?.let {
                    NumberFormat.getIntegerInstance(Locale("es")).format(it)
                } ?: "N/D"
                "GBIF Verificado · ID #${record.gbif.usageKey} · $countFormatted registros globales"
            } else {
                "Referencia taxonómica GBIF (${record.kingdom.labelEs})"
            }
            drawWrappedText(gbifLine, textX, innerY, availW, bodyPaint)

            y += boxHeight + 10f
        }

        private fun drawGbifTaxonomyStrip() {
            val g = record.gbif
            if (!g.isVerifiedInGbif) return
            val taxonomyStr = listOfNotNull(
                g.kingdom.takeIf { it.isNotBlank() }?.let { "Reino: $it" },
                g.phylum.takeIf { it.isNotBlank() }?.let { "Filo: $it" },
                g.clazz.takeIf { it.isNotBlank() }?.let { "Clase: $it" },
                g.order.takeIf { it.isNotBlank() }?.let { "Orden: $it" },
                g.family.takeIf { it.isNotBlank() }?.let { "Familia: $it" },
                g.genus.takeIf { it.isNotBlank() }?.let { "Género: $it" }
            ).joinToString("  ·  ")
            if (taxonomyStr.isBlank()) return

            val layout = buildStaticLayout(taxonomyStr, CONTENT_WIDTH - 16f, bodyPaint)
            val boxH = layout.height + 14f
            ensureSpace(boxH + 8f)
            val rect = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + boxH)
            canvas.drawRoundRect(rect, 5f, 5f, cardStrokePaint)
            canvas.save()
            canvas.translate(MARGIN_H + 8f, y + 7f)
            layout.draw(canvas)
            canvas.restore()
            y += boxH + 10f
        }

        private fun renderFloraSections(f: FloraDetails) {
            drawSectionTitle("1. DATOS GENERALES Y DE UBICACIÓN")
            drawKeyValue("Observador / Investigador", record.fieldMetadata.observador)
            drawKeyValue("Institución / Proyecto", record.fieldMetadata.institucion)
            drawKeyValue("Hora de inicio / fin", record.fieldMetadata.hora)
            drawKeyValue("País / Provincia / Localidad (GBIF)", f.distribucionGeografica)
            drawKeyValue("Altitud (m s. n. m.)", f.altitudHabitual)
            drawKeyValue("Coordenadas GPS (Lat / Lon)", record.fieldMetadata.coordenadasGps)
            drawKeyValue("Tipo de Ecosistema / Bioma", f.tipoEcosistemaBioma)
            drawCheckboxGroup(
                "Condiciones Climáticas (foto)",
                SheetOptions.climaOptions,
                listOf(f.condicionClimaticaSeleccion)
            )
            drawCheckboxGroup(
                "Sustrato / Tipo de suelo habitual",
                SheetOptions.floraSustratoOptions,
                f.sustratoSeleccion
            )

            drawSectionTitle("2. IDENTIFICACIÓN TAXONÓMICA Y ESTADO")
            drawKeyValue("Nombre Común / Vernáculo", f.nombreComun)
            drawKeyValue("Nombre Científico", f.nombreCientifico, italicValue = true)
            drawKeyValue("Familia Botánica", f.familiaBotanica)
            drawCheckboxGroup(
                "Origen de la Especie",
                SheetOptions.floraOrigenOptions,
                listOf(f.origenSeleccion),
                extraNote = f.origenNota
            )
            drawCheckboxGroup(
                "Estado de Conservación (UICN)",
                SheetOptions.uicnOptions.map { it.second },
                listOf(SheetOptions.uicnOptions.firstOrNull { it.first == record.displayConservationCode }?.second ?: "NE (No evaluado)")
            )
            drawCheckboxGroup(
                "Abundancia en el sitio",
                SheetOptions.floraAbundanciaOptions,
                f.abundanciaSitioSeleccion
            )
            drawCheckboxGroup(
                "Tipo de Evidencia / Colecta",
                SheetOptions.floraEvidenciaOptions,
                f.tipoEvidenciaSeleccion
            )

            drawSectionTitle("3. CARACTERÍSTICAS MORFOLÓGICAS Y FENOLÓGICAS (FLORA)")
            drawCheckboxGroup(
                "Hábito de Crecimiento",
                SheetOptions.floraHabitoOptions,
                listOf(f.habitoCrecimientoSeleccion)
            )
            drawKeyValue(
                "Dimensiones Estimadas",
                "Altura total: ${f.alturaTotalM}  |  Altura copa: ${f.alturaCopaM}  |  DAP: ${f.dapCm}"
            )
            drawCheckboxGroup(
                "Estado Fenológico (visible)",
                SheetOptions.floraFenologiaOptions,
                f.estadoFenologicoSeleccion
            )
            drawCheckboxGroup(
                "Exposición Solar y Estrato",
                SheetOptions.floraEstratoOptions,
                listOf(f.exposicionSolarEstratoSeleccion)
            )
            drawKeyValue("Características del Tallo / Tronco", f.caracteristicasTalloTronco)
            drawKeyValue("Características de las Hojas", f.caracteristicasHojas)
            drawKeyValue("Características de las Flores", f.caracteristicasFlores)
            drawKeyValue("Características de Frutos y Semillas", f.caracteristicasFrutosSemillas)
            drawCheckboxGroup(
                "Interacciones Ecológicas Conocidas",
                SheetOptions.floraInteraccionesOptions,
                f.interaccionesEcologicasSeleccion,
                extraNote = f.interaccionesEcologicasDetalle
            )
            drawCheckboxGroup(
                "Usos Conocidos (Etnobotánica)",
                SheetOptions.floraUsosOptions,
                f.usosEtnobotanicosSeleccion,
                extraNote = f.usosEtnobotanicosDetalle
            )

            drawSectionTitle("4. AMENAZAS Y OBSERVACIONES ADICIONALES")
            drawCheckboxGroup(
                "Principales Amenazas para la Especie",
                SheetOptions.floraAmenazasOptions,
                f.amenazasSeleccion
            )
            drawKeyValue("Notas Técnicas", f.notasTecnicas)
        }

        private fun renderFaunaSections(f: FaunaDetails) {
            drawSectionTitle("1. DATOS GENERALES Y DE UBICACIÓN")
            drawKeyValue("Observador / Investigador", record.fieldMetadata.observador)
            drawKeyValue("Institución / Proyecto", record.fieldMetadata.institucion)
            drawKeyValue("Hora de avistamiento", record.fieldMetadata.hora)
            drawKeyValue("País / Provincia / Localidad (GBIF)", f.distribucionGeografica)
            drawKeyValue("Altitud (m s. n. m.)", f.altitudHabitual)
            drawKeyValue("Coordenadas GPS (Lat / Lon)", record.fieldMetadata.coordenadasGps)
            drawKeyValue("Tipo de Ecosistema / Hábitat", f.tipoEcosistemaHabitat)
            drawCheckboxGroup(
                "Condiciones Climáticas (foto)",
                SheetOptions.climaOptions,
                listOf(f.condicionClimaticaSeleccion)
            )
            drawCheckboxGroup(
                "Microhábitat / Estrato",
                SheetOptions.faunaMicrohabitatOptions,
                f.microhabitatEstratoSeleccion
            )

            drawSectionTitle("2. IDENTIFICACIÓN TAXONÓMICA Y TIPO DE REGISTRO")
            drawKeyValue("Nombre Común / Local", f.nombreComun)
            drawKeyValue("Nombre Científico", f.nombreCientifico, italicValue = true)
            drawCheckboxGroup(
                "Clase / Grupo Animal",
                SheetOptions.faunaClaseOptions,
                listOf(f.claseGrupoAnimalSeleccion)
            )
            drawCheckboxGroup(
                "Origen de la Especie",
                SheetOptions.faunaOrigenOptions,
                listOf(f.origenSeleccion),
                extraNote = f.origenNota
            )
            drawCheckboxGroup(
                "Estado de Conservación (UICN)",
                SheetOptions.uicnOptions.map { it.second },
                listOf(SheetOptions.uicnOptions.firstOrNull { it.first == record.displayConservationCode }?.second ?: "NE (No evaluado)")
            )
            drawCheckboxGroup(
                "Individuos (en imagen: ${f.cantidadIndividuosFoto})",
                SheetOptions.faunaAgrupacionOptions,
                listOf(f.agrupacionSeleccion)
            )
            drawCheckboxGroup(
                "Tipo de Registro / Evidencia",
                SheetOptions.faunaRegistroOptions,
                f.tipoRegistroSeleccion
            )

            drawSectionTitle("3. DESCRIPCIÓN MORFOLÓGICA Y COMPORTAMIENTO (FAUNA)")
            drawCheckboxGroup("Sexo (deducible)", SheetOptions.faunaSexoOptions, listOf(f.sexoSeleccion))
            drawCheckboxGroup("Etapa de Vida", SheetOptions.faunaEdadOptions, listOf(f.etapaVidaSeleccion))
            drawKeyValue(
                "Dimensiones Estimadas",
                "Cuerpo: ${f.longitudCuerpoCm}  |  Cola: ${f.longitudColaCm}  |  Envergadura: ${f.envergaduraAlarCm}  |  Peso: ${f.pesoAprox}"
            )
            drawCheckboxGroup(
                "Cobertura Corporal",
                SheetOptions.faunaCoberturaOptions,
                listOf(f.coberturaCorporalSeleccion),
                extraNote = f.coloresMarcasDistintivas
            )
            drawKeyValue("Rasgos Anatómicos Destacados", f.rasgosAnatomicosDestacados)
            drawCheckboxGroup(
                "Comportamiento",
                SheetOptions.faunaComportamientoOptions,
                f.comportamientoSeleccion
            )
            drawCheckboxGroup(
                "Período de Actividad",
                SheetOptions.faunaActividadOptions,
                f.periodoActividadSeleccion
            )
            drawCheckboxGroup(
                "Locomoción",
                SheetOptions.faunaLocomocionOptions,
                f.locomocionSeleccion
            )
            drawCheckboxGroup(
                "Alimentación / Dieta",
                SheetOptions.faunaDietaOptions,
                f.dietaCategoriasSeleccion,
                extraNote = f.dietaDetalleHabitual
            )
            drawKeyValue("Interacción con el Entorno", f.interaccionEntorno)
            drawKeyValue("Descripción de Rastros Característicos", f.descripcionRastros)

            drawSectionTitle("4. AMENAZAS Y OBSERVACIONES ADICIONALES")
            drawCheckboxGroup(
                "Principales Amenazas para la Especie",
                SheetOptions.faunaAmenazasOptions,
                f.amenazasSeleccion
            )
            drawKeyValue("Notas Técnicas", f.notasTecnicas)
        }

        private fun drawSectionTitle(title: String) {
            ensureSpace(26f)
            y += 6f
            canvas.drawText(title, MARGIN_H, y + 11f, sectionHeaderPaint)
            y += 15f
            canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, dividerPaint)
            y += 8f
        }

        private fun drawKeyValue(label: String, value: String, italicValue: Boolean = false) {
            val safeValue = value.ifBlank { "[Completar en campo]" }
            val textPaint = if (italicValue) {
                TextPaint(bodyPaint).apply {
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                }
            } else {
                bodyPaint
            }

            val labelLayout = buildStaticLayout("$label:", CONTENT_WIDTH, labelPaint)
            val valLayout = buildStaticLayout(safeValue, CONTENT_WIDTH - 10f, textPaint)
            val blockHeight = labelLayout.height + valLayout.height + 5f
            ensureSpace(blockHeight)

            canvas.save()
            canvas.translate(MARGIN_H, y)
            labelLayout.draw(canvas)
            canvas.translate(8f, labelLayout.height + 1f)
            valLayout.draw(canvas)
            canvas.restore()

            y += blockHeight
        }

        private fun drawCheckboxGroup(
            label: String,
            options: List<String>,
            selected: List<String>,
            extraNote: String = ""
        ) {
            val selectedNormalized = selected.map { it.trim().lowercase() }.toSet()
            val columns = if (options.any { it.length > 26 }) 2 else 3
            val colWidth = (CONTENT_WIDTH - 8f) / columns
            val rows = (options.size + columns - 1) / columns
            val rowHeight = 16f

            val noteLayout = extraNote.takeIf { it.isNotBlank() }?.let {
                buildStaticLayout("Detalle: $it", CONTENT_WIDTH - 10f, bodyPaint)
            }
            val totalHeight = 14f + rows * rowHeight + (noteLayout?.height?.plus(4f) ?: 0f) + 6f
            ensureSpace(totalHeight)

            canvas.drawText("$label:", MARGIN_H, y + 10f, labelPaint)
            y += 15f

            options.forEachIndexed { index, option ->
                val row = index / columns
                val col = index % columns
                val x = MARGIN_H + 4f + col * colWidth
                val itemY = y + row * rowHeight
                val isChecked = selectedNormalized.any { sel ->
                    sel == option.lowercase() || option.lowercase().startsWith(sel)
                }
                drawCheckboxItem(x, itemY, option, isChecked, colWidth - 6f)
            }
            y += rows * rowHeight + 2f

            if (noteLayout != null) {
                canvas.save()
                canvas.translate(MARGIN_H + 8f, y)
                noteLayout.draw(canvas)
                canvas.restore()
                y += noteLayout.height + 4f
            }
            y += 3f
        }

        private fun drawCheckboxItem(
            x: Float,
            yTop: Float,
            text: String,
            checked: Boolean,
            maxWidth: Float
        ) {
            val boxSize = 9.5f
            val boxRect = RectF(x, yTop + 1.5f, x + boxSize, yTop + 1.5f + boxSize)
            if (checked) {
                canvas.drawRoundRect(boxRect, 2f, 2f, checkFillPaint)
                canvas.drawLine(x + 2f, yTop + 6.5f, x + 4.2f, yTop + 9f, checkMarkPaint)
                canvas.drawLine(x + 4.2f, yTop + 9f, x + 7.8f, yTop + 3.8f, checkMarkPaint)
            } else {
                canvas.drawRoundRect(boxRect, 2f, 2f, checkStrokePaint)
            }

            val textPaint = if (checked) labelPaint else bodyPaint
            val clipped = clipText(text, textPaint, maxWidth - 14f)
            canvas.drawText(clipped, x + 13f, yTop + 9.5f, textPaint)
        }

        private fun clipText(text: String, paint: TextPaint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var end = text.length
            while (end > 3 && paint.measureText(text.substring(0, end) + "…") > maxW) {
                end--
            }
            return text.substring(0, end) + "…"
        }

        private fun drawWrappedText(
            text: String,
            x: Float,
            yTop: Float,
            width: Float,
            paint: TextPaint
        ): Int {
            val layout = buildStaticLayout(text, width, paint)
            canvas.save()
            canvas.translate(x, yTop)
            layout.draw(canvas)
            canvas.restore()
            return layout.height
        }

        private fun buildStaticLayout(text: String, width: Float, paint: TextPaint): StaticLayout {
            return StaticLayout.Builder.obtain(
                text,
                0,
                text.length,
                paint,
                width.toInt().coerceAtLeast(40)
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(1.5f, 1.0f)
                .setIncludePad(false)
                .build()
        }

        private fun stampPageNumbers() {
            // Page footers are drawn live per page below if needed; PdfDocument finalizes on finishPage.
            // Note: Since finishPage already closed each page canvas, let's keep a clean static footer
            // rendered when starting/finishing each page! Let's actually draw footer before finishPage:
        }

        private fun iucnColorInt(code: String): Int = when (code) {
            "LC" -> Color.parseColor("#2E7D32")
            "NT" -> Color.parseColor("#689F38")
            "VU" -> Color.parseColor("#B58100")
            "EN" -> Color.parseColor("#E65100")
            "CR" -> Color.parseColor("#C62828")
            else -> Color.parseColor("#546E7A")
        }

        private fun iucnLabelEs(code: String): String = when (code) {
            "LC" -> "Preocupación menor"
            "NT" -> "Casi amenazada"
            "VU" -> "Vulnerable"
            "EN" -> "En peligro"
            "CR" -> "En peligro crítico"
            "DD" -> "Datos insuficientes"
            else -> "No evaluado"
        }
    }
}
