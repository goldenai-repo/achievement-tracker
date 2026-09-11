package com.goldenai.achievements

import androidx.compose.ui.window.ComposeUIViewController
import com.goldenai.achievements.core.apiBaseUrl
import com.goldenai.achievements.core.db.DriverFactory
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.ui.App
import platform.UIKit.UIViewController

/**
 * Entry point called from Swift. [cloudAvailable] is true when the host app
 * found GoogleService-Info.plist and ran FirebaseApp.configure().
 *
 * API base URL comes from [apiBaseUrl] (Info.plist `API_BASE_URL`), matching
 * Android's `BuildConfig.API_BASE_URL` role.
 */
fun MainViewController(cloudAvailable: Boolean): UIViewController {
    AppGraph.init(DriverFactory(), cloudAvailable, apiBaseUrl)
    return ComposeUIViewController { App() }
}
