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
