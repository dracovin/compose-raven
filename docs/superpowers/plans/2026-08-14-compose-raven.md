# composeRaven Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `composeRaven` debug overlay SDK — a zero-boilerplate `debugImplementation` library that injects a floating Compose inspector over any Activity via ContentProvider auto-init and WindowManager.

**Architecture:** A `ContentProvider`-based `Initializer` (androidx.startup) boots before `Application.onCreate()` and registers `ActivityLifecycleCallbacks`. On each Activity resume, a `ComposeView` is attached to the `WindowManager` with passthrough touch flags and full-screen transparent layout. Three feature modules (heatmap modifier, element picker, grid overlay) are driven by a central `RavenState` singleton using `MutableStateFlow` — no DI framework involved.

**Tech Stack:** Kotlin 2.0, Jetpack Compose BOM 2024.09.00, `androidx.startup:startup-runtime:1.1.1`, `kotlinx-coroutines-android:1.8.1`, AGP 8.5.0, Gradle version catalog

**Spec:** `docs/superpowers/specs/2026-08-14-compose-raven-design.md`

## Global Constraints

- minSdk 26, targetSdk 35
- Kotlin 2.0+ with `org.jetbrains.kotlin.plugin.compose` (K2-compatible Compose compiler plugin)
- Maven artifact: `io.github.dracovin:compose-raven:0.1.0-alpha01`
- Package root: `io.github.dracovin.composeraven`
- WindowManager type: `TYPE_APPLICATION` — no `SYSTEM_ALERT_WINDOW` permission required
- State layer: `MutableStateFlow` only — no Hilt, Koin, or any DI framework inside the SDK
- `debugImplementation` only — never ships into release builds

---

## File Map

| File | Responsibility |
|---|---|
| `settings.gradle.kts` | Declares `:compose-raven`, `:sample` modules |
| `build.gradle.kts` (root) | Plugin declarations only, no dependencies |
| `gradle/libs.versions.toml` | Single version source of truth |
| `compose-raven/build.gradle.kts` | Library: Compose, startup, coroutines, maven-publish, signing |
| `compose-raven/src/main/AndroidManifest.xml` | InitializationProvider meta-data entry |
| `RavenState.kt` | `internal object` with all `MutableStateFlow` state + data classes |
| `RavenInitializer.kt` | `androidx.startup.Initializer` — grabs Application, registers callbacks |
| `RavenWindowInjector.kt` | `ActivityLifecycleCallbacks` — ComposeView lifecycle in WindowManager |
| `overlay/RavenOverlayRoot.kt` | Root Compose UI: draggable FAB, toggle menu, feature host |
| `features/RecompositionModifier.kt` | `Modifier.recompositionHeatmap()` + `Modifier.ravenInspectable()` |
| `features/ElementPickerOverlay.kt` | Inspector: PixelCopy color extraction, crosshair, info card |
| `features/GridOverlay.kt` | Canvas-drawn 8/16/24dp Material grid |
| `sample/src/main/.../MainActivity.kt` | Demo Activity exercising all three features |

---

### Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle/libs.versions.toml`
- Create: `compose-raven/build.gradle.kts`
- Create: `compose-raven/src/main/AndroidManifest.xml`
- Create: `sample/build.gradle.kts`
- Create: `sample/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: compilable project that syncs in Android Studio with zero errors

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "composeRaven"
include(":compose-raven", ":sample")
```

- [ ] **Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library)     apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.compose.compiler)    apply false
}
```

- [ ] **Step 3: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp         = "8.5.0"
kotlin      = "2.0.0"
composeBom  = "2024.09.00"
startup     = "1.1.1"
coroutines  = "1.8.1"

[libraries]
compose-bom                 = { group = "androidx.compose",           name = "compose-bom",                version.ref = "composeBom" }
compose-ui                  = { group = "androidx.compose.ui",        name = "ui"                                                     }
compose-foundation          = { group = "androidx.compose.foundation",name = "foundation"                                             }
compose-material3           = { group = "androidx.compose.material3", name = "material3"                                              }
compose-material-icons-core = { group = "androidx.compose.material",  name = "material-icons-core"                                    }
compose-ui-tooling          = { group = "androidx.compose.ui",        name = "ui-tooling"                                             }
compose-ui-test             = { group = "androidx.compose.ui",        name = "ui-test-junit4"                                         }
compose-ui-test-manifest    = { group = "androidx.compose.ui",        name = "ui-test-manifest"                                       }
startup-runtime             = { group = "androidx.startup",           name = "startup-runtime",            version.ref = "startup"    }
coroutines-android          = { group = "org.jetbrains.kotlinx",      name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test             = { group = "org.jetbrains.kotlinx",      name = "kotlinx-coroutines-test",    version.ref = "coroutines" }
junit                       = { group = "junit",                      name = "junit",                      version     = "4.13.2"     }
activity-compose            = { group = "androidx.activity",          name = "activity-compose",           version     = "1.9.0"      }

[plugins]
android-application = { id = "com.android.application",             version.ref = "agp"    }
android-library     = { id = "com.android.library",                 version.ref = "agp"    }
kotlin-android      = { id = "org.jetbrains.kotlin.android",        version.ref = "kotlin" }
compose-compiler    = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
maven-publish       = { id = "maven-publish" }
signing             = { id = "signing"        }
```

