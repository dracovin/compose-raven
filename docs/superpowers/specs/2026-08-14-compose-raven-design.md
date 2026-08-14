# composeRaven — Design Spec

**Date:** 2026-08-14  
**Author:** dracovin  
**Status:** Approved for implementation  
**Artifact:** `io.github.dracovin:compose-raven:0.1.0-alpha01`  
**minSdk:** 26 | **targetSdk:** 35  
**Language:** Kotlin 2.0+ | **Compose BOM:** 2024.09.00+

---

## 1. Purpose

`composeRaven` is a `debugImplementation`-only Android SDK that injects a floating debug overlay into any Activity without requiring the host app to modify its Compose tree or call any setup code. It provides three visual debugging tools for Jetpack Compose UIs: a recomposition heatmap, an element inspector with hex color picker, and an 8dp Material grid overlay.

**Non-goals:**
- Not for release builds (enforced via no-op release artifact pattern)
- Not a profiling replacement (Perfetto/Macrobenchmark handles perf)
- No remote/network features in v0.1

---

## 2. Module Structure

Single Android library module. No multi-module split at this stage.

```
compose-raven/                          # root project
├── compose-raven/                      # library module
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/io/github/dracovin/composeraven/
│       │       ├── RavenInitializer.kt        # ContentProvider auto-init
│       │       ├── RavenWindowInjector.kt     # ActivityLifecycleCallbacks + WindowManager
│       │       ├── RavenState.kt              # Singleton state (no DI framework)
│       │       ├── overlay/
│       │       │   └── RavenOverlayRoot.kt    # Root Compose UI
│       │       └── features/
│       │           ├── RecompositionModifier.kt
│       │           ├── ElementPickerOverlay.kt
│       │           └── GridOverlay.kt
│       └── debug/                             # (empty — placeholder for future debug-only resources)
├── sample/                                    # sample app for manual testing
│   └── ...
├── build.gradle.kts                           # root build file
├── settings.gradle.kts
└── gradle/
    └── libs.versions.toml                     # version catalog
```

---

## 3. Build Configuration

### 3.1 `settings.gradle.kts`
```kotlin
rootProject.name = "composeRaven"
include(":compose-raven", ":sample")
```

### 3.2 `gradle/libs.versions.toml`
```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
composeBom = "2024.09.00"
startup = "1.1.1"
coroutines = "1.8.1"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
startup-runtime = { group = "androidx.startup", name = "startup-runtime", version.ref = "startup" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
maven-publish = { id = "maven-publish" }
signing = { id = "signing" }
```

### 3.3 `compose-raven/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
}

android {
    namespace = "io.github.dracovin.composeraven"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.startup.runtime)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
}
```

**Why `startup-runtime`?** `androidx.startup` provides the `Initializer` interface and `InitializationProvider` ContentProvider — the standard AndroidX zero-boilerplate init mechanism. It's already transitively present in most apps via WorkManager or other AndroidX libraries, so it adds minimal weight.

---

## 4. Zero-Boilerplate Initialization

### 4.1 Mechanism

Android boots `ContentProvider`s before `Application.onCreate()`. `androidx.startup` exposes an `InitializationProvider` that discovers `Initializer` implementations via the manifest. `RavenInitializer` implements `Initializer<Unit>` and registers `ActivityLifecycleCallbacks` on the `Application` context — all without the host app writing a single line of setup code.

### 4.2 Manifest Entry (library's `AndroidManifest.xml`)
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="io.github.dracovin.composeraven.RavenInitializer"
        android:value="androidx.startup.Initializer" />
</provider>
```

The `tools:node="merge"` ensures this merges correctly with any existing `InitializationProvider` the host app may already have (e.g., from WorkManager).

