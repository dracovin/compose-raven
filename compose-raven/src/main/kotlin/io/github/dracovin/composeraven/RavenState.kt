package io.github.dracovin.composeraven

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow

internal object RavenState {
    val heatmapEnabled      = MutableStateFlow(false)
    val inspectorEnabled    = MutableStateFlow(false)
    val gridEnabled         = MutableStateFlow(false)
    val rulerEnabled        = MutableStateFlow(false)
    val pickedElement       = MutableStateFlow<PickedElementInfo?>(null)
    val inspectableElements = MutableStateFlow<List<InspectableElement>>(emptyList())
    val gridConfig          = MutableStateFlow(GridConfig())
    val rulerStart          = MutableStateFlow<InspectableElement?>(null)
    val rulerEnd            = MutableStateFlow<InspectableElement?>(null)

    fun reset() {
        heatmapEnabled.value      = false
        inspectorEnabled.value    = false
        gridEnabled.value         = false
        rulerEnabled.value        = false
        pickedElement.value       = null
        inspectableElements.value = emptyList()
        rulerStart.value          = null
        rulerEnd.value            = null
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
    val label: String = "",
    val group: String = "",
    val boundsInWindow: Rect,
    val widthDp: Float,
    val heightDp: Float,
    val config: InspectorConfig = InspectorConfig(),
)
