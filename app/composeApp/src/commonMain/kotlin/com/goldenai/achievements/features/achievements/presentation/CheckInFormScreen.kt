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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.formatDate
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.api.CatalogPlace
import com.goldenai.achievements.features.api.CheckInSelection
import com.goldenai.achievements.features.catalog.CatalogPickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInFormScreen(onDone: () -> Unit) {
    val initialSelection = remember { AppGraph.consumePendingCheckInSelection() }
    val vm: CheckInFormViewModel = viewModel {
        CheckInFormViewModel(AppGraph.achievements, AppGraph.api, initialSelection)
    }
    val user by AppGraph.auth.authState.collectAsState(initial = AppGraph.auth.currentUser)
    val apiReady = AppGraph.cloudAvailable && user != null
    var datePickerOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onDone) { Text("‹ Back") }
        Text("Check in a place", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Search a country or first-level administrative region, then add the day you visited.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!apiReady) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Sign in first to search the server catalog and save this check-in.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (vm.selectedCountry == null) {
            Text(
                "Choose a country",
                style = MaterialTheme.typography.titleMedium,
            )
            CatalogPickerField(
                value = vm.countryQuery,
                label = "Search country",
                open = vm.countryDropdownOpen,
                loading = vm.countryLoading,
                places = vm.countries,
                enabled = apiReady,
                onValueChange = vm::updateCountryQuery,
                onFocus = vm::onCountryFocused,
                onClear = vm::clearCountryQuery,
                onDismiss = vm::dismissDropdowns,
                onSelect = vm::chooseCountry,
            )
        } else {
            TextButton(onClick = vm::changeCountry) { Text("‹ Change country") }
            Text("Choose a country", style = MaterialTheme.typography.titleMedium)
            CatalogPickerField(
                value = vm.countryQuery,
                label = "Search country",
                open = vm.countryDropdownOpen,
                loading = vm.countryLoading,
                places = vm.countries,
                enabled = apiReady,
                onValueChange = vm::updateCountryQuery,
                onFocus = vm::onCountryFocused,
                onClear = vm::clearCountryQuery,
                onDismiss = vm::dismissDropdowns,
                onSelect = vm::chooseCountry,
            )

            if (vm.countryHasRegions) {
                Text(
                    "This country has first-level administrative regions. Choose one to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (vm.countryHasRegions) {
                Text("Choose a state or province", style = MaterialTheme.typography.titleMedium)
                SearchField(
                    value = vm.regionQuery,
                    label = "Search state or province",
                    open = vm.regionDropdownOpen,
                    loading = vm.regionLoading,
                    places = vm.regions,
                    enabled = apiReady && vm.selectedCountry != null,
                    onValueChange = vm::updateRegionQuery,
                    onFocus = vm::onRegionFocused,
                    onClear = vm::clearRegionQuery,
                    onDismiss = vm::dismissDropdowns,
                    onSelect = vm::chooseRegion,
                )
            }
        }

        vm.selectedPlace?.let {
            SelectedPlaceCard(
                if (it.kind == "admin1") "Selected region" else "Check-in place",
                it,
            )
        }

        OutlinedButton(
            onClick = { datePickerOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Visited date: ${formatDate(vm.timestamp)}") }

        OutlinedTextField(
            value = vm.notes,
            onValueChange = { vm.notes = it },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )

        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { vm.submit(onDone) },
            enabled = vm.selectedPlace != null && !vm.saving && apiReady,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.saving) "Saving…" else "Check in") }
        Spacer(Modifier.height(24.dp))
    }

    if (datePickerOpen) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = vm.timestamp)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { vm.timestamp = it + 12 * 60 * 60 * 1000 }
                    datePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }
}

@Composable
private fun SearchField(
    value: String,
    label: String,
    open: Boolean,
    loading: Boolean,
    places: List<CatalogPlace>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (CatalogPlace) -> Unit,
) {
    CatalogPickerField(
        value = value,
        label = label,
        open = open,
        loading = loading,
        places = places,
        enabled = enabled,
        onValueChange = onValueChange,
        onFocus = onFocus,
        onClear = onClear,
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun SelectedPlaceCard(label: String, place: CatalogPlace) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(place.name, style = MaterialTheme.typography.titleMedium)
            Text(place.code, style = MaterialTheme.typography.bodySmall)
        }
    }
}
