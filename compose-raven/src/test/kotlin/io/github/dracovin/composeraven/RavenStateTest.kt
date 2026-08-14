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
