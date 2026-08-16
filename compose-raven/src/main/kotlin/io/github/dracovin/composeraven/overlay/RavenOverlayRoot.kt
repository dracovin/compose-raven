package io.github.dracovin.composeraven.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dracovin.composeraven.RavenState
import io.github.dracovin.composeraven.features.ElementPickerOverlay
import io.github.dracovin.composeraven.features.GridOverlay
import kotlin.math.roundToInt

@Composable
internal fun RavenOverlayRoot() {
    MaterialTheme {
    val heatmapEnabled   by RavenState.heatmapEnabled.collectAsStateWithLifecycle()
    val inspectorEnabled by RavenState.inspectorEnabled.collectAsStateWithLifecycle()
    val gridEnabled      by RavenState.gridEnabled.collectAsStateWithLifecycle()
    var menuExpanded     by remember { mutableStateOf(false) }
    var fabOffset        by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (gridEnabled)      GridOverlay()
        if (inspectorEnabled) ElementPickerOverlay()

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .offset { IntOffset(fabOffset.x.roundToInt(), fabOffset.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        fabOffset = Offset(fabOffset.x + drag.x, fabOffset.y + drag.y)
                    }
                },
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = menuExpanded,
                enter   = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit    = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RavenToggleChip("Heatmap",   "Toggle Heatmap",   heatmapEnabled)   { RavenState.heatmapEnabled.value   = !heatmapEnabled }
                    RavenToggleChip("Inspector", "Toggle Inspector", inspectorEnabled) { RavenState.inspectorEnabled.value = !inspectorEnabled }
                    RavenToggleChip("Grid",      "Toggle Grid",      gridEnabled)      { RavenState.gridEnabled.value      = !gridEnabled }
                }
            }

            FloatingActionButton(
                onClick  = { menuExpanded = !menuExpanded },
                modifier = Modifier.semantics { contentDescription = "Open Raven debug menu" },
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null)
            }
        }
    }
    } // MaterialTheme
}

@Composable
private fun RavenToggleChip(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    FilterChip(
        selected = checked,
        onClick  = onToggle,
        label    = { Text(label) },
        modifier = Modifier.semantics { contentDescription = description },
    )
}
