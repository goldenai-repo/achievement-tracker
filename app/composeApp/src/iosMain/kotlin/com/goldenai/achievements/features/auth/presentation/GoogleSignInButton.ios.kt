package com.goldenai.achievements.features.auth.presentation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.auth.data.AuthRepository

@Composable
actual fun GoogleSignInButton(
    enabled: Boolean,
    onIdToken: (String) -> Unit,
    onError: (String) -> Unit,
    label: String,
) {
    val deliverToken = onIdToken
    val deliverError = onError

    Button(
        onClick = {
            if (!AppGraph.auth.cloudAvailable) {
                deliverError(AuthRepository.CLOUD_UNAVAILABLE)
                return@Button
            }
            val host = GoogleSignInBridge.host
            if (host == null) {
                deliverError(
                    "Google Sign-In is not available. Rebuild iosApp so iOSApp " +
                        "registers GoogleSignInBridge when Firebase is configured.",
                )
                return@Button
            }
            val hint =
                if (preferExistingAccount) {
                    AppGraph.auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            host.signIn(
                preferExistingAccount = preferExistingAccount,
                accountHint = hint,
                listener = object : GoogleSignInResultListener {
                    override fun onIdToken(idToken: String) = deliverToken(idToken)
                    override fun onError(message: String) = deliverError(message)
                    override fun onCancelled() {
                        // User dismissed the Google sheet — not an auth failure.
                    }
                },
            )
        },
        enabled = enabled,
    ) {
        Text(label)
    }
}
