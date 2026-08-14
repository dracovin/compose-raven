package io.github.dracovin.composeraven

import android.app.Application

internal class RavenWindowInjector : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
    override fun onActivityStarted(activity: android.app.Activity) = Unit
    override fun onActivityResumed(activity: android.app.Activity) = Unit
    override fun onActivityPaused(activity: android.app.Activity) = Unit
    override fun onActivityStopped(activity: android.app.Activity) = Unit
    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
    override fun onActivityDestroyed(activity: android.app.Activity) = Unit
}
