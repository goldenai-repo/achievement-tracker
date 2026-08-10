package com.goldenai.achievements.features.achievements.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.model.AchievementType
import com.goldenai.achievements.di.AppGraph

@Composable
fun ListScreen(
    initialType: String?,
    onItemClick: (String) -> Unit,
) {
    val vm: AchievementListViewModel = viewModel {
        AchievementListViewModel(AppGraph.achievements, initialType)
    }
    val items by vm.items.collectAsState()
    val selectedType by vm.selectedType.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Log",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { vm.selectType(null) },
                    label = { Text("All") },
                )
            }
            items(AchievementType.entries.filter { it.visibleInMvp }) { type ->
                FilterChip(
                    selected = selectedType == type.key,
                    onClick = { vm.selectType(type.key) },
                    label = { Text("${type.emoji} ${type.category}") },
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                "Nothing here yet. Tap + to log an achievement.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { achievement ->
                    AchievementRow(achievement, onClick = { onItemClick(achievement.id) })
                }
            }
        }
    }
}
