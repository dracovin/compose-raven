package io.github.dracovin.composeraven

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow

internal object RavenState {
    val heatmapEnabled      = MutableStateFlow(false)
    val inspectorEnabled    = MutableStateFlow(false)
    val gridEnabled         = MutableStateFlow(false)
    val pickedElement       = MutableStateFlow<PickedElementInfo?>(null)
    val inspectableElements = MutableStateFlow<List<InspectableElement>>(emptyList())
    val gridConfig          = MutableStateFlow(GridConfig())

    fun reset() {
        heatmapEnabled.value      = false
        inspectorEnabled.value    = false
        gridEnabled.value         = false
        pickedElement.value       = null
        inspectableElements.value = emptyList()
    }
}

data class PickedElementInfo(
    val xDp: Float,
    val yDp: Float,
    val hexColor: String,
    val bounds: InspectableElement? = null,
)

data class InspectableElement(
    val tag: String,
    val boundsInWindow: Rect,
    val widthDp: Float,
    val heightDp: Float,
    val config: InspectorConfig = InspectorConfig(),
)
