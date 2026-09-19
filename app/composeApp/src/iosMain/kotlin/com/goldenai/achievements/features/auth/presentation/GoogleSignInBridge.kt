package com.goldenai.achievements.features.auth.presentation

/**
 * Host-app Google Sign-In surface. Implemented in Swift (`GoogleSignInHostImpl`)
 * and registered from `iOSApp` when Firebase is configured.
 *
 * Listener callbacks stay ObjC-friendly (no Kotlin function-type bridging).
 */
interface GoogleSignInResultListener {
    fun onIdToken(idToken: String)
    fun onError(message: String)
    fun onCancelled()
}

interface GoogleSignInHost {
    /**
     * Presents Google Sign-In and reports an ID token suitable for
     * [com.goldenai.achievements.features.auth.data.AuthRepository] Google APIs.
     *
     * @param preferExistingAccount when true (account linking), pass [accountHint]
     *   so the native picker can prefer the signed-in email.
     * @param accountHint optional email hint; null when not linking.
     */
    fun signIn(
        preferExistingAccount: Boolean,
        accountHint: String?,
        listener: GoogleSignInResultListener,
    )
}

object GoogleSignInBridge {
    var host: GoogleSignInHost? = null
}
