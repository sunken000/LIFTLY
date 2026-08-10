package com.liftly.app.ui.components

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.liftly.app.ui.theme.liftlyColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Branded screen surface with a deliberately isolated animated layer.
 *
 * Only [AnimatedLiftlyBackdrop] observes the animation clock, so the screen content passed to this
 * composable is not recomposed on every frame. The palette is entirely theme-derived and therefore
 * remains legible in the purple, light and true-dark themes.
 */
@Composable
fun LiftlyBackground(
    modifier: Modifier = Modifier,
    showGlow: Boolean = true,
    customWallpaperUri: String? = null,
    wallpaperDimPercent: Int = 45,
    rewardWallpaperKey: String? = null,
    /** Optional normalized (-1..1) offset for scroll/drag driven parallax. */
    parallax: Offset = Offset.Zero,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.liftlyColors
    val ambientEnabled = showGlow &&
        (extended.ambientPrimary.alpha > 0.01f || extended.ambientSecondary.alpha > 0.01f)
    val wallpaper = customWallpaperUri?.takeIf(String::isNotBlank)
    val systemMotionScale = if (ambientEnabled) rememberSystemMotionScale() else 0f
    val backgroundModifier = if (wallpaper != null) {
        Modifier.background(colors.background)
    } else if (ambientEnabled) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(colors.background, colors.surface.copy(alpha = 0.92f)),
            ),
        )
    } else {
        // Transparent ambient tokens intentionally produce a true solid background (black theme).
        Modifier.background(colors.background)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier),
    ) {
        if (wallpaper != null) {
            PersistedWallpaper(
                uri = wallpaper,
                modifier = Modifier.matchParentSize(),
            )
            val overlay = if (colors.background.luminance() > 0.5f) Color.White else Color.Black
            Box(
                Modifier
                    .matchParentSize()
                    .background(overlay.copy(alpha = wallpaperDimPercent.coerceIn(20, 80) / 100f)),
            )
        }
        if (ambientEnabled) {
            if (systemMotionScale > 0f) {
                AnimatedLiftlyBackdrop(
                    primary = extended.ambientPrimary,
                    secondary = extended.ambientSecondary,
                    tertiary = colors.tertiary,
                    glow = extended.glow,
                    externalParallax = parallax,
                    motionIntensity = systemMotionScale.coerceIn(0.2f, 1f),
                    wallpaperStyle = rewardWallpaperKey,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                StaticLiftlyBackdrop(
                    primary = extended.ambientPrimary,
                    secondary = extended.ambientSecondary,
                    wallpaperStyle = rewardWallpaperKey,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        content()
    }
}

@Composable
private fun PersistedWallpaper(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeWallpaper(context, Uri.parse(uri)) }.getOrNull()
        }
    }
    bitmap?.let { loaded ->
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

private fun decodeWallpaper(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            if (longest > 2_048) {
                val scale = 2_048f / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) sample *= 2
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Moving aurora, layered fabric-like waves and dust; kept private so callers retain the API. */
@Composable
private fun AnimatedLiftlyBackdrop(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    glow: Color,
    externalParallax: Offset,
    motionIntensity: Float,
    wallpaperStyle: String?,
    modifier: Modifier = Modifier,
) {
    val sensorParallax = rememberDeviceParallax()
    val transition = rememberInfiniteTransition(label = "liftly-background")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 13_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora-drift",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-breathe",
    )

    Canvas(modifier = modifier) {
        val shortest = size.minDimension
        val angle = drift * (2f * PI.toFloat())
        val parallaxX = ((sensorParallax.x + externalParallax.x) * motionIntensity).coerceIn(-1.5f, 1.5f)
        val parallaxY = ((sensorParallax.y + externalParallax.y) * motionIntensity).coerceIn(-1.5f, 1.5f)

        fun movingCenter(
            baseX: Float,
            baseY: Float,
            travelX: Float,
            travelY: Float,
            phase: Float,
        ) = Offset(
            x = size.width * (baseX + sin(angle + phase) * travelX) + shortest * parallaxX * 0.055f,
            y = size.height * (baseY + cos(angle * 0.72f + phase) * travelY) + shortest * parallaxY * 0.048f,
        )

        // Large translucent radial brushes produce a soft glow without API-specific blur effects.
        val upper = movingCenter(0.82f, 0.12f, 0.13f, 0.07f, 0f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.09f),
                    glow.copy(alpha = 0.035f),
                    Color.Transparent,
                ),
                center = upper,
                radius = shortest * 0.63f * breathe,
            ),
            radius = shortest * 0.63f * breathe,
            center = upper,
        )

        val lower = movingCenter(0.13f, 0.84f, 0.10f, 0.08f, 2.1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondary.copy(alpha = 0.06f),
                    tertiary.copy(alpha = 0.025f),
                    Color.Transparent,
                ),
                center = lower,
                radius = shortest * 0.48f / breathe,
            ),
            radius = shortest * 0.48f / breathe,
            center = lower,
        )

        // Broad translucent ribbons echo flowing fabric, while parallel lines add neon depth.
        repeat(2) { band ->
            val phase = angle * (if (band == 0) 0.82f else -0.67f) + band * 2.35f
            val centerY = size.height * (if (band == 0) 0.19f else 0.76f) +
                shortest * parallaxY * (0.024f + band * 0.012f)
            val amplitude = shortest * (if (band == 0) 0.105f else 0.135f)
            val thickness = shortest * (if (band == 0) 0.115f else 0.145f)
            val parallaxBandX = shortest * parallaxX * (0.030f + band * 0.014f)
            val steps = 38

            fun waveY(fraction: Float): Float = centerY +
                sin(fraction * PI.toFloat() * 1.78f + phase) * amplitude +
                sin(fraction * PI.toFloat() * 3.34f - phase * 0.43f) * amplitude * 0.19f

            val ribbon = Path().apply {
                for (step in 0..steps) {
                    val fraction = -0.12f + 1.24f * step / steps
                    val x = size.width * fraction + parallaxBandX
                    val taper = 0.72f + 0.28f * sin((step.toFloat() / steps) * PI.toFloat())
                    val y = waveY(fraction) - thickness * taper * 0.5f
                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                }
                for (step in steps downTo 0) {
                    val fraction = -0.12f + 1.24f * step / steps
                    val x = size.width * fraction + parallaxBandX
                    val taper = 0.72f + 0.28f * sin((step.toFloat() / steps) * PI.toFloat())
                    lineTo(x, waveY(fraction) + thickness * taper * 0.5f)
                }
                close()
            }
            drawPath(
                path = ribbon,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        (if (band == 0) secondary else primary).copy(alpha = 0.034f),
                        glow.copy(alpha = 0.052f),
                        (if (band == 0) primary else secondary).copy(alpha = 0.044f),
                        Color.Transparent,
                    ),
                ),
            )

            repeat(9) { line ->
                val lineOffset = (line - 4) * thickness * 0.115f
                val filament = Path().apply {
                    for (step in 0..steps) {
                        val fraction = -0.12f + 1.24f * step / steps
                        val x = size.width * fraction + parallaxBandX
                        val y = waveY(fraction) + lineOffset
                        if (step == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                val distanceFromCenter = kotlin.math.abs(line - 4) / 4f
                drawPath(
                    path = filament,
                    color = (if ((line + band) % 2 == 0) primary else secondary).copy(
                        alpha = 0.085f - distanceFromCenter * 0.032f,
                    ),
                    style = Stroke(
                        width = (if (line == 4) 1.15f else 0.72f).dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        when (wallpaperStyle) {
            "wallpaper_grid" -> {
                val travel = (drift * shortest * 0.12f) % (shortest * 0.12f)
                val spacing = shortest * 0.12f
                repeat(13) { index ->
                    val x = index * spacing - travel
                    drawLine(primary.copy(alpha = 0.085f), Offset(x, 0f), Offset(x, size.height), 0.75.dp.toPx())
                }
                repeat(20) { index ->
                    val y = index * spacing - travel
                    drawLine(secondary.copy(alpha = 0.065f), Offset(0f, y), Offset(size.width, y), 0.75.dp.toPx())
                }
            }
            "wallpaper_smoke" -> repeat(4) { index ->
                val center = movingCenter(
                    baseX = 0.15f + index * 0.24f,
                    baseY = if (index % 2 == 0) 0.32f else 0.68f,
                    travelX = 0.11f,
                    travelY = 0.09f,
                    phase = index * 1.4f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.07f), secondary.copy(alpha = 0.035f), Color.Transparent),
                        center = center,
                        radius = shortest * 0.34f,
                    ),
                    center = center,
                    radius = shortest * 0.34f,
                )
            }
            "wallpaper_steel" -> repeat(15) { index ->
                val offset = index * shortest * 0.11f - (drift * shortest * 0.20f)
                drawLine(
                    color = (if (index % 3 == 0) primary else secondary).copy(alpha = 0.055f),
                    start = Offset(offset - shortest, size.height),
                    end = Offset(offset + shortest, 0f),
                    strokeWidth = if (index % 3 == 0) 1.2.dp.toPx() else 0.65.dp.toPx(),
                )
            }
        }

        // Deterministic particles: no allocations or random state during animation frames.
        repeat(14) { index ->
            val seed = index * 0.6180339f
            val xFraction = (seed + drift * (0.018f + (index % 3) * 0.006f)) % 1f
            val yFraction = (index * 0.173f + sin(angle * 0.45f + index) * 0.016f + 1f) % 1f
            val alpha = 0.030f + (index % 4) * 0.007f
            drawCircle(
                color = if (index % 3 == 0) secondary.copy(alpha = alpha) else primary.copy(alpha = alpha),
                radius = (0.8f + index % 3 * 0.45f).dp.toPx(),
                center = Offset(
                    size.width * xFraction + shortest * parallaxX * (0.012f + index % 3 * 0.009f),
                    size.height * yFraction + shortest * parallaxY * (0.010f + index % 4 * 0.006f),
                ),
            )
        }
    }
}

@Composable
private fun StaticLiftlyBackdrop(
    primary: Color,
    secondary: Color,
    wallpaperStyle: String?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val shortest = size.minDimension
        val upper = Offset(size.width * 0.86f, size.height * 0.10f)
        val lower = Offset(size.width * 0.10f, size.height * 0.88f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(primary.copy(alpha = 0.07f), Color.Transparent),
                center = upper,
                radius = shortest * 0.60f,
            ),
            center = upper,
            radius = shortest * 0.60f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(secondary.copy(alpha = 0.05f), Color.Transparent),
                center = lower,
                radius = shortest * 0.46f,
            ),
            center = lower,
            radius = shortest * 0.46f,
        )
        if (wallpaperStyle == "wallpaper_grid") {
            val spacing = shortest * 0.12f
            repeat(13) { index ->
                val x = index * spacing
                drawLine(primary.copy(alpha = 0.075f), Offset(x, 0f), Offset(x, size.height), 0.75.dp.toPx())
            }
            repeat(20) { index ->
                val y = index * spacing
                drawLine(secondary.copy(alpha = 0.055f), Offset(0f, y), Offset(size.width, y), 0.75.dp.toPx())
            }
        }
    }
}

