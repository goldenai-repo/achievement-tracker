package com.goldenai.achievements.features.profile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.formatDateTime
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.achievements.data.AchievementRepository
import com.goldenai.achievements.features.api.MeResponse
import com.goldenai.achievements.features.auth.data.AppUser
import com.goldenai.achievements.features.auth.data.AuthRepository
import com.goldenai.achievements.features.auth.presentation.GoogleSignInButton
import com.goldenai.achievements.core.AppResult
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
    val localCountryCount = repo.watchUniqueCountryCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val localAdmin1Count = repo.watchUniqueAdmin1Count()
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

    private val _savingUsername = MutableStateFlow(false)
    val savingUsername: StateFlow<Boolean> = _savingUsername.asStateFlow()

    private val _deletingAccount = MutableStateFlow(false)
    val deletingAccount: StateFlow<Boolean> = _deletingAccount.asStateFlow()

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

    fun linkGoogleIdToken(idToken: String) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            try {
                when (val result = AppGraph.auth.linkGoogleIdToken(idToken)) {
                    is AppResult.Ok -> {
                        if (AppGraph.auth.currentUser != null) {
                            _profile.value = AppGraph.api.getMe()
                        }
                        repo.refresh()
                    }
                    is AppResult.Err -> _error.value = result.message
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Could not link Google account."
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun setExternalError(message: String) {
        if (!_refreshing.value) _error.value = message
    }

    fun saveUsername(username: String, onSuccess: () -> Unit = {}) {
        val normalized = username.trim().replace(Regex("\\s+"), " ")
        if (normalized.length !in 3..30) {
            _error.value = "Username must be between 3 and 30 characters."
            return
        }
        if (_savingUsername.value) return
        viewModelScope.launch {
            _savingUsername.value = true
            _error.value = null
            try {
                _profile.value = AppGraph.api.updateProfile(
                    com.goldenai.achievements.features.api.ProfileUpdateRequest(normalized),
                )
                onSuccess()
            } catch (t: Throwable) {
                _error.value = t.message ?: "Could not save username."
            } finally {
                _savingUsername.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { AppGraph.auth.signOut() }
    }

    fun deleteAccount(password: String, onSuccess: () -> Unit = {}) {
        if (_deletingAccount.value) return
        viewModelScope.launch {
            _deletingAccount.value = true
            _error.value = null
            try {
                when (val authResult = AppGraph.auth.reauthenticate(password)) {
                    is AppResult.Err -> _error.value = authResult.message
                    is AppResult.Ok -> {
                        completeAccountDeletion(onSuccess)
                    }
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Could not delete account."
            } finally {
                _deletingAccount.value = false
            }
        }
    }

    fun deleteAccountWithGoogle(idToken: String, onSuccess: () -> Unit = {}) {
        if (_deletingAccount.value) return
        viewModelScope.launch {
            _deletingAccount.value = true
            _error.value = null
            try {
                when (val authResult = AppGraph.auth.reauthenticateWithGoogleIdToken(idToken)) {
                    is AppResult.Err -> _error.value = authResult.message
                    is AppResult.Ok -> completeAccountDeletion(onSuccess)
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Could not delete account."
            } finally {
                _deletingAccount.value = false
            }
        }
    }

    private suspend fun completeAccountDeletion(onSuccess: () -> Unit) {
        AppGraph.api.deleteAccount()
        repo.clearAllLocalData()
        AppGraph.auth.signOut()
        _profile.value = null
        onSuccess()
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
    val localCountryCount by vm.localCountryCount.collectAsState()
    val localAdmin1Count by vm.localAdmin1Count.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val syncError by vm.syncError.collectAsState()
    val lastSyncAt by vm.lastSyncAt.collectAsState()
    val error by vm.error.collectAsState()
    val savingUsername by vm.savingUsername.collectAsState()
    val deletingAccount by vm.deletingAccount.collectAsState()
    var editingUsername by remember { mutableStateOf(false) }
    var usernameDraft by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteWithPassword by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        vm.refresh()
    }

    LaunchedEffect(profile?.displayName) {
        if (!editingUsername) usernameDraft = profile?.displayName.orEmpty()
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (user != null && !editingUsername) {
                            TextButton(
                                onClick = {
                                    usernameDraft = profile?.displayName.orEmpty()
                                    editingUsername = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text("Edit username") }
                        }
                        if (user != null && AppGraph.cloudAvailable) {
                            IconButton(
                                onClick = vm::syncNow,
                                enabled = !syncing && !deletingAccount,
                            ) {
                                Text(
                                    text = if (syncing) "…" else "↻",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                            }
                        }
                    }
                    Text(
                        email ?: "Local-only guest profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (user == null) {
                        Text(
                            "Guest mode · data stays on this device",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (user != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProfileStatusPill(
                                "Signed in",
                                MaterialTheme.colorScheme.secondaryContainer,
                            )
                            if (AppGraph.auth.hasProvider(AuthRepository.GOOGLE_PROVIDER)) {
                                ProfileStatusPill(
                                    "Google linked",
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                )
                            }
                            if (AppGraph.cloudAvailable) {
                                ProfileStatusPill(
                                    when {
                                        syncing -> "Syncing"
                                        pendingCount > 0L -> "Pending"
                                        else -> "Synced"
                                    },
                                    MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                    if (editingUsername && user != null) {
                        OutlinedTextField(
                            value = usernameDraft,
                            onValueChange = { usernameDraft = it },
                            label = { Text("Username") },
                            singleLine = true,
                            enabled = !savingUsername,
                            supportingText = { Text("3–30 characters") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.saveUsername(usernameDraft) { editingUsername = false }
                                },
                                enabled = !savingUsername,
                            ) {
                                Text(if (savingUsername) "Saving…" else "Save")
                            }
                            TextButton(
                                onClick = {
                                    usernameDraft = profile?.displayName.orEmpty()
                                    editingUsername = false
                                },
                                enabled = !savingUsername,
                            ) { Text("Cancel") }
                        }
                    }
                    AccountSyncSection(
                        cloudAvailable = AppGraph.cloudAvailable,
                        user = user,
                        syncing = syncing,
                        lastSyncAt = lastSyncAt,
                        syncError = syncError,
                        onSignIn = onSignIn,
                        onRegister = onRegister,
                        googleLinked = AppGraph.auth.hasProvider(AuthRepository.GOOGLE_PROVIDER),
                        onLinkGoogle = vm::linkGoogleIdToken,
                        onLinkGoogleError = vm::setExternalError,
                        deletingAccount = deletingAccount,
                    )
                    if (user != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = vm::signOut,
                                enabled = !deletingAccount && !syncing,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            ) { Text("Sign out") }
                            OutlinedButton(
                                onClick = {
                                    showDeleteDialog = true
                                    deleteWithPassword = !AppGraph.auth.hasProvider(AuthRepository.GOOGLE_PROVIDER)
                                },
                                enabled = !deletingAccount && !syncing,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            ) { Text("Delete account") }
                        }
                    }
                }
            }
        }

        item {
            val checkins = remoteSummary?.checkinCount?.toLong() ?: localCheckinCount
            // Prefer hierarchy-aware counts from the API. Falling back to the
            // local cache also keeps Profile correct while an older backend is
            // still serving the legacy byKind-only summary response.
            val countries = remoteSummary?.countryCount?.toLong() ?: localCountryCount
            val regions = remoteSummary?.admin1Count?.toLong() ?: localAdmin1Count
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Travel footprint", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Check-ins", checkins.toString(), "visits", Modifier.weight(1f))
                    StatCard("Countries", countries.toString(), "visited", Modifier.weight(1f))
                    StatCard("Regions", regions.toString(), "states / provinces", Modifier.weight(1f))
                }
            }
        }

        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Your log", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Review places and visit history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onViewLog) { Text("Open") }
                }
            }
        }

        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!deletingAccount) {
                    showDeleteDialog = false
                    deletePassword = ""
                    deleteWithPassword = false
                }
            },
            title = { Text("Delete account?") },
            text = {
                val hasGoogleProvider = AppGraph.auth.hasProvider(AuthRepository.GOOGLE_PROVIDER)
                val hasPasswordProvider = AppGraph.auth.hasProvider(AuthRepository.PASSWORD_PROVIDER)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently deletes your account, cloud check-ins, and this device's cached data.")
                    when {
                        hasGoogleProvider && !deleteWithPassword -> {
                            Text("Verify with Google to continue.", style = MaterialTheme.typography.bodySmall)
                            GoogleSignInButton(
                                enabled = !deletingAccount,
                                label = "Verify with Google and delete",
                                onIdToken = { idToken ->
                                    vm.deleteAccountWithGoogle(idToken) {
                                        showDeleteDialog = false
                                        deletePassword = ""
                                        deleteWithPassword = false
                                    }
                                },
                                onError = vm::setExternalError,
                            )
                            if (hasPasswordProvider) {
                                TextButton(
                                    onClick = {
                                        deleteWithPassword = true
                                        deletePassword = ""
                                    },
                                    enabled = !deletingAccount,
                                ) { Text("Use password instead") }
                            }
                        }
                        hasPasswordProvider -> {
                            OutlinedTextField(
                                value = deletePassword,
                                onValueChange = { deletePassword = it },
                                label = { Text("Password") },
                                singleLine = true,
                                enabled = !deletingAccount,
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            if (hasGoogleProvider) {
                                TextButton(
                                    onClick = {
                                        deleteWithPassword = false
                                        deletePassword = ""
                                    },
                                    enabled = !deletingAccount,
                                ) { Text("Use Google instead") }
                            }
                        }
                        else -> {
                            Text(
                                "No supported sign-in method is available for account verification.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (deleteWithPassword && AppGraph.auth.hasProvider(AuthRepository.PASSWORD_PROVIDER)) {
                    Button(
                        onClick = {
                            vm.deleteAccount(deletePassword) {
                                showDeleteDialog = false
                                deletePassword = ""
                                deleteWithPassword = false
                            }
                        },
                        enabled = deletePassword.isNotEmpty() && !deletingAccount,
                    ) { Text(if (deletingAccount) "Deleting…" else "Delete permanently") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deletePassword = ""
                        deleteWithPassword = false
                    },
                    enabled = !deletingAccount,
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileStatusPill(
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.height(92.dp)) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
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
private fun AccountSyncSection(
    cloudAvailable: Boolean,
    user: AppUser?,
    syncing: Boolean,
    lastSyncAt: Long?,
    syncError: String?,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    googleLinked: Boolean,
    onLinkGoogle: (String) -> Unit,
    onLinkGoogleError: (String) -> Unit,
    deletingAccount: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!cloudAvailable || user == null) {
            Text(
                if (!cloudAvailable) "Local-only storage" else "Sign in to back up your achievements",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
                !cloudAvailable -> {
                    Text(
                        "Cloud access is not configured. Your profile and achievements stay on this device.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                user == null -> {
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
                        lastSyncAt?.let { "Last synced ${formatDateTime(it)}" } ?: "Not synced yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    syncError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!googleLinked) {
                        GoogleSignInButton(
                            enabled = !syncing && !deletingAccount,
                            label = "Link Google account",
                            onIdToken = onLinkGoogle,
                            onError = onLinkGoogleError,
                            preferExistingAccount = true,
                        )
                    }
                }
        }
    }
}
