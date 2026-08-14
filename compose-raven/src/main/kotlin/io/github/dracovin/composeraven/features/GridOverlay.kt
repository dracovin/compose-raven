package io.github.dracovin.composeraven.features

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dracovin.composeraven.RavenState

@Composable
internal fun GridOverlay() {
    val config  by RavenState.gridConfig.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridPx      = with(density) { config.gridSpacing.toPx() }
        val keyline16px = with(density) { 16.dp.toPx() }
        val keyline24px = with(density) { 24.dp.toPx() }
        val thinStroke  = with(density) { 0.5.dp.toPx() }
        val thickStroke = with(density) { 1.dp.toPx()   }

        var x = 0f
        while (x <= size.width) {
            drawLine(config.gridColor, Offset(x, 0f), Offset(x, size.height), thinStroke)
            x += gridPx
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(config.gridColor, Offset(0f, y), Offset(size.width, y), thinStroke)
            y += gridPx
        }

        if (config.showKeylines) {
            drawLine(config.keyline16Color, Offset(keyline16px, 0f), Offset(keyline16px, size.height), thickStroke)
            drawLine(config.keyline16Color, Offset(size.width - keyline16px, 0f), Offset(size.width - keyline16px, size.height), thickStroke)
            drawLine(config.keyline24Color, Offset(keyline24px, 0f), Offset(keyline24px, size.height), thickStroke)
            drawLine(config.keyline24Color, Offset(size.width - keyline24px, 0f), Offset(size.width - keyline24px, size.height), thickStroke)
        }
    }
}
