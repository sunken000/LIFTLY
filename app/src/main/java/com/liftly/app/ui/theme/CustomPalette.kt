package com.liftly.app.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Five user-facing colors that can override any of Liftly's built-in themes. */
data class LiftlyCustomPalette(
    val enabled: Boolean = false,
    val primary: String = "",
    val secondary: String = "",
    val background: String = "",
    val surface: String = "",
    val text: String = "",
) {
    fun normalizedOrNull(): LiftlyCustomPalette? {
        val normalizedPrimary = PaletteColorCodec.normalize(primary) ?: return null
        val normalizedSecondary = PaletteColorCodec.normalize(secondary) ?: return null
        val normalizedBackground = PaletteColorCodec.normalize(background) ?: return null
        val normalizedSurface = PaletteColorCodec.normalize(surface) ?: return null
        val normalizedText = PaletteColorCodec.normalize(text) ?: return null
        return copy(
            primary = normalizedPrimary,
            secondary = normalizedSecondary,
            background = normalizedBackground,
            surface = normalizedSurface,
            text = normalizedText,
        )
    }

    fun isComplete(): Boolean = normalizedOrNull() != null
}

/**
 * Pure sRGB helpers, deliberately independent from Android/Compose so validation is unit-testable.
 * Packed colors use the conventional 0xAARRGGBB representation.
 */
object PaletteColorCodec {
    private const val OPAQUE_ALPHA = 0xFF000000L
    private const val RGB_MASK = 0x00FFFFFFL
    const val MINIMUM_TEXT_CONTRAST = 4.5

    fun normalize(input: String): String? {
        val compact = input.trim().removePrefix("#")
        val expanded = when (compact.length) {
            3 -> compact.flatMap { listOf(it, it) }.joinToString("")
            6 -> compact
            else -> return null
        }
        if (!expanded.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return "#${expanded.uppercase()}"
    }

    fun parse(input: String): Long? = normalize(input)
        ?.drop(1)
        ?.toLongOrNull(16)
        ?.let { OPAQUE_ALPHA or it }

    fun format(argb: Long): String = "#%06X".format(argb and RGB_MASK)

    fun relativeLuminance(argb: Long): Double {
        fun linearized(shift: Int): Double {
            val channel = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (channel <= 0.04045) channel / 12.92
            else ((channel + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearized(16) +
            0.7152 * linearized(8) +
            0.0722 * linearized(0)
    }

    fun contrastRatio(first: Long, second: Long): Double {
        val firstLum = relativeLuminance(first)
        val secondLum = relativeLuminance(second)
        return (max(firstLum, secondLum) + 0.05) / (min(firstLum, secondLum) + 0.05)
    }

    /** Keeps the requested text when it is readable; otherwise selects the strongest black/white. */
    fun readableForeground(
        background: Long,
        preferred: Long? = null,
        minimumContrast: Double = MINIMUM_TEXT_CONTRAST,
    ): Long {
        val opaqueBackground = OPAQUE_ALPHA or (background and RGB_MASK)
        val opaquePreferred = preferred?.let { OPAQUE_ALPHA or (it and RGB_MASK) }
        if (opaquePreferred != null && contrastRatio(opaqueBackground, opaquePreferred) >= minimumContrast) {
            return opaquePreferred
        }
        val black = OPAQUE_ALPHA
        val white = OPAQUE_ALPHA or RGB_MASK
        return if (contrastRatio(opaqueBackground, black) >= contrastRatio(opaqueBackground, white)) black else white
    }

    /** Linear sRGB mix. [firstFraction] is clamped so malformed UI values cannot overflow channels. */
    fun mix(first: Long, second: Long, firstFraction: Double): Long {
        val amount = firstFraction.coerceIn(0.0, 1.0)
        fun channel(shift: Int): Long {
            val a = (first shr shift) and 0xFF
            val b = (second shr shift) and 0xFF
            return (a * amount + b * (1.0 - amount)).roundToInt().toLong().coerceIn(0, 255)
        }
        return OPAQUE_ALPHA or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

/** Values shown when the color editor is first opened; activating them reproduces the base theme. */
fun defaultCustomPalette(themeMode: String): LiftlyCustomPalette = when (resolveLiftlyPalette(themeMode)) {
    LiftlyPalette.PurpleNeon -> LiftlyCustomPalette(
        primary = "#E6A6FF",
        secondary = "#C9B8FF",
        background = "#0B0312",
        surface = "#190B24",
        text = "#FBF4FF",
    )
    LiftlyPalette.White -> LiftlyCustomPalette(
        primary = "#71319D",
        secondary = "#65566D",
        background = "#FAF7FC",
        surface = "#FFFFFF",
        text = "#211D23",
    )
    LiftlyPalette.Black -> LiftlyCustomPalette(
        primary = "#D88AFF",
        secondary = "#D0C7D4",
        background = "#000000",
        surface = "#101012",
        text = "#F5F5F7",
    )
}

internal fun resolveLiftlyPalette(themeMode: String): LiftlyPalette = when (themeMode.trim().lowercase()) {
    "branco", "claro", "light", "white" -> LiftlyPalette.White
    "preto", "escuro", "dark", "noturno", "oled" -> LiftlyPalette.Black
    else -> LiftlyPalette.PurpleNeon
}
