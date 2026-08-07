package com.liftly.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPaletteTest {
    @Test
    fun normalize_acceptsShortAndLongRgb() {
        assertEquals("#BBDDFF", PaletteColorCodec.normalize(" #bdf "))
        assertEquals("#12A0EF", PaletteColorCodec.normalize("12a0ef"))
    }

    @Test
    fun normalize_rejectsAlphaMalformedAndMissingChannels() {
        assertNull(PaletteColorCodec.normalize("#8012A0EF"))
        assertNull(PaletteColorCodec.normalize("#12XXEF"))
        assertNull(PaletteColorCodec.normalize("#12EF"))
    }

    @Test
    fun blackAndWhiteHaveMaximumWcagContrast() {
        val black = requireNotNull(PaletteColorCodec.parse("#000000"))
        val white = requireNotNull(PaletteColorCodec.parse("#FFFFFF"))
        assertEquals(21.0, PaletteColorCodec.contrastRatio(black, white), 0.0001)
    }

    @Test
    fun readableForeground_keepsReadablePreference() {
        val background = requireNotNull(PaletteColorCodec.parse("#101012"))
        val preferred = requireNotNull(PaletteColorCodec.parse("#F5F5F7"))

        assertEquals(preferred, PaletteColorCodec.readableForeground(background, preferred))
    }

    @Test
    fun readableForeground_replacesLowContrastPreference() {
        val background = requireNotNull(PaletteColorCodec.parse("#FFFFFF"))
        val lowContrast = requireNotNull(PaletteColorCodec.parse("#EEEEEE"))
        val resolved = PaletteColorCodec.readableForeground(background, lowContrast)

        assertEquals("#000000", PaletteColorCodec.format(resolved))
        assertTrue(PaletteColorCodec.contrastRatio(background, resolved) >= 4.5)
    }

    @Test
    fun completePaletteRequiresEveryValidColor() {
        val defaults = defaultCustomPalette("Branco")
        assertTrue(defaults.isComplete())
        assertNotNull(defaults.normalizedOrNull())
        assertFalse(defaults.copy(surface = "").isComplete())
    }
}
