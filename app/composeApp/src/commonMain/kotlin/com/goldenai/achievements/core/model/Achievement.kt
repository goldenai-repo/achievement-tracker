package com.goldenai.achievements.core.model

/**
 * Domain model for a logged achievement. All instants are epoch millis in UTC;
 * they are converted to local time only at the presentation layer.
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
) {
    val typeInfo: AchievementType? get() = AchievementType.fromKey(type)
}
