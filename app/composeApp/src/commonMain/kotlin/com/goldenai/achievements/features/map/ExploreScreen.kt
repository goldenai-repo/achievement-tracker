package com.goldenai.achievements.features.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.mapStyleUrl
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.api.CatalogPlace
import com.goldenai.achievements.features.catalog.CatalogPickerField

@Composable
fun ExploreScreen() {
    val viewModel: ExploreViewModel = viewModel { ExploreViewModel(AppGraph.api) }
    val achievements by AppGraph.achievements.watchAll().collectAsState(emptyList())

    LaunchedEffect(Unit) {
        AppGraph.achievements.refresh()
    }

    val visibleAchievements = achievements.filter { achievement ->
        when {
            viewModel.selectedRegion != null -> achievement.entityId == viewModel.selectedRegion?.id
            viewModel.selectedCountry != null -> {
                achievement.entityId == viewModel.selectedCountry?.id ||
                    achievement.parentId == viewModel.selectedCountry?.id
            }
            else -> true
        }
    }

    val points = visibleAchievements
        .asSequence()
        .filter { it.latitude != null && it.longitude != null }
        .distinctBy { it.entityId }
        .map {
            MapPoint(
                id = it.entityId,
                title = it.locationName ?: it.content,
                subtitle = "${it.entityKind} · ${it.entityCode}",
                latitude = it.latitude!!,
                longitude = it.longitude!!,
            )
        }
        .toList()

    val selectedPlace = viewModel.selectedRegion ?: viewModel.selectedCountry
    val selectedPoint = selectedPlace
        ?.takeIf { it.latitude != null && it.longitude != null }
        ?.let { place ->
            MapPoint(
                id = "selection:${place.id}",
                title = "Selected: ${place.name}",
                subtitle = "${place.kind} · ${place.code}",
                latitude = place.latitude!!,
                longitude = place.longitude!!,
            )
        }
    val mapPoints = buildList {
        addAll(points)
        selectedPoint?.let(::add)
    }
    val boundaries = viewModel.selectedCountry
        ?.boundaryGeoJsonUrl
        ?.let { url ->
            listOf(
                MapBoundary(
                    id = viewModel.selectedCountry?.id ?: "country-boundary",
                    geoJsonUrl = url,
                    selectedCatalogId = viewModel.selectedRegion?.id,
                ),
            )
        }
        ?: emptyList()
    val viewport = selectedPlace
        ?.takeIf { it.latitude != null && it.longitude != null }
        ?.let { place ->
            MapViewport(
                latitude = place.latitude!!,
                longitude = place.longitude!!,
                zoom = if (viewModel.selectedRegion != null) 6.0 else 3.5,
                north = place.bounds?.north,
                south = place.bounds?.south,
                east = place.bounds?.east,
                west = place.bounds?.west,
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Explore", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = viewModel::clearCountry) {
                Text("Reset")
            }
        }

        if (viewModel.step == ExploreStep.COUNTRY_PICKER) {
            Text(
                "Choose a country",
                style = MaterialTheme.typography.titleMedium,
            )
            CatalogPickerField(
                value = viewModel.countryQuery,
                label = "Search country",
                open = viewModel.countryDropdownOpen,
                loading = viewModel.countryLoading,
                places = viewModel.countries,
                onValueChange = viewModel::updateCountryQuery,
                onFocus = viewModel::onCountryFocused,
                onClear = viewModel::clearCountry,
                onDismiss = viewModel::dismissDropdowns,
                onSelect = viewModel::chooseCountry,
            )
        } else {
            TextButton(onClick = viewModel::backToCountryPicker) {
                Text("‹ Change country")
            }
            Text(
                "Selected country: ${viewModel.selectedCountry?.name ?: "Unknown"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Choose a province or state",
                style = MaterialTheme.typography.titleMedium,
            )
            CatalogPickerField(
                value = viewModel.regionQuery,
                label = "Search province or state",
                open = viewModel.regionDropdownOpen,
                loading = viewModel.regionLoading,
                places = viewModel.regions,
                onValueChange = viewModel::updateRegionQuery,
                onFocus = viewModel::onRegionFocused,
                onClear = viewModel::clearRegion,
                onDismiss = viewModel::dismissDropdowns,
                onSelect = viewModel::chooseRegion,
            )
            if (!viewModel.regionLoading && viewModel.regions.isEmpty()) {
                Text(
                    "No first-level administrative regions found. Country-only fallback is available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        viewModel.error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            when {
                viewModel.selectedRegion != null -> "Showing ${viewModel.selectedRegion?.name}"
                viewModel.selectedCountry != null -> "Showing ${viewModel.selectedCountry?.name}"
                else -> "Showing the world"
            },
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            if (points.isEmpty()) "No checked-in places in this area yet."
            else "${points.size} checked-in place${if (points.size == 1) "" else "s"} on the map",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VectorMap(
            points = mapPoints,
            boundaries = boundaries,
            styleUrl = mapStyleUrl,
            viewport = viewport,
            onViewportChanged = viewModel::recordMapViewport,
            modifier = Modifier.fillMaxWidth().height(480.dp),
        )
        Text(
            "Markers represent your checked-in places. The selected country or region is shown even before you check in there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
