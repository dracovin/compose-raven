package io.github.dracovin.composeraven.features

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dracovin.composeraven.InspectableElement
import io.github.dracovin.composeraven.RavenState

private val RulerColor = Color(0xFFE91E63)
private const val TickLen = 14f

@Composable
internal fun RulerOverlay() {
    val start    by RavenState.rulerStart.collectAsStateWithLifecycle()
    val end      by RavenState.rulerEnd.collectAsStateWithLifecycle()
    val density  = LocalDensity.current
    val measurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val hit = closestElement(tapOffset)
                    when {
                        start != null && end != null -> {
                            RavenState.rulerStart.value = hit
                            RavenState.rulerEnd.value   = null
                        }
                        start == null -> RavenState.rulerStart.value = hit
                        else          -> if (hit != null && hit != start) RavenState.rulerEnd.value = hit
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            start?.let { s -> drawElementHighlight(s) }
            end?.let   { e -> drawElementHighlight(e) }
            if (start != null && end != null) {
                drawMeasurement(start!!.boundsInWindow, end!!.boundsInWindow, density, measurer)
            }
        }

        val hint = when {
            start == null        -> "Tap first element"
            end == null          -> "Tap second element"
            else                 -> "Tap to reset"
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
            color    = RulerColor,
            shape    = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        ) {
            Text(hint, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp)
        }
    }
}

private fun DrawScope.drawElementHighlight(el: InspectableElement) {
    val left   = el.boundsInWindow.left.toFloat()
    val top    = el.boundsInWindow.top.toFloat()
    val elSize = Size(el.boundsInWindow.width().toFloat(), el.boundsInWindow.height().toFloat())
    drawRect(RulerColor.copy(alpha = 0.15f), Offset(left, top), elSize)
    drawRect(RulerColor, Offset(left, top), elSize, style = Stroke(width = 2f))
}

private fun DrawScope.drawMeasurement(
    a: Rect,
    b: Rect,
    density: Density,
    measurer: TextMeasurer,
) {
    val overlaps = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    if (overlaps) {
        val aArea = a.width().toLong() * a.height()
        val bArea = b.width().toLong() * b.height()
        val outer = if (aArea >= bArea) a else b
        val inner = if (aArea >= bArea) b else a
        drawInsets(outer, inner, density, measurer)
        return
    }

    val hGapPx: Float = when {
        a.right <= b.left -> (b.left - a.right).toFloat()
        b.right <= a.left -> (a.left - b.right).toFloat()
        else              -> 0f
    }
    val vGapPx: Float = when {
        a.bottom <= b.top -> (b.top - a.bottom).toFloat()
        b.bottom <= a.top -> (a.top - b.bottom).toFloat()
        else              -> 0f
    }

    val midY = (a.centerY() + b.centerY()) / 2f
    val midX = (a.centerX() + b.centerX()) / 2f

    if (hGapPx > 0f) {
        val x1 = if (a.right <= b.left) a.right.toFloat() else b.right.toFloat()
        val x2 = if (a.right <= b.left) b.left.toFloat() else a.left.toFloat()
        drawLine(RulerColor, Offset(x1, midY), Offset(x2, midY), strokeWidth = 2f)
        drawLine(RulerColor, Offset(x1, midY - TickLen), Offset(x1, midY + TickLen), strokeWidth = 2f)
        drawLine(RulerColor, Offset(x2, midY - TickLen), Offset(x2, midY + TickLen), strokeWidth = 2f)
        drawDistanceLabel(measurer, "H: ${with(density) { hGapPx.toDp().value }.fmt()}dp", (x1 + x2) / 2f, midY - 4f, above = true)
    }

    if (vGapPx > 0f) {
        val y1 = if (a.bottom <= b.top) a.bottom.toFloat() else b.bottom.toFloat()
        val y2 = if (a.bottom <= b.top) b.top.toFloat() else a.top.toFloat()
        drawLine(RulerColor, Offset(midX, y1), Offset(midX, y2), strokeWidth = 2f)
        drawLine(RulerColor, Offset(midX - TickLen, y1), Offset(midX + TickLen, y1), strokeWidth = 2f)
        drawLine(RulerColor, Offset(midX - TickLen, y2), Offset(midX + TickLen, y2), strokeWidth = 2f)
        drawDistanceLabel(measurer, "V: ${with(density) { vGapPx.toDp().value }.fmt()}dp", midX + TickLen + 4f, (y1 + y2) / 2f, above = false)
    }
}

