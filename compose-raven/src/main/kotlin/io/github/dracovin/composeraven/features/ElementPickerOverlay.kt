package io.github.dracovin.composeraven.features

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dracovin.composeraven.InspectableElement
import io.github.dracovin.composeraven.InspectorConfig
import io.github.dracovin.composeraven.PickedElementInfo
import io.github.dracovin.composeraven.RavenState

@Composable
internal fun ElementPickerOverlay() {
    val pickedElement by RavenState.pickedElement.collectAsStateWithLifecycle()
    val allElements   by RavenState.inspectableElements.collectAsStateWithLifecycle()
    val density       = LocalDensity.current
    val view          = LocalView.current
    var isPinned      by remember { mutableStateOf(false) }

    LaunchedEffect(allElements) {
        val picked = RavenState.pickedElement.value?.bounds ?: return@LaunchedEffect
        val present = allElements.any { el ->
            when {
                picked.tag.isNotEmpty()   -> el.tag == picked.tag
                picked.label.isNotEmpty() -> el.label == picked.label
                else                      -> el.boundsInWindow == picked.boundsInWindow
            }
        }
        if (!present) {
            RavenState.pickedElement.value = null
            isPinned = false
        }
    }
    var zoomBitmap    by remember { mutableStateOf<ImageBitmap?>(null) }
    val lastTapTime   = remember { longArrayOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    val isDoubleTap = now - lastTapTime[0] < 300L
                    lastTapTime[0] = now
                    if (isDoubleTap) {
                        isPinned = !isPinned
                        return@detectTapGestures
                    }
                    if (isPinned) return@detectTapGestures

                    val xDp = with(density) { tapOffset.x.toDp().value }
                    val yDp = with(density) { tapOffset.y.toDp().value }

                    val activity    = (view.context as? Activity) ?: return@detectTapGestures
                    val contentView = activity.findViewById<View>(android.R.id.content)
                    if (!contentView.isLaidOut) return@detectTapGestures

                    val loc = IntArray(2)
                    contentView.getLocationInWindow(loc)
                    val screenX = tapOffset.x.toInt()
                    val screenY = tapOffset.y.toInt()
                    val px = (screenX - loc[0]).coerceIn(0, contentView.width - 1)
                    val py = (screenY - loc[1]).coerceIn(0, contentView.height - 1)

                    val closest = closestElement(tapOffset)
                    if (closest == null) {
                        RavenState.pickedElement.value = null
                        zoomBitmap = null
                        return@detectTapGestures
                    }

                    // Clear overlay so it doesn't bleed into the PixelCopy capture.
                    // Frame 1: Compose recomposes with null pickedElement (overlay gone).
                    // Frame 2: hardware surface renders the clean frame.
                    RavenState.pickedElement.value = null
                    zoomBitmap = null

                    val zoomRadius = 40
                    val zLeft   = (px - zoomRadius).coerceAtLeast(0)
                    val zTop    = (py - zoomRadius).coerceAtLeast(0)
                    val zRight  = (px + zoomRadius).coerceAtMost(contentView.width)
                    val zBottom = (py + zoomRadius).coerceAtMost(contentView.height)
                    val zW = zRight - zLeft
                    val zH = zBottom - zTop

                    if (zW > 0 && zH > 0) {
                        val zBmp = Bitmap.createBitmap(zW, zH, Bitmap.Config.ARGB_8888)
                        Choreographer.getInstance().postFrameCallback {
                            Choreographer.getInstance().postFrameCallback {
                                PixelCopy.request(
                                    activity.window,
                                    Rect(zLeft + loc[0], zTop + loc[1], zRight + loc[0], zBottom + loc[1]),
                                    zBmp,
                                    { result ->
                                        val cx = (px - zLeft).coerceIn(0, zW - 1)
                                        val cy = (py - zTop).coerceIn(0, zH - 1)
                                        val hex = if (result == PixelCopy.SUCCESS) zBmp.getPixel(cx, cy).toRavenHex() else "#000000"
                                        zoomBitmap = if (result == PixelCopy.SUCCESS) zBmp.asImageBitmap() else null
                                        RavenState.pickedElement.value = PickedElementInfo(xDp, yDp, hex, closest)
                                    },
                                    Handler(Looper.getMainLooper()),
                                )
                            }
                        }
                    } else {
                        RavenState.pickedElement.value = PickedElementInfo(xDp, yDp, "#000000", closest)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            pickedElement?.let { info ->
                val cx  = with(density) { info.xDp.dp.toPx() }
                val cy  = with(density) { info.yDp.dp.toPx() }
                val arm = 28f
                val cfg = info.bounds?.config ?: InspectorConfig()

                // Crosshair
                drawLine(cfg.crosshairColor, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = cfg.strokeWidth)
                drawLine(cfg.crosshairColor, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = cfg.strokeWidth)

                // Highlight: tapped element + group siblings
                val group = info.bounds?.group.orEmpty()
                val toHighlight = if (group.isNotEmpty()) {
                    allElements.filter { it.group == group }
                } else {
                    listOfNotNull(info.bounds)
                }
                toHighlight.forEach { el ->
                    val left   = el.boundsInWindow.left.toFloat()
                    val top    = el.boundsInWindow.top.toFloat()
                    val elSize = Size(el.boundsInWindow.width().toFloat(), el.boundsInWindow.height().toFloat())
                    val r      = el.config.cornerRadius
                    if (r > 0f) {
                        drawRoundRect(el.config.highlightColor.copy(alpha = el.config.highlightFillAlpha), Offset(left, top), elSize, CornerRadius(r))
                        drawRoundRect(el.config.highlightColor, Offset(left, top), elSize, CornerRadius(r), style = Stroke(width = el.config.strokeWidth))
                    } else {
                        drawRect(el.config.highlightColor.copy(alpha = el.config.highlightFillAlpha), Offset(left, top), elSize)
                        drawRect(el.config.highlightColor, Offset(left, top), elSize, style = Stroke(width = el.config.strokeWidth))
                    }
                }

                // Zoom preview (top-left corner)
                zoomBitmap?.let { img ->
                    val previewPx = with(density) { 120.dp.toPx() }
                    val margin    = with(density) { 16.dp.toPx() }
                    drawRect(Color.Black.copy(alpha = 0.6f), Offset(margin, margin), Size(previewPx, previewPx))
                    drawImage(img, dstOffset = IntOffset(margin.toInt(), margin.toInt()), dstSize = IntSize(previewPx.toInt(), previewPx.toInt()))
                    drawRect(cfg.crosshairColor, Offset(margin, margin), Size(previewPx, previewPx), style = Stroke(width = cfg.strokeWidth))
                    // Center crosshair on zoom
                    val zCx  = margin + previewPx / 2
                    val zCy  = margin + previewPx / 2
                    val zArm = previewPx / 8
                    drawLine(cfg.crosshairColor.copy(alpha = 0.8f), Offset(zCx - zArm, zCy), Offset(zCx + zArm, zCy), strokeWidth = 1f)
                    drawLine(cfg.crosshairColor.copy(alpha = 0.8f), Offset(zCx, zCy - zArm), Offset(zCx, zCy + zArm), strokeWidth = 1f)
                }

                // Pin dot indicator
                if (isPinned) {
                    drawCircle(cfg.crosshairColor, radius = with(density) { 4.dp.toPx() }, center = Offset(cx, cy))
                }
            }
        }

        pickedElement?.let { info ->
            ElementInfoCard(
                info     = info,
                isPinned = isPinned,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 88.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ElementInfoCard(info: PickedElementInfo, isPinned: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Card(
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val cfg = info.bounds?.config
            if (isPinned) Text("Pinned", fontSize = 10.sp, color = Color.Gray)
            if (cfg == null || cfg.showColor) Text("Color    ${info.hexColor}", fontSize = 14.sp)
            if (cfg == null || cfg.showTap)   Text("Tap      x=${info.xDp.fmt()}dp  y=${info.yDp.fmt()}dp", fontSize = 12.sp)
            info.bounds?.let { el ->
                if (el.config.showPosition) {
                    val leftDp = with(density) { el.boundsInWindow.left.toDp().value }
                    val topDp  = with(density) { el.boundsInWindow.top.toDp().value }
                    Text("Pos      x=${leftDp.fmt()}dp  y=${topDp.fmt()}dp", fontSize = 12.sp)
                }
                if (el.config.showTag) {
                    val display = el.label.ifEmpty { el.tag }.ifEmpty { "(untagged)" }
                    Text("Tag      $display", fontSize = 12.sp)
                }
                if (el.config.showSize)  Text("Size     W=${el.widthDp.fmt()}dp × H=${el.heightDp.fmt()}dp", fontSize = 12.sp)
                if (el.config.showGroup && el.group.isNotEmpty()) Text("Group    ${el.group}", fontSize = 12.sp)
            }
        }
    }
}

internal fun Int.toRavenHex(): String = "#%06x".format(this and 0xFFFFFF)

private fun Float.fmt() = "%.1f".format(this)

internal fun closestElement(tapOffset: Offset): InspectableElement? {
    val x = tapOffset.x.toInt()
    val y = tapOffset.y.toInt()
    val hit = RavenState.inspectableElements.value.filter { it.boundsInWindow.contains(x, y) }
    return hit.minByOrNull { it.boundsInWindow.width().toLong() * it.boundsInWindow.height() }
}
