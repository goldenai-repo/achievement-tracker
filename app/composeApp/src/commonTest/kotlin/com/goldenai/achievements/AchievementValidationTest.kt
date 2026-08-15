package com.goldenai.achievements

import com.goldenai.achievements.features.achievements.domain.AchievementValidation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AchievementValidationTest {

    @Test
    fun `valid submission passes`() {
        assertTrue(AchievementValidation.isValid("geography.country", "Japan"))
    }

    @Test
    fun `blank content fails`() {
        assertFalse(AchievementValidation.isValid("geography.country", "   "))
    }

    @Test
    fun `unknown or missing type fails`() {
        assertFalse(AchievementValidation.isValid(null, "Japan"))
        assertFalse(AchievementValidation.isValid("nope.nope", "Japan"))
    }

    @Test
    fun `email validation`() {
        assertNull(AchievementValidation.emailError("user@example.com"))
        assertNotNull(AchievementValidation.emailError("not-an-email"))
        assertNotNull(AchievementValidation.emailError("user@"))
    }

    @Test
    fun `password validation`() {
        assertNull(AchievementValidation.passwordError("secret1"))
        assertNotNull(AchievementValidation.passwordError("short"))
    }
}
