package com.goldenai.achievements.features.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.formatDateTime
import com.goldenai.achievements.di.AppGraph

@Composable
fun AccountScreen(
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
) {
    val vm: AccountViewModel = viewModel { AccountViewModel() }
    val user by vm.user.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val syncError by vm.syncError.collectAsState()
    val lastSyncAt by vm.lastSyncAt.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Account", style = MaterialTheme.typography.headlineMedium)

        when {
            !AppGraph.cloudAvailable -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cloud sync not configured", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "This build has no Firebase configuration, so the app runs in " +
                                "guest mode only. Your achievements stay on this device. " +
                                "See docs/MOBILE_SETUP.md to enable cloud backup.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            user == null -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Back up your achievements", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "You're using the app as a guest — everything is saved on this " +
                                "device. Create a free account to upload your log to the cloud " +
                                "and access it from any device.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
                            Text("Create account")
                        }
                        OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign in")
                        }
                    }
                }
            }

            else -> {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Signed in", style = MaterialTheme.typography.titleSmall)
                        Text(user?.email ?: user?.uid.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                syncing -> "Syncing…"
                                pendingCount > 0 -> "$pendingCount waiting to upload"
                                else -> "All achievements backed up"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { vm.syncNow() },
                            enabled = !syncing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Sync now") }
                        OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign out")
                        }
                    }
                }
            }
        }
    }
}