/** Observes the public animator duration scale; zero means the user disabled animations. */
@Composable
private fun rememberSystemMotionScale(): Float {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun readScale(): Float = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f).coerceAtLeast(0f)

    var scale by remember(resolver) { mutableFloatStateOf(readScale()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readScale()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val registered = runCatching {
            resolver.registerContentObserver(uri, false, observer)
        }.isSuccess
        onDispose {
            if (registered) runCatching { resolver.unregisterContentObserver(observer) }
        }
    }
    return scale
}

/**
 * Rotation-vector parallax with lifecycle-safe registration. Both supported sensors are permission
 * free. The auto animation above remains the fallback on devices without a rotation sensor.
 */
@Composable
private fun rememberDeviceParallax(): Offset {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val sensor = remember(manager) {
        manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    val listener = remember(sensor) {
        object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val orientation = FloatArray(3)
            private var baselineRoll: Float? = null
            private var baselinePitch: Float? = null

            fun resetBaseline() {
                baselineRoll = null
                baselinePitch = null
            }

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val roll = orientation[2]
                val pitch = orientation[1]
                val originRoll = baselineRoll ?: roll.also { baselineRoll = it }
                val originPitch = baselinePitch ?: pitch.also { baselinePitch = it }
                val rollDelta = kotlin.math.atan2(sin(roll - originRoll), cos(roll - originRoll))
                val pitchDelta = kotlin.math.atan2(sin(pitch - originPitch), cos(pitch - originPitch))
                val targetX = (rollDelta / 0.38f).coerceIn(-1f, 1f)
                val targetY = (-pitchDelta / 0.38f).coerceIn(-1f, 1f)
                val nextX = tiltX + (targetX - tiltX) * 0.16f
                val nextY = tiltY + (targetY - tiltY) * 0.16f
                if (kotlin.math.abs(nextX - tiltX) > 0.002f) tiltX = nextX
                if (kotlin.math.abs(nextY - tiltY) > 0.002f) tiltY = nextY
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
    }

    DisposableEffect(lifecycleOwner, manager, sensor, listener) {
        var registered = false
        fun register() {
            if (!registered && manager != null && sensor != null) {
                listener.resetBaseline()
                tiltX = 0f
                tiltY = 0f
                registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        fun unregister() {
            if (registered) manager?.unregisterListener(listener)
            registered = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose {
            unregister()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return Offset(tiltX, tiltY)
}

/**
 * Theme-aware vector icon with a soft neon halo.
 *
 * It intentionally installs no pointer handler, so click targets and ripple behaviour remain owned
 * by the parent (for example, an IconButton or NavigationBarItem).
 */
@Composable
fun NeonIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    intensity: Float = 1f,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    tint: Color = Color.Unspecified,
) {
    val resolvedTint = when {
        tint != Color.Unspecified -> tint
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val glowColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val safeIntensity = intensity.coerceIn(0f, 2f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (safeIntensity > 0f && selected) {
            Canvas(Modifier.matchParentSize()) {
                val center = this.center
                val radius = this.size.minDimension * 0.5f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.16f * safeIntensity.coerceAtMost(1f)),
                            glowColor.copy(alpha = 0.04f * safeIntensity.coerceAtMost(1.5f)),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier.size(size * 0.78f),
        )
    }
}

/** Translucent premium surface with a theme-aware gradient edge. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    elevation: androidx.compose.ui.unit.Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.liftlyColors
    val resolvedContainer = if (containerColor == Color.Unspecified) extended.glassSurface else containerColor
    val derivedContentColor = contentColorFor(resolvedContainer)
    val resolvedContentColor = when {
        contentColor != Color.Unspecified -> contentColor
        derivedContentColor != Color.Unspecified -> derivedContentColor
        else -> colors.onSurface
    }
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            colors.primary.copy(alpha = 0.18f),
            colors.secondary.copy(alpha = 0.08f),
            colors.outlineVariant.copy(alpha = 0.44f),
        ),
    )

    Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, clip = false)
            .background(borderBrush, shape)
            .padding(1.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = resolvedContainer,
            contentColor = resolvedContentColor,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}

/** Clickable glass surface with a restrained press scale and complete button semantics. */
@Composable
fun InteractiveGlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    elevation: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.99f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 180, easing = FastOutSlowInEasing),
        label = "glass-card-press",
    )
    val accessibility = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    GlassCard(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.55f
            }
            .then(accessibility)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        contentPadding = contentPadding,
        elevation = elevation,
        content = content,
    )
}

/** High-emphasis gradient action with generic Row content for text and/or icons. */
@Composable
fun GradientActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 80 else 170, easing = FastOutSlowInEasing),
        label = "gradient-action-press",
    )
    val accessibility = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.48f
            }
            .shadow(if (enabled) 2.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(colors.primary, colors.tertiary),
                ),
            )
            .then(accessibility)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onPrimary) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (subtitle.isNullOrBlank()) 30.dp else 46.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

