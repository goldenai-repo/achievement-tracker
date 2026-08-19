package com.goldenai.achievements.core.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.goldenai.achievements.db.AchievementDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(
            schema = AchievementDatabase.Schema,
            name = "achievements.db",
            maxReaderConnections = 1,
        )
}
