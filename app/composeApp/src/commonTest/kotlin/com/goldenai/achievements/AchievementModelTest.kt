package com.goldenai.achievements

import com.goldenai.achievements.core.model.Achievement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AchievementModelTest {

    @Test
    fun `category is derived from the type key`() {
        val achievement = sample(type = "geography.country")
        assertEquals("Geography", achievement.category)
        assertEquals("geography.country", achievement.type)
    }

    @Test
    fun `unknown type has no category`() {
        assertNull(sample(type = "not.a.type").category)
    }

    @Test
    fun `guest rows have no owner uid`() {
        val achievement = sample(ownerUid = null)
        assertNull(achievement.ownerUid)
    }

    @Test
    fun `signed-in ownership is stored on the model`() {
        val achievement = sample(ownerUid = "firebase-uid-1")
        assertEquals("firebase-uid-1", achievement.ownerUid)
    }

    @Test
    fun `location coordinates are part of the model`() {
        val achievement = sample(latitude = 35.68, longitude = 139.69, locationName = "Tokyo")
        assertEquals(35.68, achievement.latitude)
        assertEquals(139.69, achievement.longitude)
        assertEquals("Tokyo", achievement.locationName)
    }

    private fun sample(
        type: String = "geography.country",
        latitude: Double? = null,
        longitude: Double? = null,
        locationName: String? = null,
        ownerUid: String? = null,
    ) = Achievement(
        id = "id-1",
        type = type,
        timestamp = 1_700_000_000_000,
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        content = "Japan",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_000_000,
        ownerUid = ownerUid,
    )
}
