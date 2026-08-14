package io.github.dracovin.composeraven.features

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dracovin.composeraven.InspectableElement
import io.github.dracovin.composeraven.PickedElementInfo
import io.github.dracovin.composeraven.RavenState
import kotlin.math.hypot

@Composable
internal fun ElementPickerOverlay() {
    val pickedElement by RavenState.pickedElement.collectAsState()
    val density       = LocalDensity.current
    val view          = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val xDp    = with(density) { tapOffset.x.toDp().value }
                    val yDp    = with(density) { tapOffset.y.toDp().value }
                    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    val window = (view.context as? Activity)?.window ?: return@detectTapGestures

                    PixelCopy.request(
                        window,
                        android.graphics.Rect(
                            tapOffset.x.toInt(), tapOffset.y.toInt(),
                            tapOffset.x.toInt() + 1, tapOffset.y.toInt() + 1,
                        ),
                        bitmap,
                        { result ->
                            if (result == PixelCopy.SUCCESS) {
                                val hex     = bitmap.getPixel(0, 0).toRavenHex()
                                val closest = closestElement(tapOffset)
                                RavenState.pickedElement.value = PickedElementInfo(xDp, yDp, hex, closest)
                            }
                        },
                        Handler(Looper.getMainLooper()),
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            pickedElement?.let { info ->
                val cx   = with(density) { info.xDp.dp.toPx() }
                val cy   = with(density) { info.yDp.dp.toPx() }
                val arm  = 28f
                drawLine(Color.Red, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 2f)
                drawLine(Color.Red, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 2f)

                info.bounds?.let { el ->
                    val left   = el.boundsInWindow.left.toFloat()
                    val top    = el.boundsInWindow.top.toFloat()
                    val elSize = Size(el.boundsInWindow.width().toFloat(), el.boundsInWindow.height().toFloat())
                    drawRect(Color(0xFF2196F3).copy(alpha = 0.2f), Offset(left, top), elSize)
                    drawRect(Color(0xFF2196F3), Offset(left, top), elSize, style = Stroke(width = 2f))
                }
            }
        }

        pickedElement?.let { info ->
            ElementInfoCard(info = info, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ElementInfoCard(info: PickedElementInfo, modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier.fillMaxWidth().padding(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Color  ${info.hexColor}", fontSize = 14.sp)
            Text("Tap    x=${info.xDp.fmt()}dp  y=${info.yDp.fmt()}dp", fontSize = 12.sp)
            info.bounds?.let { el ->
                Text("Tag    ${el.tag.ifEmpty { "(untagged)" }}", fontSize = 12.sp)
                Text("Size   W=${el.widthDp.fmt()}dp × H=${el.heightDp.fmt()}dp", fontSize = 12.sp)
            }
        }
    }
}

// Strips alpha; formats RGB as lowercase hex e.g. "#c18a35"
internal fun Int.toRavenHex(): String = "#%06x".format(this and 0xFFFFFF)

private fun Float.fmt() = "%.1f".format(this)

private fun closestElement(tapOffset: Offset): InspectableElement? =
    RavenState.inspectableElements.value.minByOrNull { el ->
        val cx = (el.boundsInWindow.left + el.boundsInWindow.right) / 2f
        val cy = (el.boundsInWindow.top  + el.boundsInWindow.bottom) / 2f
        hypot(tapOffset.x - cx, tapOffset.y - cy)
    }
