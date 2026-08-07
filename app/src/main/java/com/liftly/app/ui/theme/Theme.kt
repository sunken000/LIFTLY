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

val LiftlyPurple = Color(0xFFB779E8)
val LiftlyPurpleBright = Color(0xFFD8B7F4)
val LiftlyPurpleDark = Color(0xFF68458A)
val LiftlyInk = Color(0xFF0C0910)
val LiftlySlate = Color(0xFF18131C)

// Kept as aliases so older UI code or previews importing the former brand names still compile.
val LiftlyTeal = LiftlyPurple
val LiftlyTealBright = LiftlyPurpleBright
val LiftlyTealDark = LiftlyPurpleDark

private val PurpleNeonColors = darkColorScheme(
    primary = LiftlyPurpleBright,
    onPrimary = Color(0xFF2D153A),
    primaryContainer = Color(0xFF332440),
    onPrimaryContainer = Color(0xFFF1E3FC),
    secondary = Color(0xFFC7BCCB),
    onSecondary = Color(0xFF2E2931),
    secondaryContainer = Color(0xFF302B33),
    onSecondaryContainer = Color(0xFFEAE3ED),
    tertiary = Color(0xFFD1ACC2),
    onTertiary = Color(0xFF392532),
    tertiaryContainer = Color(0xFF3C2B36),
    onTertiaryContainer = Color(0xFFF2E0EA),
    background = LiftlyInk,
    onBackground = Color(0xFFF8F2FA),
    surface = LiftlySlate,
    onSurface = Color(0xFFF8F2FA),
    surfaceVariant = Color(0xFF262129),
    onSurfaceVariant = Color(0xFFCEC5D1),
    outline = Color(0xFF8D838F),
    outlineVariant = Color(0xFF403943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val WhiteColors = lightColorScheme(
    primary = Color(0xFF71319D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0D9FF),
    onPrimaryContainer = Color(0xFF2D0048),
    secondary = Color(0xFF65566D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDDEF2),
    onSecondaryContainer = Color(0xFF211829),
    tertiary = Color(0xFF87506E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E8),
    onTertiaryContainer = Color(0xFF381126),
    background = Color(0xFFFAF7FC),
    onBackground = Color(0xFF211D23),
    surface = Color(0xFAFFFFFF),
    onSurface = Color(0xFF211D23),
    surfaceVariant = Color(0xFFEFE8F1),
    onSurfaceVariant = Color(0xFF514A54),
    outline = Color(0xFF7D747F),
    outlineVariant = Color(0xFFCFC4D1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

private val BlackColors = darkColorScheme(
    primary = Color(0xFFD88AFF),
    onPrimary = Color(0xFF3D0055),
    primaryContainer = Color(0xFF591D75),
    onPrimaryContainer = Color(0xFFF1D5FF),
    secondary = Color(0xFFD0C7D4),
    onSecondary = Color(0xFF302E32),
    secondaryContainer = Color(0xFF454347),
    onSecondaryContainer = Color(0xFFECE7EF),
    tertiary = Color(0xFFD0CDD1),
    onTertiary = Color(0xFF302D31),
    tertiaryContainer = Color(0xFF474448),
    onTertiaryContainer = Color(0xFFECE8EC),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF101012),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF242426),
    onSurfaceVariant = Color(0xFFD1CDD3),
    outline = Color(0xFF969297),
    outlineVariant = Color(0xFF464347),
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
    chartGrid = Color(0xFF4A3A52),
    glow = LiftlyPurple.copy(alpha = 0.30f),
    ambientPrimary = Color(0xFF9A55C8),
    ambientSecondary = Color(0xFFB56B9B),
    glassSurface = Color(0xF21A151E),
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
    chartGrid = Color(0xFFDED5E1),
    glow = LiftlyPurple.copy(alpha = 0.14f),
    ambientPrimary = Color(0xFFE3C7F4),
    ambientSecondary = Color(0xFFF4D7E8),
    glassSurface = Color(0xE6FFFFFF),
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
    chartGrid = Color(0xFF303033),
    glow = Color.Transparent,
    ambientPrimary = Color.Transparent,
    ambientSecondary = Color.Transparent,
    glassSurface = Color(0xF21A1A1D),
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
