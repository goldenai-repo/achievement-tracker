import SwiftUI
import FirebaseCore
import GoogleSignIn
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
            // Only register Google Sign-In when Firebase is configured.
            GoogleSignInBridge.shared.host = GoogleSignInHostImpl()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Let Compose own safe areas (parity with Android edge-to-edge +
                // Material3 Scaffold insets). Without this, iOS double-pads or
                // clips under the notch / home indicator.
                .ignoresSafeArea(edges: .all)
                // Compose handles IME via imePadding(); SwiftUI must not also
                // shrink the hosting controller when the keyboard opens.
                .ignoresSafeArea(.keyboard)
                // Required for Google Sign-In OAuth redirect when
                // CFBundleURLTypes includes REVERSED_CLIENT_ID from
                // GoogleService-Info.plist.
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
