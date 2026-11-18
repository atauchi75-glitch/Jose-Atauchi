package com.example.model

import com.squareup.moshi.JsonClass

enum class KingdomType(val labelEs: String, val codePrefix: String) {
    FLORA("FLORA · Plantae", "FL"),
    FAUNA("FAUNA · Animalia", "FA")
}

@JsonClass(generateAdapter = true)
data class FieldMetadata(
    val codigoFicha: String = "[Completar]",
    val fecha: String = "[Completar]",
    val observador: String = "[Completar en campo]",
    val institucion: String = "[Completar en campo]",
    val hora: String = "[Completar en campo]",
    val coordenadasGps: String = "[Completar en campo]"
)

@JsonClass(generateAdapter = true)
data class GbifTaxonVerification(
    val usageKey: Long? = null,
    val canonicalName: String = "",
    val scientificName: String = "",
    val authorship: String = "",
    val kingdom: String = "",
    val phylum: String = "",
    val clazz: String = "",
    val order: String = "",
    val family: String = "",
    val genus: String = "",
    val taxonomicStatus: String = "",
    val matchConfidence: Int? = null,
    val occurrenceCount: Long? = null,
    val iucnCategoryFromGbif: String? = null,
    val vernacularNames: List<String> = emptyList(),
    val isVerifiedInGbif: Boolean = false
)

@JsonClass(generateAdapter = true)
data class FloraDetails(
    // 1. Datos generales y de ubicación
    val distribucionGeografica: String,
    val altitudHabitual: String,
    val tipoEcosistemaBioma: String,
    val condicionClimaticaSeleccion: String,
    val sustratoSeleccion: List<String>,

    // 2. Identificación taxonómica y estado
    val nombreComun: String,
    val nombreCientifico: String,
    val familiaBotanica: String,
    val origenSeleccion: String,
    val origenNota: String,
    val estadoConservacionUicn: String,
    val abundanciaSitioSeleccion: List<String>,
    val tipoEvidenciaSeleccion: List<String>,

    // 3. Características morfológicas y fenológicas
    val habitoCrecimientoSeleccion: String,
    val alturaTotalM: String,
    val alturaCopaM: String,
    val dapCm: String,
    val estadoFenologicoSeleccion: List<String>,
    val exposicionSolarEstratoSeleccion: String,
    val caracteristicasTalloTronco: String,
    val caracteristicasHojas: String,
    val caracteristicasFlores: String,
    val caracteristicasFrutosSemillas: String,
    val interaccionesEcologicasSeleccion: List<String>,
    val interaccionesEcologicasDetalle: String,
    val usosEtnobotanicosSeleccion: List<String>,
    val usosEtnobotanicosDetalle: String,

    // 4. Amenazas y observaciones
    val amenazasSeleccion: List<String>,
    val notasTecnicas: String
)

@JsonClass(generateAdapter = true)
data class FaunaDetails(
    // 1. Datos generales y de ubicación
    val distribucionGeografica: String,
    val altitudHabitual: String,
    val tipoEcosistemaHabitat: String,
    val condicionClimaticaSeleccion: String,
    val microhabitatEstratoSeleccion: List<String>,

    // 2. Identificación taxonómica y tipo de registro
    val nombreComun: String,
    val nombreCientifico: String,
    val claseGrupoAnimalSeleccion: String,
    val origenSeleccion: String,
    val origenNota: String,
    val estadoConservacionUicn: String,
    val cantidadIndividuosFoto: String,
    val agrupacionSeleccion: String,
    val tipoRegistroSeleccion: List<String>,

    // 3. Descripción morfológica y comportamiento
    val sexoSeleccion: String,
    val etapaVidaSeleccion: String,
    val longitudCuerpoCm: String,
    val longitudColaCm: String,
    val envergaduraAlarCm: String,
    val pesoAprox: String,
    val coberturaCorporalSeleccion: String,
    val coloresMarcasDistintivas: String,
    val rasgosAnatomicosDestacados: String,
    val comportamientoSeleccion: List<String>,
    val periodoActividadSeleccion: List<String>,
    val locomocionSeleccion: List<String>,
    val dietaCategoriasSeleccion: List<String>,
    val dietaDetalleHabitual: String,
    val interaccionEntorno: String,
    val descripcionRastros: String,

    // 4. Amenazas y observaciones
    val amenazasSeleccion: List<String>,
    val notasTecnicas: String
)

