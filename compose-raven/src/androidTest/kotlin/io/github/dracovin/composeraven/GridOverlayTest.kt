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
