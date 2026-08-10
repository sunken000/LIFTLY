package com.liftly.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1.5.4: violeta mais sóbrio e menos saturado. O roxo continua sendo a assinatura do Liftly,
// mas deixa de competir com conteúdo, métricas e estados funcionais.
val LiftlyPurple = Color(0xFF8E72B5)
val LiftlyPurpleBright = Color(0xFFB8A7D6)
val LiftlyPurpleDark = Color(0xFF5D4A78)
val LiftlyInk = Color(0xFF0E0D10)
val LiftlySlate = Color(0xFF17161A)

// Aliases preservados para compatibilidade com código e previews antigos.
val LiftlyTeal = LiftlyPurple
val LiftlyTealBright = LiftlyPurpleBright
val LiftlyTealDark = LiftlyPurpleDark

private val PurpleNeonColors = darkColorScheme(
    primary = LiftlyPurpleBright,
    onPrimary = Color(0xFF251F2C),
    primaryContainer = Color(0xFF2B2532),
    onPrimaryContainer = Color(0xFFE9E2F1),
    secondary = Color(0xFFAEA5B5),
    onSecondary = Color(0xFF29262D),
    secondaryContainer = Color(0xFF2D2931),
    onSecondaryContainer = Color(0xFFE8E3EA),
    tertiary = Color(0xFFA997B8),
    onTertiary = Color(0xFF2C2630),
    tertiaryContainer = Color(0xFF332C37),
    onTertiaryContainer = Color(0xFFEBE3ED),
    background = LiftlyInk,
    onBackground = Color(0xFFF1EFF3),
    surface = LiftlySlate,
    onSurface = Color(0xFFF1EFF3),
    surfaceVariant = Color(0xFF222126),
    onSurfaceVariant = Color(0xFFC7C1CB),
    outline = Color(0xFF7B7580),
    outlineVariant = Color(0xFF39353D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val WhiteColors = lightColorScheme(
    primary = Color(0xFF67507E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E1EE),
    onPrimaryContainer = Color(0xFF271F2D),
    secondary = Color(0xFF726A77),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8EE),
    onSecondaryContainer = Color(0xFF27232A),
    tertiary = Color(0xFF7C687A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E6ED),
    onTertiaryContainer = Color(0xFF2B222A),
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF1D1C20),
    surface = Color.White,
    onSurface = Color(0xFF1D1C20),
    surfaceVariant = Color(0xFFEEEEF1),
    onSurfaceVariant = Color(0xFF535057),
    outline = Color(0xFF7D7881),
    outlineVariant = Color(0xFFD0CDD4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

private val BlackColors = darkColorScheme(
    primary = Color(0xFFB8A7D6),
    onPrimary = Color(0xFF251F2C),
    primaryContainer = Color(0xFF2A2430),
    onPrimaryContainer = Color(0xFFE9E2F1),
    secondary = Color(0xFFAAA3AD),
    onSecondary = Color(0xFF28262A),
    secondaryContainer = Color(0xFF272629),
    onSecondaryContainer = Color(0xFFE9E6EA),
    tertiary = Color(0xFFA89EAA),
    onTertiary = Color(0xFF292629),
    tertiaryContainer = Color(0xFF2B292D),
    onTertiaryContainer = Color(0xFFEBE8EC),
    background = Color.Black,
    onBackground = Color(0xFFF3F2F5),
    surface = Color(0xFF0D0D0F),
    onSurface = Color(0xFFF3F2F5),
    surfaceVariant = Color(0xFF1B1B1E),
    onSurfaceVariant = Color(0xFFC8C5CB),
    outline = Color(0xFF87848A),
    outlineVariant = Color(0xFF333236),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

@Immutable
data class LiftlyExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val chartGrid: Color,
    val glow: Color,
    val ambientPrimary: Color,
    val ambientSecondary: Color,
    val glassSurface: Color,
)

private val PurpleNeonExtendedColors = LiftlyExtendedColors(
    success = Color(0xFF74D99A),
    onSuccess = Color(0xFF00391C),
    successContainer = Color(0xFF0D4F2C),
    onSuccessContainer = Color(0xFF90F6B4),
    warning = Color(0xFFFFD06A),
    onWarning = Color(0xFF422C00),
    warningContainer = Color(0xFF5E4200),
    onWarningContainer = Color(0xFFFFDF99),
    chartGrid = Color(0xFF3A3740),
    glow = LiftlyPurple.copy(alpha = 0.14f),
    ambientPrimary = Color(0xFF6E5A88),
    ambientSecondary = Color(0xFF7A6677),
    glassSurface = Color(0xFA19181C),
)

private val WhiteExtendedColors = LiftlyExtendedColors(
    success = Color(0xFF166C3B),
    onSuccess = Color.White,
    successContainer = Color(0xFFACF2C3),
    onSuccessContainer = Color(0xFF00210C),
    warning = Color(0xFF795900),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFDEA1),
    onWarningContainer = Color(0xFF261900),
    chartGrid = Color(0xFFDCDCE1),
    glow = LiftlyPurple.copy(alpha = 0.08f),
    ambientPrimary = Color(0xFFE6E0EC),
    ambientSecondary = Color(0xFFEBE5E9),
    glassSurface = Color(0xFCFFFFFF),
)

private val BlackExtendedColors = LiftlyExtendedColors(
    success = Color(0xFF74D99A),
    onSuccess = Color(0xFF00391C),
    successContainer = Color(0xFF0D4F2C),
    onSuccessContainer = Color(0xFF90F6B4),
    warning = Color(0xFFFFD06A),
    onWarning = Color(0xFF422C00),
    warningContainer = Color(0xFF5E4200),
    onWarningContainer = Color(0xFFFFDF99),
    chartGrid = Color(0xFF2C2C30),
    glow = Color.Transparent,
    ambientPrimary = Color.Transparent,
    ambientSecondary = Color.Transparent,
    glassSurface = Color(0xFC111113),
)

private val LocalLiftlyExtendedColors = staticCompositionLocalOf { PurpleNeonExtendedColors }

val MaterialTheme.liftlyColors: LiftlyExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalLiftlyExtendedColors.current

private val LiftlyShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

private val LiftlyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

/** Applies Liftly's brand theme, including compatibility aliases from earlier releases. */
@Composable
fun LiftlyTheme(
    themeMode: String,
    customPalette: LiftlyCustomPalette = LiftlyCustomPalette(),
    content: @Composable () -> Unit,
) {
    val palette = when (themeMode.trim().lowercase()) {
        "roxo neon", "roxo", "purple neon", "purple", "neon" -> LiftlyPalette.PurpleNeon
        "branco", "claro", "light" -> LiftlyPalette.White
        "preto", "escuro", "dark", "noturno", "oled" -> LiftlyPalette.Black
        "sistema", "system", "automático", "automatico", "auto" -> LiftlyPalette.PurpleNeon
        else -> LiftlyPalette.PurpleNeon
    }

    val baseColors = when (palette) {
        LiftlyPalette.PurpleNeon -> PurpleNeonColors
        LiftlyPalette.White -> WhiteColors
        LiftlyPalette.Black -> BlackColors
    }
    val baseExtendedColors = when (palette) {
        LiftlyPalette.PurpleNeon -> PurpleNeonExtendedColors
        LiftlyPalette.White -> WhiteExtendedColors
        LiftlyPalette.Black -> BlackExtendedColors
    }
    val normalizedCustom = customPalette.takeIf { it.enabled }?.normalizedOrNull()
    val colors = normalizedCustom?.let { customColorScheme(baseColors, it) } ?: baseColors
    val extendedColors = normalizedCustom?.let { customExtendedColors(baseExtendedColors, it) }
        ?: baseExtendedColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLiftlyExtendedColors provides extendedColors,
        LocalContentColor provides colors.onBackground,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = LiftlyTypography,
            shapes = LiftlyShapes,
            content = content,
        )
    }
}