- [ ] **Step 4: Create `compose-raven/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
}

android {
    namespace  = "io.github.dracovin.composeraven"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.startup.runtime)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.compose.ui.test.manifest)
}

// Publishing block added in Task 10
```

- [ ] **Step 5: Create stub `compose-raven/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
</manifest>
```

- [ ] **Step 6: Create `sample/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace   = "io.github.dracovin.sample"
    compileSdk  = 35
    defaultConfig {
        applicationId = "io.github.dracovin.sample"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    debugImplementation(project(":compose-raven"))
}
```

- [ ] **Step 7: Create `sample/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Raven Sample"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 8: Sync and verify zero errors**

```bash
./gradlew :compose-raven:assemble
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git init
git add settings.gradle.kts build.gradle.kts gradle/ \
        compose-raven/build.gradle.kts compose-raven/src/ \
        sample/build.gradle.kts sample/src/
git commit -m "chore: project scaffolding — compose-raven library + sample modules"
```

---

### Task 2: Core State Model

**Files:**
- Create: `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/RavenState.kt`
- Create: `compose-raven/src/test/kotlin/io/github/dracovin/composeraven/RavenStateTest.kt`

**Interfaces:**
- Produces:
  - `internal object RavenState` with `heatmapEnabled`, `inspectorEnabled`, `gridEnabled: MutableStateFlow<Boolean>`, `pickedElement: MutableStateFlow<PickedElementInfo?>`, `inspectableElements: MutableStateFlow<List<InspectableElement>>`, `fun reset()`
  - `data class PickedElementInfo(xDp: Float, yDp: Float, hexColor: String, bounds: InspectableElement?)`
  - `data class InspectableElement(tag: String, boundsInWindow: android.graphics.Rect, widthDp: Float, heightDp: Float)`

- [ ] **Step 1: Write the failing test**

Create `compose-raven/src/test/kotlin/io/github/dracovin/composeraven/RavenStateTest.kt`:

```kotlin
package io.github.dracovin.composeraven

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RavenStateTest {

    @Before fun setUp() = RavenState.reset()

    @Test fun `initial — all features disabled`() {
        assertFalse(RavenState.heatmapEnabled.value)
        assertFalse(RavenState.inspectorEnabled.value)
        assertFalse(RavenState.gridEnabled.value)
    }

    @Test fun `initial — no picked element`() {
        assertNull(RavenState.pickedElement.value)
    }

    @Test fun `initial — no inspectable elements`() {
        assertEquals(emptyList<InspectableElement>(), RavenState.inspectableElements.value)
    }

    @Test fun `toggling heatmap updates flow`() {
        RavenState.heatmapEnabled.value = true
        assertEquals(true, RavenState.heatmapEnabled.value)
    }

    @Test fun `reset clears all state`() {
        RavenState.heatmapEnabled.value = true
        RavenState.pickedElement.value  = PickedElementInfo(10f, 20f, "#ff0000", null)
        RavenState.reset()
        assertFalse(RavenState.heatmapEnabled.value)
        assertNull(RavenState.pickedElement.value)
    }
}
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
./gradlew :compose-raven:test
```
Expected: FAIL with `RavenState not found`

- [ ] **Step 3: Create `RavenState.kt`**

```kotlin
package io.github.dracovin.composeraven

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow

internal object RavenState {
    val heatmapEnabled      = MutableStateFlow(false)
    val inspectorEnabled    = MutableStateFlow(false)
    val gridEnabled         = MutableStateFlow(false)
    val pickedElement       = MutableStateFlow<PickedElementInfo?>(null)
    val inspectableElements = MutableStateFlow<List<InspectableElement>>(emptyList())

    fun reset() {
        heatmapEnabled.value      = false
        inspectorEnabled.value    = false
        gridEnabled.value         = false
        pickedElement.value       = null
        inspectableElements.value = emptyList()
    }
}

data class PickedElementInfo(
    val xDp: Float,
    val yDp: Float,
    val hexColor: String,
    val bounds: InspectableElement? = null,
)

