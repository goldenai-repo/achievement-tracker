package com.goldenai.achievements.features.api

import com.goldenai.achievements.features.auth.data.AuthRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

@Serializable
data class CatalogPlace(
    val id: String,
    val kind: String,
    val code: String,
    val name: String,
    val nameAscii: String? = null,
    val parentId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val boundaryGeoJsonUrl: String? = null,
    val bounds: CatalogBounds? = null,
)

/** A place selection passed from a map surface into the shared check-in form. */
data class CheckInSelection(
    val country: CatalogPlace,
    val place: CatalogPlace,
) {
    val isCountrySelection: Boolean get() = place.kind == "country"
}

@Serializable
data class CheckInRequest(
    @SerialName("entity_id") val entityId: String,
    @SerialName("visited_at") val visitedAt: String,
    val note: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class CheckInUpdateRequest(
    @SerialName("visited_at") val visitedAt: String,
    val note: String? = null,
)

@Serializable
data class CheckInResponse(
    val id: String,
    val entityId: String,
    val entity: CatalogPlace? = null,
    val visitedAt: String,
    val note: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: String,
)

@Serializable
data class SummaryResponse(
    val checkinCount: Int,
    val uniqueUnlockCount: Int,
    val byKind: Map<String, Int> = emptyMap(),
)

@Serializable
data class MeResponse(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ProfileUpdateRequest(
    val username: String,
)

@Serializable
data class AchievementGroupResponse(
    val entityId: String,
    val entity: CatalogPlace? = null,
    val visitCount: Int,
    val firstVisitedAt: String,
    val lastVisitedAt: String,
    val checkins: List<CheckInResponse> = emptyList(),
)

@Serializable
data class GeographyRankingEntry(
    val rank: Int,
    val displayName: String,
    val countryCount: Int,
    val admin1Count: Int,
    val isCurrentUser: Boolean = false,
)

@Serializable
data class GeographyRankingResponse(
    val category: String = "geography",
    val primaryMetric: String = "uniqueAdmin1",
    val secondaryMetric: String = "uniqueCountries",
    val entries: List<GeographyRankingEntry> = emptyList(),
    val me: GeographyRankingEntry? = null,
)

interface AchievementApi {
    suspend fun getMe(): MeResponse
    suspend fun updateProfile(request: ProfileUpdateRequest): MeResponse
    suspend fun deleteAccount()
    suspend fun searchCatalog(kind: String, query: String?, parentId: String? = null, limit: Int = 25): List<CatalogPlace>
    suspend fun listCheckins(limit: Int = 50): List<CheckInResponse>
    suspend fun listAchievements(limit: Int = 50): List<AchievementGroupResponse>
    suspend fun createCheckIn(request: CheckInRequest): CheckInResponse
    suspend fun updateCheckIn(checkinId: String, request: CheckInUpdateRequest): CheckInResponse
    suspend fun deleteCheckIn(checkinId: String)
    suspend fun summary(): SummaryResponse
    suspend fun getGeographyRanking(limit: Int = 50): GeographyRankingResponse
}

expect fun createAchievementApi(auth: AuthRepository, baseUrl: String): AchievementApi
