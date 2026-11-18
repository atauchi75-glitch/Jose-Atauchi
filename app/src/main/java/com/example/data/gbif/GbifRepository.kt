package com.example.data.gbif

import android.util.Log
import com.example.model.GbifTaxonVerification
import com.example.model.KingdomType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "GbifRepository"
private const val GBIF_BASE_URL = "https://api.gbif.org/v1/"

class GbifRepository(
    private val api: GbifApiService = createDefaultApi()
) {
    suspend fun verifySpecies(
        scientificName: String,
        kingdomType: KingdomType
    ): GbifTaxonVerification = withContext(Dispatchers.IO) {
        val cleanName = scientificName.trim()
        if (cleanName.isBlank()) {
            return@withContext GbifTaxonVerification(isVerifiedInGbif = false)
        }

        val expectedKingdom = when (kingdomType) {
            KingdomType.FLORA -> "Plantae"
            KingdomType.FAUNA -> "Animalia"
        }

        try {
            val matchResp = api.matchSpecies(
                name = cleanName,
                kingdom = expectedKingdom,
                strict = false
            )
            val match = if (matchResp.isSuccessful) matchResp.body() else null
            val usageKey = match?.usageKey

            if (usageKey == null || match.matchType == "NONE") {
                return@withContext GbifTaxonVerification(
                    canonicalName = cleanName,
                    scientificName = cleanName,
                    isVerifiedInGbif = false
                )
            }

            coroutineScope {
                val detailDeferred = async {
                    runCatching { api.getSpeciesDetail(usageKey).body() }.getOrNull()
                }
                val vernacularDeferred = async {
                    runCatching { api.getVernacularNames(usageKey).body()?.results.orEmpty() }
                        .getOrDefault(emptyList())
                }
                val iucnDeferred = async {
                    runCatching {
                        val resp = api.getIucnRedListCategory(usageKey)
                        if (resp.isSuccessful) resp.body()?.code ?: resp.body()?.category else null
                    }.getOrNull()
                }
                val occDeferred = async {
                    runCatching {
                        val resp = api.searchOccurrencesCount(usageKey)
                        if (resp.isSuccessful) resp.body()?.count else null
                    }.getOrNull()
                }

                val detail = detailDeferred.await()
                val vernaculars = vernacularDeferred.await()
                val iucnCode = iucnDeferred.await()
                val occCount = occDeferred.await()

                val spanishOrCommon = vernaculars
                    .filter { !it.vernacularName.isNullOrBlank() }
                    .sortedByDescending { item ->
                        val lang = item.language.orEmpty().lowercase()
                        when {
                            lang == "spa" || lang == "es" -> 3
                            lang == "eng" || lang == "en" -> 2
                            else -> 1
                        }
                    }
                    .mapNotNull { it.vernacularName?.trim() }
                    .distinct()
                    .take(5)

                GbifTaxonVerification(
                    usageKey = usageKey,
                    canonicalName = detail?.canonicalName ?: match.canonicalName ?: cleanName,
                    scientificName = detail?.scientificName ?: match.scientificName ?: cleanName,
                    authorship = detail?.authorship.orEmpty(),
                    kingdom = detail?.kingdom ?: match.kingdom ?: expectedKingdom,
                    phylum = detail?.phylum ?: match.phylum.orEmpty(),
                    clazz = detail?.clazz ?: match.clazz.orEmpty(),
                    order = detail?.order ?: match.order.orEmpty(),
                    family = detail?.family ?: match.family.orEmpty(),
                    genus = detail?.genus ?: match.genus.orEmpty(),
                    taxonomicStatus = detail?.taxonomicStatus ?: match.status.orEmpty(),
                    matchConfidence = match.confidence,
                    occurrenceCount = occCount,
                    iucnCategoryFromGbif = iucnCode,
                    vernacularNames = spanishOrCommon,
                    isVerifiedInGbif = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "GBIF lookup failed for $cleanName: ${e.localizedMessage}")
            GbifTaxonVerification(
                canonicalName = cleanName,
                scientificName = cleanName,
                isVerifiedInGbif = false
            )
        }
    }

    companion object {
        fun createDefaultApi(): GbifApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(GBIF_BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GbifApiService::class.java)
        }
    }
}
