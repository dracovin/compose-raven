package io.github.dracovin.composeraven

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

internal class RavenInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        (context.applicationContext as Application)
            .registerActivityLifecycleCallbacks(RavenWindowInjector())
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
