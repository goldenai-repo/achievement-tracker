import SwiftUI
import ComposeApp

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(cloudAvailable: iOSApp.cloudAvailable)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
