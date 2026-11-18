package com.example.data.gbif

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GbifSpeciesMatchDto(
    val usageKey: Long? = null,
    val scientificName: String? = null,
    val canonicalName: String? = null,
    val rank: String? = null,
    val status: String? = null,
    val confidence: Int? = null,
    val matchType: String? = null,
    val kingdom: String? = null,
    val phylum: String? = null,
    @Json(name = "class")
    val clazz: String? = null,
    val order: String? = null,
    val family: String? = null,
    val genus: String? = null,
    val species: String? = null
)

@JsonClass(generateAdapter = true)
data class GbifSpeciesDetailDto(
    val key: Long? = null,
    val scientificName: String? = null,
    val canonicalName: String? = null,
    val authorship: String? = null,
    val taxonomicStatus: String? = null,
    val vernacularName: String? = null,
    val kingdom: String? = null,
    val phylum: String? = null,
    @Json(name = "class")
    val clazz: String? = null,
    val order: String? = null,
    val family: String? = null,
    val genus: String? = null
)

@JsonClass(generateAdapter = true)
data class GbifVernacularListDto(
    val results: List<GbifVernacularNameDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GbifVernacularNameDto(
    val vernacularName: String? = null,
    val language: String? = null
)

@JsonClass(generateAdapter = true)
data class GbifIucnCategoryDto(
    val category: String? = null,
    val code: String? = null
)

@JsonClass(generateAdapter = true)
data class GbifOccurrenceSearchDto(
    val count: Long? = null
)

interface GbifApiService {
    @GET("species/match")
    suspend fun matchSpecies(
        @Query("name") name: String,
        @Query("kingdom") kingdom: String? = null,
        @Query("strict") strict: Boolean = false
    ): Response<GbifSpeciesMatchDto>

    @GET("species/{key}")
    suspend fun getSpeciesDetail(
        @Path("key") usageKey: Long
    ): Response<GbifSpeciesDetailDto>

    @GET("species/{key}/vernacularNames")
    suspend fun getVernacularNames(
        @Path("key") usageKey: Long,
        @Query("limit") limit: Int = 20
    ): Response<GbifVernacularListDto>

    @GET("species/{key}/iucnRedListCategory")
    suspend fun getIucnRedListCategory(
        @Path("key") usageKey: Long
    ): Response<GbifIucnCategoryDto>

    @GET("occurrence/search")
    suspend fun searchOccurrencesCount(
        @Query("taxonKey") taxonKey: Long,
        @Query("limit") limit: Int = 0
    ): Response<GbifOccurrenceSearchDto>
}
