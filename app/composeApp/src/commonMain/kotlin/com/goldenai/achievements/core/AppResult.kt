package com.goldenai.achievements.core

/**
 * Repositories return AppResult instead of throwing, so callers always handle
 * the failure path explicitly.
 */
sealed interface AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>
    data class Err(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}

inline fun <T> runCatchingResult(errorMessage: String, block: () -> T): AppResult<T> =
    try {
        AppResult.Ok(block())
    } catch (t: Throwable) {
        AppResult.Err(t.message ?: errorMessage, t)
    }
