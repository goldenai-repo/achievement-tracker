import SwiftUI
import FirebaseCore
import ComposeApp

@main
struct iOSApp: App {
    static var cloudAvailable = false

    init() {
        // Register MapLibre host before any Compose map composable runs.
        AchievementMapBridge.shared.factory = AchievementMapHandleFactoryImpl()

        // Firebase config is per-developer and gitignored; without it the app
        // runs in guest-only mode (see docs/MOBILE_SETUP.md).
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            iOSApp.cloudAvailable = true
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.keyboard)
        }
    }
}
