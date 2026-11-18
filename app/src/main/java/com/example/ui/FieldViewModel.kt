package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.SpeciesAnalyzerService
import com.example.data.ai.SpeciesNotIdentifiableException
import com.example.data.gbif.GbifRepository
import com.example.data.local.SpecimenLocalStore
import com.example.model.FieldMetadata
import com.example.model.ObservationRecord
import com.example.pdf.FieldSheetPdfGenerator
import com.example.pdf.PdfExportResult
import com.example.util.ImageUtils
import com.example.util.LoadedImageFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val TAG = "FieldViewModel"

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState

    data class ImageReady(
        val fileInfo: LoadedImageFile
    ) : AnalysisUiState

    data class Analyzing(
        val fileInfo: LoadedImageFile,
        val stepIndex: Int,
        val stepLabel: String
    ) : AnalysisUiState

    data class Result(val record: ObservationRecord) : AnalysisUiState

    data class Error(
        val message: String,
        val fileInfo: LoadedImageFile? = null
    ) : AnalysisUiState
}

sealed interface PdfExportUiState {
    data object Idle : PdfExportUiState
    data object Exporting : PdfExportUiState
    data class Success(val result: PdfExportResult) : PdfExportUiState
    data class Error(val message: String) : PdfExportUiState
}

class FieldViewModel(application: Application) : AndroidViewModel(application) {

    private val analyzerService = SpeciesAnalyzerService()
    private val gbifRepository = GbifRepository()
    private val localStore = SpecimenLocalStore(application.applicationContext)

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _pdfState = MutableStateFlow<PdfExportUiState>(PdfExportUiState.Idle)
    val pdfState: StateFlow<PdfExportUiState> = _pdfState.asStateFlow()

    val recentObservations: StateFlow<List<ObservationRecord>> =
        localStore.recentObservations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private var activeFile: LoadedImageFile? = null

