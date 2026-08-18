package io.github.dracovin.composeraven.features

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dracovin.composeraven.InspectableElement
import io.github.dracovin.composeraven.InspectorConfig
import io.github.dracovin.composeraven.RavenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun Modifier.recompositionHeatmap(
    color: Color = Color(0xFFFF6D00),
    peakAlpha: Float = 0.55f,
    durationMs: Int = 400,
    showCount: Boolean = false,
): Modifier = composed {
    val enabled by RavenState.heatmapEnabled.collectAsStateWithLifecycle()
    if (!enabled) return@composed Modifier

    val alpha       = remember { Animatable(0f) }
    val scope       = rememberCoroutineScope()
    val activated   = remember { booleanArrayOf(false) }
    val jobRef      = remember { arrayOfNulls<Job>(1) }
    val count       = remember { mutableIntStateOf(0) }
    val textMeasurer = if (showCount) rememberTextMeasurer() else null

    SideEffect {
        if (!activated[0]) {
            activated[0] = true
            return@SideEffect
        }
        count.intValue++
        jobRef[0]?.cancel()
        jobRef[0] = scope.launch {
            alpha.snapTo(peakAlpha)
            alpha.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = durationMs))
        }
    }

    drawWithContent {
        drawContent()
        drawRect(color = color.copy(alpha = alpha.value))
        if (showCount && textMeasurer != null && count.intValue > 0) {
            drawCountBadge(textMeasurer, count.intValue)
        }
    }
}

private fun DrawScope.drawCountBadge(measurer: TextMeasurer, count: Int) {
    val label  = count.toString()
    val style  = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val result = measurer.measure(label, style)
    val pad    = 4f
    val w      = result.size.width + pad * 2
    val h      = result.size.height + pad * 2
    val x      = size.width - w - 4f
    val y      = 4f
    drawRect(color = badgeColor(count), topLeft = Offset(x, y), size = Size(w, h))
    drawText(result, topLeft = Offset(x + pad, y + pad))
}

private val Green  = Color(0xFF4CAF50)
private val Yellow = Color(0xFFFFC107)
private val Red    = Color(0xFFF44336)

// green → yellow (0–10), yellow → red (10–30), red beyond
private fun badgeColor(count: Int): Color = when {
    count <= 10 -> lerp(Green, Yellow, count / 10f)
    count <= 30 -> lerp(Yellow, Red, (count - 10) / 20f)
    else        -> Red
}

fun Modifier.ravenInspectable(
    tag: String = "",
    label: String = "",
    group: String = "",
    config: InspectorConfig = InspectorConfig(),
): Modifier = composed {
    val inspectorOn by RavenState.inspectorEnabled.collectAsStateWithLifecycle()
    val rulerOn     by RavenState.rulerEnabled.collectAsStateWithLifecycle()
    if (!inspectorOn && !rulerOn) return@composed Modifier

    DisposableEffect(tag, label) {
        onDispose {
            val updated = RavenState.inspectableElements.value.toMutableList()
            val removed = updated.removeAll { el ->
                when {
                    tag.isNotEmpty()   -> el.tag == tag
                    label.isNotEmpty() -> el.label == label
                    else               -> false
                }
            }
            if (removed) RavenState.inspectableElements.value = updated
        }
    }

    val density = LocalDensity.current
    onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        val element = InspectableElement(
            tag            = tag,
            label          = label,
            group          = group,
            boundsInWindow = Rect(
                bounds.left.toInt(), bounds.top.toInt(),
                bounds.right.toInt(), bounds.bottom.toInt(),
            ),
            widthDp  = with(density) { bounds.width.toDp().value },
            heightDp = with(density) { bounds.height.toDp().value },
            config   = config,
        )
        val current = RavenState.inspectableElements.value.toMutableList()
        val idx = when {
            tag.isNotEmpty()   -> current.indexOfFirst { it.tag == tag }
            label.isNotEmpty() -> current.indexOfFirst { it.label == label }
            else               -> current.indexOfFirst { it.boundsInWindow == element.boundsInWindow }
        }
        if (idx >= 0) current[idx] = element else current.add(element)
        RavenState.inspectableElements.value = current
    }
}
