package com.goldenai.achievements.features.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.formatDateTime
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.achievements.data.AchievementRepository
import com.goldenai.achievements.features.api.MeResponse
import com.goldenai.achievements.features.auth.data.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repo: AchievementRepository,
) : ViewModel() {
    val user: StateFlow<AppUser?> = AppGraph.auth.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppGraph.auth.currentUser)

    val summary = repo.summary
    val localCheckinCount = repo.watchCountAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val localUniquePlaceCount = repo.watchUniqueEntityCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val pendingCount = repo.watchPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val syncing = AppGraph.sync.syncing
    val syncError = AppGraph.sync.lastError
    val lastSyncAt = AppGraph.sync.lastSyncAt

    private val _profile = MutableStateFlow<MeResponse?>(null)
    val profile: StateFlow<MeResponse?> = _profile.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            try {
                if (AppGraph.auth.currentUser != null) {
                    _profile.value = AppGraph.api.getMe()
                } else {
                    _profile.value = null
                }
                repo.refresh()
            } catch (t: Throwable) {
                _error.value = t.message ?: "Could not refresh profile."
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun syncNow() = AppGraph.sync.requestSync()

    fun signOut() {
        viewModelScope.launch { AppGraph.auth.signOut() }
    }
}

@Composable
fun ProfileScreen(
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onViewLog: () -> Unit,
) {
    val vm: ProfileViewModel = viewModel { ProfileViewModel(AppGraph.achievements) }
    val user by vm.user.collectAsState()
    val profile by vm.profile.collectAsState()
    val summary by vm.summary.collectAsState()
    val localCheckinCount by vm.localCheckinCount.collectAsState()
    val localUniquePlaceCount by vm.localUniquePlaceCount.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val syncError by vm.syncError.collectAsState()
    val lastSyncAt by vm.lastSyncAt.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(user?.uid) {
        vm.refresh()
    }

    val remoteSummary = if (user != null) summary else null
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: "Guest Explorer"
    val email = profile?.email ?: user?.email

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(displayName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Profile", style = MaterialTheme.typography.headlineMedium)
                    Text(displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        email ?: "Local-only guest profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (user != null) {
            profile?.uid?.let { uid ->
                item {
                    Text(
                        "User ID: ${uid.take(18)}${if (uid.length > 18) "…" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            val checkins = remoteSummary?.checkinCount?.toLong() ?: localCheckinCount
            val unlocked = remoteSummary?.uniqueUnlockCount?.toLong() ?: localUniquePlaceCount
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your progress", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Check-ins", checkins, "visits", Modifier.weight(1f))
                    StatCard("Places", unlocked, "unlocked", Modifier.weight(1f))
                }
            }
        }

        item {
            OutlinedButton(onClick = onViewLog, modifier = Modifier.fillMaxWidth()) {
                Text("View full log")
            }
        }

        item {
            SyncCard(
                cloudAvailable = AppGraph.cloudAvailable,
                user = user,
                pendingCount = pendingCount,
                syncing = syncing,
                lastSyncAt = lastSyncAt,
                syncError = syncError,
                onSync = vm::syncNow,
                onSignIn = onSignIn,
                onRegister = onRegister,
                onSignOut = vm::signOut,
            )
        }

        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ProfileAvatar(name: String) {
    Surface(
        modifier = Modifier.clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = name.take(1).uppercase(),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Long,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncCard(
    cloudAvailable: Boolean,
    user: AppUser?,
    pendingCount: Long,
    syncing: Boolean,
    lastSyncAt: Long?,
    syncError: String?,
    onSync: () -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (user == null) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Cloud sync", style = MaterialTheme.typography.titleMedium)
            when {
                !cloudAvailable -> {
                    Text(
                        "Cloud sync is not configured. Your profile and achievements stay on this device.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                user == null -> {
                    Text(
                        "Guest mode is active. Sign in to back up your achievements.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRegister, modifier = Modifier.weight(1f)) {
                            Text("Create account")
                        }
                        OutlinedButton(onClick = onSignIn, modifier = Modifier.weight(1f)) {
                            Text("Sign in")
                        }
                    }
                }
                else -> {
                    Text(
                        when {
                            syncing -> "Syncing…"
                            pendingCount > 0 -> "$pendingCount waiting to upload"
                            else -> "All achievements backed up"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    lastSyncAt?.let {
                        Text(
                            "Last synced ${formatDateTime(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    syncError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSync,
                            enabled = !syncing,
                            modifier = Modifier.weight(1f),
                        ) { Text("Sync now") }
                        OutlinedButton(onClick = onSignOut, modifier = Modifier.weight(1f)) {
                            Text("Sign out")
                        }
                    }
                }
            }
        }
    }
}
