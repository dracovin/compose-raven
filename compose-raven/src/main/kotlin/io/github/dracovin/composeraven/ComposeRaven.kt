package io.github.dracovin.composeraven

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class InspectorConfig(
    val showColor: Boolean = true,
    val showTap: Boolean = true,
    val showTag: Boolean = true,
    val showSize: Boolean = true,
    val crosshairColor: Color = Color.Red,
    val highlightColor: Color = Color(0xFF2196F3),
    val highlightFillAlpha: Float = 0.2f,
    val strokeWidth: Float = 2f,
)

data class GridConfig(
    val gridSpacing: Dp = 8.dp,
    val gridColor: Color = Color.White.copy(alpha = 0.10f),
    val keyline16Color: Color = Color(0xFF00BCD4).copy(alpha = 0.50f),
    val keyline24Color: Color = Color(0xFFFF5722).copy(alpha = 0.50f),
    val showKeylines: Boolean = true,
)

object ComposeRaven {
    fun setGridConfig(config: GridConfig) {
        RavenState.gridConfig.value = config
    }
}
