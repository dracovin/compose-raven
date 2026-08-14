package io.github.dracovin.composeraven

import android.app.Application
import org.junit.Assert.assertNotNull
import org.junit.Test

class RavenWindowInjectorTest {
    @Test
    fun `RavenWindowInjector implements ActivityLifecycleCallbacks`() {
        val injector: Application.ActivityLifecycleCallbacks = RavenWindowInjector()
        assertNotNull(injector)
    }
}
