import ComposeApp
import FirebaseCore
import GoogleSignIn
import UIKit

/// Presents Google Sign-In and returns an ID token for Firebase Auth (GitLive).
///
/// APIs verified against GoogleSignIn-iOS 8.0.0 headers:
/// - `signIn(withPresenting:completion:)`
/// - `signIn(withPresenting:hint:completion:)`
/// - `GIDGoogleUser.idToken.tokenString`
/// - cancel: `kGIDSignInErrorDomain` + `kGIDSignInErrorCodeCanceled` (-5)
final class GoogleSignInHostImpl: GoogleSignInHost {
    func signIn(
        preferExistingAccount: Bool,
        accountHint: String?,
        listener: GoogleSignInResultListener
    ) {
        guard FirebaseApp.app() != nil else {
            listener.onError(
                "Cloud sync is not configured in this build."
            )
            return
        }
        guard let clientID = FirebaseApp.app()?.options.clientID, !clientID.isEmpty else {
            listener.onError(
                "Google sign-in is not configured. Add GoogleService-Info.plist with CLIENT_ID."
            )
            return
        }

        // Matches Firebase Auth Google Sign-In docs: iOS CLIENT_ID from GoogleService-Info.
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

        guard let presenting = Self.topViewController() else {
            listener.onError("Could not find a view controller to present Google Sign-In.")
            return
        }

        let completion: (GIDSignInResult?, (any Error)?) -> Void = { result, error in
            if let error {
                if Self.isUserCancellation(error) {
                    listener.onCancelled()
                } else {
                    listener.onError(error.localizedDescription)
                }
                return
            }
            guard let idToken = result?.user.idToken?.tokenString, !idToken.isEmpty else {
                listener.onError("Google did not return an ID token.")
                return
            }
            listener.onIdToken(idToken: idToken)
        }

        // Linking prefers a hint so the picker can surface the linked email.
        // Sign-in / re-auth use the default native flow (not Android Credential Manager).
        if preferExistingAccount, let hint = accountHint, !hint.isEmpty {
            GIDSignIn.sharedInstance.signIn(
                withPresenting: presenting,
                hint: hint,
                completion: completion
            )
        } else {
            GIDSignIn.sharedInstance.signIn(
                withPresenting: presenting,
                completion: completion
            )
        }
    }

    /// Typed cancel from GoogleSignIn 8.0.0:
    /// domain `kGIDSignInErrorDomain` (GIDSignIn.h) and
    /// `GIDSignInErrorCode.canceled` (-5) per Google’s Swift API reference.
    private static func isUserCancellation(_ error: Error) -> Bool {
        let nsError = error as NSError
        return nsError.domain == kGIDSignInErrorDomain
            && nsError.code == GIDSignInErrorCode.canceled.rawValue
    }

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
            ?? scenes.flatMap { $0.windows }.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
