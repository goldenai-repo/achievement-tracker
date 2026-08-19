package com.goldenai.achievements.features.achievements.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.core.db.databaseDispatcher
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.core.nowEpochMillis
import com.goldenai.achievements.core.randomUuid
import com.goldenai.achievements.core.runCatchingResult
import com.goldenai.achievements.db.AchievementDatabase
import com.goldenai.achievements.db.AchievementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The local database is the source of truth for the UI in both guest and
 * signed-in modes. Every write lands here first with pendingSync = 1; the sync
 * layer pushes pending rows to Firestore when a user is signed in.
 */
class AchievementRepository(
    private val db: AchievementDatabase,
    /** Invoked after any successful local write so sync can be scheduled. */
    var onLocalChange: () -> Unit = {},
) {
    private val q = db.achievementQueries

    fun watchAll(): Flow<List<Achievement>> =
        q.selectAll().asFlow().mapToList(databaseDispatcher).map { rows -> rows.map { it.toModel() } }

    fun watchByType(type: String): Flow<List<Achievement>> =
        q.selectByType(type).asFlow().mapToList(databaseDispatcher).map { rows -> rows.map { it.toModel() } }

    fun watchByTypes(types: Collection<String>): Flow<List<Achievement>> {
        if (types.isEmpty()) return flowOf(emptyList())
        return q.selectByTypes(types).asFlow().mapToList(databaseDispatcher)
            .map { rows -> rows.map { it.toModel() } }
    }

    fun watchRecent(limit: Long): Flow<List<Achievement>> =
        q.selectRecent(limit).asFlow().mapToList(databaseDispatcher).map { rows -> rows.map { it.toModel() } }

    fun watchCountsByType(): Flow<Map<String, Long>> =
        q.countsByType().asFlow().mapToList(databaseDispatcher)
            .map { rows -> rows.associate { it.type to it.cnt } }

    fun watchPendingCount(): Flow<Long> =
        q.countPending().asFlow().mapToOne(databaseDispatcher)

    suspend fun get(id: String): Achievement? = withContext(databaseDispatcher) {
        q.selectById(id).executeAsOneOrNull()?.toModel()
    }

    suspend fun create(
        type: String,
        content: String,
        timestamp: Long,
        subtype: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationName: String? = null,
        notes: String? = null,
        mediaUrl: String? = null,
        ownerUid: String? = null,
    ): AppResult<Achievement> = withContext(databaseDispatcher) {
        runCatchingResult("Could not save achievement") {
            val now = nowEpochMillis()
            val achievement = Achievement(
                id = randomUuid(),
                type = type,
                subtype = subtype,
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                locationName = locationName?.takeIf { it.isNotBlank() },
                content = content.trim(),
                notes = notes?.takeIf { it.isNotBlank() },
                mediaUrl = mediaUrl?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
                ownerUid = ownerUid?.takeIf { it.isNotBlank() },
            )
            upsertLocal(achievement)
            achievement
        }.also { if (it is AppResult.Ok) onLocalChange() }
    }

    suspend fun update(achievement: Achievement): AppResult<Achievement> = withContext(databaseDispatcher) {
        runCatchingResult("Could not update achievement") {
            val updated = achievement.copy(
                content = achievement.content.trim(),
                locationName = achievement.locationName?.takeIf { it.isNotBlank() },
                notes = achievement.notes?.takeIf { it.isNotBlank() },
                updatedAt = nowEpochMillis(),
            )
            upsertLocal(updated)
            updated
        }.also { if (it is AppResult.Ok) onLocalChange() }
    }

    /** Soft delete: the row becomes a tombstone that syncs to the cloud. */
    suspend fun delete(id: String): AppResult<Unit> = withContext(databaseDispatcher) {
        runCatchingResult("Could not delete achievement") {
            q.markDeleted(updatedAt = nowEpochMillis(), id = id)
            Unit
        }.also { if (it is AppResult.Ok) onLocalChange() }
    }

    private fun upsertLocal(a: Achievement) {
        q.upsert(
            id = a.id,
            type = a.type,
            subtype = a.subtype,
            timestamp = a.timestamp,
            latitude = a.latitude,
            longitude = a.longitude,
            locationName = a.locationName,
            content = a.content,
            notes = a.notes,
            mediaUrl = a.mediaUrl,
            createdAt = a.createdAt,
            updatedAt = a.updatedAt,
            deleted = 0,
            pendingSync = 1,
            ownerUid = a.ownerUid,
        )
    }
}

fun AchievementEntity.toModel(): Achievement = Achievement(
    id = id,
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
    ownerUid = ownerUid,
)