data class InspectableElement(
    val tag: String,
    val boundsInWindow: Rect,
    val widthDp: Float,
    val heightDp: Float,
)
```

- [ ] **Step 4: Run — confirm PASS**

```bash
./gradlew :compose-raven:test
```
Expected: `BUILD SUCCESSFUL`, 5 tests pass

- [ ] **Step 5: Commit**

```bash
git add compose-raven/src/main/kotlin/io/github/dracovin/composeraven/RavenState.kt \
        compose-raven/src/test/kotlin/io/github/dracovin/composeraven/RavenStateTest.kt
git commit -m "feat: RavenState singleton with MutableStateFlow state model and data classes"
```

---

### Task 3: Auto-Initializer + Manifest

**Files:**
- Create: `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/RavenInitializer.kt`
- Modify: `compose-raven/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `internal class RavenInitializer : Initializer<Unit>` — host app needs zero setup code after this task

- [ ] **Step 1: Update `AndroidManifest.xml`**

Replace the stub with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application>
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="io.github.dracovin.composeraven.RavenInitializer"
                android:value="androidx.startup.Initializer" />
        </provider>
    </application>

</manifest>
```

`tools:node="merge"` is required: the host app may already have an `InitializationProvider` from WorkManager. This directive merges the `<meta-data>` entries rather than replacing the entire block.

- [ ] **Step 2: Create `RavenInitializer.kt`**

```kotlin
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
```

- [ ] **Step 3: Verify manifest merge**

```bash
./gradlew :sample:processDebugManifest
```

Open `sample/build/intermediates/merged_manifests/debug/AndroidManifest.xml` and confirm `io.github.dracovin.composeraven.RavenInitializer` appears under `InitializationProvider`.

- [ ] **Step 4: Commit**

```bash
git add compose-raven/src/main/AndroidManifest.xml \
        compose-raven/src/main/kotlin/io/github/dracovin/composeraven/RavenInitializer.kt
git commit -m "feat: ContentProvider-based auto-initializer — zero-boilerplate host setup"
```

---

### Task 4: WindowManager Injector

**Files:**
- Create: `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/RavenWindowInjector.kt`
- Create: `compose-raven/src/test/kotlin/io/github/dracovin/composeraven/RavenWindowInjectorTest.kt`

**Interfaces:**
- Consumes: `RavenState.inspectorEnabled: MutableStateFlow<Boolean>`
- Produces: `internal class RavenWindowInjector : Application.ActivityLifecycleCallbacks`

Note on touch flags: `FLAG_NOT_FOCUSABLE` implicitly enables `FLAG_NOT_TOUCH_MODAL` per Android docs. So passthrough mode sets only `FLAG_NOT_FOCUSABLE`. Intercept mode (inspector active) clears `FLAG_NOT_FOCUSABLE`, which also clears the implied `FLAG_NOT_TOUCH_MODAL`, causing the overlay to receive all touches.

Note on `OverlayContent`: `RavenOverlayRoot()` doesn't exist until Task 5. Use an empty `@Composable` placeholder in `setContent` and replace it in Task 5.

- [ ] **Step 1: Write the failing test**

Create `compose-raven/src/test/kotlin/io/github/dracovin/composeraven/RavenWindowInjectorTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
./gradlew :compose-raven:test
```
Expected: FAIL with `RavenWindowInjector not found`

- [ ] **Step 3: Create `RavenWindowInjector.kt`**

```kotlin
package io.github.dracovin.composeraven

import android.app.Activity
import android.app.Application
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
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
            setContent { OverlayPlaceholder() }
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
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    // Clearing FLAG_NOT_FOCUSABLE also clears the implied FLAG_NOT_TOUCH_MODAL
    private fun interceptFlags() =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

// Replaced with RavenOverlayRoot() in Task 5
@Composable
private fun OverlayPlaceholder() {}
```

- [ ] **Step 4: Run — confirm PASS**

```bash
./gradlew :compose-raven:test
```

- [ ] **Step 5: Install sample and verify no crash**

```bash
./gradlew :sample:installDebug
```
Launch the sample app. Expected: no crash, no visible overlay (placeholder is empty).

- [ ] **Step 6: Commit**

```bash
git add compose-raven/src/main/kotlin/io/github/dracovin/composeraven/RavenWindowInjector.kt \
        compose-raven/src/test/kotlin/io/github/dracovin/composeraven/RavenWindowInjectorTest.kt