### 4.3 `RavenInitializer.kt`
```kotlin
internal class RavenInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(RavenWindowInjector())
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

---

## 5. WindowManager Injector

### 5.1 Design

`RavenWindowInjector` implements `ActivityLifecycleCallbacks`. On `onActivityResumed` it:
1. Creates a `ComposeView`
2. Wires `ViewTreeLifecycleOwner`, `ViewTreeViewModelStoreOwner`, `ViewTreeSavedStateRegistryOwner` from the Activity (required for Compose to function inside a manually-added view)
3. Adds the view to the Activity's `WindowManager` with `TYPE_APPLICATION_OVERLAY`-free params — specifically `TYPE_APPLICATION`, which requires no `SYSTEM_ALERT_WINDOW` permission because it attaches inside the app's own window
4. Tracks the view keyed by Activity instance to remove on `onActivityPaused`

**Why `TYPE_APPLICATION` not `TYPE_APPLICATION_OVERLAY`?** `TYPE_APPLICATION_OVERLAY` draws over other apps and requires the `SYSTEM_ALERT_WINDOW` dangerous permission (user must manually grant in Settings). Since we're overlaying the host app's own UI, `TYPE_APPLICATION` is correct, safer, and permission-free.

**Why not just add to DecorView?** WindowManager gives us precise `LayoutParams` control (position, gravity, flags, touch passthrough via `FLAG_NOT_TOUCH_MODAL | FLAG_NOT_FOCUSABLE`). It also avoids coupling to the host's view hierarchy depth. The overlay must pass touches through to the host UI when in idle state.

### 5.2 LayoutParams Flags

```
FLAG_NOT_TOUCH_MODAL   — touches outside the overlay bounds fall through to host
FLAG_NOT_FOCUSABLE     — overlay never steals keyboard focus
FLAG_LAYOUT_IN_SCREEN  — overlay respects screen bounds
FLAG_LAYOUT_NO_LIMITS  — allows the draggable FAB to be positioned freely
```

When the inspector mode is active, `FLAG_NOT_TOUCH_MODAL` is temporarily cleared so the overlay intercepts taps for element picking.

---

## 6. Overlay UI

### 6.1 RavenOverlayRoot

A full-screen transparent `Box` composable that contains:
- A draggable `FloatingActionButton` (bottom-end by default) with the raven icon
- On FAB tap: expand a compact vertical menu with three toggle chips

### 6.2 State Model (`RavenState.kt`)

```kotlin
internal object RavenState {
    val heatmapEnabled   = MutableStateFlow(false)
    val inspectorEnabled = MutableStateFlow(false)
    val gridEnabled      = MutableStateFlow(false)
    // Inspector pick result — null when nothing picked
    val pickedElement    = MutableStateFlow<PickedElementInfo?>(null)
}

