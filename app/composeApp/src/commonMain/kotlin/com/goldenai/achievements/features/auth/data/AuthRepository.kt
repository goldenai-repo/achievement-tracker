package com.goldenai.achievements.features.auth.data

import com.goldenai.achievements.core.AppResult
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class AppUser(val uid: String, val email: String?)

/**
 * Wraps Firebase Auth. When the build has no Firebase config (guest-only
 * build), [cloudAvailable] is false and every call degrades gracefully
 * instead of crashing on an uninitialized Firebase app.
 */
class AuthRepository(val cloudAvailable: Boolean) {

    val authState: Flow<AppUser?> =
        if (cloudAvailable) Firebase.auth.authStateChanged.map { it?.toAppUser() }
        else flowOf(null)

    val currentUser: AppUser?
        get() = if (cloudAvailable) Firebase.auth.currentUser?.toAppUser() else null

    fun hasProvider(providerId: String): Boolean =
        cloudAvailable && Firebase.auth.currentUser?.providerData.orEmpty().any {
            it.providerId == providerId
        }

    /** ID token used by the FastAPI Authorization: Bearer middleware. */
    suspend fun idToken(forceRefresh: Boolean = false): String? =
        if (cloudAvailable) Firebase.auth.currentUser?.getIdToken(forceRefresh) else null

    suspend fun signIn(email: String, password: String): AppResult<AppUser> {
        if (!cloudAvailable) return AppResult.Err(CLOUD_UNAVAILABLE)
        return try {
            val result = Firebase.auth.signInWithEmailAndPassword(email.trim(), password)
            val user = result.user ?: return AppResult.Err("Sign-in failed: no user returned")
            AppResult.Ok(user.toAppUser())
        } catch (t: Throwable) {
            AppResult.Err(t.message ?: "Sign-in failed", t)
        }
    }

    suspend fun register(email: String, password: String): AppResult<AppUser> {
        if (!cloudAvailable) return AppResult.Err(CLOUD_UNAVAILABLE)
        return try {
            val result = Firebase.auth.createUserWithEmailAndPassword(email.trim(), password)
            val user = result.user ?: return AppResult.Err("Registration failed: no user returned")
            AppResult.Ok(user.toAppUser())
        } catch (t: Throwable) {
            AppResult.Err(t.message ?: "Registration failed", t)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AppResult<AppUser> {
        if (!cloudAvailable) return AppResult.Err(CLOUD_UNAVAILABLE)
        return try {
            val credential = GoogleAuthProvider.credential(idToken, null)
            val result = Firebase.auth.signInWithCredential(credential)
            val user = result.user ?: return AppResult.Err("Google sign-in failed: no user returned")
            AppResult.Ok(user.toAppUser())
        } catch (t: Throwable) {
            AppResult.Err(t.message ?: "Google sign-in failed", t)
        }
    }

    /** Links Google to the current Firebase user without changing its UID. */
    suspend fun linkGoogleIdToken(idToken: String): AppResult<AppUser> {
        if (!cloudAvailable) return AppResult.Err(CLOUD_UNAVAILABLE)
        return try {
            val user = Firebase.auth.currentUser
                ?: return AppResult.Err("Sign in before linking a Google account.")
            val credential = GoogleAuthProvider.credential(idToken, null)
            val result = user.linkWithCredential(credential)
            val linkedUser = result.user ?: Firebase.auth.currentUser
                ?: return AppResult.Err("Google account linking failed: no user returned")
            AppResult.Ok(linkedUser.toAppUser())
        } catch (t: Throwable) {
            AppResult.Err(t.message ?: "Could not link Google account", t)
        }
    }

    /** Re-authenticates the current email/password account before destructive actions. */
    suspend fun reauthenticate(password: String): AppResult<Unit> {
        if (!cloudAvailable) return AppResult.Err(CLOUD_UNAVAILABLE)
        return try {
            val user = Firebase.auth.currentUser
                ?: return AppResult.Err("Sign in before deleting a cloud check-in.")
            val email = user.email
                ?: return AppResult.Err("This account does not have an email/password credential.")
            user.reauthenticate(EmailAuthProvider.credential(email, password))
            AppResult.Ok(Unit)
        } catch (t: Throwable) {
            AppResult.Err(t.message ?: "Password verification failed", t)
        }
    }

    /** Re-authenticates a Google-linked account before a sensitive action. */
    suspend fun reauthenticateWithGoogleIdToken(idToken: String): AppResult<Unit> {
        if (!cloudAvailable) return AppResult.Err(CLOUD_UNAVAILABLE)
        return try {
            val user = Firebase.auth.currentUser
                ?: return AppResult.Err("Sign in before deleting your account.")
            user.reauthenticate(GoogleAuthProvider.credential(idToken, null))
            AppResult.Ok(Unit)
        } catch (t: Throwable) {
            AppResult.Err(t.message ?: "Google verification failed", t)
        }
    }

    suspend fun signOut(): AppResult<Unit> {
        if (!cloudAvailable) return AppResult.Err(CLOUD_UNAVAILABLE)
        return try {
            Firebase.auth.signOut()
            AppResult.Ok(Unit)
        } catch (t: Throwable) {
            AppResult.Err(t.message ?: "Sign-out failed", t)
        }
    }

    private fun FirebaseUser.toAppUser() = AppUser(uid = uid, email = email)

    companion object {
        const val PASSWORD_PROVIDER = "password"
        const val GOOGLE_PROVIDER = "google.com"
        const val CLOUD_UNAVAILABLE =
            "Cloud sync is not configured in this build. See docs/MOBILE_SETUP.md."
    }
}
