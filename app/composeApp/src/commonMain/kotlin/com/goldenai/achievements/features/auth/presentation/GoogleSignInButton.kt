package com.goldenai.achievements.features.auth.presentation

import androidx.compose.runtime.Composable

@Composable
expect fun GoogleSignInButton(
    enabled: Boolean,
    onIdToken: (String) -> Unit,
    onError: (String) -> Unit,
    label: String = "Continue with Google",
    preferExistingAccount: Boolean = false,
)
