package com.goldenai.achievements.di

import com.goldenai.achievements.core.db.DriverFactory
import com.goldenai.achievements.db.AchievementDatabase
import com.goldenai.achievements.features.achievements.data.AchievementRepository
import com.goldenai.achievements.features.auth.data.AuthRepository
import com.goldenai.achievements.features.sync.SyncCoordinator
import com.goldenai.achievements.features.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled composition root. [init] is called once from each platform
 * entry point before the first composable runs.
 */
object AppGraph {
    lateinit var achievements: AchievementRepository
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var sync: SyncCoordinator
        private set

    var cloudAvailable: Boolean = false
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initialized = false

    fun init(driverFactory: DriverFactory, cloudAvailable: Boolean) {
        if (initialized) return
        initialized = true

        this.cloudAvailable = cloudAvailable
        val database = AchievementDatabase(driverFactory.createDriver())

        achievements = AchievementRepository(database)
        auth = AuthRepository(cloudAvailable)
        sync = SyncCoordinator(auth, SyncEngine(database, cloudAvailable), appScope)
        achievements.onLocalChange = { sync.requestSync() }
        sync.start()
    }
}
