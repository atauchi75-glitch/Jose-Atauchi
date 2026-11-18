package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.FaunaDetails
import com.example.model.FloraDetails
import com.example.model.KingdomType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "SpeciesAnalyzerService"

data class AiAnalysisOutput(
    val kingdom: KingdomType,
    val scientificName: String,
    val confidenceNote: String,
    val flora: FloraDetails? = null,
    val fauna: FaunaDetails? = null
)

class SpeciesNotIdentifiableException(message: String) : Exception(message)

class SpeciesAnalyzerService {

    private val floraSchema = Schema.obj(
        properties = mapOf(
            "distribucionGeografica" to Schema.string("Distribución geográfica principal de la especie según GBIF"),
            "altitudHabitual" to Schema.string("Rango altitudinal habitual en m s. n. m."),
            "tipoEcosistemaBioma" to Schema.string("Bioma o ecosistema característico donde habita"),
            "condicionClimaticaSeleccion" to Schema.enumeration(
                listOf("Soleado", "Nublado", "Lluvia", "No determinable"),
                "Condición climática deducida de la fotografía"
            ),
            "sustratoSeleccion" to Schema.array(
                Schema.string(),
                "Opciones aplicables de: Tierra firme, Rocoso, Arenoso, Humedal / Acuático, Epífita, Otro"
            ),
            "nombreComun" to Schema.string("Nombres comunes principales en español"),
            "nombreCientifico" to Schema.string("Nombre científico binomial (Género especie)"),
            "familiaBotanica" to Schema.string("Familia botánica"),
            "origenSeleccion" to Schema.enumeration(
                listOf("Nativa", "Endémica", "Introducida / Cultivada"),
                "Origen natural principal"
            ),
            "origenNota" to Schema.string("Nota breve sobre su región de origen biogeográfico"),
            "estadoConservacionUicn" to Schema.enumeration(
                listOf("LC", "NT", "VU", "EN", "CR", "NE"),
                "Código UICN de la Lista Roja"
            ),
            "abundanciaSitioSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Única (1 ind.), Escasa, Frecuente, Dominante, Evaluar en campo. Incluir Evaluar en campo si no es determinable"
            ),
            "tipoEvidenciaSeleccion" to Schema.array(
                Schema.string(),
                "Incluir siempre Fotografía y otras aplicables"
            ),
            "habitoCrecimientoSeleccion" to Schema.enumeration(
                listOf("Árbol", "Arbusto", "Hierba", "Palma", "Liana / Trepadora", "Helecho", "Musgo / Liquen"),
                "Hábito de crecimiento principal"
            ),
            "alturaTotalM" to Schema.string("Altura promedio de la especie en metros (ej. 1.5 - 3 m)"),
            "alturaCopaM" to Schema.string("Altura o diámetro de copa promedio en metros o N/A"),
            "dapCm" to Schema.string("DAP promedio en cm o N/A si no es árbol"),
            "estadoFenologicoSeleccion" to Schema.array(
                Schema.string(),
                "Opciones visibles en la imagen: Vegetativo (solo hojas), Botones florales, Floración plena, Fructificación / Semillas"
            ),
            "exposicionSolarEstratoSeleccion" to Schema.enumeration(
                listOf("Dosel (sol pleno)", "Subdosel (semisombra)", "Sotobosque (sombra)", "Claro / Borde de camino"),
                "Estrato y exposición solar probable o habitual"
            ),
            "caracteristicasTalloTronco" to Schema.string("Descripción detallada de forma, corteza, color, espinas o savia"),
            "caracteristicasHojas" to Schema.string("Disposición, forma, borde, color, tamaño y aroma característico"),
            "caracteristicasFlores" to Schema.string("Color, tamaño, aroma, forma e inflorescencia típica"),
            "caracteristicasFrutosSemillas" to Schema.string("Tipo de fruto, color, tamaño y mecanismo de dispersión"),
            "interaccionesEcologicasSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Polinizadores (abejas, aves, murciélagos), Herbivoría, Dispersores de semillas, Nidos / Refugio"
            ),
            "interaccionesEcologicasDetalle" to Schema.string("Detalle de la fauna asociada y relaciones ecológicas"),
            "usosEtnobotanicosSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Medicinal, Alimenticio, Maderable, Ornamental, Artesanal, Ninguno / Desconocido"
            ),
            "usosEtnobotanicosDetalle" to Schema.string("Detalle de usos etnobotánicos conocidos"),
            "amenazasSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Tala / Deforestación, Quema / Incendios, Especies invasoras, Pastoreo / Agricultura, Contaminación, Sin amenazas evidentes"
            ),
            "notasTecnicas" to Schema.string("Datos técnicos relevantes, toxicidad o curiosidades botánicas")
        )
    )

    private val faunaSchema = Schema.obj(
        properties = mapOf(
            "distribucionGeografica" to Schema.string("Distribución geográfica principal según GBIF"),
            "altitudHabitual" to Schema.string("Rango altitudinal habitual en m s. n. m."),
            "tipoEcosistemaHabitat" to Schema.string("Hábitat natural y ecosistemas donde habita la especie"),
            "condicionClimaticaSeleccion" to Schema.enumeration(
                listOf("Soleado", "Nublado", "Lluvia", "No determinable"),
                "Condición climática deducida de la fotografía"
            ),
            "microhabitatEstratoSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Suelo / Hojarasca, Sotobosque / Arbusto, Tronco / Rama, Dosel, Aire (en vuelo), Cuerpo de agua / Orilla"
            ),
            "nombreComun" to Schema.string("Nombres comunes principales en español"),
            "nombreCientifico" to Schema.string("Nombre científico binomial (Género especie)"),
            "claseGrupoAnimalSeleccion" to Schema.enumeration(
                listOf("Mamífero", "Ave", "Reptil", "Anfibio", "Pez", "Invertebrado"),
                "Clase o grupo animal"
            ),
            "origenSeleccion" to Schema.enumeration(
                listOf("Nativa", "Endémica", "Migratoria", "Introducida"),
                "Origen natural principal"
            ),
            "origenNota" to Schema.string("Nota sobre su región biogeográfica de origen"),
            "estadoConservacionUicn" to Schema.enumeration(
                listOf("LC", "NT", "VU", "EN", "CR", "NE"),
                "Código UICN de la Lista Roja"
            ),
            "cantidadIndividuosFoto" to Schema.string("Número exacto de individuos visibles en la foto (ej. 1)"),
            "agrupacionSeleccion" to Schema.enumeration(
                listOf("Solitario", "Pareja", "Grupo"),
                "Agrupación observada en la imagen"
            ),
            "tipoRegistroSeleccion" to Schema.array(
                Schema.string(),
                "Incluir siempre Fotografía / Video y otras aplicables"
            ),
            "sexoSeleccion" to Schema.enumeration(
                listOf("Macho", "Hembra", "Indeterminado"),
                "Sexo deducible de la fotografía"
            ),
            "etapaVidaSeleccion" to Schema.enumeration(
                listOf("Cría / Larva", "Juvenil", "Adulto"),
                "Etapa de vida deducible"
            ),
            "longitudCuerpoCm" to Schema.string("Longitud promedio del cuerpo en cm"),
            "longitudColaCm" to Schema.string("Longitud promedio de cola en cm o N/A"),
            "envergaduraAlarCm" to Schema.string("Envergadura alar promedio en cm o N/A"),
            "pesoAprox" to Schema.string("Peso aproximado con unidad g o kg"),
            "coberturaCorporalSeleccion" to Schema.enumeration(
                listOf("Pelaje", "Plumaje", "Escamas", "Piel desnuda"),
                "Cobertura corporal principal"
            ),
            "coloresMarcasDistintivas" to Schema.string("Colores principales y marcas distintivas de la especie"),
            "rasgosAnatomicosDestacados" to Schema.string("Forma de pico, patas, garras, orejas, antenas o cola"),
            "comportamientoSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Alimentándose / Forrajeando, Desplazándose, Descansando / Perchado, Vocalizando, Cortejo / Apareamiento, Cuidado parental"
            ),
            "periodoActividadSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Diurna, Nocturna, Crepuscular"
            ),
            "locomocionSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Camina, Vuela, Nada, Trepa"
            ),
            "dietaCategoriasSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Herbívoro, Carnívoro, Omnívoro, Insectívoro, Frugívoro, Nectarívoro, Carroñero"
            ),
            "dietaDetalleHabitual" to Schema.string("Explicación detallada de su dieta habitual"),
            "interaccionEntorno" to Schema.string("Comportamiento ecológico, relación con plantas/otros animales y reacción ante humanos"),
            "descripcionRastros" to Schema.string("Descripción típica de huellas, madrigueras, nidos o vocalizaciones"),
            "amenazasSeleccion" to Schema.array(
                Schema.string(),
                "Opciones de: Caza / Tráfico ilegal, Pérdida de hábitat, Atropellamiento en vías, Animales domésticos, Contaminación, Sin amenazas evidentes"
            ),
            "notasTecnicas" to Schema.string("Datos relevantes adicionales, veneno/peligrosidad o curiosidades zoológicas")
        )
    )

    private val rootSchema = Schema.obj(
        properties = mapOf(
            "detectedKingdom" to Schema.enumeration(
                listOf("FLORA", "FAUNA", "NO_IDENTIFICABLE"),
                "Clasificación automática del reino principal en la fotografía"
            ),
            "scientificName" to Schema.string("Nombre científico binomial identificado según GBIF"),
            "confidenceNote" to Schema.string("Nota breve sobre rasgos diagnósticos observados en la imagen"),
            "notIdentifiableReason" to Schema.string("Explicación si no se distingue ninguna planta ni animal"),
            "flora" to floraSchema,
            "fauna" to faunaSchema
        ),
        optionalProperties = listOf("notIdentifiableReason", "flora", "fauna")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun analyzeSpecimen(bitmap: Bitmap): AiAnalysisOutput {
        val promptText = """
            Actúa como un biólogo, botánico y zoólogo experto, utilizando como referencia principal la base de datos taxonómica global de GBIF (Global Biodiversity Information Facility) y los criterios de la Lista Roja de la UICN.

            Analiza cuidadosamente la fotografía proporcionada y determina automáticamente si el organismo principal pertenece al reino Plantae (FLORA) o Animalia (FAUNA).
            - Si corresponde a una planta, hongo macroscópico asimilado o flora, selecciona detectedKingdom = "FLORA" y completa ÚNICAMENTE el objeto `flora` con rigor científico en español.
            - Si corresponde a un animal (vertebrado o invertebrado), selecciona detectedKingdom = "FAUNA" y completa ÚNICAMENTE el objeto `fauna` con rigor científico en español.
            - Si la imagen no contiene ningún organismo vivo identificable (por ejemplo, objetos inertes, texto, pantalla negra), selecciona detectedKingdom = "NO_IDENTIFICABLE" y explica brevemente en `notIdentifiableReason`.

            REGLAS ESTRICTAS:
            1. Usa nomenclatura binomial científica válida según GBIF (ej. "Panthera onca", "Monstera deliciosa", "Ara macao") en `scientificName` y dentro de la ficha.
            2. En los campos de selección múltiple (arrays), utiliza EXACTAMENTE las cadenas permitidas descritas en cada propiedad del esquema.
            3. En `tipoEvidenciaSeleccion` (Flora) incluye siempre "Fotografía". En `abundanciaSitioSeleccion` incluye "Evaluar en campo" si no se aprecia una población completa. En `tipoRegistroSeleccion` (Fauna) incluye siempre "Fotografía / Video".
            4. Basa el estado de conservación en la Lista Roja de la UICN ("LC", "NT", "VU", "EN", "CR", "NE").
            5. Sé detallado, técnico, preciso y redacta todo en español científico claro.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
            }

            val rawJson = response.text
                ?: throw IllegalStateException("El modelo no devolvió contenido.")
            parseOutput(rawJson)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Analysis timed out", e)
            throw IllegalStateException(
                "El análisis excedió el tiempo de espera (60 s). Verifica tu conexión e inténtalo nuevamente.",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeciesNotIdentifiableException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Species analysis failed", e)
            throw IllegalStateException(
                e.localizedMessage ?: "No se pudo analizar la fotografía.",
                e
            )
        }
    }

    private fun parseOutput(rawJson: String): AiAnalysisOutput {
        val root = JSONObject(rawJson)
        val kingdomStr = root.optString("detectedKingdom", "NO_IDENTIFICABLE").uppercase()
        if (kingdomStr == "NO_IDENTIFICABLE") {
            val reason = root.optString(
                "notIdentifiableReason",
                "No se detectó una especie animal o vegetal identificable en la imagen."
            )
            throw SpeciesNotIdentifiableException(reason)
        }

        val scientificName = root.optString("scientificName").trim()
        val confidenceNote = root.optString("confidenceNote").trim()

        return when (kingdomStr) {
            "FLORA" -> {
                val obj = root.optJSONObject("flora")
                    ?: throw IllegalStateException("Faltan los datos de flora en la respuesta.")
                val flora = FloraDetails(
                    distribucionGeografica = obj.optString("distribucionGeografica"),
                    altitudHabitual = obj.optString("altitudHabitual"),
                    tipoEcosistemaBioma = obj.optString("tipoEcosistemaBioma"),
                    condicionClimaticaSeleccion = obj.optString("condicionClimaticaSeleccion", "No determinable"),
                    sustratoSeleccion = obj.optStringList("sustratoSeleccion"),
                    nombreComun = obj.optString("nombreComun"),
                    nombreCientifico = obj.optString("nombreCientifico", scientificName),
                    familiaBotanica = obj.optString("familiaBotanica"),
                    origenSeleccion = obj.optString("origenSeleccion", "Nativa"),
                    origenNota = obj.optString("origenNota"),
                    estadoConservacionUicn = obj.optString("estadoConservacionUicn", "NE"),
                    abundanciaSitioSeleccion = obj.optStringList("abundanciaSitioSeleccion")
                        .ifEmpty { listOf("Evaluar en campo") },
                    tipoEvidenciaSeleccion = obj.optStringList("tipoEvidenciaSeleccion")
                        .ifEmpty { listOf("Fotografía") },
                    habitoCrecimientoSeleccion = obj.optString("habitoCrecimientoSeleccion", "Hierba"),
                    alturaTotalM = obj.optString("alturaTotalM", "N/A"),
                    alturaCopaM = obj.optString("alturaCopaM", "N/A"),
                    dapCm = obj.optString("dapCm", "N/A"),
                    estadoFenologicoSeleccion = obj.optStringList("estadoFenologicoSeleccion"),
                    exposicionSolarEstratoSeleccion = obj.optString("exposicionSolarEstratoSeleccion", "Subdosel (semisombra)"),
                    caracteristicasTalloTronco = obj.optString("caracteristicasTalloTronco"),
                    caracteristicasHojas = obj.optString("caracteristicasHojas"),
                    caracteristicasFlores = obj.optString("caracteristicasFlores"),
                    caracteristicasFrutosSemillas = obj.optString("caracteristicasFrutosSemillas"),
                    interaccionesEcologicasSeleccion = obj.optStringList("interaccionesEcologicasSeleccion"),
                    interaccionesEcologicasDetalle = obj.optString("interaccionesEcologicasDetalle"),
                    usosEtnobotanicosSeleccion = obj.optStringList("usosEtnobotanicosSeleccion"),
                    usosEtnobotanicosDetalle = obj.optString("usosEtnobotanicosDetalle"),
                    amenazasSeleccion = obj.optStringList("amenazasSeleccion"),
                    notasTecnicas = obj.optString("notasTecnicas")
                )
                AiAnalysisOutput(
                    kingdom = KingdomType.FLORA,
                    scientificName = scientificName.ifBlank { flora.nombreCientifico },
                    confidenceNote = confidenceNote,
                    flora = flora
                )
            }

            "FAUNA" -> {
                val obj = root.optJSONObject("fauna")
                    ?: throw IllegalStateException("Faltan los datos de fauna en la respuesta.")
                val fauna = FaunaDetails(
                    distribucionGeografica = obj.optString("distribucionGeografica"),
                    altitudHabitual = obj.optString("altitudHabitual"),
                    tipoEcosistemaHabitat = obj.optString("tipoEcosistemaHabitat"),
                    condicionClimaticaSeleccion = obj.optString("condicionClimaticaSeleccion", "No determinable"),
                    microhabitatEstratoSeleccion = obj.optStringList("microhabitatEstratoSeleccion"),
                    nombreComun = obj.optString("nombreComun"),
                    nombreCientifico = obj.optString("nombreCientifico", scientificName),
                    claseGrupoAnimalSeleccion = obj.optString("claseGrupoAnimalSeleccion", "Mamífero"),
                    origenSeleccion = obj.optString("origenSeleccion", "Nativa"),
                    origenNota = obj.optString("origenNota"),
                    estadoConservacionUicn = obj.optString("estadoConservacionUicn", "NE"),
                    cantidadIndividuosFoto = obj.optString("cantidadIndividuosFoto", "1"),
                    agrupacionSeleccion = obj.optString("agrupacionSeleccion", "Solitario"),
                    tipoRegistroSeleccion = obj.optStringList("tipoRegistroSeleccion")
                        .ifEmpty { listOf("Fotografía / Video") },
                    sexoSeleccion = obj.optString("sexoSeleccion", "Indeterminado"),
                    etapaVidaSeleccion = obj.optString("etapaVidaSeleccion", "Adulto"),
                    longitudCuerpoCm = obj.optString("longitudCuerpoCm", "N/A"),
                    longitudColaCm = obj.optString("longitudColaCm", "N/A"),
                    envergaduraAlarCm = obj.optString("envergaduraAlarCm", "N/A"),
                    pesoAprox = obj.optString("pesoAprox", "N/A"),
                    coberturaCorporalSeleccion = obj.optString("coberturaCorporalSeleccion", "Pelaje"),
                    coloresMarcasDistintivas = obj.optString("coloresMarcasDistintivas"),
                    rasgosAnatomicosDestacados = obj.optString("rasgosAnatomicosDestacados"),
                    comportamientoSeleccion = obj.optStringList("comportamientoSeleccion"),
                    periodoActividadSeleccion = obj.optStringList("periodoActividadSeleccion"),
                    locomocionSeleccion = obj.optStringList("locomocionSeleccion"),
                    dietaCategoriasSeleccion = obj.optStringList("dietaCategoriasSeleccion"),
                    dietaDetalleHabitual = obj.optString("dietaDetalleHabitual"),
                    interaccionEntorno = obj.optString("interaccionEntorno"),
                    descripcionRastros = obj.optString("descripcionRastros"),
                    amenazasSeleccion = obj.optStringList("amenazasSeleccion"),
                    notasTecnicas = obj.optString("notasTecnicas")
                )
                AiAnalysisOutput(
                    kingdom = KingdomType.FAUNA,
                    scientificName = scientificName.ifBlank { fauna.nombreCientifico },
                    confidenceNote = confidenceNote,
                    fauna = fauna
                )
            }

            else -> throw SpeciesNotIdentifiableException("No se pudo determinar si la especie es Flora o Fauna.")
        }
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length())
            .mapNotNull { idx -> arr.optString(idx)?.trim() }
            .filter { it.isNotEmpty() }
    }
}