@JsonClass(generateAdapter = true)
data class ObservationRecord(
    val id: String,
    val createdAtEpochMs: Long,
    val imageLocalPath: String,
    val kingdom: KingdomType,
    val confidenceNote: String,
    val fieldMetadata: FieldMetadata,
    val gbif: GbifTaxonVerification,
    val flora: FloraDetails? = null,
    val fauna: FaunaDetails? = null
) {
    val displayCommonName: String
        get() = when (kingdom) {
            KingdomType.FLORA -> flora?.nombreComun.orEmpty()
            KingdomType.FAUNA -> fauna?.nombreComun.orEmpty()
        }.ifBlank { "Especie identificada" }

    val displayScientificName: String
        get() = gbif.canonicalName.ifBlank {
            when (kingdom) {
                KingdomType.FLORA -> flora?.nombreCientifico.orEmpty()
                KingdomType.FAUNA -> fauna?.nombreCientifico.orEmpty()
            }
        }

    val displayConservationCode: String
        get() {
            val raw = when (kingdom) {
                KingdomType.FLORA -> flora?.estadoConservacionUicn.orEmpty()
                KingdomType.FAUNA -> fauna?.estadoConservacionUicn.orEmpty()
            }
            return normalizeUicnCode(raw)
        }

    val displayHabitat: String
        get() = when (kingdom) {
            KingdomType.FLORA -> flora?.tipoEcosistemaBioma.orEmpty()
            KingdomType.FAUNA -> fauna?.tipoEcosistemaHabitat.orEmpty()
        }

    companion object {
        fun normalizeUicnCode(raw: String): String {
            val upper = raw.uppercase()
            return when {
                upper.startsWith("CR") || upper.contains("PELIGRO CRÍTICO") -> "CR"
                upper.startsWith("EN") || upper.contains("EN PELIGRO") -> "EN"
                upper.startsWith("VU") || upper.contains("VULNERABLE") -> "VU"
                upper.startsWith("NT") || upper.contains("CASI AMENAZADA") -> "NT"
                upper.startsWith("LC") || upper.contains("PREOCUPACIÓN MENOR") -> "LC"
                upper.startsWith("DD") -> "DD"
                else -> "NE"
            }
        }
    }
}

object SheetOptions {
    val climaOptions = listOf("Soleado", "Nublado", "Lluvia", "No determinable")
    val uicnOptions = listOf(
        "LC" to "LC (Preocupación menor)",
        "NT" to "NT (Casi amenazada)",
        "VU" to "VU (Vulnerable)",
        "EN" to "EN (En peligro)",
        "CR" to "CR (En peligro crítico)",
        "NE" to "NE (No evaluado)"
    )

    // FLORA options
    val floraSustratoOptions = listOf("Tierra firme", "Rocoso", "Arenoso", "Humedal / Acuático", "Epífita", "Otro")
    val floraOrigenOptions = listOf("Nativa", "Endémica", "Introducida / Cultivada")
    val floraAbundanciaOptions = listOf("Única (1 ind.)", "Escasa", "Frecuente", "Dominante", "Evaluar en campo")
    val floraEvidenciaOptions = listOf("Fotografía", "Muestra de herbario", "Semillas / Frutos", "Solo observación visual")
    val floraHabitoOptions = listOf("Árbol", "Arbusto", "Hierba", "Palma", "Liana / Trepadora", "Helecho", "Musgo / Liquen")
    val floraFenologiaOptions = listOf("Vegetativo (solo hojas)", "Botones florales", "Floración plena", "Fructificación / Semillas")
    val floraEstratoOptions = listOf("Dosel (sol pleno)", "Subdosel (semisombra)", "Sotobosque (sombra)", "Claro / Borde de camino")
    val floraInteraccionesOptions = listOf("Polinizadores (abejas, aves, murciélagos)", "Herbivoría", "Dispersores de semillas", "Nidos / Refugio")
    val floraUsosOptions = listOf("Medicinal", "Alimenticio", "Maderable", "Ornamental", "Artesanal", "Ninguno / Desconocido")
    val floraAmenazasOptions = listOf("Tala / Deforestación", "Quema / Incendios", "Especies invasoras", "Pastoreo / Agricultura", "Contaminación", "Sin amenazas evidentes")

    // FAUNA options
    val faunaMicrohabitatOptions = listOf("Suelo / Hojarasca", "Sotobosque / Arbusto", "Tronco / Rama", "Dosel", "Aire (en vuelo)", "Cuerpo de agua / Orilla")
    val faunaClaseOptions = listOf("Mamífero", "Ave", "Reptil", "Anfibio", "Pez", "Invertebrado")
    val faunaOrigenOptions = listOf("Nativa", "Endémica", "Migratoria", "Introducida")
    val faunaAgrupacionOptions = listOf("Solitario", "Pareja", "Grupo")
    val faunaRegistroOptions = listOf("Avistamiento directo", "Fotografía / Video", "Canto / Vocalización", "Huellas / Rastros", "Heces / Plumas / Nido")
    val faunaSexoOptions = listOf("Macho", "Hembra", "Indeterminado")
    val faunaEdadOptions = listOf("Cría / Larva", "Juvenil", "Adulto")
    val faunaCoberturaOptions = listOf("Pelaje", "Plumaje", "Escamas", "Piel desnuda")
    val faunaComportamientoOptions = listOf("Alimentándose / Forrajeando", "Desplazándose", "Descansando / Perchado", "Vocalizando", "Cortejo / Apareamiento", "Cuidado parental")
    val faunaActividadOptions = listOf("Diurna", "Nocturna", "Crepuscular")
    val faunaLocomocionOptions = listOf("Camina", "Vuela", "Nada", "Trepa")
    val faunaDietaOptions = listOf("Herbívoro", "Carnívoro", "Omnívoro", "Insectívoro", "Frugívoro", "Nectarívoro", "Carroñero")
    val faunaAmenazasOptions = listOf("Caza / Tráfico ilegal", "Pérdida de hábitat", "Atropellamiento en vías", "Animales domésticos", "Contaminación", "Sin amenazas evidentes")
}
