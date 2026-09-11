package com.goldenai.achievements

import com.goldenai.achievements.core.country.countryFlagEmoji
import com.goldenai.achievements.core.model.AchievementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Documents which category/flag emoji sequences include U+FE0F or regional
 * indicators — the ones that require Apple Color Emoji on Compose/iOS.
 */
class EmojiCodepointTest {

    @Test
    fun `category emoji with emoji presentation do not need FE0F`() {
        val presentationYes = listOf(
            AchievementType.GeographyCountry.emoji, // U+1F30D
            AchievementType.WildlifeAnimal.emoji, // U+1F981
            AchievementType.WildlifePlant.emoji, // U+1F33F
            AchievementType.EntertainmentMovie.emoji, // U+1F3AC
            AchievementType.CulinaryMichelin.emoji, // U+2B50
            AchievementType.HeritageUnesco.emoji, // U+1F3EF
        )
        presentationYes.forEach { emoji ->
            assertFalse(emoji.any { it.code == 0xFE0F }, "unexpected FE0F in $emoji")
            assertFalse(emoji.any { it.code == 0x200D }, "unexpected ZWJ in $emoji")
        }
    }

    @Test
    fun `text-default emoji ship with VS16 for emoji presentation`() {
        // Unicode Emoji_Presentation=No for these bases; Kotlin source includes U+FE0F.
        val withVs16 = listOf(
            AchievementType.GeographyState.emoji, // U+1F5FA U+FE0F
            AchievementType.GeographyCity.emoji, // U+1F3D9 U+FE0F
            AchievementType.CultureMuseum.emoji, // U+1F3DB U+FE0F
        )
        withVs16.forEach { emoji ->
            assertTrue(emoji.any { it.code == 0xFE0F }, "expected FE0F in $emoji")
            assertFalse(emoji.any { it.code == 0x200D }, "unexpected ZWJ in $emoji")
        }
    }

    @Test
    fun `country flags are regional indicator pairs without ZWJ or FE0F`() {
        val us = countryFlagEmoji("US")
        assertEquals(4, us.length) // two UTF-16 surrogates per RI
        assertFalse(us.any { it.code == 0xFE0F })
        assertFalse(us.any { it.code == 0x200D })
        // U+1F1FA U+1F1F8 as UTF-16
        assertEquals(0xD83C, us[0].code)
        assertEquals(0xDDFA, us[1].code)
        assertEquals(0xD83C, us[2].code)
        assertEquals(0xDDF8, us[3].code)
    }
}
