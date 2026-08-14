package io.github.dracovin.composeraven

import android.app.Activity
import android.app.Application
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import io.github.dracovin.composeraven.overlay.RavenOverlayRoot
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class RavenWindowInjector : Application.ActivityLifecycleCallbacks {

    private data class OverlayEntry(
        val view: ComposeView,
        val params: WindowManager.LayoutParams,
        val scope: CoroutineScope,
    )

    private val overlays = mutableMapOf<Activity, OverlayEntry>()

    override fun onActivityResumed(activity: Activity) {
        if (overlays.containsKey(activity)) return
        // ComponentActivity implements all three; skip anything else (e.g. system activities)
        if (activity !is LifecycleOwner ||
            activity !is ViewModelStoreOwner ||
            activity !is SavedStateRegistryOwner) return

        val wm     = activity.windowManager
        val params = buildParams(passthrough = true)

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent { RavenOverlayRoot() }
        }

        // Swap touch flags when inspector activates so taps reach the overlay
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        scope.launch {
            RavenState.inspectorEnabled.collect { inspecting ->
                params.flags = if (inspecting) interceptFlags() else passthroughFlags()
                runCatching { wm.updateViewLayout(composeView, params) }
            }
        }

        wm.addView(composeView, params)
        overlays[activity] = OverlayEntry(composeView, params, scope)
    }

    override fun onActivityPaused(activity: Activity) = detach(activity)

    override fun onActivityDestroyed(activity: Activity) = detach(activity)

    private fun detach(activity: Activity) {
        overlays.remove(activity)?.let { (view, _, scope) ->
            scope.cancel()
            runCatching { activity.windowManager.removeView(view) }
        }
    }

    private fun buildParams(passthrough: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION,
        if (passthrough) passthroughFlags() else interceptFlags(),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    // FLAG_NOT_FOCUSABLE implicitly enables FLAG_NOT_TOUCH_MODAL — touches fall through
    private fun passthroughFlags() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    // Clearing FLAG_NOT_FOCUSABLE also clears the implied FLAG_NOT_TOUCH_MODAL
    private fun interceptFlags() =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