git commit -m "feat: WindowManager injector with lifecycle-aware attach/detach and inspector flag toggling"
```

---

### Task 5: Overlay Root UI — Draggable FAB + Toggle Menu

**Files:**
- Create: `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/overlay/RavenOverlayRoot.kt`
- Create: `compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/RavenOverlayRootTest.kt`
- Modify: `RavenWindowInjector.kt` — replace `OverlayPlaceholder` call with `RavenOverlayRoot()`

**Interfaces:**
- Consumes: `RavenState.heatmapEnabled`, `inspectorEnabled`, `gridEnabled`
- Produces: `@Composable internal fun RavenOverlayRoot()` — full-screen transparent host with draggable FAB; `GridOverlay()` and `ElementPickerOverlay()` are stubs until Tasks 7 & 8 (add empty `@Composable internal fun` stubs in their respective files now so the import compiles)

- [ ] **Step 1: Create stub files for features so imports compile**

Create `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/GridOverlay.kt`:

```kotlin
package io.github.dracovin.composeraven.features

import androidx.compose.runtime.Composable

@Composable
internal fun GridOverlay() {}
```

Create `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/ElementPickerOverlay.kt`:

```kotlin
package io.github.dracovin.composeraven.features

import androidx.compose.runtime.Composable

@Composable
internal fun ElementPickerOverlay() {}
```

- [ ] **Step 2: Write the failing instrumented test**

Create `compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/RavenOverlayRootTest.kt`:

```kotlin
package io.github.dracovin.composeraven

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.dracovin.composeraven.overlay.RavenOverlayRoot
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RavenOverlayRootTest {

    @get:Rule val composeRule = createComposeRule()

    @Before fun setUp() = RavenState.reset()

    @Test
    fun `FAB is visible on launch`() {
        composeRule.setContent { RavenOverlayRoot() }
        composeRule.onNodeWithContentDescription("Open Raven debug menu").assertIsDisplayed()
    }

    @Test
    fun `tapping FAB reveals toggle chips`() {
        composeRule.setContent { RavenOverlayRoot() }
        composeRule.onNodeWithContentDescription("Open Raven debug menu").performClick()
        composeRule.onNodeWithContentDescription("Toggle Heatmap").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Toggle Inspector").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Toggle Grid").assertIsDisplayed()
    }

    @Test
    fun `toggling Heatmap chip updates RavenState`() {
        composeRule.setContent { RavenOverlayRoot() }
        composeRule.onNodeWithContentDescription("Open Raven debug menu").performClick()
        composeRule.onNodeWithContentDescription("Toggle Heatmap").performClick()
        assert(RavenState.heatmapEnabled.value) { "Expected heatmapEnabled = true" }
    }
}
```

- [ ] **Step 3: Run — confirm FAIL**

```bash
./gradlew :compose-raven:connectedDebugAndroidTest
```
Expected: FAIL with `RavenOverlayRoot not found`

- [ ] **Step 4: Create `RavenOverlayRoot.kt`**

```kotlin
package io.github.dracovin.composeraven.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.dracovin.composeraven.RavenState
import io.github.dracovin.composeraven.features.ElementPickerOverlay
import io.github.dracovin.composeraven.features.GridOverlay
import kotlin.math.roundToInt

@Composable
internal fun RavenOverlayRoot() {
    val heatmapEnabled   by RavenState.heatmapEnabled.collectAsState()
    val inspectorEnabled by RavenState.inspectorEnabled.collectAsState()
    val gridEnabled      by RavenState.gridEnabled.collectAsState()
    var menuExpanded     by remember { mutableStateOf(false) }
    var fabOffset        by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (gridEnabled)      GridOverlay()
        if (inspectorEnabled) ElementPickerOverlay()

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .offset { IntOffset(fabOffset.x.roundToInt(), fabOffset.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        fabOffset = Offset(fabOffset.x + drag.x, fabOffset.y + drag.y)
                    }
                },
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = menuExpanded,
                enter   = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit    = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RavenToggleChip("Heatmap",   "Toggle Heatmap",   heatmapEnabled)   { RavenState.heatmapEnabled.value   = !heatmapEnabled }
                    RavenToggleChip("Inspector", "Toggle Inspector", inspectorEnabled) { RavenState.inspectorEnabled.value = !inspectorEnabled }
                    RavenToggleChip("Grid",      "Toggle Grid",      gridEnabled)      { RavenState.gridEnabled.value      = !gridEnabled }
                }
            }

            FloatingActionButton(
                onClick  = { menuExpanded = !menuExpanded },
                modifier = Modifier.semantics { contentDescription = "Open Raven debug menu" },
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null)
            }
        }
    }
}

