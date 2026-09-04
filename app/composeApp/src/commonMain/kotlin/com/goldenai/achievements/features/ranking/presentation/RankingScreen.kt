package com.goldenai.achievements.features.ranking.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.api.AchievementApi
import com.goldenai.achievements.features.api.GeographyRankingEntry
import com.goldenai.achievements.features.api.GeographyRankingResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RankingViewModel(
    private val api: AchievementApi,
) : ViewModel() {
    private val _ranking = MutableStateFlow<GeographyRankingResponse?>(null)
    val ranking: StateFlow<GeographyRankingResponse?> = _ranking.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val signedIn: Boolean
        get() = AppGraph.auth.currentUser != null

    fun refresh() {
        if (_loading.value) return
        if (!signedIn) {
            _ranking.value = null
            _error.value = null
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _ranking.value = api.getGeographyRanking()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _error.value = t.message ?: "Could not load the geography ranking."
            } finally {
                _loading.value = false
            }
        }
    }
}

@Composable
fun RankingScreen(
    onSignIn: () -> Unit,
) {
    val vm: RankingViewModel = viewModel { RankingViewModel(AppGraph.api) }
    val ranking by vm.ranking.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(AppGraph.auth.currentUser?.uid) {
        vm.refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Ranking", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Geo ranking is based on unique first-level regions, then countries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!vm.signedIn) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sign in to join the ranking", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Guest check-ins stay on this device and are not included in the global leaderboard.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onSignIn) { Text("Sign in") }
                    }
                }
            }
        } else if (loading && ranking == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            ranking?.me?.let { me ->
                item { MyRankingCard(me) }
            } ?: item {
                Card {
                    Text(
                        "No ranked check-ins yet. Record a region to join the leaderboard.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item { Text("Top explorers", style = MaterialTheme.typography.titleLarge) }
            if (ranking?.entries.isNullOrEmpty()) {
                item {
                    Text(
                        "No other ranked explorers yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(ranking?.entries.orEmpty(), key = { "${it.rank}-${it.displayName}" }) { entry ->
                    RankingRow(entry)
                }
            }
        }

        error?.let { message ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = vm::refresh) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun MyRankingCard(entry: GeographyRankingEntry) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your position", style = MaterialTheme.typography.labelLarge)
            Text("#${entry.rank} · ${entry.displayName}", style = MaterialTheme.typography.titleLarge)
            Text(
                "${entry.admin1Count} regions · ${entry.countryCount} countries",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RankingRow(entry: GeographyRankingEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#${entry.rank}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${entry.admin1Count} regions · ${entry.countryCount} countries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