private fun DrawScope.drawInsets(
    outer: Rect,
    inner: Rect,
    density: Density,
    measurer: TextMeasurer,
) {
    fun dpLabel(px: Float) = "${with(density) { px.toDp().value }.fmt()}dp"

    val iCx = inner.centerX().toFloat()
    val iCy = inner.centerY().toFloat()

    val leftPx   = (inner.left   - outer.left).toFloat()
    val rightPx  = (outer.right  - inner.right).toFloat()
    val topPx    = (inner.top    - outer.top).toFloat()
    val bottomPx = (outer.bottom - inner.bottom).toFloat()

    // Left
    if (leftPx > 0f) {
        val x1 = outer.left.toFloat(); val x2 = inner.left.toFloat()
        drawLine(RulerColor, Offset(x1, iCy), Offset(x2, iCy), strokeWidth = 2f)
        drawLine(RulerColor, Offset(x1, iCy - TickLen), Offset(x1, iCy + TickLen), strokeWidth = 2f)
        drawLine(RulerColor, Offset(x2, iCy - TickLen), Offset(x2, iCy + TickLen), strokeWidth = 2f)
        drawDistanceLabel(measurer, dpLabel(leftPx), (x1 + x2) / 2f, iCy - 4f, above = true)
    }
    // Right
    if (rightPx > 0f) {
        val x1 = inner.right.toFloat(); val x2 = outer.right.toFloat()
        drawLine(RulerColor, Offset(x1, iCy), Offset(x2, iCy), strokeWidth = 2f)
        drawLine(RulerColor, Offset(x1, iCy - TickLen), Offset(x1, iCy + TickLen), strokeWidth = 2f)
        drawLine(RulerColor, Offset(x2, iCy - TickLen), Offset(x2, iCy + TickLen), strokeWidth = 2f)
        drawDistanceLabel(measurer, dpLabel(rightPx), (x1 + x2) / 2f, iCy - 4f, above = true)
    }
    // Top
    if (topPx > 0f) {
        val y1 = outer.top.toFloat(); val y2 = inner.top.toFloat()
        drawLine(RulerColor, Offset(iCx, y1), Offset(iCx, y2), strokeWidth = 2f)
        drawLine(RulerColor, Offset(iCx - TickLen, y1), Offset(iCx + TickLen, y1), strokeWidth = 2f)
        drawLine(RulerColor, Offset(iCx - TickLen, y2), Offset(iCx + TickLen, y2), strokeWidth = 2f)
        drawDistanceLabel(measurer, dpLabel(topPx), iCx + TickLen + 4f, (y1 + y2) / 2f, above = false)
    }
    // Bottom
    if (bottomPx > 0f) {
        val y1 = inner.bottom.toFloat(); val y2 = outer.bottom.toFloat()
        drawLine(RulerColor, Offset(iCx, y1), Offset(iCx, y2), strokeWidth = 2f)
        drawLine(RulerColor, Offset(iCx - TickLen, y1), Offset(iCx + TickLen, y1), strokeWidth = 2f)
        drawLine(RulerColor, Offset(iCx - TickLen, y2), Offset(iCx + TickLen, y2), strokeWidth = 2f)
        drawDistanceLabel(measurer, dpLabel(bottomPx), iCx + TickLen + 4f, (y1 + y2) / 2f, above = false)
    }
}

private fun DrawScope.drawDistanceLabel(
    measurer: TextMeasurer,
    text: String,
    cx: Float,
    cy: Float,
    above: Boolean,
) {
    val style   = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val result  = measurer.measure(text, style)
    val pad     = 4f
    val w       = result.size.width + pad * 2
    val h       = result.size.height + pad * 2
    val lx      = if (above) cx - w / 2f else cx
    val ly      = if (above) cy - h else cy - h / 2f
    drawRect(RulerColor, Offset(lx, ly), Size(w, h))
    drawText(result, topLeft = Offset(lx + pad, ly + pad))
}

private fun Float.fmt() = "%.1f".format(this)
