package com.goldenai.achievements.features.ranking.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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

private val rankingCategories = listOf(
    "geography" to "🌍 Geography",
    "wildlife" to "🦁 Wildlife",
    "culture" to "🏛️ Culture",
    "heritage" to "🏯 Heritage",
)

@Composable
fun RankingScreen(
    onSignIn: () -> Unit,
) {
    val vm: RankingViewModel = viewModel { RankingViewModel(AppGraph.api) }
    val ranking by vm.ranking.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    var selectedCategory by remember { mutableStateOf("geography") }

    LaunchedEffect(AppGraph.auth.currentUser?.uid) {
        vm.refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ranking", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Compare your geographic footprint with other explorers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = vm::refresh, enabled = !loading) {
                    Text(
                        text = if (loading) "…" else "↻",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rankingCategories.size) { index ->
                    val (key, label) = rankingCategories[index]
                    FilterChip(
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key },
                        label = { Text(label) },
                    )
                }
            }
        }

        if (selectedCategory != "geography") {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        "${rankingCategories.first { it.first == selectedCategory }.second} rankings are coming soon.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
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
            if (ranking?.me == null && ranking?.entries.isNullOrEmpty()) {
                item {
                    Card {
                        Text(
                            "No ranked check-ins yet. Record a region to join the leaderboard.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            val entries = ranking?.entries.orEmpty()
            val currentUser = ranking?.me
            val topEntries = entries.take(10).map { entry ->
                if (currentUser != null && entry.rank == currentUser.rank) {
                    entry.copy(isCurrentUser = true)
                } else {
                    entry
                }
            }
            val currentEntry = currentUser?.takeIf { me ->
                topEntries.none { it.rank == me.rank }
            }
            if (topEntries.isNotEmpty() || currentEntry != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Leaderboard", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Regions first, then countries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { RankingTable(topEntries, currentEntry) }
            }
            if (topEntries.isEmpty() && currentEntry == null) {
                item {
                    Text(
                        "No other ranked explorers yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
private fun RankingTable(
    entries: List<GeographyRankingEntry>,
    currentEntry: GeographyRankingEntry?,
) {
    Card {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "#",
                    modifier = Modifier.width(42.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Explorer",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Regions",
                    modifier = Modifier.width(68.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Countries",
                    modifier = Modifier.width(78.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (entry.isCurrentUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .then(
                            if (entry.isCurrentUser) {
                                Modifier.padding(horizontal = 4.dp)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${entry.rank}",
                        modifier = Modifier.width(42.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (entry.isCurrentUser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            entry.displayName.take(1).uppercase(),
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (entry.isCurrentUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            entry.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                    }
                    Text(
                        "${entry.admin1Count}",
                        modifier = Modifier.width(68.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${entry.countryCount}",
                        modifier = Modifier.width(78.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (index < entries.lastIndex) HorizontalDivider()
            }
            currentEntry?.let { me ->
                HorizontalDivider()
                Text(
                    "…",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                RankingTableRow(me)
            }
        }
    }
}

@Composable
private fun RankingTableRow(entry: GeographyRankingEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${entry.rank}",
            modifier = Modifier.width(42.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.displayName.take(1).uppercase(),
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(entry.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        Text(
            "${entry.admin1Count}",
            modifier = Modifier.width(68.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "${entry.countryCount}",
            modifier = Modifier.width(78.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
