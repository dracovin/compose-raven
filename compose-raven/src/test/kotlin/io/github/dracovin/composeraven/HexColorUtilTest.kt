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
