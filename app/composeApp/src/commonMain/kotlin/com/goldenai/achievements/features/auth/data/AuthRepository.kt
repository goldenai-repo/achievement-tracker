package com.goldenai.achievements.features.auth.data

import com.goldenai.achievements.core.AppResult
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
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
        const val CLOUD_UNAVAILABLE =
            "Cloud sync is not configured in this build. See docs/MOBILE_SETUP.md."
    }
}