@Composable
private fun RavenToggleChip(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    FilterChip(
        selected = checked,
        onClick  = onToggle,
        label    = { Text(label) },
        modifier = Modifier.semantics { contentDescription = description },
    )
}
```

- [ ] **Step 5: Wire `RavenOverlayRoot` into `RavenWindowInjector.kt`**

In `RavenWindowInjector.kt`, replace:
```kotlin
setContent { OverlayPlaceholder() }
```
With:
```kotlin
setContent { RavenOverlayRoot() }
```

Add import at the top of `RavenWindowInjector.kt`:
```kotlin
import io.github.dracovin.composeraven.overlay.RavenOverlayRoot
```

Delete the `OverlayPlaceholder` composable function and its import of `androidx.compose.runtime.Composable` if it's now unused.

- [ ] **Step 6: Run — confirm PASS**

```bash
./gradlew :compose-raven:connectedDebugAndroidTest
```

- [ ] **Step 7: Install sample and verify manually**

```bash
./gradlew :sample:installDebug
```
Confirm: floating Settings FAB appears bottom-right; tapping reveals three FilterChips; FAB is draggable.

- [ ] **Step 8: Commit**

```bash
git add compose-raven/src/main/kotlin/io/github/dracovin/composeraven/overlay/ \
        compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/ \
        compose-raven/src/main/kotlin/io/github/dracovin/composeraven/RavenWindowInjector.kt \
        compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/RavenOverlayRootTest.kt
git commit -m "feat: floating FAB overlay with animated toggle menu and drag repositioning"
```

---

### Task 6: Recomposition Heatmap + ravenInspectable Modifier

**Files:**
- Create: `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/RecompositionModifier.kt`
- Create: `compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/RecompositionModifierTest.kt`

**Interfaces:**
- Consumes: `RavenState.heatmapEnabled`, `RavenState.inspectorEnabled`, `RavenState.inspectableElements`, `InspectableElement`
- Produces:
  - `fun Modifier.recompositionHeatmap(): Modifier` — amber flash on each recomposition when enabled; `Modifier` no-op when disabled
  - `fun Modifier.ravenInspectable(tag: String = ""): Modifier` — registers `LayoutCoordinates` in `RavenState.inspectableElements` when inspector enabled; `Modifier` no-op when disabled

- [ ] **Step 1: Write the failing instrumented test**

Create `compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/RecompositionModifierTest.kt`:

```kotlin
package io.github.dracovin.composeraven

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.dracovin.composeraven.features.ravenInspectable
import io.github.dracovin.composeraven.features.recompositionHeatmap
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecompositionModifierTest {

    @get:Rule val composeRule = createComposeRule()

    @Before fun setUp() = RavenState.reset()

    @Test
    fun `heatmap modifier applies without crash when disabled`() {
        composeRule.setContent {
            Box(modifier = Modifier.size(100.dp).recompositionHeatmap())
        }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `inspectable registers element when inspector enabled`() {
        RavenState.inspectorEnabled.value = true
        composeRule.setContent {
            Box(modifier = Modifier.size(100.dp).ravenInspectable("test-box"))
        }
        composeRule.waitForIdle()
        assert(RavenState.inspectableElements.value.any { it.tag == "test-box" }) {
            "Expected 'test-box' in inspectableElements, got: ${RavenState.inspectableElements.value}"
        }
    }

    @Test
    fun `inspectable does not register when inspector disabled`() {
        RavenState.inspectorEnabled.value = false
        composeRule.setContent {
            Box(modifier = Modifier.size(100.dp).ravenInspectable("hidden-box"))
        }
        composeRule.waitForIdle()
        assert(RavenState.inspectableElements.value.none { it.tag == "hidden-box" })
    }
}
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
./gradlew :compose-raven:connectedDebugAndroidTest
```
Expected: FAIL with `recompositionHeatmap not found`

- [ ] **Step 3: Create `RecompositionModifier.kt`**

```kotlin
package io.github.dracovin.composeraven.features

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import io.github.dracovin.composeraven.InspectableElement
import io.github.dracovin.composeraven.RavenState

fun Modifier.recompositionHeatmap(): Modifier = composed {
    val enabled by RavenState.heatmapEnabled.collectAsState()
    if (!enabled) return@composed Modifier

    var recomposeCount by remember { mutableIntStateOf(0) }
    // SideEffect runs after every successful recomposition — increments the counter
    SideEffect { recomposeCount++ }

    val alpha = remember { Animatable(0f) }
    // LaunchedEffect restarts on each count change: snap to 0.55, fade to 0 over 400ms
    LaunchedEffect(recomposeCount) {
        alpha.snapTo(0.55f)
        alpha.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 400))
    }

    drawWithContent {
        drawContent()
        drawRect(color = Color(0xFFFF6D00).copy(alpha = alpha.value))
    }
}

