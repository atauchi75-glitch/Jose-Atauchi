package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.FaunaDetails
import com.example.model.FieldMetadata
import com.example.model.FloraDetails
import com.example.model.KingdomType
import com.example.model.ObservationRecord
import com.example.model.SheetOptions
import com.example.pdf.PdfExportResult
import com.example.ui.PdfExportUiState
import com.example.ui.components.CheckboxOptionGrid
import com.example.ui.components.FieldDataRow
import com.example.ui.components.FieldSectionCard
import com.example.ui.components.GbifVerificationCard
import com.example.ui.components.UicnStatusBadge
import com.example.ui.theme.ForestDeep
import com.example.ui.theme.SageAccent
import com.example.ui.theme.ScientificNameStyle
import com.example.ui.theme.SlateTealFauna
import com.example.ui.theme.TerracottaFauna
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetReportScreen(
    record: ObservationRecord,
    pdfState: PdfExportUiState,
    onBack: () -> Unit,
    onUpdateFieldMetadata: (FieldMetadata) -> Unit,
    onDownloadPdf: () -> Unit,
    onSaveAsPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onOpenExportedPdf: (PdfExportResult) -> Unit,
    onDismissPdfBanner: () -> Unit,
    onOpenGbifUrl: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    var showMetadataDialog by remember { mutableStateOf(false) }
    val isFlora = record.kingdom == KingdomType.FLORA
    val accentColor = if (isFlora) ForestDeep else SlateTealFauna
    val badgeColor = if (isFlora) SageAccent else TerracottaFauna

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isFlora) "Ficha de Observación · Flora" else "Ficha de Observación · Fauna",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${record.fieldMetadata.codigoFicha} · Fecha: ${record.fieldMetadata.fecha}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_to_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a inicio"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMetadataDialog = true },
                        modifier = Modifier.testTag("edit_field_data_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = "Completar datos de campo"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            PdfBottomActionBar(
                pdfState = pdfState,
                accentColor = accentColor,
                onDownloadPdf = onDownloadPdf,
                onSaveAsPdf = onSaveAsPdf,
                onSharePdf = onSharePdf,
                onOpenExportedPdf = onOpenExportedPdf,
                onDismissPdfBanner = onDismissPdfBanner
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= 680.dp

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SpecimenIdentitySummaryCard(
                            record = record,
                            badgeColor = badgeColor,
                            onEditMetadata = { showMetadataDialog = true }
                        )
                        GbifVerificationCard(
                            gbif = record.gbif,
                            kingdom = record.kingdom,
                            onOpenGbifUrl = onOpenGbifUrl
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ObservationSectionsContent(record = record, accentColor = accentColor)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SpecimenIdentitySummaryCard(
                        record = record,
                        badgeColor = badgeColor,
                        onEditMetadata = { showMetadataDialog = true }
                    )
                    GbifVerificationCard(
                        gbif = record.gbif,
                        kingdom = record.kingdom,
                        onOpenGbifUrl = onOpenGbifUrl
                    )
                    ObservationSectionsContent(record = record, accentColor = accentColor)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showMetadataDialog) {
        FieldMetadataEditorDialog(
            initial = record.fieldMetadata,
            kingdom = record.kingdom,
            onDismiss = { showMetadataDialog = false },
            onSave = { updated ->
                onUpdateFieldMetadata(updated)
                showMetadataDialog = false
            }
        )
    }
}

