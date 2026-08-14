package io.github.dracovin.composeraven.features

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun GridOverlay() {
    val density = LocalDensity.current

    Canvas(modifier = Modifier.fillMaxSize()) {
        val grid8px     = with(density) { 8.dp.toPx()  }
        val keyline16px = with(density) { 16.dp.toPx() }
        val keyline24px = with(density) { 24.dp.toPx() }
        val thinStroke  = with(density) { 0.5.dp.toPx() }
        val thickStroke = with(density) { 1.dp.toPx()   }

        val gridColor      = Color.White.copy(alpha = 0.10f)
        val keyline16Color = Color(0xFF00BCD4).copy(alpha = 0.50f)  // cyan
        val keyline24Color = Color(0xFFFF5722).copy(alpha = 0.50f)  // deep-orange

        // 8dp vertical grid lines
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), thinStroke)
            x += grid8px
        }
        // 8dp horizontal grid lines
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), thinStroke)
            y += grid8px
        }
        // 16dp keylines — left and right
        drawLine(keyline16Color, Offset(keyline16px, 0f), Offset(keyline16px, size.height), thickStroke)
        drawLine(keyline16Color, Offset(size.width - keyline16px, 0f), Offset(size.width - keyline16px, size.height), thickStroke)
        // 24dp keylines — left and right
        drawLine(keyline24Color, Offset(keyline24px, 0f), Offset(keyline24px, size.height), thickStroke)
        drawLine(keyline24Color, Offset(size.width - keyline24px, 0f), Offset(size.width - keyline24px, size.height), thickStroke)
    }
}
