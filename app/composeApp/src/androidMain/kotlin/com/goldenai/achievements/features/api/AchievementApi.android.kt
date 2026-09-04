package com.goldenai.achievements.features.api

import com.goldenai.achievements.features.auth.data.AuthRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

actual fun createAchievementApi(auth: AuthRepository, baseUrl: String): AchievementApi =
    AndroidAchievementApi(auth, baseUrl)

private class AndroidAchievementApi(
    private val auth: AuthRepository,
    baseUrl: String,
) : AchievementApi {
    private val root = baseUrl.trimEnd('/')

    override suspend fun getMe(): MeResponse = request(
        method = "GET",
        path = "/v1/me",
        serializer = MeResponse.serializer(),
    )

    override suspend fun updateProfile(payload: ProfileUpdateRequest): MeResponse = request(
        method = "PATCH",
        path = "/v1/me",
        body = json.encodeToString(ProfileUpdateRequest.serializer(), payload),
        serializer = MeResponse.serializer(),
    )

    override suspend fun deleteAccount() {
        requestNoContent(
            method = "DELETE",
            path = "/v1/me",
        )
    }

    override suspend fun searchCatalog(
        kind: String,
        query: String?,
        parentId: String?,
        limit: Int,
    ): List<CatalogPlace> = request(
        method = "GET",
        path = "/v1/catalog?kind=${encode(kind)}" +
            query?.takeIf { it.isNotBlank() }?.let { "&q=${encode(it)}" }.orEmpty() +
            parentId?.let { "&parent_id=${encode(it)}" }.orEmpty() +
            "&limit=$limit",
        serializer = kotlinx.serialization.builtins.ListSerializer(CatalogPlace.serializer()),
    )

    override suspend fun listCheckins(limit: Int): List<CheckInResponse> = request(
        method = "GET",
        path = "/v1/checkins?limit=$limit",
        serializer = kotlinx.serialization.builtins.ListSerializer(CheckInResponse.serializer()),
    )

    override suspend fun listAchievements(limit: Int): List<AchievementGroupResponse> = request(
        method = "GET",
        path = "/v1/achievements?limit=$limit",
        serializer = kotlinx.serialization.builtins.ListSerializer(AchievementGroupResponse.serializer()),
    )

    override suspend fun createCheckIn(payload: CheckInRequest): CheckInResponse = request(
        method = "POST",
        path = "/v1/checkins",
        body = json.encodeToString(CheckInRequest.serializer(), payload),
        serializer = CheckInResponse.serializer(),
    )

    override suspend fun updateCheckIn(
        checkinId: String,
        payload: CheckInUpdateRequest,
    ): CheckInResponse = request(
        method = "PATCH",
        path = "/v1/checkins/${encode(checkinId)}",
        body = json.encodeToString(CheckInUpdateRequest.serializer(), payload),
        serializer = CheckInResponse.serializer(),
    )

    override suspend fun deleteCheckIn(checkinId: String) {
        requestNoContent(
            method = "DELETE",
            path = "/v1/checkins/${encode(checkinId)}",
        )
    }

    override suspend fun summary(): SummaryResponse = request(
        method = "GET",
        path = "/v1/summary",
        serializer = SummaryResponse.serializer(),
    )

    override suspend fun getGeographyRanking(limit: Int): GeographyRankingResponse = request(
        method = "GET",
        path = "/v1/rankings/geography?limit=$limit",
        serializer = GeographyRankingResponse.serializer(),
    )

    private suspend fun <T> request(
        method: String,
        path: String,
        body: String? = null,
        serializer: KSerializer<T>,
    ): T = withContext(Dispatchers.IO) {
        val token = auth.idToken() ?: throw IllegalStateException("Sign in to use the Achievement Tracker API.")
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("API $code: ${response.take(300)}")
            json.decodeFromString(serializer, response)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestNoContent(
        method: String,
        path: String,
    ): Unit = withContext(Dispatchers.IO) {
        val token = auth.idToken() ?: throw IllegalStateException("Sign in to use the Achievement Tracker API.")
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (connection.responseCode !in 200..299) {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("API ${connection.responseCode}: ${response.take(300)}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
