package com.goldenai.achievements.di

import com.goldenai.achievements.core.db.DriverFactory
import com.goldenai.achievements.db.AchievementDatabase
import com.goldenai.achievements.features.achievements.data.AchievementRepository
import com.goldenai.achievements.features.api.createAchievementApi
import com.goldenai.achievements.features.api.AchievementApi
import com.goldenai.achievements.features.api.CheckInSelection
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
    lateinit var api: AchievementApi
        private set
    lateinit var sync: SyncCoordinator
        private set

    var cloudAvailable: Boolean = false
        private set

    /** One-shot selection used when Explore opens the shared check-in form. */
    var pendingCheckInSelection: CheckInSelection? = null

    fun consumePendingCheckInSelection(): CheckInSelection? =
        pendingCheckInSelection.also { pendingCheckInSelection = null }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initialized = false

    fun init(driverFactory: DriverFactory, cloudAvailable: Boolean, apiBaseUrl: String) {
        if (initialized) return
        initialized = true

        this.cloudAvailable = cloudAvailable
        val database = AchievementDatabase(driverFactory.createDriver())

        auth = AuthRepository(cloudAvailable)
        api = createAchievementApi(auth, apiBaseUrl)
        achievements = AchievementRepository(database, api, auth)
        sync = SyncCoordinator(auth, SyncEngine(achievements), appScope)
        sync.start()
    }
}
