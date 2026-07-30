package com.goldenai.achievements.features.sync

import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.core.nowEpochMillis
import com.goldenai.achievements.db.AchievementDatabase
import com.goldenai.achievements.db.AchievementEntity
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.GeoPoint
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Firestore document shape, matching the schema in CLAUDE.md:
 * users/{uid}/achievements/{achievementId}. Timestamps are Firestore
 * timestamps (UTC); location is a nullable geopoint.
 */
@Serializable
data class AchievementDoc(
    val type: String,
    val subtype: String? = null,
    val timestamp: Timestamp,
    val location: GeoPoint? = null,
    val locationName: String? = null,
    val content: String,
    val notes: String? = null,
    val mediaUrl: String? = null,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val deleted: Boolean = false,
)

/** Pure decision logic, unit-testable without a database or Firestore. */
object SyncLogic {
    /**
     * Last-write-wins on updatedAt: apply the remote copy when we have no
     * local row or the remote edit is strictly newer.
     */
    fun shouldApplyRemote(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
        localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt

    fun timestampToMillis(ts: Timestamp): Long =
        ts.seconds * 1000 + ts.nanoseconds / 1_000_000

    fun millisToTimestamp(millis: Long): Timestamp =
        Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())
}

class SyncEngine(
    private val db: AchievementDatabase,
    private val cloudAvailable: Boolean,
) {
    private val q = db.achievementQueries

    private fun collection(uid: String): CollectionReference =
        Firebase.firestore.collection("users").document(uid).collection("achievements")

    /**
     * Two-way sync: push rows flagged pendingSync, then pull the remote
     * collection and merge with last-write-wins. Guest-created rows keep
     * pendingSync = 1 until the first signed-in sync, which is what uploads
     * pre-login data to a fresh account.
     */
    suspend fun sync(uid: String): AppResult<Unit> {
        if (!cloudAvailable) return AppResult.Err("Cloud sync is not configured in this build.")
        return try {
            handleAccountSwitch(uid)
            push(uid)
            pull(uid)
            withContext(Dispatchers.Default) {
                q.setMeta("lastSyncedUid", uid)
                q.setMeta("lastSyncAt", nowEpochMillis().toString())
            }
            AppResult.Ok(Unit)
        } catch (t: Throwable) {
            AppResult.Err(t.message ?: "Sync failed", t)
        }
    }

    suspend fun lastSyncAt(): Long? = withContext(Dispatchers.Default) {
        q.getMeta("lastSyncAt").executeAsOneOrNull()?.toLongOrNull()
    }

    /**
     * If a different account was synced on this device before, drop rows that
     * belong to that account (pendingSync = 0). Unsynced local rows are kept
     * so guest data can be uploaded to the account that signs in.
     */
    private suspend fun handleAccountSwitch(uid: String) = withContext(Dispatchers.Default) {
        val lastUid = q.getMeta("lastSyncedUid").executeAsOneOrNull()
        if (lastUid != null && lastUid != uid) {
            q.deleteSynced()
        }
    }

    private suspend fun push(uid: String) {
        val pending = withContext(Dispatchers.Default) { q.selectPending().executeAsList() }
        for (row in pending) {
            collection(uid).document(row.id).set(row.toDoc(), merge = true)
            withContext(Dispatchers.Default) { q.clearPending(row.id) }
        }
    }

    private suspend fun pull(uid: String) {
        val snapshot = collection(uid).get()
        val remote = snapshot.documents.map { it.id to it.data<AchievementDoc>() }
        withContext(Dispatchers.Default) {
            db.transaction {
                for ((id, doc) in remote) {
                    val local = q.selectById(id).executeAsOneOrNull()
                    val remoteUpdatedAt = SyncLogic.timestampToMillis(doc.updatedAt)
                    if (SyncLogic.shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                        q.upsert(
                            id = id,
                            type = doc.type,
                            subtype = doc.subtype,
                            timestamp = SyncLogic.timestampToMillis(doc.timestamp),
                            latitude = doc.location?.latitude,
                            longitude = doc.location?.longitude,
                            locationName = doc.locationName,
                            content = doc.content,
                            notes = doc.notes,
                            mediaUrl = doc.mediaUrl,
                            createdAt = SyncLogic.timestampToMillis(doc.createdAt),
                            updatedAt = remoteUpdatedAt,
                            deleted = if (doc.deleted) 1 else 0,
                            pendingSync = 0,
                        )
                    }
                }
            }
        }
    }
}

private fun AchievementEntity.toDoc(): AchievementDoc = AchievementDoc(
    type = type,
    subtype = subtype,
    timestamp = SyncLogic.millisToTimestamp(timestamp),
    location = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
    locationName = locationName,
    content = content,
    notes = notes,
    mediaUrl = mediaUrl,
    createdAt = SyncLogic.millisToTimestamp(createdAt),
    updatedAt = SyncLogic.millisToTimestamp(updatedAt),
    deleted = deleted == 1L,
)
