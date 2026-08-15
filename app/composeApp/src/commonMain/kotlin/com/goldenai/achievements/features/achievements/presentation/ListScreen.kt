package com.goldenai.achievements.features.achievements.presentation

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.country.countryFlagEmoji
import com.goldenai.achievements.core.formatDate
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.di.AppGraph
import androidx.compose.material3.rememberDatePickerState

private val logFilters = listOf(
    "geography" to "🌍 Geography",
    "wildlife" to "🦁 Wildlife",
    "culture" to "🏛️ Culture",
    "heritage" to "🏯 Heritage",
    "entertainment" to "🎬 Entertainment",
    "culinary" to "⭐ Culinary",
)

@Composable
fun ListScreen(
    initialType: String?,
) {
    val vm: AchievementListViewModel = viewModel {
        AchievementListViewModel(AppGraph.achievements, initialType)
    }
    val groupedItems by vm.groupedItems.collectAsState()
    val geographyDirectory by vm.geographyDirectory.collectAsState()
    val selectedType by vm.selectedType.collectAsState()
    val user by AppGraph.auth.authState.collectAsState(initial = AppGraph.auth.currentUser)
    var deleteTarget by remember { mutableStateOf<Achievement?>(null) }
    var editTarget by remember { mutableStateOf<Achievement?>(null) }
    var batchDeleteTarget by remember { mutableStateOf<Set<String>?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // The tab destination can be reused by Navigation Compose. Keep the
    // ViewModel filter in sync when Home navigates to another category.
    LaunchedEffect(initialType) {
        vm.selectType(initialType)
    }
    LaunchedEffect(selectedType) {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Log", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (selectionMode) {
                Text(
                    "${selectedIds.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { batchDeleteTarget = selectedIds },
                    enabled = selectedIds.isNotEmpty(),
                ) { Text("Delete") }
                TextButton(
                    onClick = {
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                ) { Text("Cancel") }
            } else {
                TextButton(onClick = { selectionMode = true }) { Text("Select") }
            }
        }
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
            items(logFilters) { (filter, label) ->
                FilterChip(
                    selected = selectedType == filter,
                    onClick = { vm.selectType(filter) },
                    label = { Text(label) },
                )
            }
        }

        if (selectedType == "geography") {
            if (geographyDirectory.isEmpty()) {
                EmptyLogMessage()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "Countries → regions → visit history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(geographyDirectory, key = { it.countryId }) { country ->
                        GeographyCountryRow(
                            country = country,
                            selectionMode = selectionMode,
                            selectedIds = selectedIds,
                            onToggleSelection = ::toggleSelection,
                            onEdit = { editTarget = it },
                            onDelete = { deleteTarget = it },
                        )
                    }
                }
            }
        } else if (groupedItems.isEmpty()) {
            EmptyLogMessage()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groupedItems, key = { it.place.entityId }) { group ->
                    GroupedAchievementRow(
                        group = group,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelection = ::toggleSelection,
                        onEdit = { editTarget = it },
                        onDelete = { deleteTarget = it },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        DeleteVisitDialog(
            visit = target,
            requiresPassword = user != null,
            deleting = vm.deletingId == target.id,
            error = vm.deleteError,
            onDismiss = { if (vm.deletingId == null) deleteTarget = null },
            onConfirm = { password ->
                vm.deleteVisit(target.id, password) { deleteTarget = null }
            },
        )
    }

    batchDeleteTarget?.let { ids ->
        BatchDeleteDialog(
            count = ids.size,
            requiresPassword = user != null,
            deleting = vm.deletingBatch,
            error = vm.deleteError,
            onDismiss = { if (!vm.deletingBatch) batchDeleteTarget = null },
            onConfirm = { password ->
                vm.deleteVisits(ids.toList(), password) {
                    batchDeleteTarget = null
                    selectionMode = false
                    selectedIds = emptySet()
                }
            },
        )
    }

    editTarget?.let { target ->
        EditVisitDialog(
            visit = target,
            saving = vm.updatingId == target.id,
            error = vm.updateError,
            onDismiss = { if (vm.updatingId == null) editTarget = null },
            onConfirm = { timestamp, notes ->
                vm.updateVisit(target.id, timestamp, notes) { editTarget = null }
            },
        )
    }
}

@Composable
private fun EmptyLogMessage() {
    Text(
        "Nothing here yet. Tap + to log an achievement.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun GeographyCountryRow(
    country: GeographyCountryGroup,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onEdit: (Achievement) -> Unit,
    onDelete: (Achievement) -> Unit,
) {
    var manuallyExpanded by remember(country.countryId) { mutableStateOf(false) }
    val expanded = selectionMode || manuallyExpanded

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(countryFlagEmoji(country.countryCode), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(country.countryName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${country.regions.size} region${if (country.regions.size == 1) "" else "s"} · " +
                            "${country.visitCount} visit${if (country.visitCount == 1) "" else "s"} · " +
                            "Last ${formatDate(country.lastVisitedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { manuallyExpanded = !manuallyExpanded }) {
                    Text(if (expanded) "⌃" else "⌄")
                }
            }

            if (expanded) {
                country.countryVisits?.let { group ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Country visits", style = MaterialTheme.typography.labelLarge)
                    VisitHistory(group, selectionMode, selectedIds, onToggleSelection, onEdit, onDelete)
                }
                country.regions.forEach { region ->
                    RegionRow(region, selectionMode, selectedIds, onToggleSelection, onEdit, onDelete)
                }
            }
        }
    }
}

