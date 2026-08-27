package com.goldenai.achievements.core.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.goldenai.achievements.db.AchievementDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        // Match Android's versioned file name so schema bumps stay intentional
        // on both platforms until a formal SQLDelight migration is added.
        NativeSqliteDriver(AchievementDatabase.Schema, "achievements-api-v1.db")
}
