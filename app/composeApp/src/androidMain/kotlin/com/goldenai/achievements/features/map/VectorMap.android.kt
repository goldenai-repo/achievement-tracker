package com.goldenai.achievements.features.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URI
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

@Composable
actual fun VectorMap(
    points: List<MapPoint>,
    boundaries: List<MapBoundary>,
    styleUrl: String,
    viewport: MapViewport?,
    onViewportChanged: (MapViewport) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val latestViewportCallback = rememberUpdatedState(onViewportChanged)
    val mapReference = remember { mutableStateOf<MapLibreMap?>(null) }
    val loadedStyleUrl = remember { mutableStateOf<String?>(null) }
    val appliedCameraKey = remember { mutableStateOf<String?>(null) }
    val cameraIdleListener = remember {
        MapLibreMap.OnCameraIdleListener {
            val map = mapReference.value ?: return@OnCameraIdleListener
            val camera = map.cameraPosition
            val target = camera.target ?: return@OnCameraIdleListener
            val bounds = map.projection.visibleRegion.latLngBounds
            latestViewportCallback.value(
                MapViewport(
                    latitude = target.latitude,
                    longitude = target.longitude,
                    zoom = camera.zoom,
                    north = bounds.latitudeNorth,
                    south = bounds.latitudeSouth,
                    east = bounds.longitudeEast,
                    west = bounds.longitudeWest,
                ),
            )
        }
    }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapReference.value?.removeOnCameraIdleListener(cameraIdleListener)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { map ->
                if (mapReference.value !== map) {
                    mapReference.value?.removeOnCameraIdleListener(cameraIdleListener)
                    map.addOnCameraIdleListener(cameraIdleListener)
                    mapReference.value = map
                }

                val render = {
                    renderMapContent(map, points, boundaries)
                    val cameraKey = cameraKey(viewport)
                    if (appliedCameraKey.value != cameraKey) {
                        appliedCameraKey.value = cameraKey
                        applyViewport(view, map, viewport)
                    }
                }

                // Avoid reloading the style on every Compose recomposition;
                // reloading would reset the camera and flicker the map.
                if (loadedStyleUrl.value != styleUrl) {
                    loadedStyleUrl.value = styleUrl
                    appliedCameraKey.value = null
                    map.setStyle(styleUrl) { render() }
                } else {
                    render()
                }
            }
        },
    )
}

private fun cameraKey(viewport: MapViewport?): String = viewport?.let {
    listOf(
        it.latitude,
        it.longitude,
        it.zoom,
        it.north,
        it.south,
        it.east,
        it.west,
    ).joinToString("/")
} ?: "world"

private fun applyViewport(
    mapView: MapView,
    map: MapLibreMap,
    viewport: MapViewport?,
) {
    val apply = {
        val bounds = viewport?.let {
            val north = it.north
            val south = it.south
            val east = it.east
            val west = it.west
            if (north != null && south != null && east != null && west != null) {
                LatLngBounds.from(north, east, south, west)
            } else {
                null
            }
        }
        if (bounds != null) {
            val padding = (48 * mapView.resources.displayMetrics.density).toInt()
            map.easeCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, padding),
                550,
            )
        } else if (viewport != null) {
            map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(viewport.latitude, viewport.longitude),
                    viewport.zoom,
                ),
                550,
            )
        } else {
            map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 1.2),
                550,
            )
        }
    }

    // Camera bounds require a laid-out map view. This also covers the first
    // style callback during Activity startup.
    if (mapView.width == 0 || mapView.height == 0) {
        mapView.post { apply() }
    } else {
        apply()
    }
}

private fun renderMapContent(
    map: MapLibreMap,
    points: List<MapPoint>,
    boundaries: List<MapBoundary>,
) {
    map.clear()
    val style = map.style ?: return

    // Boundary layers are owned by this composable. Remove previous versions
    // so Reset/back navigation cannot leave stale polygons behind.
    style.layers
        .map { it.id }
        .filter { it.startsWith(BOUNDARY_LAYER_PREFIX) }
        .forEach(style::removeLayer)
    style.sources
        .map { it.id }
        .filter { it.startsWith(BOUNDARY_SOURCE_PREFIX) }
        .forEach(style::removeSource)

    points.forEach { point ->
        map.addMarker(
            MarkerOptions()
                .position(LatLng(point.latitude, point.longitude))
                .title(point.title)
                .snippet(point.subtitle),
        )
    }

    boundaries.forEach { boundary ->
        val sourceId = "$BOUNDARY_SOURCE_PREFIX${boundary.id}"
        val lineId = "$BOUNDARY_LAYER_PREFIX${boundary.id}-line"
        val fillId = "$BOUNDARY_LAYER_PREFIX${boundary.id}-fill"
        val selectedLineId = "$BOUNDARY_LAYER_PREFIX${boundary.id}-selected-line"
        // The String overload expects raw GeoJSON text. Use the URI overload
        // so MapLibre fetches the local FastAPI boundary asset asynchronously.
        style.addSource(GeoJsonSource(sourceId, URI.create(boundary.geoJsonUrl)))
        style.addLayer(
            LineLayer(lineId, sourceId).withProperties(
                lineColor("#64748B"),
                lineOpacity(0.72f),
                lineWidth(1.1f),
            ),
        )

        boundary.selectedCatalogId?.let { selectedId ->
            style.addLayer(
                FillLayer(fillId, sourceId)
                    .withFilter(
                        Expression.eq(Expression.get("catalog_id"), selectedId),
                    )
                    .withProperties(
                        fillColor("#2563EB"),
                        fillOpacity(0.28f),
                        fillOutlineColor("#1D4ED8"),
                    ),
            )
            style.addLayer(
                LineLayer(selectedLineId, sourceId)
                    .withFilter(
                        Expression.eq(Expression.get("catalog_id"), selectedId),
                    )
                    .withProperties(
                        lineColor("#1D4ED8"),
                        lineOpacity(0.95f),
                        lineWidth(3.0f),
                    ),
            )
        }
    }
}

private const val BOUNDARY_SOURCE_PREFIX = "achievement-boundary-source-"
private const val BOUNDARY_LAYER_PREFIX = "achievement-boundary-layer-"
