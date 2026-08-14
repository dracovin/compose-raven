package io.github.dracovin.composeraven

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import io.github.dracovin.composeraven.overlay.RavenOverlayRoot
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

internal class RavenWindowInjector : Application.ActivityLifecycleCallbacks {

    private val overlays = mutableMapOf<Activity, ComposeView>()

    override fun onActivityResumed(activity: Activity) {
        if (overlays.containsKey(activity)) return
        if (activity !is LifecycleOwner ||
            activity !is ViewModelStoreOwner ||
            activity !is SavedStateRegistryOwner) return

        val decorView = activity.window.decorView as? ViewGroup ?: return
        val composeView = ComposeView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent { RavenOverlayRoot() }
        }
        decorView.addView(composeView)
        overlays[activity] = composeView
    }

    override fun onActivityPaused(activity: Activity) = detach(activity)

    override fun onActivityDestroyed(activity: Activity) = detach(activity)

    private fun detach(activity: Activity) {
        overlays.remove(activity)?.let { view ->
            (activity.window.decorView as? ViewGroup)?.removeView(view)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}