fun Modifier.ravenInspectable(tag: String = ""): Modifier = composed {
    val enabled by RavenState.inspectorEnabled.collectAsState()
    if (!enabled) return@composed Modifier

    val density = LocalDensity.current
    onGloballyPositioned { coords ->
        val bounds  = coords.boundsInWindow()
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
        val idx = if (tag.isNotEmpty()) current.indexOfFirst { it.tag == tag } else -1
        if (idx >= 0) current[idx] = element else current.add(element)
        RavenState.inspectableElements.value = current
    }
}
```

- [ ] **Step 4: Run — confirm PASS**

```bash
./gradlew :compose-raven:connectedDebugAndroidTest
```

- [ ] **Step 5: Commit**

```bash
git add compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/RecompositionModifier.kt \
        compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/RecompositionModifierTest.kt
git commit -m "feat: recompositionHeatmap amber-flash modifier + ravenInspectable bounds registration"
```

---

### Task 7: Element Picker Overlay

**Files:**
- Modify: `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/ElementPickerOverlay.kt` (replace stub)
- Create: `compose-raven/src/test/kotlin/io/github/dracovin/composeraven/HexColorUtilTest.kt`

**Interfaces:**
- Consumes: `RavenState.pickedElement`, `RavenState.inspectableElements`, `PickedElementInfo`, `InspectableElement`
- Produces:
  - `@Composable internal fun ElementPickerOverlay()` — full-screen tap interceptor
  - `internal fun Int.toRavenHex(): String` — strips alpha, formats RGB as `#rrggbb` lowercase

- [ ] **Step 1: Write the failing unit test**

Create `compose-raven/src/test/kotlin/io/github/dracovin/composeraven/HexColorUtilTest.kt`:

```kotlin
package io.github.dracovin.composeraven

import io.github.dracovin.composeraven.features.toRavenHex
import org.junit.Assert.assertEquals
import org.junit.Test

class HexColorUtilTest {

    @Test fun `amber converts correctly`() {
        assertEquals("#c18a35", 0xFFc18a35.toInt().toRavenHex())
    }

    @Test fun `white converts correctly`() {
        assertEquals("#ffffff", 0xFFFFFFFF.toInt().toRavenHex())
    }

    @Test fun `black converts correctly`() {
        assertEquals("#000000", 0xFF000000.toInt().toRavenHex())
    }

    @Test fun `alpha is stripped`() {
        // Semi-transparent red — alpha discarded, only RGB shown
        assertEquals("#ff0000", 0x80FF0000.toInt().toRavenHex())
    }
}
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
./gradlew :compose-raven:test
```
Expected: FAIL with `toRavenHex not found`

- [ ] **Step 3: Replace `ElementPickerOverlay.kt` stub with full implementation**

```kotlin
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
```

- [ ] **Step 4: Run unit tests — confirm PASS**

```bash
./gradlew :compose-raven:test
```
Expected: all 4 `HexColorUtilTest` tests pass

- [ ] **Step 5: Commit**

```bash
git add compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/ElementPickerOverlay.kt \
        compose-raven/src/test/kotlin/io/github/dracovin/composeraven/HexColorUtilTest.kt
git commit -m "feat: element picker — PixelCopy hex extraction, crosshair, bounds card, toRavenHex util"
```

---

### Task 8: Grid Overlay

**Files:**
- Modify: `compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/GridOverlay.kt` (replace stub)
- Create: `compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/GridOverlayTest.kt`

**Interfaces:**
- Consumes: nothing (self-contained Canvas; toggled externally via `RavenState.gridEnabled` read in `RavenOverlayRoot`)
- Produces: `@Composable internal fun GridOverlay()` — full-screen Canvas with 8dp grid and 16/24dp keylines

- [ ] **Step 1: Write the failing instrumented test**

Create `compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/GridOverlayTest.kt`:

```kotlin
package io.github.dracovin.composeraven

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.dracovin.composeraven.features.GridOverlay
import org.junit.Rule
import org.junit.Test

class GridOverlayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `grid overlay renders without crash`() {
        composeRule.setContent { GridOverlay() }
        composeRule.onRoot().assertExists()
    }
}
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
./gradlew :compose-raven:connectedDebugAndroidTest
```
Expected: FAIL (stub is empty, test still passes structure-wise — but let's confirm the full impl is what we're testing)

- [ ] **Step 3: Replace `GridOverlay.kt` stub with full implementation**

