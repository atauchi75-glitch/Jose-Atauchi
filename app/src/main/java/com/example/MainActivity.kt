package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdf.PdfExportResult
import com.example.security.configureAppCheck
import com.example.ui.AnalysisUiState
import com.example.ui.FieldViewModel
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.screens.HomeCaptureScreen
import com.example.ui.screens.SheetReportScreen
import com.example.ui.theme.FaunaGbifTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModel creation
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge and render Jetpack Compose UI
        enableEdgeToEdge()
        setContent {
            FaunaGbifTheme {
                FaunaGbifApp()
            }
        }
    }
}

@Composable
fun FaunaGbifApp(
    viewModel: FieldViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.pdfState.collectAsStateWithLifecycle()
    val recentRecords by viewModel.recentObservations.collectAsStateWithLifecycle()

    var previewPdfResult by remember { mutableStateOf<PdfExportResult?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onMediaSelected(it) }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onMediaSelected(it) }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.exportPdfToUri(it) }
    }

    val activeRecord = (uiState as? AnalysisUiState.Result)?.record

    Crossfade(
        targetState = activeRecord != null,
        label = "screen_crossfade"
    ) { showingReport ->
        if (showingReport && activeRecord != null) {
            SheetReportScreen(
                record = activeRecord,
                pdfState = pdfState,
                onBack = viewModel::returnToHome,
                onUpdateFieldMetadata = viewModel::updateFieldMetadata,
                onDownloadPdf = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        viewModel.exportPdfToDownloads()
                    } else {
                        createPdfLauncher.launch(viewModel.suggestPdfFileName())
                    }
                },
                onSaveAsPdf = {
                    createPdfLauncher.launch(viewModel.suggestPdfFileName())
                },
                onSharePdf = {
                    viewModel.generatePdfForShare { shareUri, _ ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Compartir ficha PDF")
                            )
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "No se encontró una aplicación para compartir PDF.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onOpenExportedPdf = { exportResult: PdfExportResult ->
                    previewPdfResult = exportResult
                },
                onDismissPdfBanner = viewModel::clearPdfBanner,
                onOpenGbifUrl = { url ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                    runCatching { context.startActivity(browserIntent) }
                }
            )
        } else {
            HomeCaptureScreen(
                uiState = uiState,
                recentRecords = recentRecords,
                onPickFromGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickFromFiles = {
                    openDocumentLauncher.launch(arrayOf("image/*"))
                },
                onSelectSavedImage = viewModel::selectFromSavedRecordImage,
                onClearSelectedFile = viewModel::clearSelectedImage,
                onAnalyzeImage = viewModel::analyzeSelectedImage,
                onOpenRecord = viewModel::openSavedRecord,
                onDeleteRecord = viewModel::deleteSavedRecord
            )
        }
    }

    previewPdfResult?.let { result ->
        val externalIntent = remember(result.cacheShareUri) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(result.cacheShareUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val canOpenExternally = remember(externalIntent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    externalIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                ) != null
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(externalIntent, 0) != null
            }
        }

        PdfPreviewDialog(
            pdfFile = result.cacheFile,
            fileName = result.fileName,
            canOpenExternally = canOpenExternally,
            onDismiss = { previewPdfResult = null },
            onOpenExternal = {
                runCatching {
                    context.startActivity(Intent.createChooser(externalIntent, "Abrir ficha PDF"))
                }.onFailure {
                    Toast.makeText(
                        context,
                        "No se pudo abrir en una aplicación externa.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, result.cacheShareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(shareIntent, "Compartir ficha PDF"))
                }
            }
        )
    }
}
