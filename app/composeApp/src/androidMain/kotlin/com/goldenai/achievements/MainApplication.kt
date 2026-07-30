package com.goldenai.achievements

import android.app.Application
import com.goldenai.achievements.core.db.DriverFactory
import com.goldenai.achievements.di.AppGraph
import com.google.firebase.FirebaseApp

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase auto-initializes only when google-services.json was present
        // at build time; without it the app runs in guest-only mode.
        val cloudAvailable = FirebaseApp.getApps(this).isNotEmpty()
        AppGraph.init(DriverFactory(this), cloudAvailable)
    }
}