internal enum class LiftlyPalette {
    PurpleNeon,
    White,
    Black,
}

/** Used by edge-to-edge system bars before MaterialTheme has been installed. */
fun isLiftlyBackgroundLight(themeMode: String, customPalette: LiftlyCustomPalette): Boolean {
    val customBackground = customPalette
        .takeIf { it.enabled }
        ?.normalizedOrNull()
        ?.background
        ?.let(PaletteColorCodec::parse)
    return customBackground?.let { PaletteColorCodec.relativeLuminance(it) > 0.5 }
        ?: (resolveLiftlyPalette(themeMode) == LiftlyPalette.White)
}

private fun customColorScheme(
    base: androidx.compose.material3.ColorScheme,
    palette: LiftlyCustomPalette,
): androidx.compose.material3.ColorScheme {
    val primaryLong = requireNotNull(PaletteColorCodec.parse(palette.primary))
    val secondaryLong = requireNotNull(PaletteColorCodec.parse(palette.secondary))
    val backgroundLong = requireNotNull(PaletteColorCodec.parse(palette.background))
    val surfaceLong = requireNotNull(PaletteColorCodec.parse(palette.surface))
    val preferredTextLong = requireNotNull(PaletteColorCodec.parse(palette.text))

    val primaryContainerLong = PaletteColorCodec.mix(primaryLong, surfaceLong, 0.26)
    val secondaryContainerLong = PaletteColorCodec.mix(secondaryLong, surfaceLong, 0.24)
    val surfaceVariantLong = PaletteColorCodec.mix(primaryLong, surfaceLong, 0.10)
    val outlineLong = PaletteColorCodec.mix(preferredTextLong, surfaceLong, 0.58)
    val outlineVariantLong = PaletteColorCodec.mix(preferredTextLong, surfaceLong, 0.28)

    fun color(value: Long) = Color(value.toInt())
    fun readable(background: Long, preferred: Long? = preferredTextLong) =
        color(PaletteColorCodec.readableForeground(background, preferred))

    return base.copy(
        primary = color(primaryLong),
        onPrimary = readable(primaryLong),
        primaryContainer = color(primaryContainerLong),
        onPrimaryContainer = readable(primaryContainerLong),
        inversePrimary = color(PaletteColorCodec.mix(primaryLong, preferredTextLong, 0.72)),
        secondary = color(secondaryLong),
        onSecondary = readable(secondaryLong),
        secondaryContainer = color(secondaryContainerLong),
        onSecondaryContainer = readable(secondaryContainerLong),
        tertiary = color(secondaryLong),
        onTertiary = readable(secondaryLong),
        tertiaryContainer = color(secondaryContainerLong),
        onTertiaryContainer = readable(secondaryContainerLong),
        background = color(backgroundLong),
        onBackground = readable(backgroundLong),
        surface = color(surfaceLong),
        onSurface = readable(surfaceLong),
        surfaceVariant = color(surfaceVariantLong),
        onSurfaceVariant = readable(surfaceVariantLong),
        surfaceTint = color(primaryLong),
        inverseSurface = readable(surfaceLong),
        inverseOnSurface = color(surfaceLong),
        outline = color(outlineLong),
        outlineVariant = color(outlineVariantLong),
    )
}

private fun customExtendedColors(
    base: LiftlyExtendedColors,
    palette: LiftlyCustomPalette,
): LiftlyExtendedColors {
    val primaryLong = requireNotNull(PaletteColorCodec.parse(palette.primary))
    val secondaryLong = requireNotNull(PaletteColorCodec.parse(palette.secondary))
    val backgroundLong = requireNotNull(PaletteColorCodec.parse(palette.background))
    val surfaceLong = requireNotNull(PaletteColorCodec.parse(palette.surface))
    val textLong = requireNotNull(PaletteColorCodec.parse(palette.text))
    fun color(value: Long) = Color(value.toInt())
    val darkBackground = PaletteColorCodec.relativeLuminance(backgroundLong) < 0.35

    return base.copy(
        chartGrid = color(PaletteColorCodec.mix(textLong, backgroundLong, 0.20)),
        glow = color(primaryLong).copy(alpha = if (darkBackground) 0.42f else 0.18f),
        ambientPrimary = color(primaryLong),
        ambientSecondary = color(secondaryLong),
        glassSurface = color(surfaceLong).copy(alpha = if (darkBackground) 0.86f else 0.91f),
    )
}
