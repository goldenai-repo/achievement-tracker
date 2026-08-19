package com.goldenai.achievements.features.achievements.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.core.formatIsoUtc
import com.goldenai.achievements.core.nowEpochMillis
import com.goldenai.achievements.core.parseIsoUtc
import com.goldenai.achievements.core.randomUuid
import com.goldenai.achievements.core.runCatchingSuspendResult
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.db.AchievementDatabase
import com.goldenai.achievements.db.AchievementEntity
import com.goldenai.achievements.features.api.AchievementApi
import com.goldenai.achievements.features.api.CatalogPlace
import com.goldenai.achievements.features.api.CheckInRequest
import com.goldenai.achievements.features.api.CheckInResponse
import com.goldenai.achievements.features.api.CheckInUpdateRequest
import com.goldenai.achievements.features.auth.data.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Local SQLite is the source of truth for the UI. Authenticated writes go to
 * FastAPI first and are then cached locally; guest writes remain pending until
 * a future sync run can upload them.
 */
class AchievementRepository(
    private val db: AchievementDatabase,
    private val api: AchievementApi,
    private val auth: AuthRepository,
) {
    private val q = db.achievementQueries
    private val _summary = MutableStateFlow<com.goldenai.achievements.features.api.SummaryResponse?>(null)
    val summary: StateFlow<com.goldenai.achievements.features.api.SummaryResponse?> = _summary.asStateFlow()

    fun watchAll(): Flow<List<Achievement>> =
        q.selectAll().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toModel() } }

    fun watchByType(type: String): Flow<List<Achievement>> =
        q.selectByType(type).asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toModel() } }

    fun watchRecent(limit: Long): Flow<List<Achievement>> =
        q.selectRecent(limit).asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toModel() } }

    fun watchCountsByType(): Flow<Map<String, Long>> =
        q.countsByType().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.associate { it.type to it.cnt } }

    fun watchPendingCount(): Flow<Long> =
        q.countPending().asFlow().mapToOne(Dispatchers.Default)

    fun watchCountAll(): Flow<Long> =
        q.countAll().asFlow().mapToOne(Dispatchers.Default)

    fun watchUniqueEntityCount(): Flow<Long> =
        q.countUniqueEntities().asFlow().mapToOne(Dispatchers.Default)

    suspend fun get(id: String): Achievement? = withContext(Dispatchers.Default) {
        q.selectById(id).executeAsOneOrNull()?.toModel()
    }

    suspend fun refresh(): AppResult<Unit> = runCatchingSuspendResult("Could not load check-ins") {
        val uid = auth.currentUser?.uid
        if (uid != null) prepareAccount(uid)
        val remote = api.listCheckins()
        withContext(Dispatchers.Default) {
            db.transaction {
                remote.forEach { response -> upsertLocal(response, pendingSync = 0) }
            }
        }
        try {
            _summary.value = api.summary()
        } catch (_: Throwable) {
            // The history is still useful if the optional summary request fails.
        }
        if (uid != null) withContext(Dispatchers.Default) { q.setMeta("lastSyncedUid", uid) }
    }

    suspend fun create(
        place: CatalogPlace,
        timestamp: Long,
        notes: String?,
    ): AppResult<Achievement> {
        val request = CheckInRequest(
            entityId = place.id,
            visitedAt = formatIsoUtc(timestamp),
            note = notes?.takeIf { it.isNotBlank() },
            latitude = place.latitude,
            longitude = place.longitude,
        )
        val result = if (auth.currentUser != null) {
            runCatchingSuspendResult("Could not create check-in") { api.createCheckIn(request) }
        } else {
            AppResult.Ok(null)
        }

        return when (result) {
            is AppResult.Ok -> {
                val achievement = result.value?.let { response ->
                    withContext(Dispatchers.Default) {
                        // Keep the catalog coordinates selected by the user
                        // even if an older API response omits them.
                        val model = response.toModel(place)
                        upsertLocal(model, pendingSync = 0)
                        model
                    }
                } ?: withContext(Dispatchers.Default) {
                    val local = Achievement(
                        id = randomUuid(),
                        entityId = place.id,
                        entityKind = place.kind,
                        entityCode = place.code,
                        parentId = place.parentId,
                        type = place.toAchievementType(),
                        timestamp = timestamp,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        locationName = place.name,
                        content = place.name,
                        notes = notes?.takeIf { it.isNotBlank() },
                        createdAt = nowEpochMillis(),
                        updatedAt = nowEpochMillis(),
                    )
                    upsertLocal(local, pendingSync = 1)
                    local
                }
                AppResult.Ok(achievement)
            }
            is AppResult.Err -> result
        }
    }

    /** Deletes one visit locally immediately and queues a server delete when needed. */
    suspend fun delete(id: String): AppResult<Unit> {
        val row = withContext(Dispatchers.Default) { q.selectById(id).executeAsOneOrNull() }
            ?: return AppResult.Err("Check-in not found")

        // A guest row has never reached the server, so removing it locally is enough.
        if (row.pendingSync == 1L) {
            withContext(Dispatchers.Default) { q.deleteById(id) }
            return AppResult.Ok(Unit)
        }

        if (auth.currentUser == null) {
            return AppResult.Err("Sign in before deleting a synced check-in.")
        }

        return when (val result = runCatchingSuspendResult("Could not delete check-in") {
            api.deleteCheckIn(id)
        }) {
            is AppResult.Ok -> {
                withContext(Dispatchers.Default) { q.deleteById(id) }
                // Refresh the remote summary so Home and Profile immediately reflect
                // a place that may have lost its final visit.
                refresh()
                AppResult.Ok(Unit)
            }
            is AppResult.Err -> {
                // Hide it now and retry the DELETE during the next sync.
                withContext(Dispatchers.Default) {
                    q.markDeleted(updatedAt = nowEpochMillis(), id = id)
                }
                AppResult.Ok(Unit)
            }
        }
    }

    /** Updates only the visit date and note; the catalog place remains fixed. */
    suspend fun update(id: String, timestamp: Long, notes: String?): AppResult<Achievement> {
        val row = withContext(Dispatchers.Default) { q.selectById(id).executeAsOneOrNull() }
            ?: return AppResult.Err("Check-in not found")
        val normalizedNotes = notes?.takeIf { it.isNotBlank() }

        // Guest records have not reached the server yet. Updating the local
        // create payload is enough; the next sync will upload the latest data.
        if (row.pendingSync == 1L) {
            withContext(Dispatchers.Default) {
                q.updateDetails(
                    timestamp = timestamp,
                    notes = normalizedNotes,
                    updatedAt = nowEpochMillis(),
                    id = id,
                )
            }
            return get(id)?.let { AppResult.Ok(it) }
                ?: AppResult.Err("Could not update local check-in")
        }

        if (auth.currentUser == null) {
            return AppResult.Err("Sign in before editing a synced check-in.")
        }

        return when (val result = runCatchingSuspendResult("Could not update check-in") {
            api.updateCheckIn(
                id,
                CheckInUpdateRequest(
                    visitedAt = formatIsoUtc(timestamp),
                    note = normalizedNotes,
                ),
            )
        }) {
            is AppResult.Ok -> {
                withContext(Dispatchers.Default) {
                    upsertLocal(result.value, pendingSync = 0)
                    result.value.toModel()
                }.let { AppResult.Ok(it) }
            }
            is AppResult.Err -> result
        }
    }

    /** Uploads local guest rows one at a time using the existing API contract. */
    suspend fun sync(): AppResult<Unit> {
        if (auth.currentUser == null) return AppResult.Ok(Unit)
        return runCatchingSuspendResult("Could not sync check-ins") {
            val pending = withContext(Dispatchers.Default) { q.selectPending().executeAsList() }
            pending.forEach { row ->
                if (row.deleted == 1L) {
                    api.deleteCheckIn(row.id)
                    withContext(Dispatchers.Default) { q.deleteById(row.id) }
                } else {
                    val response = api.createCheckIn(
                        CheckInRequest(
                            entityId = row.entityId,
                            visitedAt = formatIsoUtc(row.timestamp),
                            note = row.notes,
                            latitude = row.latitude,
                            longitude = row.longitude,
                        ),
                    )
                    withContext(Dispatchers.Default) {
                        db.transaction {
                            q.deleteById(row.id)
                            upsertLocal(response, pendingSync = 0)
                        }
                    }
                }
            }
            refresh()
            Unit
        }
    }

    private fun upsertLocal(response: CheckInResponse, pendingSync: Long) {
        val place = response.entity ?: CatalogPlace(
            id = response.entityId,
            kind = "country",
            code = response.entityId.removePrefix("country:"),
            name = response.entityId,
        )
        upsertLocal(response.toModel(place), pendingSync)
    }

    private fun upsertLocal(achievement: Achievement, pendingSync: Long) {
        q.upsert(
            id = achievement.id,
            entityId = achievement.entityId,
            entityKind = achievement.entityKind,
            entityCode = achievement.entityCode,
            parentId = achievement.parentId,
            type = achievement.type,
            subtype = achievement.subtype,
            timestamp = achievement.timestamp,
            latitude = achievement.latitude,
            longitude = achievement.longitude,
            locationName = achievement.locationName,
            content = achievement.content,
            notes = achievement.notes,
            mediaUrl = achievement.mediaUrl,
            createdAt = achievement.createdAt,
            updatedAt = achievement.updatedAt,
            deleted = 0,
            pendingSync = pendingSync,
        )
    }

    private suspend fun prepareAccount(uid: String) = withContext(Dispatchers.Default) {
        val lastUid = q.getMeta("lastSyncedUid").executeAsOneOrNull()
        if (lastUid != null && lastUid != uid) q.deleteSynced()
    }
}

private fun CheckInResponse.toModel(place: CatalogPlace = entity ?: error("Missing catalog entity")): Achievement = Achievement(
    id = id,
    entityId = entityId,
    entityKind = place.kind,
    entityCode = place.code,
    parentId = place.parentId,
    type = place.toAchievementType(),
    timestamp = parseIsoUtc(visitedAt),
    locationName = place.name,
    content = place.name,
    notes = note,
    latitude = latitude ?: place.latitude,
    longitude = longitude ?: place.longitude,
    createdAt = parseIsoUtc(createdAt),
    updatedAt = parseIsoUtc(createdAt),
)

private fun CatalogPlace.toAchievementType(): String = when (kind) {
    "country" -> "geography.country"
    "admin1" -> "geography.state"
    else -> "geography.city"
}

fun AchievementEntity.toModel(): Achievement = Achievement(
    id = id,
    entityId = entityId,
    entityKind = entityKind,
    entityCode = entityCode,
    parentId = parentId,
    type = type,
    subtype = subtype,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    locationName = locationName,
    content = content,
    notes = notes,
    mediaUrl = mediaUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
