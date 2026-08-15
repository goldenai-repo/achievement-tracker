package com.goldenai.achievements.features.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.goldenai.achievements.features.api.CatalogPlace

/** Shared country/admin1 picker used by Explore and Check-in. */
@Composable
fun CatalogPickerField(
    value: String,
    label: String,
    open: Boolean,
    loading: Boolean,
    places: List<CatalogPlace>,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (CatalogPlace) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (state.isFocused) onFocus()
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null) onFocus()
                    }
                },
            singleLine = true,
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear) { Text("✕") }
                }
            },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                focusable = false,
                dismissOnClickOutside = true,
            ),
            modifier = Modifier
                .widthIn(min = 240.dp, max = 360.dp)
                .heightIn(max = 280.dp),
        ) {
            when {
                loading && places.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                places.isEmpty() -> {
                    Text(
                        "No matching places found.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> {
                    // Material3 DropdownMenu already provides the scrollable
                    // menu container. Do not nest a LazyColumn here: Popup
                    // measurement can otherwise receive infinite height and
                    // crash as soon as the field is opened.
                    places.forEach { place ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(place.name)
                                    Text(
                                        place.code,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = { onSelect(place) },
                        )
                    }
                }
            }
        }
    }
}
