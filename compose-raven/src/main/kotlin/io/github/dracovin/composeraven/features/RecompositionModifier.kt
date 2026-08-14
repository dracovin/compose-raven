package io.github.dracovin.composeraven.features

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import io.github.dracovin.composeraven.InspectableElement
import io.github.dracovin.composeraven.RavenState

fun Modifier.recompositionHeatmap(): Modifier = composed {
    val enabled by RavenState.heatmapEnabled.collectAsState()
    if (!enabled) return@composed Modifier

    var recomposeCount by remember { mutableIntStateOf(0) }
    // SideEffect runs after every successful recomposition — increments the counter
    SideEffect { recomposeCount++ }

    val alpha = remember { Animatable(0f) }
    // LaunchedEffect restarts on each count change: snap to 0.55, fade to 0 over 400ms
    LaunchedEffect(recomposeCount) {
        alpha.snapTo(0.55f)
        alpha.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 400))
    }

    drawWithContent {
        drawContent()
        drawRect(color = Color(0xFFFF6D00).copy(alpha = alpha.value))
    }
}

fun Modifier.ravenInspectable(tag: String = ""): Modifier = composed {
    val enabled by RavenState.inspectorEnabled.collectAsState()
    if (!enabled) return@composed Modifier

    val density = LocalDensity.current
    onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        val element = InspectableElement(
            tag            = tag,
            boundsInWindow = Rect(
                bounds.left.toInt(), bounds.top.toInt(),
                bounds.right.toInt(), bounds.bottom.toInt(),
            ),
            widthDp  = with(density) { bounds.width.toDp().value },
            heightDp = with(density) { bounds.height.toDp().value },
        )
        val current = RavenState.inspectableElements.value.toMutableList()
        // Use tag for named elements; bounds identity for untagged to prevent unbounded growth
        val idx = if (tag.isNotEmpty()) {
            current.indexOfFirst { it.tag == tag }
        } else {
            current.indexOfFirst { it.boundsInWindow == element.boundsInWindow }
        }
        if (idx >= 0) current[idx] = element else current.add(element)
        RavenState.inspectableElements.value = current
    }
}
