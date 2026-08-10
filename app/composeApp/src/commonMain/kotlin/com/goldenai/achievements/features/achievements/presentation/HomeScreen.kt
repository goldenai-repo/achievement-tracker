package com.goldenai.achievements.features.achievements.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.core.model.AchievementType
import com.goldenai.achievements.di.AppGraph

@Composable
fun HomeScreen(
    onBackupClick: () -> Unit,
) {
    val vm: HomeViewModel = viewModel { HomeViewModel(AppGraph.achievements) }
    val counts by vm.counts.collectAsState()
    val recent by vm.recent.collectAsState()
    val summary by vm.summary.collectAsState()
    val user by AppGraph.auth.authState.collectAsState(initial = AppGraph.auth.currentUser)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Achievements", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${summary?.checkinCount ?: counts.values.sum()} logged so far",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (AppGraph.cloudAvailable && user == null) {
            item {
                Card(
                    onClick = onBackupClick,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("You're in guest mode", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Achievements are stored on this device only. " +
                                "Sign in to back them up to the cloud.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        items(AchievementType.entries.filter { it.visibleInMvp }.chunked(2)) { rowTypes ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTypes.forEach { type ->
                    CategoryCard(
                        type = type,
                        count = counts[type.key] ?: 0L,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Recent", style = MaterialTheme.typography.titleMedium)
            }
            items(recent, key = { it.id }) { achievement ->
                // Home is a dashboard. Detailed/edit flows can be added later
                // without making the Home tab jump into the Log tab.
                AchievementRow(achievement)
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nothing logged yet — tap + to record your first achievement!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    type: AchievementType,
    count: Long,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(type.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("$count", style = MaterialTheme.typography.titleLarge)
            Text(
                type.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun AchievementRow(achievement: Achievement, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(achievement.typeInfo?.emoji ?: "🏆", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(achievement.content, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                val subtitle = buildString {
                    achievement.locationName?.let { append(it).append(" · ") }
                    append(com.goldenai.achievements.core.formatDate(achievement.timestamp))
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), content = { content() })
    } else {
        Card(modifier = Modifier.fillMaxWidth(), content = { content() })
    }
}
