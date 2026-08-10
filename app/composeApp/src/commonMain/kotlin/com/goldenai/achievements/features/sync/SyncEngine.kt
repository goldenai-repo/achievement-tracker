package com.goldenai.achievements.features.sync

import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.core.nowEpochMillis
import com.goldenai.achievements.features.achievements.data.AchievementRepository

/** FastAPI-backed sync adapter. Firestore is deliberately not used here. */
class SyncEngine(private val repository: AchievementRepository) {
    private var lastSync: Long? = null

    suspend fun sync(): AppResult<Unit> = when (val result = repository.sync()) {
        is AppResult.Ok -> {
            lastSync = nowEpochMillis()
            result
        }
        is AppResult.Err -> result
    }

    fun lastSyncAt(): Long? = lastSync
}
