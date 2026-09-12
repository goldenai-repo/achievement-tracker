package com.goldenai.achievements.features.auth.presentation

import android.content.Context
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
actual fun GoogleSignInButton(
    enabled: Boolean,
    onIdToken: (String) -> Unit,
    onError: (String) -> Unit,
    label: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    Button(
        onClick = {
            scope.launch {
                try {
                    val result = credentialManager.getCredential(
                        context = context,
                        request = GetCredentialRequest.Builder()
                            .addCredentialOption(
                                // This is an explicit sign-in button, so use
                                // the Sign-in-with-Google option rather than
                                // only querying already-authorized credentials.
                                GetSignInWithGoogleOption.Builder(context.googleWebClientId())
                                    .build(),
                            )
                            .build(),
                    )
                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        onIdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                    } else {
                        onError("Google did not return a supported credential.")
                    }
                } catch (_: CancellationException) {
                    // Closing the account picker is a normal user action.
                } catch (_: NoCredentialException) {
                    onError(
                        "No Google account is available on this emulator. " +
                            "Add a Google account in Settings and try again.",
                    )
                } catch (t: Throwable) {
                    onError(t.message ?: "Google sign-in failed.")
                }
            }
        },
        enabled = enabled,
    ) {
        Text(label)
    }
}

private fun Context.googleWebClientId(): String {
    val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
    check(resourceId != 0) {
        "Google sign-in is not configured for this build. Add google-services.json and rebuild."
    }
    return getString(resourceId)
}
