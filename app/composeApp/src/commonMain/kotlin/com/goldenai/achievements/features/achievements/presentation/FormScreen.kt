package com.goldenai.achievements.features.achievements.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.formatDate
import com.goldenai.achievements.core.model.AchievementType
import com.goldenai.achievements.di.AppGraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(
    editId: String?,
    onDone: () -> Unit,
) {
    val vm: AchievementFormViewModel = viewModel(key = editId ?: "new") {
        AchievementFormViewModel(AppGraph.achievements, editId)
    }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("‹ Back") }
            Spacer(Modifier.weight(1f))
            if (vm.isEdit) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(
            if (vm.isEdit) "Edit achievement" else "New achievement",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!vm.loaded) {
            CircularProgressIndicator()
            return@Column
        }

        ExposedDropdownMenuBox(expanded = typeMenuOpen, onExpandedChange = { typeMenuOpen = it }) {
            OutlinedTextField(
                value = vm.typeKey?.let { key ->
                    AchievementType.fromKey(key)?.let { "${it.emoji} ${it.label}" }
                } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                AchievementType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text("${type.emoji} ${type.label}") },
                        onClick = {
                            vm.typeKey = type.key
                            typeMenuOpen = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = vm.content,
            onValueChange = { vm.content = it },
            label = { Text(contentLabel(vm.typeKey)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = vm.locationName,
            onValueChange = { vm.locationName = it },
            label = { Text("Place (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedButton(onClick = { datePickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Date: ${formatDate(vm.timestamp)}")
        }

        OutlinedTextField(
            value = vm.notes,
            onValueChange = { vm.notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )

        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { vm.save(onSuccess = onDone) },
            enabled = vm.isValid && !vm.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.saving) "Saving…" else "Save")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (datePickerOpen) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = vm.timestamp)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    // Noon UTC keeps the picked calendar date stable across timezones.
                    dateState.selectedDateMillis?.let { vm.timestamp = it + 12 * 60 * 60 * 1000 }
                    datePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete achievement?") },
            text = { Text("This removes it from your log (and from the cloud on next sync).") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onSuccess = onDone)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

private fun contentLabel(typeKey: String?): String = when (AchievementType.fromKey(typeKey ?: "")) {
    AchievementType.GeographyCountry -> "Country"
    AchievementType.GeographyState -> "State or province"
    AchievementType.GeographyCity -> "City"
    AchievementType.WildlifeAnimal -> "Species"
    AchievementType.WildlifePlant -> "Species"
    AchievementType.CultureMuseum -> "Museum or gallery"
    AchievementType.EntertainmentMovie -> "Movie title"
    AchievementType.CulinaryMichelin -> "Restaurant"
    AchievementType.HeritageUnesco -> "Heritage site"
    null -> "What was it?"
}
