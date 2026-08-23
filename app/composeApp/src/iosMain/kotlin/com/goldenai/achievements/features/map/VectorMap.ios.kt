package com.goldenai.achievements.features.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * iOS map placeholder. Catalog search in [ExploreScreen] remains the primary
 * navigation path; tap a listed marker to open the check-in sheet.
 */
@Composable
actual fun VectorMap(
    points: List<MapPoint>,
    boundaries: List<MapBoundary>,
    styleUrl: String,
    viewport: MapViewport?,
    onViewportChanged: (MapViewport) -> Unit,
    onPointClick: (MapPoint) -> Unit,
    onBoundaryClick: (String) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(viewport) {
        viewport?.let(onViewportChanged)
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Map preview (iOS)",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (points.isEmpty()) {
                    "No map markers in this view yet."
                } else {
                    "${points.size} marker${if (points.size == 1) "" else "s"} — tap to select"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (boundaries.isNotEmpty()) {
                Text(
                    "Boundary overlay available via catalog search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            points.forEach { point ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPointClick(point) }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp),
                ) {
                    Text(point.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        point.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
