package com.goldenai.achievements.features.achievements.domain

import com.goldenai.achievements.core.model.AchievementType

object AchievementValidation {

    fun contentError(content: String): String? =
        if (content.isBlank()) "This field is required" else null

    fun typeError(typeKey: String?): String? =
        if (typeKey == null || AchievementType.fromKey(typeKey) == null) "Pick a category" else null

    fun isValid(typeKey: String?, content: String): Boolean =
        typeError(typeKey) == null && contentError(content) == null

    fun emailError(email: String): String? =
        if (!email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) "Enter a valid email" else null

    fun passwordError(password: String): String? =
        if (password.length < 6) "Password must be at least 6 characters" else null
}