```kotlin
package io.github.dracovin.composeraven.features

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun GridOverlay() {
    val density = LocalDensity.current

    Canvas(modifier = Modifier.fillMaxSize()) {
        val grid8px     = with(density) { 8.dp.toPx()  }
        val keyline16px = with(density) { 16.dp.toPx() }
        val keyline24px = with(density) { 24.dp.toPx() }
        val thinStroke  = with(density) { 0.5.dp.toPx() }
        val thickStroke = with(density) { 1.dp.toPx()   }

        val gridColor      = Color.White.copy(alpha = 0.10f)
        val keyline16Color = Color(0xFF00BCD4).copy(alpha = 0.50f)  // cyan
        val keyline24Color = Color(0xFFFF5722).copy(alpha = 0.50f)  // deep-orange

        // 8dp vertical grid lines
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), thinStroke)
            x += grid8px
        }
        // 8dp horizontal grid lines
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), thinStroke)
            y += grid8px
        }
        // 16dp keylines — left and right
        drawLine(keyline16Color, Offset(keyline16px, 0f), Offset(keyline16px, size.height), thickStroke)
        drawLine(keyline16Color, Offset(size.width - keyline16px, 0f), Offset(size.width - keyline16px, size.height), thickStroke)
        // 24dp keylines — left and right
        drawLine(keyline24Color, Offset(keyline24px, 0f), Offset(keyline24px, size.height), thickStroke)
        drawLine(keyline24Color, Offset(size.width - keyline24px, 0f), Offset(size.width - keyline24px, size.height), thickStroke)
    }
}
```

- [ ] **Step 4: Run — confirm PASS**

```bash
./gradlew :compose-raven:connectedDebugAndroidTest
```

- [ ] **Step 5: Commit**

```bash
git add compose-raven/src/main/kotlin/io/github/dracovin/composeraven/features/GridOverlay.kt \
        compose-raven/src/androidTest/kotlin/io/github/dracovin/composeraven/GridOverlayTest.kt
git commit -m "feat: 8dp Material grid overlay with cyan 16dp and orange 24dp keylines"
```

---

### Task 9: Sample App Integration + Full Manual Verification

**Files:**
- Create: `sample/src/main/kotlin/io/github/dracovin/sample/MainActivity.kt`

**Interfaces:**
- Consumes: `Modifier.recompositionHeatmap()`, `Modifier.ravenInspectable()` from `:compose-raven`
- Produces: runnable sample that exercises all three features end-to-end

- [ ] **Step 1: Create `MainActivity.kt`**

```kotlin
package io.github.dracovin.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.dracovin.composeraven.features.ravenInspectable
import io.github.dracovin.composeraven.features.recompositionHeatmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { SampleScreen() }
            }
        }
    }
}

@Composable
private fun SampleScreen() {
    var counter by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        // Recomposes on every counter change — heatmap will flash it
        Text(
            text     = "Count: $counter",
            modifier = Modifier
                .ravenInspectable("counter-text")
                .recompositionHeatmap(),
        )
        Button(
            onClick  = { counter++ },
            modifier = Modifier
                .ravenInspectable("increment-button")
                .recompositionHeatmap(),
        ) {
            Text("Increment")
        }
        // Static amber box — inspect it to see #c18a35
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFFc18a35))
                .ravenInspectable("amber-box"),
        )
    }
}
```

- [ ] **Step 2: Install and run full manual test**

```bash
./gradlew :sample:installDebug
```

Test each feature:

**Heatmap:** Tap the Settings FAB → enable Heatmap → tap "Increment" repeatedly. The counter `Text` and `Button` should flash amber on each tap.

**Inspector — color:** Tap FAB → enable Inspector → tap anywhere on the amber box. Bottom card shows `Color #c18a35`.

**Inspector — bounds:** With Inspector on, tap the amber box. Card shows `Tag amber-box`, `W ~120dp × H ~120dp` (slight variation from screen density).

**Grid:** Tap FAB → enable Grid. White 8dp grid appears across the screen; cyan vertical lines at 16dp from each edge; orange vertical lines at 24dp from each edge.

**Drag:** Drag the FAB to top-left. Toggle features. Confirm FAB stays in new position.

- [ ] **Step 3: Commit**

```bash
git add sample/src/
git commit -m "feat: sample app exercising heatmap, inspector (color + bounds), and grid overlay"
```

---

### Task 10: Publishing Configuration

**Files:**
- Modify: `compose-raven/build.gradle.kts` — append `afterEvaluate { publishing { } signing { } }` block
- Create: `.gitignore`

**Interfaces:**
- Produces: `./gradlew :compose-raven:publishReleasePublicationToLocalRepository` working; Sonatype upload ready

- [ ] **Step 1: Append publishing + signing to `compose-raven/build.gradle.kts`**

