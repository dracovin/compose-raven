package io.github.dracovin.composeraven.features

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dracovin.composeraven.InspectableElement
import io.github.dracovin.composeraven.RavenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun Modifier.recompositionHeatmap(): Modifier = composed {
    val enabled by RavenState.heatmapEnabled.collectAsStateWithLifecycle()
    if (!enabled) return@composed Modifier

    val alpha    = remember { Animatable(0f) }
    val scope    = rememberCoroutineScope()
    // Plain (non-state) holders — writes here don't trigger recomposition
    val activated = remember { booleanArrayOf(false) }
    val jobRef    = remember { arrayOfNulls<Job>(1) }

    SideEffect {
        if (!activated[0]) {
            // First SideEffect is the activation recompose — skip it
            activated[0] = true
            return@SideEffect
        }
        // Real recompose: cancel any in-flight animation, restart fresh
        jobRef[0]?.cancel()
        jobRef[0] = scope.launch {
            alpha.snapTo(0.55f)
            alpha.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 400))
        }
    }

    drawWithContent {
        drawContent()
        drawRect(color = Color(0xFFFF6D00).copy(alpha = alpha.value))
    }
}

fun Modifier.ravenInspectable(tag: String = ""): Modifier = composed {
    val enabled by RavenState.inspectorEnabled.collectAsStateWithLifecycle()
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
        val idx = if (tag.isNotEmpty()) {
            current.indexOfFirst { it.tag == tag }
        } else {
            current.indexOfFirst { it.boundsInWindow == element.boundsInWindow }
        }
        if (idx >= 0) current[idx] = element else current.add(element)
        RavenState.inspectableElements.value = current
    }
}
