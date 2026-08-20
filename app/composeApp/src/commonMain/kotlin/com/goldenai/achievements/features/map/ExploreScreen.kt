package com.goldenai.achievements.features.map

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goldenai.achievements.core.formatDate
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.core.mapStyleUrl
import com.goldenai.achievements.di.AppGraph
import com.goldenai.achievements.features.api.CatalogPlace
import com.goldenai.achievements.features.api.CheckInSelection
import com.goldenai.achievements.features.catalog.CatalogPickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onCheckIn: (CheckInSelection) -> Unit) {
    val viewModel: ExploreViewModel = viewModel { ExploreViewModel(AppGraph.api) }
    val achievements by AppGraph.achievements.watchAll().collectAsState(emptyList())
    var selectedMapSelection by remember { mutableStateOf<CheckInSelection?>(null) }

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
                isCheckedIn = true,
            )
        }
        .toList()

    val selectedPlace = viewModel.selectedRegion ?: viewModel.selectedCountry
    val selectedPlaceIsCheckedIn = selectedPlace?.let { place ->
        achievements.any { it.entityId == place.id }
    } == true
    val selectedPoint = selectedPlace
        ?.takeIf {
            it.latitude != null &&
                it.longitude != null &&
                !selectedPlaceIsCheckedIn
        }
        ?.let { place ->
            MapPoint(
                id = "selection:${place.id}",
                title = "Selected: ${place.name}",
                subtitle = "${place.kind} · ${place.code}",
                latitude = place.latitude!!,
                longitude = place.longitude!!,
                isCheckedIn = false,
                isSearchSelection = true,
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
            cameraResetKey = viewModel.cameraResetKey,
            onViewportChanged = viewModel::recordMapViewport,
            onPointClick = { point ->
                val place = when {
                    point.id.startsWith("selection:") -> selectedPlace
                    else -> visibleAchievements
                        .firstOrNull { it.entityId == point.id }
                        ?.toCatalogPlace()
                }
                val country = when {
                    place == null -> null
                    place.kind == "country" -> place
                    else -> viewModel.selectedCountry
                }
                if (place != null && country != null) {
                    selectedMapSelection = CheckInSelection(country, place)
                }
            },
            onBoundaryClick = { catalogId ->
                val region = viewModel.regionById(catalogId)
                val country = viewModel.selectedCountry
                if (region != null && country != null) {
                    viewModel.chooseRegion(region)
                    selectedMapSelection = CheckInSelection(country, region)
                }
            },
            modifier = Modifier.fillMaxWidth().height(480.dp),
        )
        Text(
            "Tap a marker or an administrative boundary to review the place and add a check-in. The selected country or region is shown even before you check in there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Blue markers are checked-in places. Orange markers are the current search selection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    selectedMapSelection?.let { selection ->
        val visits = achievements
            .filter { achievement ->
                if (selection.place.kind == "country") {
                    achievement.entityId == selection.place.id ||
                        achievement.parentId == selection.place.id
                } else {
                    achievement.entityId == selection.place.id
                }
            }
            .sortedByDescending { it.timestamp }
        val visitedRegionCount = visits
            .filter { it.entityKind == "admin1" }
            .map { it.entityId }
            .distinct()
            .size
        ModalBottomSheet(
            onDismissRequest = { selectedMapSelection = null },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(selection.place.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${selection.country.name} · ${selection.place.kind}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        visits.isEmpty() -> "Not checked in yet"
                        selection.place.kind == "country" && visitedRegionCount > 0 ->
                            "Visited in $visitedRegionCount region${if (visitedRegionCount == 1) "" else "s"}"
                        else -> "${visits.size} visit${if (visits.size == 1) "" else "s"}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                visits.take(5).forEach { visit ->
                    Text(
                        "• ${formatDate(visit.timestamp)}${visit.notes?.let { " · $it" }.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = {
                        selectedMapSelection = null
                        onCheckIn(selection)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            selection.place.kind == "country" -> "Choose a state or province"
                            visits.isEmpty() -> "Add check-in"
                            else -> "Add another visit"
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun Achievement.toCatalogPlace(): CatalogPlace = CatalogPlace(
    id = entityId,
    kind = entityKind,
    code = entityCode,
    name = locationName ?: content,
    parentId = parentId,
    latitude = latitude,
    longitude = longitude,
)
