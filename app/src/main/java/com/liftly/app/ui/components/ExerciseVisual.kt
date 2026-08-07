package com.liftly.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.ExerciseVisualKey
import com.liftly.app.data.ExerciseVisualResolver
import com.liftly.app.data.ExerciseVisualSpec
import com.liftly.app.R
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ExerciseVisualThumbnail(
    exercise: ExerciseEntity,
    modifier: Modifier = Modifier,
) {
    ExerciseVisual(
        exercise = exercise,
        modifier = modifier,
        detailed = false,
    )
}

@Composable
fun ExerciseVisualHero(
    exercise: ExerciseEntity,
    modifier: Modifier = Modifier,
) {
    ExerciseVisual(
        exercise = exercise,
        modifier = modifier,
        detailed = true,
    )
}

@Composable
private fun ExerciseVisual(
    exercise: ExerciseEntity,
    modifier: Modifier,
    detailed: Boolean,
) {
    val context = LocalContext.current
    val spec = remember(
        exercise.id,
        exercise.imageUri,
        exercise.isCustom,
        exercise.name,
        exercise.muscleGroup,
        exercise.movementType,
        exercise.category,
    ) { ExerciseVisualResolver.resolve(exercise) }
    val shape = if (detailed) RoundedCornerShape(24.dp) else RoundedCornerShape(16.dp)
    val visualModifier = modifier
        .clip(shape)
        .semantics { contentDescription = "Imagem de ${exercise.name}" }

    when (spec) {
        is ExerciseVisualSpec.LocalImage -> {
            val targetPixels = if (detailed) HERO_TARGET_PIXELS else THUMBNAIL_TARGET_PIXELS
            val bitmap by produceState<ImageBitmap?>(
                initialValue = null,
                key1 = spec.uri,
                key2 = targetPixels,
            ) {
                value = withContext(Dispatchers.IO) {
                    decodeLocalImage(context, spec.uri, targetPixels)?.asImageBitmap()
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Imagem de ${exercise.name}",
                    modifier = visualModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = if (detailed) ContentScale.Fit else ContentScale.Crop,
                    alignment = if (detailed) Alignment.Center else thumbnailAlignment(exercise, spec.fallbackKey),
                )
            } else {
                GeneratedExerciseVisual(exercise, spec.fallbackKey, visualModifier, detailed)
            }
        }

        is ExerciseVisualSpec.BundledOrFallback -> {
            val resourceId = remember(context, spec.drawableName) {
                drawableResourceId(context, spec.drawableName)
            }
            if (resourceId != 0) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(resourceId),
                    contentDescription = "Ilustração de ${exercise.name}",
                    modifier = visualModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = if (detailed) ContentScale.Fit else ContentScale.Crop,
                    alignment = if (detailed) Alignment.Center else thumbnailAlignment(exercise, spec.fallbackKey),
                )
            } else {
                GeneratedExerciseVisual(exercise, spec.fallbackKey, visualModifier, detailed)
            }
        }

        is ExerciseVisualSpec.GeneratedFallback -> {
            GeneratedExerciseVisual(exercise, spec.fallbackKey, visualModifier, detailed)
        }
    }
}

@Composable
private fun GeneratedExerciseVisual(
    exercise: ExerciseEntity,
    key: ExerciseVisualKey,
    modifier: Modifier,
    detailed: Boolean,
) {
    if (detailed) {
        AnatomyFallbackHero(exercise = exercise, key = key, modifier = modifier)
        return
    }
    val anatomy = anatomyVisualFor(exercise, key)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(anatomy.drawableRes),
            contentDescription = "Mapa anatômico de ${anatomy.label}",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = THUMBNAIL_ANATOMY_ZOOM,
                    scaleY = THUMBNAIL_ANATOMY_ZOOM,
                ),
            contentScale = ContentScale.Crop,
            alignment = thumbnailAlignment(exercise, key),
        )
    }
}

