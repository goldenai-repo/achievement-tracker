package com.goldenai.achievements

import com.goldenai.achievements.core.model.AchievementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AchievementTypeTest {

    @Test
    fun `registry contains the nine documented types`() {
        assertEquals(9, AchievementType.entries.size)
    }

    @Test
    fun `fromKey resolves canonical keys`() {
        assertEquals(AchievementType.GeographyCountry, AchievementType.fromKey("geography.country"))
        assertEquals(AchievementType.HeritageUnesco, AchievementType.fromKey("heritage.unesco"))
        assertEquals(AchievementType.CulinaryMichelin, AchievementType.fromKey("culinary.michelin"))
    }

    @Test
    fun `fromKey returns null for unknown keys`() {
        assertNull(AchievementType.fromKey("geography.planet"))
        assertNull(AchievementType.fromKey(""))
    }

    @Test
    fun `keys are unique`() {
        val keys = AchievementType.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
