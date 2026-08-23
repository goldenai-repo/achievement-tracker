package com.goldenai.achievements.features.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val mapJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

@Serializable
private data class PointPayload(
    val id: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val isSearchSelection: Boolean,
)

@Serializable
private data class BoundaryPayload(
    val id: String,
    val geoJsonUrl: String,
    val selectedCatalogId: String? = null,
)

@Serializable
private data class ViewportPayload(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val north: Double? = null,
    val south: Double? = null,
    val east: Double? = null,
    val west: Double? = null,
)

/**
 * MapLibre Native map embedded via UIKitView. Rendering lives in the Swift
 * host (`AchievementMapView`); this actual only forwards shared map state.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VectorMap(
    points: List<MapPoint>,
    boundaries: List<MapBoundary>,
    styleUrl: String,
    viewport: MapViewport?,
    cameraResetKey: Long,
    onViewportChanged: (MapViewport) -> Unit,
    onPointClick: (MapPoint) -> Unit,
    onBoundaryClick: (String) -> Unit,
    modifier: Modifier,
) {
    val factory = AchievementMapBridge.factory
    if (factory == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "MapLibre host was not registered. Rebuild iosApp so iOSApp registers AchievementMapBridge.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }

    val handle = remember(factory) { factory.create() }
    val latestPoints = rememberUpdatedState(points)
    val latestPointClick = rememberUpdatedState(onPointClick)
    val latestBoundaryClick = rememberUpdatedState(onBoundaryClick)
    val latestViewportChanged = rememberUpdatedState(onViewportChanged)

    val listener = remember {
        object : AchievementMapListener {
            override fun onPointClick(pointId: String) {
                latestPoints.value.firstOrNull { it.id == pointId }
                    ?.let(latestPointClick.value)
            }

            override fun onBoundaryClick(catalogId: String) {
                latestBoundaryClick.value(catalogId)
            }

            override fun onViewportChanged(viewportJson: String) {
                runCatching {
                    mapJson.decodeFromString(ViewportPayload.serializer(), viewportJson)
                }.getOrNull()?.let { payload ->
                    latestViewportChanged.value(
                        MapViewport(
                            latitude = payload.latitude,
                            longitude = payload.longitude,
                            zoom = payload.zoom,
                            north = payload.north,
                            south = payload.south,
                            east = payload.east,
                            west = payload.west,
                        ),
                    )
                }
            }
        }
    }

    DisposableEffect(handle) {
        handle.setListener(listener)
        onDispose { handle.setListener(null) }
    }

    val pointsJson = remember(points) {
        mapJson.encodeToString(
            points.map {
                PointPayload(
                    id = it.id,
                    title = it.title,
                    subtitle = it.subtitle,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    isSearchSelection = it.isSearchSelection,
                )
            },
        )
    }
    val boundariesJson = remember(boundaries) {
        mapJson.encodeToString(
            boundaries.map {
                BoundaryPayload(
                    id = it.id,
                    geoJsonUrl = it.geoJsonUrl,
                    selectedCatalogId = it.selectedCatalogId,
                )
            },
        )
    }
    val viewportJson = remember(viewport) {
        viewport?.let {
            mapJson.encodeToString(
                ViewportPayload(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    zoom = it.zoom,
                    north = it.north,
                    south = it.south,
                    east = it.east,
                    west = it.west,
                ),
            )
        }
    }

    UIKitView(
        factory = { handle.view() },
        modifier = modifier.fillMaxSize(),
        update = {
            handle.bind(
                styleUrl = styleUrl,
                pointsJson = pointsJson,
                boundariesJson = boundariesJson,
                viewportJson = viewportJson,
                cameraResetKey = cameraResetKey,
            )
        },
    )
}
