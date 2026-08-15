package com.goldenai.achievements.features.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class MapPoint(
    val id: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
)

data class MapViewport(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val north: Double? = null,
    val south: Double? = null,
    val east: Double? = null,
    val west: Double? = null,
)

/**
 * A GeoJSON boundary asset rendered above the basemap.
 *
 * The GeoJSON contract uses a `catalog_id` feature property. This lets the
 * map render all admin-1 boundaries and fill only the currently selected
 * country/region without coupling the renderer to a particular provider.
 */
data class MapBoundary(
    val id: String,
    val geoJsonUrl: String,
    val selectedCatalogId: String? = null,
)

@Composable
expect fun VectorMap(
    points: List<MapPoint>,
    boundaries: List<MapBoundary> = emptyList(),
    styleUrl: String,
    viewport: MapViewport? = null,
    onViewportChanged: (MapViewport) -> Unit = {},
    modifier: Modifier,
)
