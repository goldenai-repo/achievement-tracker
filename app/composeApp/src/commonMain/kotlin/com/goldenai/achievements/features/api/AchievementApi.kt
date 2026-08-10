package com.goldenai.achievements.features.api

import com.goldenai.achievements.features.auth.data.AuthRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogPlace(
    val id: String,
    val kind: String,
    val code: String,
    val name: String,
    val nameAscii: String? = null,
    val parentId: String? = null,
)

@Serializable
data class CheckInRequest(
    @SerialName("entity_id") val entityId: String,
    @SerialName("visited_at") val visitedAt: String,
    val note: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
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

interface AchievementApi {
    suspend fun searchCatalog(kind: String, query: String?, parentId: String? = null, limit: Int = 25): List<CatalogPlace>
    suspend fun listCheckins(limit: Int = 50): List<CheckInResponse>
    suspend fun createCheckIn(request: CheckInRequest): CheckInResponse
    suspend fun summary(): SummaryResponse
}

expect fun createAchievementApi(auth: AuthRepository, baseUrl: String): AchievementApi