Add after the `dependencies { }` block:

```kotlin
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId    = "io.github.dracovin"
                artifactId = "compose-raven"
                version    = "0.1.0-alpha01"

                pom {
                    name        = "composeRaven"
                    description = "Zero-boilerplate in-app UI inspector & debug overlay for Jetpack Compose"
                    url         = "https://github.com/dracovin/compose-raven"
                    licenses {
                        license {
                            name = "Apache-2.0"
                            url  = "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    }
                    developers {
                        developer {
                            id    = "dracovin"
                            name  = "dracovin"
                            email = "siddhardha.d@kynhood.com"
                        }
                    }
                    scm {
                        connection          = "scm:git:git://github.com/dracovin/compose-raven.git"
                        developerConnection = "scm:git:ssh://github.com/dracovin/compose-raven.git"
                        url                 = "https://github.com/dracovin/compose-raven"
                    }
                }
            }
        }

        repositories {
            maven {
                name = "Local"
                url  = uri(layout.buildDirectory.dir("local-repo"))
            }
            maven {
                name = "MavenCentral"
                url  = uri(
                    if (version.toString().endsWith("SNAPSHOT"))
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    else
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                )
                credentials {
                    username = providers.gradleProperty("ossrhUsername").orNull
                    password = providers.gradleProperty("ossrhPassword").orNull
                }
            }
        }
    }

    signing {
        val keyId      = providers.gradleProperty("signing.keyId").orNull
        val password   = providers.gradleProperty("signing.password").orNull
        val secretRing = providers.gradleProperty("signing.secretKeyRingFile").orNull
        if (keyId != null && password != null && secretRing != null) {
            useInMemoryPgpKeys(file(secretRing).readText(), password)
            sign(publishing.publications["release"])
        }
    }
}
```

- [ ] **Step 2: Verify local publish**

```bash
./gradlew :compose-raven:publishReleasePublicationToLocalRepository
```

Inspect `compose-raven/build/local-repo/io/github/dracovin/compose-raven/0.1.0-alpha01/`. Confirm `.aar` and `.pom` are present.

- [ ] **Step 3: Set up signing credentials (local machine only — never commit)**

Add to `~/.gradle/gradle.properties` (not the project file):

```properties
ossrhUsername=YOUR_SONATYPE_USERNAME
ossrhPassword=YOUR_SONATYPE_TOKEN
signing.keyId=LAST_8_CHARS_OF_KEY_ID
signing.password=YOUR_GPG_PASSPHRASE
signing.secretKeyRingFile=/Users/vinay/.gnupg/secring.gpg
```

Generate GPG key if needed:
```bash
gpg --gen-key
gpg --export-secret-keys YOUR_KEY_ID > ~/.gnupg/secring.gpg
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

Register namespace at https://central.sonatype.com — verify `io.github.dracovin` ownership via GitHub OAuth (no ticket needed for GitHub namespaces on the new portal).

- [ ] **Step 4: Create `.gitignore`**

```
*.iml
.gradle/
.idea/
build/
local.properties
*.keystore
*.jks
secring.gpg
gradle.properties.local
```

- [ ] **Step 5: Commit**

```bash
git add compose-raven/build.gradle.kts .gitignore
git commit -m "chore: maven-publish + signing config for Maven Central (0.1.0-alpha01)"
```

---

## Spec Coverage Check

| Spec requirement | Task |
|---|---|
| minSdk 26, targetSdk 35 | Task 1 |
| Zero-boilerplate ContentProvider init | Task 3 |
| ActivityLifecycleCallbacks + WindowManager | Task 4 |
| TYPE_APPLICATION — no SYSTEM_ALERT_WINDOW | Task 4 |
| Flag toggling for inspector intercept mode | Task 4 |
| Draggable FAB + animated toggle menu | Task 5 |
| RavenState singleton, no DI framework | Task 2 |
| Hilt/Koin isolation (no graph entries) | Task 2 — `object` not in any component |
| `Modifier.recompositionHeatmap()` amber flash | Task 6 |
| `Modifier.ravenInspectable()` bounds registration | Task 6 |
| PixelCopy hex color extraction | Task 7 |
| `#rrggbb` lowercase hex format (#c18a35) | Task 7 |
| Tap coordinates in dp | Task 7 |
| ElementInfoCard with W/H dp dimensions | Task 7 |
| 8dp/16dp/24dp Canvas grid | Task 8 |
| Sample app manual verification | Task 9 |
| `io.github.dracovin:compose-raven:0.1.0-alpha01` | Task 10 |
| Sonatype OSSRH + GPG signing | Task 10 |
