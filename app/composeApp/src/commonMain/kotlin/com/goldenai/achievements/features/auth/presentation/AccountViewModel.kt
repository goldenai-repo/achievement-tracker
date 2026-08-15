package com.goldenai.achievements.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.auth.data.AppUser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel : ViewModel() {

    val user: StateFlow<AppUser?> = AppGraph.auth.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppGraph.auth.currentUser)

    val pendingCount: StateFlow<Long> = AppGraph.achievements.watchPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val syncing = AppGraph.sync.syncing
    val syncError = AppGraph.sync.lastError
    val lastSyncAt = AppGraph.sync.lastSyncAt

    fun syncNow() = AppGraph.sync.requestSync()

    fun signOut() {
        viewModelScope.launch { AppGraph.auth.signOut() }
    }
}
