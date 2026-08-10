package com.goldenai.achievements.core.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.goldenai.achievements.db.AchievementDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        // The API-backed schema adds catalog identity columns compared with
        // the earlier Firestore prototype. Use a versioned file until a
        // production SQLDelight migration is added.
        AndroidSqliteDriver(AchievementDatabase.Schema, context, "achievements-api-v1.db")
}
