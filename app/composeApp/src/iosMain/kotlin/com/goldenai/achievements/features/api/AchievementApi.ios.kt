package com.goldenai.achievements.features.api

import com.goldenai.achievements.features.auth.data.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

actual fun createAchievementApi(auth: AuthRepository, baseUrl: String): AchievementApi =
    IosAchievementApi(auth, baseUrl)

/**
 * Darwin/Ktor client mirroring [AndroidAchievementApi] endpoints, bearer auth,
 * and timeouts (15s connect / 20s request).
 */
private class IosAchievementApi(
    private val auth: AuthRepository,
    baseUrl: String,
) : AchievementApi {
    private val root = baseUrl.trimEnd('/')
    private val client = HttpClient(Darwin) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }

    override suspend fun getMe(): MeResponse = decode(
        method = "GET",
        path = "/v1/me",
        serializer = MeResponse.serializer(),
    )

    override suspend fun searchCatalog(
        kind: String,
        query: String?,
        parentId: String?,
        limit: Int,
    ): List<CatalogPlace> = decode(
        method = "GET",
        path = "/v1/catalog?kind=${encode(kind)}" +
            query?.takeIf { it.isNotBlank() }?.let { "&q=${encode(it)}" }.orEmpty() +
            parentId?.let { "&parent_id=${encode(it)}" }.orEmpty() +
            "&limit=$limit",
        serializer = kotlinx.serialization.builtins.ListSerializer(CatalogPlace.serializer()),
    )

    override suspend fun listCheckins(limit: Int): List<CheckInResponse> = decode(
        method = "GET",
        path = "/v1/checkins?limit=$limit",
        serializer = kotlinx.serialization.builtins.ListSerializer(CheckInResponse.serializer()),
    )

    override suspend fun listAchievements(limit: Int): List<AchievementGroupResponse> = decode(
        method = "GET",
        path = "/v1/achievements?limit=$limit",
        serializer = kotlinx.serialization.builtins.ListSerializer(AchievementGroupResponse.serializer()),
    )

    override suspend fun createCheckIn(request: CheckInRequest): CheckInResponse = decode(
        method = "POST",
        path = "/v1/checkins",
        body = json.encodeToString(CheckInRequest.serializer(), request),
        serializer = CheckInResponse.serializer(),
    )

    override suspend fun updateCheckIn(
        checkinId: String,
        request: CheckInUpdateRequest,
    ): CheckInResponse = decode(
        method = "PATCH",
        path = "/v1/checkins/${encode(checkinId)}",
        body = json.encodeToString(CheckInUpdateRequest.serializer(), request),
        serializer = CheckInResponse.serializer(),
    )

    override suspend fun deleteCheckIn(checkinId: String) {
        decodeNoContent(
            method = "DELETE",
            path = "/v1/checkins/${encode(checkinId)}",
        )
    }

    override suspend fun summary(): SummaryResponse = decode(
        method = "GET",
        path = "/v1/summary",
        serializer = SummaryResponse.serializer(),
    )

    private suspend fun <T> decode(
        method: String,
        path: String,
        body: String? = null,
        serializer: KSerializer<T>,
    ): T {
        val (code, response) = execute(method, path, body)
        if (code !in 200..299) throw ApiException(code, response)
        return json.decodeFromString(serializer, response)
    }

    private suspend fun decodeNoContent(
        method: String,
        path: String,
    ) {
        val (code, response) = execute(method, path, body = null)
        if (code !in 200..299) throw ApiException(code, response)
    }

    private suspend fun execute(
        method: String,
        path: String,
        body: String?,
    ): Pair<Int, String> {
        val token = auth.idToken()
            ?: throw IllegalStateException("Sign in to use the Achievement Tracker API.")
        val url = root + path
        val response = when (method) {
            "GET" -> client.get(url) {
                authHeaders(token)
            }
            "POST" -> client.post(url) {
                authHeaders(token)
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            "PATCH" -> client.patch(url) {
                authHeaders(token)
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            "DELETE" -> client.delete(url) {
                authHeaders(token)
            }
            else -> error("Unsupported method: $method")
        }
        val text = response.bodyAsText()
        return response.status.value to text
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(token: String) {
        header("Accept", "application/json")
        header("Authorization", "Bearer $token")
    }

    private fun encode(value: String): String = value.encodeURLParameter()
}

private class ApiException(code: Int, body: String) :
    Exception("API $code: ${body.take(300)}")
