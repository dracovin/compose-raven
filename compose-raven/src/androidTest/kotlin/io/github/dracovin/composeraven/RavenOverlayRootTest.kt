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