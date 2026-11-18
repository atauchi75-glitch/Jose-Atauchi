package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SpecimenLocalStore
import com.example.model.FaunaDetails
import com.example.model.FieldMetadata
import com.example.model.FloraDetails
import com.example.model.GbifTaxonVerification
import com.example.model.KingdomType
import com.example.model.ObservationRecord
import com.example.pdf.FieldSheetPdfGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FaunaGBIF", appName)
    }

    @Test
    fun `normalize IUCN codes handles full labels and codes`() {
        assertEquals("VU", ObservationRecord.normalizeUicnCode("VU (Vulnerable)"))
        assertEquals("CR", ObservationRecord.normalizeUicnCode("En peligro crítico"))
        assertEquals("LC", ObservationRecord.normalizeUicnCode("LC"))
        assertEquals("NE", ObservationRecord.normalizeUicnCode("Desconocido"))
    }

    @Test
    fun `image utils formats file size and short labels`() {
        assertEquals("512 KB", com.example.util.ImageUtils.formatFileSize(512L * 1024L))
        assertEquals("JPG", com.example.util.ImageUtils.shortFormatLabel("image/jpeg", "iguana.jpeg"))
        assertEquals("PNG", com.example.util.ImageUtils.shortFormatLabel("image/png", "hoja.png"))
        assertEquals("WEBP", com.example.util.ImageUtils.shortFormatLabel("image/webp", "specimen"))
    }

    @Test
    fun `gbif binomial cleaner strips authorships and taxonomic qualifiers`() {
        assertEquals(
            "Panthera onca",
            com.example.data.gbif.GbifRepository.cleanScientificNameForMatch("Panthera onca (Linnaeus, 1758)")
        )
        assertEquals(
            "Monstera deliciosa",
            com.example.data.gbif.GbifRepository.cleanScientificNameForMatch("*Monstera deliciosa* Liebm.")
        )
        assertEquals(
            "Quercus",
            com.example.data.gbif.GbifRepository.cleanScientificNameForMatch("Quercus sp.")
        )
    }

    @Test
    fun `buildFileName formats Flora and Fauna PDF names cleanly`() {
        val floraRecord = ObservationRecord(
            id = "flora-1",
            createdAtEpochMs = 1700000000000L,
            imageLocalPath = "",
            kingdom = KingdomType.FLORA,
            confidenceNote = "",
            fieldMetadata = FieldMetadata(),
            gbif = GbifTaxonVerification(canonicalName = "Monstera deliciosa", isVerifiedInGbif = true),
            flora = sampleFlora()
        )
        val faunaRecord = ObservationRecord(
            id = "fauna-1",
            createdAtEpochMs = 1700000001000L,
            imageLocalPath = "",
            kingdom = KingdomType.FAUNA,
            confidenceNote = "",
            fieldMetadata = FieldMetadata(),
            gbif = GbifTaxonVerification(canonicalName = "Panthera onca", isVerifiedInGbif = true),
            fauna = sampleFauna()
        )

        val floraName = FieldSheetPdfGenerator.buildFileName(floraRecord)
        val faunaName = FieldSheetPdfGenerator.buildFileName(faunaRecord)

        assertTrue(floraName.startsWith("Ficha_Flora_Monstera_deliciosa_"))
        assertTrue(floraName.endsWith(".pdf"))
        assertTrue(faunaName.startsWith("Ficha_Fauna_Panthera_onca_"))
        assertTrue(faunaName.endsWith(".pdf"))
    }

    @Test
    fun `room specimen local store persists and retrieves observation records`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SpecimenLocalStore(context)

        val record = ObservationRecord(
            id = "fauna-cache-1",
            createdAtEpochMs = 1700000005000L,
            imageLocalPath = "",
            kingdom = KingdomType.FAUNA,
            confidenceNote = "Rasgos diagnósticos confirmados.",
            fieldMetadata = FieldMetadata(codigoFicha = "FA-042", fecha = "18/11/2026"),
            gbif = GbifTaxonVerification(
                usageKey = 5219426L,
                canonicalName = "Panthera onca",
                kingdom = "Animalia",
                family = "Felidae",
                occurrenceCount = 24800L,
                isVerifiedInGbif = true
            ),
            fauna = sampleFauna()
        )

        store.save(record)
        val items = store.recentObservations.first()
        assertEquals(1, items.size)
        assertEquals("Panthera onca", items.first().displayScientificName)
        assertEquals("FA-042", items.first().fieldMetadata.codigoFicha)
    }

    private fun sampleFlora() = FloraDetails(
        distribucionGeografica = "México a Panamá (GBIF)",
        altitudHabitual = "0 - 1500 m s. n. m.",
        tipoEcosistemaBioma = "Bosque húmedo tropical",
        condicionClimaticaSeleccion = "Nublado",
        sustratoSeleccion = listOf("Tierra firme", "Epífita"),
        nombreComun = "Costilla de Adán",
        nombreCientifico = "Monstera deliciosa",
        familiaBotanica = "Araceae",
        origenSeleccion = "Nativa",
        origenNota = "Mesoamérica.",
        estadoConservacionUicn = "LC",
        abundanciaSitioSeleccion = listOf("Evaluar en campo"),
        tipoEvidenciaSeleccion = listOf("Fotografía"),
        habitoCrecimientoSeleccion = "Liana / Trepadora",
        alturaTotalM = "3 - 10 m",
        alturaCopaM = "N/A",
        dapCm = "N/A",
        estadoFenologicoSeleccion = listOf("Vegetativo (solo hojas)"),
        exposicionSolarEstratoSeleccion = "Subdosel (semisombra)",
        caracteristicasTalloTronco = "Tallo cilíndrico verde con raíces aéreas.",
        caracteristicasHojas = "Hojas grandes cordadas con perforaciones.",
        caracteristicasFlores = "Espádice blanco crema.",
        caracteristicasFrutosSemillas = "Infrutescencia comestible.",
        interaccionesEcologicasSeleccion = listOf("Polinizadores (abejas, aves, murciélagos)"),
        interaccionesEcologicasDetalle = "Escarabajos.",
        usosEtnobotanicosSeleccion = listOf("Ornamental"),
        usosEtnobotanicosDetalle = "Ornamental.",
        amenazasSeleccion = listOf("Tala / Deforestación"),
        notasTecnicas = "Cristales de oxalato de calcio."
    )

    private fun sampleFauna() = FaunaDetails(
        distribucionGeografica = "América Central y del Sur (GBIF)",
        altitudHabitual = "0 - 2000 m s. n. m.",
        tipoEcosistemaHabitat = "Bosque húmedo tropical",
        condicionClimaticaSeleccion = "Soleado",
        microhabitatEstratoSeleccion = listOf("Suelo / Hojarasca"),
        nombreComun = "Jaguar",
        nombreCientifico = "Panthera onca",
        claseGrupoAnimalSeleccion = "Mamífero",
        origenSeleccion = "Nativa",
        origenNota = "Neotrópico.",
        estadoConservacionUicn = "NT",
        cantidadIndividuosFoto = "1",
        agrupacionSeleccion = "Solitario",
        tipoRegistroSeleccion = listOf("Fotografía / Video"),
        sexoSeleccion = "Indeterminado",
        etapaVidaSeleccion = "Adulto",
        longitudCuerpoCm = "112 - 185 cm",
        longitudColaCm = "45 - 75 cm",
        envergaduraAlarCm = "N/A",
        pesoAprox = "56 - 96 kg",
        coberturaCorporalSeleccion = "Pelaje",
        coloresMarcasDistintivas = "Pelaje amarillo dorado con rosetas negras.",
        rasgosAnatomicosDestacados = "Cráneo robusto.",
        comportamientoSeleccion = listOf("Desplazándose"),
        periodoActividadSeleccion = listOf("Crepuscular", "Nocturna"),
        locomocionSeleccion = listOf("Camina"),
        dietaCategoriasSeleccion = listOf("Carnívoro"),
        dietaDetalleHabitual = "Pecaríes, capibaras y tapires.",
        interaccionEntorno = "Regulador trófico.",
        descripcionRastros = "Huellas redondeadas sin garras.",
        amenazasSeleccion = listOf("Caza / Tráfico ilegal", "Pérdida de hábitat"),
        notasTecnicas = "Potente mordida."
    )
}