    fun onMediaSelected(uri: Uri) {
        viewModelScope.launch {
            _pdfState.value = PdfExportUiState.Idle
            try {
                val loaded = ImageUtils.loadAndCacheSpecimenImage(getApplication(), uri)
                activeFile = loaded
                _uiState.value = AnalysisUiState.ImageReady(fileInfo = loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load selected image", e)
                _uiState.value = AnalysisUiState.Error(
                    message = "No se pudo cargar el archivo seleccionado. Verifica que sea una imagen válida."
                )
            }
        }
    }

    fun selectFromSavedRecordImage(record: ObservationRecord) {
        viewModelScope.launch {
            _pdfState.value = PdfExportUiState.Idle
            val bitmap = ImageUtils.loadBitmapFromFile(record.imageLocalPath, maxDimension = 1600)
            if (bitmap == null) {
                _uiState.value = AnalysisUiState.Error(
                    message = "La imagen guardada de este registro ya no está disponible."
                )
                return@launch
            }
            val fileObj = File(record.imageLocalPath)
            val loaded = LoadedImageFile(
                bitmap = bitmap,
                localPath = record.imageLocalPath,
                displayName = "${record.displayScientificName.ifBlank { record.displayCommonName }}.jpg",
                mimeType = "image/jpeg",
                fileSizeBytes = fileObj.length(),
                widthPx = bitmap.width,
                heightPx = bitmap.height
            )
            activeFile = loaded
            _uiState.value = AnalysisUiState.ImageReady(fileInfo = loaded)
        }
    }

    fun clearSelectedImage() {
        _pdfState.value = PdfExportUiState.Idle
        activeFile = null
        _uiState.value = AnalysisUiState.Idle
    }

    fun analyzeSelectedImage() {
        val fileInfo = activeFile ?: return
        val bitmap: Bitmap = fileInfo.bitmap
        val localPath: String = fileInfo.localPath

        viewModelScope.launch {
            _pdfState.value = PdfExportUiState.Idle
            _uiState.value = AnalysisUiState.Analyzing(
                fileInfo = fileInfo,
                stepIndex = 1,
                stepLabel = "Clasificando reino (Flora / Fauna) y morfología..."
            )
            try {
                val aiResult = analyzerService.analyzeSpecimen(bitmap)

                _uiState.value = AnalysisUiState.Analyzing(
                    fileInfo = fileInfo,
                    stepIndex = 2,
                    stepLabel = "Consultando taxonomía oficial en GBIF..."
                )
                val gbifInfo = gbifRepository.verifySpecies(
                    scientificName = aiResult.scientificName,
                    kingdomType = aiResult.kingdom
                )

                _uiState.value = AnalysisUiState.Analyzing(
                    fileInfo = fileInfo,
                    stepIndex = 3,
                    stepLabel = "Estructurando ficha técnica y Lista Roja UICN..."
                )

                val enrichedFlora = aiResult.flora?.let { f ->
                    val uicn = gbifInfo.iucnCategoryFromGbif
                        ?.takeIf { it.isNotBlank() && f.estadoConservacionUicn == "NE" }
                        ?: f.estadoConservacionUicn
                    val family = gbifInfo.family.ifBlank { f.familiaBotanica }
                    val sci = gbifInfo.canonicalName.ifBlank { f.nombreCientifico }
                    f.copy(
                        nombreCientifico = sci,
                        familiaBotanica = family,
                        estadoConservacionUicn = ObservationRecord.normalizeUicnCode(uicn)
                    )
                }

                val enrichedFauna = aiResult.fauna?.let { f ->
                    val uicn = gbifInfo.iucnCategoryFromGbif
                        ?.takeIf { it.isNotBlank() && f.estadoConservacionUicn == "NE" }
                        ?: f.estadoConservacionUicn
                    val sci = gbifInfo.canonicalName.ifBlank { f.nombreCientifico }
                    f.copy(
                        nombreCientifico = sci,
                        estadoConservacionUicn = ObservationRecord.normalizeUicnCode(uicn)
                    )
                }

                val record = ObservationRecord(
                    id = UUID.randomUUID().toString(),
                    createdAtEpochMs = System.currentTimeMillis(),
                    imageLocalPath = localPath,
                    kingdom = aiResult.kingdom,
                    confidenceNote = aiResult.confidenceNote,
                    fieldMetadata = FieldMetadata(
                        codigoFicha = "${aiResult.kingdom.codePrefix}-[Completar]",
                        fecha = "[Completar]"
                    ),
                    gbif = gbifInfo,
                    flora = enrichedFlora,
                    fauna = enrichedFauna
                )

                localStore.save(record)
                _uiState.value = AnalysisUiState.Result(record)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeciesNotIdentifiableException) {
                Log.w(TAG, "Image not identifiable: ${e.message}")
                _uiState.value = AnalysisUiState.Error(
                    message = e.message ?: "No se identificó ninguna especie vegetal o animal.",
                    fileInfo = fileInfo
                )
            } catch (e: Exception) {
                Log.e(TAG, "Specimen analysis error", e)
                _uiState.value = AnalysisUiState.Error(
                    message = e.localizedMessage ?: "Ocurrió un error al analizar la especie.",
                    fileInfo = fileInfo
                )
            }
        }
    }

    fun openSavedRecord(record: ObservationRecord) {
        _pdfState.value = PdfExportUiState.Idle
        _uiState.value = AnalysisUiState.Result(record)
    }

    fun deleteSavedRecord(record: ObservationRecord) {
        viewModelScope.launch {
            localStore.delete(record)
        }
    }

    fun updateFieldMetadata(newMetadata: FieldMetadata) {
        val current = (_uiState.value as? AnalysisUiState.Result)?.record ?: return
        val updated = current.copy(fieldMetadata = newMetadata)
        _uiState.value = AnalysisUiState.Result(updated)
        _pdfState.value = PdfExportUiState.Idle
        viewModelScope.launch {
            localStore.save(updated)
        }
    }

    fun returnToHome() {
        _pdfState.value = PdfExportUiState.Idle
        _uiState.value = AnalysisUiState.Idle
        activeFile = null
    }

    fun clearPdfBanner() {
        _pdfState.value = PdfExportUiState.Idle
    }

    fun exportPdfToDownloads() {
        val record = (_uiState.value as? AnalysisUiState.Result)?.record ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        viewModelScope.launch {
            _pdfState.value = PdfExportUiState.Exporting
            try {
                val res = FieldSheetPdfGenerator.exportToDownloads(getApplication(), record)
                _pdfState.value = PdfExportUiState.Success(res)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF download failed", e)
                _pdfState.value = PdfExportUiState.Error(
                    e.localizedMessage ?: "No se pudo descargar el PDF."
                )
            }
        }
    }

    fun exportPdfToUri(targetUri: Uri) {
        val record = (_uiState.value as? AnalysisUiState.Result)?.record ?: return
        viewModelScope.launch {
            _pdfState.value = PdfExportUiState.Exporting
            try {
                val res = FieldSheetPdfGenerator.exportToUri(getApplication(), record, targetUri)
                _pdfState.value = PdfExportUiState.Success(res)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF save-as failed", e)
                _pdfState.value = PdfExportUiState.Error(
                    e.localizedMessage ?: "No se pudo guardar el PDF."
                )
            }
        }
    }

    fun generatePdfForShare(onReady: (Uri, String) -> Unit) {
        val record = (_uiState.value as? AnalysisUiState.Result)?.record ?: return
        viewModelScope.launch {
            _pdfState.value = PdfExportUiState.Exporting
            try {
                val (file, uri) = FieldSheetPdfGenerator.exportToCache(getApplication(), record)
                _pdfState.value = PdfExportUiState.Idle
                onReady(uri, file.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF share prep failed", e)
                _pdfState.value = PdfExportUiState.Error(
                    e.localizedMessage ?: "No se pudo preparar el PDF para compartir."
                )
            }
        }
    }

    fun suggestPdfFileName(): String {
        val record = (_uiState.value as? AnalysisUiState.Result)?.record
            ?: return "Ficha_FaunaGBIF.pdf"
        return FieldSheetPdfGenerator.buildFileName(record)
    }
}