@Composable
private fun SpecimenIdentitySummaryCard(
    record: ObservationRecord,
    badgeColor: Color,
    onEditMetadata: () -> Unit
) {
    val isFlora = record.kingdom == KingdomType.FLORA

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val imgFile = File(record.imageLocalPath)
                if (imgFile.exists()) {
                    AsyncImage(
                        model = imgFile,
                        contentDescription = record.displayCommonName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = if (isFlora) Icons.Filled.Eco else Icons.Filled.Pets,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = record.kingdom.labelEs,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = record.displayCommonName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = record.displayScientificName,
                style = ScientificNameStyle,
                color = badgeColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            UicnStatusBadge(code = record.displayConservationCode)

            if (record.confidenceNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = record.confidenceNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onEditMetadata,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("edit_field_data_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.edit_field_data_button))
            }
        }
    }
}

@Composable
private fun ObservationSectionsContent(
    record: ObservationRecord,
    accentColor: Color
) {
    when (record.kingdom) {
        KingdomType.FLORA -> record.flora?.let { flora ->
            FloraSheetSections(
                meta = record.fieldMetadata,
                flora = flora,
                uicnCode = record.displayConservationCode,
                accentColor = accentColor
            )
        }

        KingdomType.FAUNA -> record.fauna?.let { fauna ->
            FaunaSheetSections(
                meta = record.fieldMetadata,
                fauna = fauna,
                uicnCode = record.displayConservationCode,
                accentColor = accentColor
            )
        }
    }
}

@Composable
private fun FloraSheetSections(
    meta: FieldMetadata,
    flora: FloraDetails,
    uicnCode: String,
    accentColor: Color
) {
    val uicnLabel = SheetOptions.uicnOptions.firstOrNull { it.first == uicnCode }?.second
        ?: "NE (No evaluado)"

    FieldSectionCard(
        title = "1. Datos Generales y de Ubicación",
        icon = Icons.Filled.LocationOn,
        accentColor = accentColor
    ) {
        FieldDataRow("Observador / Investigador", meta.observador)
        FieldDataRow("Institución / Proyecto", meta.institucion)
        FieldDataRow("Hora de inicio / fin", meta.hora)
        FieldDataRow("País / Provincia / Cantón / Localidad (GBIF)", flora.distribucionGeografica)
        FieldDataRow("Altitud (m s. n. m.)", flora.altitudHabitual)
        FieldDataRow("Coordenadas GPS (Lat / Lon)", meta.coordenadasGps)
        FieldDataRow("Tipo de Ecosistema / Bioma", flora.tipoEcosistemaBioma)
        CheckboxOptionGrid(
            label = "Condiciones Climáticas (deducidas de la foto)",
            options = SheetOptions.climaOptions,
            selected = listOf(flora.condicionClimaticaSeleccion),
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Sustrato / Tipo de suelo habitual",
            options = SheetOptions.floraSustratoOptions,
            selected = flora.sustratoSeleccion,
            accentColor = accentColor
        )
    }

    FieldSectionCard(
        title = "2. Identificación Taxonómica y Estado",
        icon = Icons.Filled.Biotech,
        accentColor = accentColor
    ) {
        FieldDataRow("Nombre Común / Vernáculo", flora.nombreComun)
        FieldDataRow("Nombre Científico (Género y especie)", flora.nombreCientifico, isItalic = true)
        FieldDataRow("Familia Botánica", flora.familiaBotanica)
        CheckboxOptionGrid(
            label = "Origen de la Especie (según distribución natural)",
            options = SheetOptions.floraOrigenOptions,
            selected = listOf(flora.origenSeleccion),
            accentColor = accentColor,
            extraNote = flora.origenNota
        )
        CheckboxOptionGrid(
            label = "Estado de Conservación (UICN)",
            options = SheetOptions.uicnOptions.map { it.second },
            selected = listOf(uicnLabel),
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Abundancia en el sitio",
            options = SheetOptions.floraAbundanciaOptions,
            selected = flora.abundanciaSitioSeleccion,
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Tipo de Evidencia / Colecta",
            options = SheetOptions.floraEvidenciaOptions,
            selected = flora.tipoEvidenciaSeleccion,
            accentColor = accentColor
        )
    }

    FieldSectionCard(
        title = "3. Características Morfológicas y Fenológicas",
        icon = Icons.Filled.Eco,
        accentColor = accentColor
    ) {
        CheckboxOptionGrid(
            label = "Hábito de Crecimiento",
            options = SheetOptions.floraHabitoOptions,
            selected = listOf(flora.habitoCrecimientoSeleccion),
            accentColor = accentColor
        )
        FieldDataRow(
            "Dimensiones Estimadas (Promedio)",
            "Altura total: ${flora.alturaTotalM} · Altura copa: ${flora.alturaCopaM} · DAP: ${flora.dapCm}"
        )
        CheckboxOptionGrid(
            label = "Estado Fenológico (visible en la imagen)",
            options = SheetOptions.floraFenologiaOptions,
            selected = flora.estadoFenologicoSeleccion,
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Exposición Solar y Estrato",
            options = SheetOptions.floraEstratoOptions,
            selected = listOf(flora.exposicionSolarEstratoSeleccion),
            accentColor = accentColor
        )
        FieldDataRow("Características del Tallo / Tronco", flora.caracteristicasTalloTronco)
        FieldDataRow("Características de las Hojas", flora.caracteristicasHojas)
        FieldDataRow("Características de las Flores", flora.caracteristicasFlores)
        FieldDataRow("Características de Frutos y Semillas", flora.caracteristicasFrutosSemillas)
        CheckboxOptionGrid(
            label = "Interacciones Ecológicas Conocidas",
            options = SheetOptions.floraInteraccionesOptions,
            selected = flora.interaccionesEcologicasSeleccion,
            accentColor = accentColor,
            extraNote = flora.interaccionesEcologicasDetalle
        )
        CheckboxOptionGrid(
            label = "Usos Conocidos (Etnobotánica)",
            options = SheetOptions.floraUsosOptions,
            selected = flora.usosEtnobotanicosSeleccion,
            accentColor = accentColor,
            extraNote = flora.usosEtnobotanicosDetalle
        )
    }

    FieldSectionCard(
        title = "4. Amenazas y Observaciones Adicionales",
        icon = Icons.Filled.WarningAmber,
        accentColor = accentColor
    ) {
        CheckboxOptionGrid(
            label = "Principales Amenazas para la Especie",
            options = SheetOptions.floraAmenazasOptions,
            selected = flora.amenazasSeleccion,
            accentColor = accentColor
        )
        FieldDataRow("Notas Técnicas", flora.notasTecnicas)
    }
}

@Composable
private fun FaunaSheetSections(
    meta: FieldMetadata,
    fauna: FaunaDetails,
    uicnCode: String,
    accentColor: Color
) {
    val uicnLabel = SheetOptions.uicnOptions.firstOrNull { it.first == uicnCode }?.second
        ?: "NE (No evaluado)"

    FieldSectionCard(
        title = "1. Datos Generales y de Ubicación",
        icon = Icons.Filled.LocationOn,
        accentColor = accentColor
    ) {
        FieldDataRow("Observador / Investigador", meta.observador)
        FieldDataRow("Institución / Proyecto", meta.institucion)
        FieldDataRow("Hora de avistamiento", meta.hora)
        FieldDataRow("País / Provincia / Cantón / Localidad (GBIF)", fauna.distribucionGeografica)
        FieldDataRow("Altitud (m s. n. m.)", fauna.altitudHabitual)
        FieldDataRow("Coordenadas GPS (Lat / Lon)", meta.coordenadasGps)
        FieldDataRow("Tipo de Ecosistema / Hábitat", fauna.tipoEcosistemaHabitat)
        CheckboxOptionGrid(
            label = "Condiciones Climáticas (deducidas de la foto)",
            options = SheetOptions.climaOptions,
            selected = listOf(fauna.condicionClimaticaSeleccion),
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Microhábitat / Estrato (visible o habitual)",
            options = SheetOptions.faunaMicrohabitatOptions,
            selected = fauna.microhabitatEstratoSeleccion,
            accentColor = accentColor
        )
    }

    FieldSectionCard(
        title = "2. Identificación Taxonómica y Tipo de Registro",
        icon = Icons.Filled.Biotech,
        accentColor = accentColor
    ) {
        FieldDataRow("Nombre Común / Local", fauna.nombreComun)
        FieldDataRow("Nombre Científico (Género y especie)", fauna.nombreCientifico, isItalic = true)
        CheckboxOptionGrid(
            label = "Clase / Grupo Animal",
            options = SheetOptions.faunaClaseOptions,
            selected = listOf(fauna.claseGrupoAnimalSeleccion),
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Origen de la Especie (según distribución natural)",
            options = SheetOptions.faunaOrigenOptions,
            selected = listOf(fauna.origenSeleccion),
            accentColor = accentColor,
            extraNote = fauna.origenNota
        )
        CheckboxOptionGrid(
            label = "Estado de Conservación (UICN)",
            options = SheetOptions.uicnOptions.map { it.second },
            selected = listOf(uicnLabel),
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Cantidad de Individuos (en imagen: ${fauna.cantidadIndividuosFoto})",
            options = SheetOptions.faunaAgrupacionOptions,
            selected = listOf(fauna.agrupacionSeleccion),
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Tipo de Registro / Evidencia",
            options = SheetOptions.faunaRegistroOptions,
            selected = fauna.tipoRegistroSeleccion,
            accentColor = accentColor
        )
    }

    FieldSectionCard(
        title = "3. Descripción Morfológica y Comportamiento",
        icon = Icons.Filled.Pets,
        accentColor = accentColor
    ) {
        CheckboxOptionGrid(
            label = "Sexo (deducible de la foto)",
            options = SheetOptions.faunaSexoOptions,
            selected = listOf(fauna.sexoSeleccion),
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Etapa de Vida",
            options = SheetOptions.faunaEdadOptions,
            selected = listOf(fauna.etapaVidaSeleccion),
            accentColor = accentColor
        )
        FieldDataRow(
            "Dimensiones Estimadas (Promedio)",
            "Cuerpo: ${fauna.longitudCuerpoCm} · Cola: ${fauna.longitudColaCm} · Envergadura: ${fauna.envergaduraAlarCm} · Peso: ${fauna.pesoAprox}"
        )
        CheckboxOptionGrid(
            label = "Cobertura Corporal y Coloración",
            options = SheetOptions.faunaCoberturaOptions,
            selected = listOf(fauna.coberturaCorporalSeleccion),
            accentColor = accentColor,
            extraNote = fauna.coloresMarcasDistintivas
        )
        FieldDataRow("Rasgos Anatómicos Destacados", fauna.rasgosAnatomicosDestacados)
        CheckboxOptionGrid(
            label = "Comportamiento (visible o habitual)",
            options = SheetOptions.faunaComportamientoOptions,
            selected = fauna.comportamientoSeleccion,
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Período de Actividad",
            options = SheetOptions.faunaActividadOptions,
            selected = fauna.periodoActividadSeleccion,
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Locomoción",
            options = SheetOptions.faunaLocomocionOptions,
            selected = fauna.locomocionSeleccion,
            accentColor = accentColor
        )
        CheckboxOptionGrid(
            label = "Alimentación / Dieta",
            options = SheetOptions.faunaDietaOptions,
            selected = fauna.dietaCategoriasSeleccion,
            accentColor = accentColor,
            extraNote = fauna.dietaDetalleHabitual
        )
        FieldDataRow("Interacción con el Entorno y Otras Especies", fauna.interaccionEntorno)
        FieldDataRow("Descripción de Rastros Característicos", fauna.descripcionRastros)
    }

    FieldSectionCard(
        title = "4. Amenazas y Observaciones Adicionales",
        icon = Icons.Filled.WarningAmber,
        accentColor = accentColor
    ) {
        CheckboxOptionGrid(
            label = "Principales Amenazas para la Especie",
            options = SheetOptions.faunaAmenazasOptions,
            selected = fauna.amenazasSeleccion,
            accentColor = accentColor
        )
        FieldDataRow("Notas Técnicas", fauna.notasTecnicas)
    }
}

@Composable
private fun PdfBottomActionBar(
    pdfState: PdfExportUiState,
    accentColor: Color,
    onDownloadPdf: () -> Unit,
    onSaveAsPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onOpenExportedPdf: (PdfExportResult) -> Unit,
    onDismissPdfBanner: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(visible = pdfState is PdfExportUiState.Success) {
                val success = pdfState as? PdfExportUiState.Success
                if (success != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PDF guardado: ${success.result.savedLocationLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { onOpenExportedPdf(success.result) },
                                modifier = Modifier.testTag("open_pdf_button")
                            ) {
                                Text(stringResource(id = R.string.open_pdf_button))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = pdfState is PdfExportUiState.Error) {
                val err = pdfState as? PdfExportUiState.Error
                if (err != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = err.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissPdfBanner) {
                                Text("Cerrar")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDownloadPdf,
                    enabled = pdfState !is PdfExportUiState.Exporting,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("download_pdf_button")
                ) {
                    if (pdfState is PdfExportUiState.Exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generando PDF...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.export_pdf_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSaveAsPdf,
                    enabled = pdfState !is PdfExportUiState.Exporting,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("save_as_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SaveAs,
                        contentDescription = stringResource(id = R.string.save_as_pdf_button),
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedButton(
                    onClick = onSharePdf,
                    enabled = pdfState !is PdfExportUiState.Exporting,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("share_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(id = R.string.share_pdf_button),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldMetadataEditorDialog(
    initial: FieldMetadata,
    kingdom: KingdomType,
    onDismiss: () -> Unit,
    onSave: (FieldMetadata) -> Unit
) {
    var codigo by remember {
        mutableStateOf(
            if (initial.codigoFicha.contains("[Completar")) "${kingdom.codePrefix}-001"
            else initial.codigoFicha
        )
    }
    var fecha by remember {
        mutableStateOf(
            if (initial.fecha.contains("[Completar")) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            } else {
                initial.fecha
            }
        )
    }
    var observador by remember {
        mutableStateOf(
            if (initial.observador.contains("[Completar")) "" else initial.observador
        )
    }
    var institucion by remember {
        mutableStateOf(
            if (initial.institucion.contains("[Completar")) "" else initial.institucion
        )
    }
    var hora by remember {
        mutableStateOf(
            if (initial.hora.contains("[Completar")) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            } else {
                initial.hora
            }
        )
    }
    var gps by remember {
        mutableStateOf(
            if (initial.coordenadasGps.contains("[Completar")) "" else initial.coordenadasGps
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Datos de campo",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Completa los datos presenciales antes de exportar la ficha PDF. Los campos vacíos conservarán \"[Completar en campo]\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text("Código de Ficha") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fecha,
                    onValueChange = { fecha = it },
                    label = { Text("Fecha") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = observador,
                    onValueChange = { observador = it },
                    label = { Text("Observador / Investigador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = institucion,
                    onValueChange = { institucion = it },
                    label = { Text("Institución / Proyecto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hora,
                    onValueChange = { hora = it },
                    label = { Text("Hora de registro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gps,
                    onValueChange = { gps = it },
                    label = { Text("Coordenadas GPS (Lat / Lon)") },
                    placeholder = { Text("Ej. -0.2298, -78.5249") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        FieldMetadata(
                            codigoFicha = codigo.trim().ifBlank { "${kingdom.codePrefix}-[Completar]" },
                            fecha = fecha.trim().ifBlank { "[Completar]" },
                            observador = observador.trim().ifBlank { "[Completar en campo]" },
                            institucion = institucion.trim().ifBlank { "[Completar en campo]" },
                            hora = hora.trim().ifBlank { "[Completar en campo]" },
                            coordenadasGps = gps.trim().ifBlank { "[Completar en campo]" }
                        )
                    )
                },
                modifier = Modifier.testTag("save_field_data_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