data class PickedElementInfo(
    val bounds: Rect,
    val widthDp: Float,
    val heightDp: Float,
    val hexColor: String,   // e.g. "#c18a35"
)
```

Plain Kotlin `object` + `StateFlow`. No Hilt `@Singleton`, no Koin `single {}`. The host app's DI graphs never see this type — there is no clash by construction.

**Hilt host apps:** `RavenState` is not an `@Inject`able type and is not in any `@Component`. Hilt's generated code is unaware it exists.  
**Koin host apps:** `RavenState` is not declared in any `module {}` block. Koin's registry is unaware it exists.

---

## 7. Feature Specifications

### 7.1 Recomposition Heatmap

**Modifier:** `Modifier.recompositionHeatmap()`

**Mechanism:**
- Each application of the modifier holds a local `var recomposeCount by remember { mutableIntStateOf(0) }`
- A `SideEffect` (runs after every successful recomposition) increments the count
- The count drives an `animateColorAsState` that transitions from a bright flash color (amber/red) back to `Color.Transparent` over ~400ms
- The animated color is drawn as a `drawWithContent` layer over the composable

**Usage by developers:** Wrap any composable with `Modifier.recompositionHeatmap()` to watch it flash on recompose. When `RavenState.heatmapEnabled` is false, the modifier is a no-op (zero overhead).

### 7.2 X-Ray Element Picker

Two-tier approach — Tier 1 requires zero host changes; Tier 2 is opt-in for bounds:

**Tier 1 — always available (zero-boilerplate):**
- When inspector mode is active, the overlay intercepts taps via `Modifier.pointerInput`
- On tap, captures the tap pixel coordinate and converts to dp using `LocalDensity`
- `PixelCopy.request()` (API 26+) captures the pixel at the tap point from the Activity's window surface → converts ARGB int to hex string
- A crosshair is drawn at the tap point; tap coordinates + hex color displayed in bottom sheet

**Tier 2 — opt-in bounds (requires host to apply modifier):**
- Host dev applies `Modifier.ravenInspectable()` to composables they want inspectable
- Each inspectable composable registers its `LayoutCoordinates` via `onGloballyPositioned` into `RavenState`
- On tap, the closest registered element by Euclidean distance to tap center is selected
- Its bounds are drawn as a rectangle overlay; `W: 120dp × H: 48dp` shown in the bottom sheet

**Hex extraction:** `PixelCopy` samples the live rendered frame — it correctly reads blended, anti-aliased colors as they appear on screen, not source values.

**Why two tiers?** Truly zero-boilerplate color extraction is achievable anywhere via `PixelCopy`. Bounds, however, require knowing which Composable was tapped — which means either the accessibility semantic tree (brittle for non-semantic nodes) or explicit registration. The opt-in modifier is the honest, reliable choice.

### 7.3 8dp Grid & Keyline Overlay

**Mechanism:** A `Canvas` composable sized to fill the full screen, drawn on top of everything, toggled by `RavenState.gridEnabled`.

**Lines drawn:**
- **8dp grid:** thin lines (0.5dp stroke, 10% white opacity) every 8dp in both axes
- **16dp keylines:** slightly brighter vertical lines (20% opacity) at x=16dp and mirrored from right edge
- **24dp keylines:** same treatment at x=24dp / right-24dp

Colors are configurable via a `RavenTheme` object for future customization (uses sensible defaults in v0.1).

---

## 8. Dependency Isolation Summary

| Concern | Resolution |
|---|---|
| Host uses Hilt | `RavenState` is not `@Inject`able. `RavenInitializer` is `internal`. Zero component entries. |
| Host uses Koin | No `module {}` declarations. Koin's `GlobalContext` is never touched. |
| Host uses Compose | SDK creates its own `ComposeView` with its own composition. No shared composition tree. |
| SDK Compose version vs host | SDK declares Compose as `implementation` (not `api`). Host's BOM wins for its own tree. |
| ContentProvider authority clash | `${applicationId}.androidx-startup` is the standard authority; `tools:node="merge"` handles dedup. |

---

## 9. Publishing

### 9.1 Maven Central via Sonatype OSSRH

1. Register at `https://central.sonatype.com` — verify namespace ownership of `io.github.dracovin` via GitHub OAuth (no ticket process needed for GitHub namespaces on the new Central portal)
2. Generate a GPG key pair: `gpg --gen-key` → publish public key to `keyserver.ubuntu.com`
3. Add to `~/.gradle/gradle.properties`:
   ```
   signing.keyId=LAST8CHARS
   signing.password=YOUR_GPG_PASSPHRASE
   signing.secretKeyRingFile=/path/to/secring.gpg
   ossrhUsername=YOUR_SONATYPE_USERNAME
   ossrhPassword=YOUR_SONATYPE_TOKEN
   ```
4. Configure `maven-publish` + `signing` in `build.gradle.kts` with POM metadata (name, description, SCM, licenses, developers)
5. Run `./gradlew publishReleasePublicationToSonatypeRepository closeAndReleaseSonatypeStagingRepository`

### 9.2 Release Artifact Structure

Two artifacts published per version:
- `compose-raven` — the actual SDK (for `debugImplementation`)
- `compose-raven-no-op` — an empty stub with the same public API surface (all methods are no-ops) for `releaseImplementation`, so host apps can keep one dependency declaration in both configurations

### 9.3 GitHub Actions CI

```yaml
# .github/workflows/publish.yml
# Triggered on tag push: v*.*.*
# Steps: checkout → setup JDK 17 → GPG import → gradlew publish
```

---

## 10. Testing

- `RecompositionModifier`: `@Test` in `compose-ui-test` — assert flash color appears after triggering recomposition
- `ElementPickerOverlay`: unit test hex conversion logic (`Int.toHexString()` util)
- `GridOverlay`: snapshot test via Paparazzi for visual regression
- `RavenWindowInjector`: Robolectric test for attach/detach lifecycle

---

## 11. Open Questions / Future Work

- **v0.2:** Slow recomposition detection (flag composables that recompose > N times/sec)
- **v0.2:** Shake-to-toggle as alternative to FAB
- **v0.3:** Export inspector results as JSON to logcat / clipboard
- The `compose-raven-no-op` artifact is listed in Phase 5 but not implemented in v0.1 — document this clearly in the README
