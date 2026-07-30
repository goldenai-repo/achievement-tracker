package com.goldenai.achievements.features.sync

import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.features.auth.data.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns when syncs happen: after sign-in, after local writes while signed in,
 * and on explicit user request from the account screen.
 */
class SyncCoordinator(
    private val auth: AuthRepository,
    private val engine: SyncEngine,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastSyncAt = MutableStateFlow<Long?>(null)
    val lastSyncAt: StateFlow<Long?> = _lastSyncAt.asStateFlow()

    fun start() {
        scope.launch {
            _lastSyncAt.value = engine.lastSyncAt()
            auth.authState.collect { user ->
                if (user != null) requestSync()
            }
        }
    }

    /** Fire-and-forget; no-op when signed out or when cloud is unavailable. */
    fun requestSync() {
        val user = auth.currentUser ?: return
        scope.launch {
            mutex.withLock {
                _syncing.value = true
                when (val result = engine.sync(user.uid)) {
                    is AppResult.Ok -> {
                        _lastError.value = null
                        _lastSyncAt.value = engine.lastSyncAt()
                    }
                    is AppResult.Err -> _lastError.value = result.message
                }
                _syncing.value = false
            }
        }
    }
}