enum class StatusTone { INFO, SUCCESS, WARNING, ERROR }

private data class StatusPalette(
    val foreground: Color,
    val container: Color,
    val icon: ImageVector,
)

@Composable
private fun statusPalette(tone: StatusTone): StatusPalette {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.liftlyColors
    return when (tone) {
        StatusTone.INFO -> StatusPalette(
            foreground = colors.onPrimaryContainer,
            container = colors.primaryContainer,
            icon = Icons.Rounded.Info,
        )
        StatusTone.SUCCESS -> StatusPalette(
            foreground = extended.onSuccessContainer,
            container = extended.successContainer,
            icon = Icons.Rounded.CheckCircle,
        )
        StatusTone.WARNING -> StatusPalette(
            foreground = extended.onWarningContainer,
            container = extended.warningContainer,
            icon = Icons.Rounded.WarningAmber,
        )
        StatusTone.ERROR -> StatusPalette(
            foreground = colors.onErrorContainer,
            container = colors.errorContainer,
            icon = Icons.Rounded.ErrorOutline,
        )
    }
}

/** Inline feedback for loading results, validation, success and errors. */
@Composable
fun StatusCard(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: StatusTone = StatusTone.INFO,
    icon: ImageVector? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val palette = statusPalette(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.container,
        contentColor = palette.foreground,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon ?: palette.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!title.isNullOrBlank()) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                }
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dispensar")
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/** Helpful empty state that always explains the next useful action. */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.FitnessCenter,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(22.dp))
                FilledTonalButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    accent: Color = Color.Unspecified,
) {
    val resolvedAccent = if (accent == Color.Unspecified) MaterialTheme.colorScheme.primary else accent
    Card(
        modifier = modifier.heightIn(min = 126.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (icon != null) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = resolvedAccent.copy(alpha = 0.14f),
                        contentColor = resolvedAccent,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supportingText.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = resolvedAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

data class ChartPoint(
    val label: String,
    val value: Float,
)

/** Lightweight, dependency-free and tappable line chart for progress and body-weight data. */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Unspecified,
    valueFormatter: (Float) -> String = { value ->
        if (value % 1f == 0f) value.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", value)
    },
    emptyLabel: String = "Sem dados suficientes",
) {
    val resolvedLineColor = if (lineColor == Color.Unspecified) MaterialTheme.colorScheme.primary else lineColor
    val gridColor = MaterialTheme.liftlyColors.chartGrid
    val pointFillColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val horizontalPadding = with(density) { 20.dp.toPx() }
    var selectedIndex by remember(points) { mutableIntStateOf(-1) }

    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val minimum = points.minOf { it.value }
    val maximum = points.maxOf { it.value }
    val range = (maximum - minimum).takeIf { it > 0f } ?: 1f
    val selected = points.getOrNull(selectedIndex)
    val accessibilitySummary = points.joinToString { "${it.label}: ${valueFormatter(it.value)}" }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .semantics { contentDescription = accessibilitySummary },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val available = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
                            val fraction = ((offset.x - horizontalPadding) / available).coerceIn(0f, 1f)
                            selectedIndex = if (points.size == 1) 0 else {
                                (fraction * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex)
                            }
                        }
                    },
            ) {
                val left = 20.dp.toPx()
                val right = size.width - 20.dp.toPx()
                val top = 34.dp.toPx()
                val bottom = size.height - 12.dp.toPx()
                val chartWidth = (right - left).coerceAtLeast(1f)
                val chartHeight = (bottom - top).coerceAtLeast(1f)

                repeat(4) { index ->
                    val y = top + (chartHeight * index / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                fun pointOffset(index: Int): Offset {
                    val x = if (points.size == 1) {
                        left + chartWidth / 2f
                    } else {
                        left + chartWidth * index / points.lastIndex.toFloat()
                    }
                    val yFraction = (points[index].value - minimum) / range
                    return Offset(x, bottom - chartHeight * yFraction)
                }

                val area = Path()
                val line = Path()
                points.indices.forEach { index ->
                    val offset = pointOffset(index)
                    if (index == 0) {
                        line.moveTo(offset.x, offset.y)
                        area.moveTo(offset.x, bottom)
                        area.lineTo(offset.x, offset.y)
                    } else {
                        line.lineTo(offset.x, offset.y)
                        area.lineTo(offset.x, offset.y)
                    }
                }
                area.lineTo(pointOffset(points.lastIndex).x, bottom)
                area.close()

                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(resolvedLineColor.copy(alpha = 0.28f), Color.Transparent),
                        startY = top,
                        endY = bottom,
                    ),
                )
                drawPath(
                    path = line,
                    color = resolvedLineColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                points.indices.forEach { index ->
                    val point = pointOffset(index)
                    drawCircle(
                        color = if (index == selectedIndex) pointFillColor else resolvedLineColor,
                        radius = if (index == selectedIndex) 6.dp.toPx() else 3.5.dp.toPx(),
                        center = point,
                    )
                    if (index == selectedIndex) {
                        drawCircle(
                            color = resolvedLineColor,
                            radius = 6.dp.toPx(),
                            center = point,
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                    }
                }
            }

            if (selected != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = "${selected.label}  •  ${valueFormatter(selected.value)}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (points.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = points.first().label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = points.last().label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