@Composable
private fun AnatomyFallbackHero(
    exercise: ExerciseEntity,
    key: ExerciseVisualKey,
    modifier: Modifier,
) {
    val anatomy = anatomyVisualFor(exercise, key)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(anatomy.drawableRes),
            contentDescription = "Mapa anatômico de ${anatomy.label}",
            modifier = Modifier.fillMaxSize().padding(bottom = 54.dp),
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Mapa muscular: ${anatomy.label}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Referência anatômica • não demonstra a técnica",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class AnatomyVisual(val drawableRes: Int, val label: String)

/**
 * The anatomy maps stay tall so the expanded visual can show the full body. In the square card
 * thumbnail we zoom into the relevant region, preserving aspect ratio without leaving a narrow
 * white strip surrounded by empty space.
 */
private fun thumbnailAlignment(exercise: ExerciseEntity, key: ExerciseVisualKey): Alignment {
    val muscle = Normalizer.normalize(exercise.muscleGroup, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)

    return when {
        listOf("panturrilha", "gastrocnem", "soleo").any(muscle::contains) -> Alignment.BottomCenter
        listOf("quadriceps", "adutor", "glute", "posterior", "isquiotib", "quadril")
            .any(muscle::contains) -> Alignment.Center
        listOf(
            "peito", "peitoral", "costas", "dorsal", "trapezio", "romboide", "lombar",
            "ombro", "deltoide", "manguito", "biceps", "triceps", "braco", "antebraco",
            "braquial", "core", "abdomen", "abdominal", "obliquo",
        ).any(muscle::contains) -> Alignment.TopCenter
        key == ExerciseVisualKey.SQUAT || key == ExerciseVisualKey.HIP_HINGE ||
            key == ExerciseVisualKey.SINGLE_LEG -> Alignment.Center
        key == ExerciseVisualKey.HORIZONTAL_PUSH || key == ExerciseVisualKey.VERTICAL_PUSH ||
            key == ExerciseVisualKey.HORIZONTAL_PULL || key == ExerciseVisualKey.VERTICAL_PULL ||
            key == ExerciseVisualKey.ARMS || key == ExerciseVisualKey.SHOULDERS ||
            key == ExerciseVisualKey.CORE -> Alignment.TopCenter
        else -> Alignment.Center
    }
}

/**
 * Selects an educational target-muscle map for every catalog entry. It deliberately does not
 * imply that a neutral anatomy pose is a technique demonstration.
 */
private fun anatomyVisualFor(exercise: ExerciseEntity, key: ExerciseVisualKey): AnatomyVisual {
    val muscle = Normalizer.normalize(exercise.muscleGroup, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)

    fun byMuscle(): AnatomyVisual? = when {
        listOf("panturrilha", "gastrocnem", "soleo").any(muscle::contains) ->
            AnatomyVisual(R.drawable.exercise_group_calves, "panturrilhas")
        listOf("peito", "peitoral").any(muscle::contains) ->
            AnatomyVisual(R.drawable.exercise_group_chest, "peito")
        listOf("costas", "dorsal", "trapezio", "romboide", "lombar").any(muscle::contains) ->
            AnatomyVisual(R.drawable.exercise_group_back, "costas")
        listOf("ombro", "deltoide", "manguito").any(muscle::contains) ->
            AnatomyVisual(R.drawable.exercise_group_shoulders, "ombros")
        listOf("biceps", "triceps", "braco", "antebraco", "braquial").any(muscle::contains) ->
            AnatomyVisual(R.drawable.exercise_group_arms, "braços")
        listOf("quadriceps", "adutor").any(muscle::contains) ->
            AnatomyVisual(R.drawable.exercise_group_quadriceps, "quadríceps")
        listOf("glute", "posterior", "isquiotib", "quadril").any(muscle::contains) ->
            AnatomyVisual(R.drawable.exercise_group_posterior_chain, "glúteos e posteriores")
        listOf("core", "abdomen", "abdominal", "obliquo").any(muscle::contains) ->
            AnatomyVisual(R.drawable.exercise_group_core, "core")
        else -> null
    }

    return byMuscle() ?: when (key) {
        ExerciseVisualKey.HORIZONTAL_PUSH -> AnatomyVisual(R.drawable.exercise_group_chest, "peito")
        ExerciseVisualKey.VERTICAL_PUSH,
        ExerciseVisualKey.SHOULDERS -> AnatomyVisual(R.drawable.exercise_group_shoulders, "ombros")
        ExerciseVisualKey.HORIZONTAL_PULL,
        ExerciseVisualKey.VERTICAL_PULL -> AnatomyVisual(R.drawable.exercise_group_back, "costas")
        ExerciseVisualKey.SQUAT,
        ExerciseVisualKey.SINGLE_LEG -> AnatomyVisual(R.drawable.exercise_group_quadriceps, "quadríceps")
        ExerciseVisualKey.HIP_HINGE -> AnatomyVisual(R.drawable.exercise_group_posterior_chain, "glúteos e posteriores")
        ExerciseVisualKey.ARMS -> AnatomyVisual(R.drawable.exercise_group_arms, "braços")
        ExerciseVisualKey.CORE -> AnatomyVisual(R.drawable.exercise_group_core, "core")
        else -> AnatomyVisual(R.drawable.exercise_anatomy_fallback, exercise.muscleGroup)
    }
}

@Suppress("DiscouragedApi")
private fun drawableResourceId(context: Context, drawableName: String): Int =
    context.resources.getIdentifier(drawableName, "drawable", context.packageName)

private fun decodeLocalImage(context: Context, rawUri: String, targetPixels: Int): Bitmap? = runCatching {
    val uri = Uri.parse(rawUri)
    require(uri.scheme !in setOf("http", "https")) { "Imagens remotas não são carregadas." }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(context, uri, targetPixels)
    } else {
        decodeWithBitmapFactory(context, uri, targetPixels)
    }
}.getOrNull()

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoder(context: Context, uri: Uri, targetPixels: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val width = info.size.width.coerceAtLeast(1)
        val height = info.size.height.coerceAtLeast(1)
        val largest = maxOf(width, height)
        if (largest > targetPixels) {
            val scale = targetPixels.toFloat() / largest
            decoder.setTargetSize(
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
            )
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

private fun decodeWithBitmapFactory(context: Context, uri: Uri, targetPixels: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > targetPixels * 2) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}

private const val THUMBNAIL_TARGET_PIXELS = 256
private const val HERO_TARGET_PIXELS = 1_024
private const val THUMBNAIL_ANATOMY_ZOOM = 1.35f