@Composable
private fun RegionRow(
    group: AchievementGroup,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onEdit: (Achievement) -> Unit,
    onDelete: (Achievement) -> Unit,
) {
    var manuallyExpanded by remember(group.place.entityId) { mutableStateOf(false) }
    val expanded = selectionMode || manuallyExpanded
    val place = group.place

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🗺️", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(place.content, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${group.visitCount} visit${if (group.visitCount == 1) "" else "s"} · " +
                        "Last ${formatDate(group.lastVisitedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { manuallyExpanded = !manuallyExpanded }) {
                Text(if (expanded) "⌃" else "⌄")
            }
        }
        if (expanded) VisitHistory(group, selectionMode, selectedIds, onToggleSelection, onEdit, onDelete)
    }
}

@Composable
private fun VisitHistory(
    group: AchievementGroup,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onEdit: (Achievement) -> Unit,
    onDelete: (Achievement) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 34.dp, bottom = 4.dp)) {
        group.visits.forEach { visit ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(formatDate(visit.timestamp), style = MaterialTheme.typography.bodyMedium)
                    visit.notes?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (selectionMode) {
                    Checkbox(
                        checked = visit.id in selectedIds,
                        onCheckedChange = { onToggleSelection(visit.id) },
                    )
                } else {
                    IconButton(onClick = { onEdit(visit) }) {
                        Text("✎", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { onDelete(visit) }) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedAchievementRow(
    group: AchievementGroup,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onEdit: (Achievement) -> Unit,
    onDelete: (Achievement) -> Unit,
) {
    var manuallyExpanded by remember(group.place.entityId) { mutableStateOf(false) }
    val expanded = selectionMode || manuallyExpanded
    val place = group.place

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(place.typeInfo?.emoji ?: "🏆", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.content, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${group.visitCount} visit${if (group.visitCount == 1) "" else "s"} · " +
                            "Last ${formatDate(group.lastVisitedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { manuallyExpanded = !manuallyExpanded }) {
                    Text(if (expanded) "⌃" else "⌄")
                }
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                VisitHistory(group, selectionMode, selectedIds, onToggleSelection, onEdit, onDelete)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditVisitDialog(
    visit: Achievement,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (Long, String?) -> Unit,
) {
    var timestamp by remember(visit.id) { mutableStateOf(visit.timestamp) }
    var notes by remember(visit.id) { mutableStateOf(visit.notes.orEmpty()) }
    var datePickerOpen by remember(visit.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit check-in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${visit.locationName ?: visit.content} · ${visit.entityKind}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { datePickerOpen = true },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Visited date: ${formatDate(timestamp)}") }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note (optional)") },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(timestamp, notes.takeIf { it.isNotBlank() }) },
                enabled = !saving,
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            if (!saving) OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (datePickerOpen) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { timestamp = it + 12 * 60 * 60 * 1000 }
                    datePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }
}

@Composable
private fun DeleteVisitDialog(
    visit: Achievement,
    requiresPassword: Boolean,
    deleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var password by remember(visit.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this visit?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${visit.locationName ?: visit.content} · ${formatDate(visit.timestamp)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (requiresPassword) {
                        "Re-enter your account password to permanently remove this visit from your cloud log."
                    } else {
                        "This removes the visit from this device. Guest records have not been uploaded."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (requiresPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Account password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !deleting,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password.takeIf { requiresPassword }) },
                enabled = !deleting && (!requiresPassword || password.isNotBlank()),
            ) {
                Text(if (deleting) "Deleting…" else "Delete")
            }
        },
        dismissButton = {
            if (!deleting) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            } else {
                TextButton(onClick = {}, enabled = false) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun BatchDeleteDialog(
    count: Int,
    requiresPassword: Boolean,
    deleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var password by remember(count) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $count visits?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (requiresPassword) {
                        "Re-enter your account password to remove these visits from your cloud log."
                    } else {
                        "These visits will be removed from this device. Guest records have not been uploaded."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (requiresPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Account password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !deleting,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password.takeIf { requiresPassword }) },
                enabled = !deleting && (!requiresPassword || password.isNotBlank()),
            ) {
                Text(if (deleting) "Deleting…" else "Delete all")
            }
        },
        dismissButton = {
            if (!deleting) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            } else {
                TextButton(onClick = {}, enabled = false) { Text("Cancel") }
            }
        },
    )
}
