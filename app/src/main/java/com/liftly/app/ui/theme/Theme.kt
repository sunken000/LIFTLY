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

// 1.5.5: identidade "Liftline" — violeta vivo em superfícies escuras sólidas.
val LiftlyPurple = Color(0xFF9B5DE5)
val LiftlyPurpleBright = Color(0xFFC9A7FF)
val LiftlyPurpleDark = Color(0xFF5D2E86)
val LiftlyInk = Color(0xFF0B0910)
val LiftlySlate = Color(0xFF16121B)

// Aliases preservados para compatibilidade com código e previews antigos.
val LiftlyTeal = LiftlyPurple
val LiftlyTealBright = LiftlyPurpleBright
val LiftlyTealDark = LiftlyPurpleDark

private val PurpleNeonColors = darkColorScheme(
    primary = LiftlyPurpleBright,
    onPrimary = Color(0xFF24142F),
    primaryContainer = Color(0xFF2E1D3D),
    onPrimaryContainer = Color(0xFFF1E6FF),
    secondary = Color(0xFFD0C7D6),
    onSecondary = Color(0xFF29252D),
    secondaryContainer = Color(0xFF29242D),
    onSecondaryContainer = Color(0xFFEDE7F0),
    tertiary = Color(0xFFE4A06A),
    onTertiary = Color(0xFF3E2109),
    tertiaryContainer = Color(0xFF402A1B),
    onTertiaryContainer = Color(0xFFFFE2C9),
    background = LiftlyInk,
    onBackground = Color(0xFFF1EFF3),
    surface = LiftlySlate,
    onSurface = Color(0xFFF1EFF3),
    surfaceVariant = Color(0xFF241E29),
    onSurfaceVariant = Color(0xFFCEC5D3),
    outline = Color(0xFF7B7580),
    outlineVariant = Color(0xFF413747),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val WhiteColors = lightColorScheme(
    primary = Color(0xFF713AA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0E3FF),
    onPrimaryContainer = Color(0xFF2C123F),
    secondary = Color(0xFF726A77),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8EE),
    onSecondaryContainer = Color(0xFF27232A),
    tertiary = Color(0xFF9A5B2C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1CB),
    onTertiaryContainer = Color(0xFF341704),
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
    primary = Color(0xFFC9A7FF),
    onPrimary = Color(0xFF24142F),
    primaryContainer = Color(0xFF2D1B3B),
    onPrimaryContainer = Color(0xFFF1E6FF),
    secondary = Color(0xFFAAA3AD),
    onSecondary = Color(0xFF28262A),
    secondaryContainer = Color(0xFF272629),
    onSecondaryContainer = Color(0xFFE9E6EA),
    tertiary = Color(0xFFE4A06A),
    onTertiary = Color(0xFF3E2109),
    tertiaryContainer = Color(0xFF3C281A),
    onTertiaryContainer = Color(0xFFFFE2C9),
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
    chartGrid = Color(0xFF403649),
    glow = LiftlyPurple.copy(alpha = 0.18f),
    ambientPrimary = Color(0xFF7039A5),
    ambientSecondary = Color(0xFF3B2A45),
    glassSurface = Color(0xFF19141F),
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
    glassSurface = Color(0xFFFFFFFF),
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
    glassSurface = Color(0xFF111013),
)

private val LocalLiftlyExtendedColors = staticCompositionLocalOf { PurpleNeonExtendedColors }

val MaterialTheme.liftlyColors: LiftlyExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalLiftlyExtendedColors.current

private val LiftlyShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 6.dp, topEnd = 10.dp, bottomEnd = 6.dp, bottomStart = 10.dp,
    ),
    small = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 8.dp, topEnd = 14.dp, bottomEnd = 8.dp, bottomStart = 14.dp,
    ),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 10.dp, topEnd = 18.dp, bottomEnd = 10.dp, bottomStart = 18.dp,
    ),
    large = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 12.dp, topEnd = 24.dp, bottomEnd = 12.dp, bottomStart = 24.dp,
    ),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 14.dp, topEnd = 32.dp, bottomEnd = 14.dp, bottomStart = 32.dp,
    ),
)

private val LiftlyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 42.sp,
        lineHeight = 48.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.7.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.9.sp,
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
