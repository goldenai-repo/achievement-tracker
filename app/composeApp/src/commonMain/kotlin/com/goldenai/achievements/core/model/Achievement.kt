package com.goldenai.achievements.core.model

/**
 * Domain model for a logged achievement. All instants are epoch millis in UTC;
 * they are converted to local time only at the presentation layer.
 *
 * [type] is the persisted canonical key (e.g. `geography.country`). [category]
 * is derived from the type registry, not stored separately.
 *
 * [ownerUid] is null for guest / not-yet-attributed local rows. Queries do not
 * filter on it, so guest and signed-in rows remain visible on this device.
 */
data class Achievement(
    val id: String,
    val type: String,
    val subtype: String? = null,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val content: String,
    val notes: String? = null,
    val mediaUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerUid: String? = null,
) {
    val typeInfo: AchievementType? get() = AchievementType.fromKey(type)

    val category: String? get() = typeInfo?.category
}
