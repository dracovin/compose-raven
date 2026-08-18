package io.github.dracovin.composeraven

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @param showColor Show the sampled tap color in the info card.
 * @param showTap Show the tap x/y coordinates in the info card.
 * @param showTag Show the element tag/label in the info card.
 * @param showSize Show the element width × height in the info card.
 * @param showPosition Show the element x/y position in the info card.
 * @param showGroup Show the group name in the info card (only when the element has a non-empty group).
 * @param crosshairColor Color of the crosshair drawn at the tap point.
 * @param highlightColor Color of the highlight rect drawn around the element.
 * @param highlightFillAlpha Fill opacity of the highlight rect. Range 0f–1f.
 * @param strokeWidth Stroke width for the crosshair and highlight rect, in pixels.
 * @param cornerRadius Corner radius for the highlight rect, in pixels. 0 = sharp corners.
 */
data class InspectorConfig(
    val showColor: Boolean = true,
    val showTap: Boolean = true,
    val showTag: Boolean = true,
    val showSize: Boolean = true,
    val showPosition: Boolean = true,
    val showGroup: Boolean = true,
    val crosshairColor: Color = Color.Red,
    val highlightColor: Color = Color(0xFF2196F3),
    val highlightFillAlpha: Float = 0.2f,
    val strokeWidth: Float = 2f,
    val cornerRadius: Float = 0f,
)

/**
 * Global configuration for the Raven grid overlay.
 *
 * Apply via [ComposeRaven.setGridConfig]. Enable the overlay via the Raven FAB → Grid chip.
 *
 * @param gridSpacing Distance between grid lines. Defaults to 8dp (Material baseline grid).
 * @param gridColor Color of the baseline grid lines.
 * @param keyline16Color Color of the 16dp margin keylines.
 * @param keyline24Color Color of the 24dp margin keylines.
 * @param showKeylines Whether to draw the 16dp and 24dp keylines.
 */
data class GridConfig(
    val gridSpacing: Dp = 8.dp,
    val gridColor: Color = Color.White.copy(alpha = 0.10f),
    val keyline16Color: Color = Color(0xFF00BCD4).copy(alpha = 0.50f),
    val keyline24Color: Color = Color(0xFFFF5722).copy(alpha = 0.50f),
    val showKeylines: Boolean = true,
)

object ComposeRaven {
    /**
     * Sets the global grid overlay configuration.
     *
     * Call once at startup (e.g. in `Application.onCreate`) to customize grid spacing,
     * line colors, and keyline visibility. If not called, defaults to an 8dp grid with
     * cyan 16dp and orange 24dp keylines.
     */
    fun setGridConfig(config: GridConfig) {
        RavenState.gridConfig.value = config
    }
}
