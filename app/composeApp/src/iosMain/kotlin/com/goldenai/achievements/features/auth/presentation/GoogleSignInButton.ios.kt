package com.goldenai.achievements.features.auth.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun GoogleSignInButton(
    enabled: Boolean,
    onIdToken: (String) -> Unit,
    onError: (String) -> Unit,
    label: String,
    preferExistingAccount: Boolean,
) {
    Text("$label is available on Android")
}
