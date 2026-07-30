package com.goldenai.achievements

import androidx.compose.ui.window.ComposeUIViewController
import com.goldenai.achievements.core.db.DriverFactory
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.ui.App
import platform.UIKit.UIViewController

/**
 * Entry point called from Swift. [cloudAvailable] is true when the host app
 * found GoogleService-Info.plist and ran FirebaseApp.configure().
 */
fun MainViewController(cloudAvailable: Boolean): UIViewController {
    AppGraph.init(DriverFactory(), cloudAvailable)
    return ComposeUIViewController { App() }
}
