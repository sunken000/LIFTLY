package com.liftly.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftly.app.data.ExerciseEntity
import com.liftly.app.domain.ExerciseGuideResolver
import com.liftly.app.domain.ExerciseMovementFamily
import com.liftly.app.domain.ExerciseSubstitution
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    exercise: ExerciseEntity,
    alternatives: List<ExerciseSubstitution>,
    onDismiss: () -> Unit,
    onChooseAlternative: ((ExerciseEntity) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val guide = ExerciseGuideResolver.resolve(exercise)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${guide.movementFamily.label} • ${exercise.equipment}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ExerciseTechniqueAnimation(
                    family = guide.movementFamily,
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                )
            }

            if (exercise.imageUri != null) {
                item {
                    ExerciseVisualHero(
                        exercise = exercise,
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                    )
                }
            }

            item {
                GuideSection(title = "Músculos trabalhados") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            AssistChip(
                                onClick = {},
                                label = { Text("Principal • ${guide.primaryMuscle}") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.FitnessCenter,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                        items(guide.secondaryMuscles, key = { "secondary-$it" }) { muscle ->
                            AssistChip(onClick = {}, label = { Text(muscle) })
                        }
                    }
                }
            }

            item {
                GuideSection(title = "Como executar") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        guide.steps.forEachIndexed { index, step ->
                            NumberedGuideItem(number = index + 1, text = step)
                        }
                    }
                }
            }

            item {
                GuideMessageCard(
                    title = "Erros comuns",
                    lines = guide.commonMistakes,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            item {
                GuideMessageCard(
                    title = "Dicas de postura",
                    lines = guide.postureTips,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            if (alternatives.isNotEmpty()) {
                item {
                    GuideSection(title = "Alternativas compatíveis") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            alternatives.take(5).forEach { suggestion ->
                                OutlinedCard(
                                    onClick = { onChooseAlternative?.invoke(suggestion.exercise) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = onChooseAlternative != null,
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.SwapHoriz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                suggestion.exercise.name,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "${suggestion.exercise.equipment} • ${suggestion.reasons.firstOrNull { it.points > 0 }?.label.orEmpty()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (onEdit != null || onDelete != null) {
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        onEdit?.let {
                            TextButton(onClick = it) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Editar")
                            }
                        }
                        onDelete?.let {
                            TextButton(onClick = it) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Excluir")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun NumberedGuideItem(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GuideMessageCard(
    title: String,
    lines: List<String>,
    containerColor: Color,
    contentColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text("•", fontWeight = FontWeight.Bold)
                    Text(line, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Small vector motion study: no remote media, tracking or copyrighted footage. */
@Composable
fun ExerciseTechniqueAnimation(
    family: ExerciseMovementFamily,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "exercise-motion")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "movement-phase",
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val figure = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .semantics { contentDescription = "Animação demonstrativa: ${family.label}" },
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp)) {
            drawMotionGrid(guide)
            drawExercisePose(family, progress, figure, primary, secondary)
        }
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ) {
            Text(
                family.label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Demonstração simplificada do padrão de movimento",
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawMotionGrid(color: Color) {
    val gap = size.width / 7f
    for (index in 1..6) {
        drawLine(
            color = color.copy(alpha = 0.22f),
            start = Offset(gap * index, 0f),
            end = Offset(gap * index, size.height),
            strokeWidth = 1f,
        )
    }
    drawLine(
        color = color.copy(alpha = 0.5f),
        start = Offset(size.width * 0.12f, size.height * 0.88f),
        end = Offset(size.width * 0.88f, size.height * 0.88f),
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawExercisePose(
    family: ExerciseMovementFamily,
    progress: Float,
    figureColor: Color,
    accent: Color,
    secondary: Color,
) {
    fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
    val pulse = sin(progress * Math.PI).toFloat()
    val jump = if (family == ExerciseMovementFamily.PLYOMETRIC) -0.18f * pulse else 0f
    val squat = when (family) {
        ExerciseMovementFamily.SQUAT,
        ExerciseMovementFamily.FULL_BODY,
        -> 0.13f * progress
        ExerciseMovementFamily.PLYOMETRIC -> 0.06f * (1f - pulse)
        else -> 0f
    }
    val hip = point(0.5f, 0.58f + squat + jump)
    val torsoLean = when (family) {
        ExerciseMovementFamily.HIP_HINGE -> 0.20f * progress
        ExerciseMovementFamily.CORE -> 0.08f * pulse
        ExerciseMovementFamily.CARDIO -> 0.05f
        else -> 0f
    }
    val shoulder = point(0.5f + torsoLean, 0.34f + squat + jump)
    val head = point(shoulder.x / size.width + torsoLean * 0.18f, shoulder.y / size.height - 0.11f)
    val leftShoulder = shoulder + Offset(-size.width * 0.055f, 0f)
    val rightShoulder = shoulder + Offset(size.width * 0.055f, 0f)

    val (leftHand, rightHand) = armTargets(family, progress, shoulder, size)
    val leftElbow = Offset((leftShoulder.x + leftHand.x) / 2f - size.width * 0.035f, (leftShoulder.y + leftHand.y) / 2f)
    val rightElbow = Offset((rightShoulder.x + rightHand.x) / 2f + size.width * 0.035f, (rightShoulder.y + rightHand.y) / 2f)

    val legSwing = if (family == ExerciseMovementFamily.CARDIO) 0.09f * (progress * 2f - 1f) else 0f
    val singleLegLift = if (family == ExerciseMovementFamily.SINGLE_LEG) 0.13f * progress else 0f
    val leftKnee = point(0.43f + squat * 0.35f + legSwing, 0.74f + squat * 0.45f + jump)
    val rightKnee = point(0.57f - squat * 0.35f - legSwing, 0.74f + squat * 0.45f + jump - singleLegLift)
    val leftFoot = point(0.37f - legSwing, 0.88f + jump)
    val rightFoot = point(0.63f + legSwing, 0.88f + jump - singleLegLift * 0.25f)

    val limbWidth = size.minDimension * 0.035f
    fun limb(start: Offset, end: Offset, color: Color = figureColor) {
        drawLine(color, start, end, limbWidth, StrokeCap.Round)
    }

    drawCircle(accent.copy(alpha = 0.13f + pulse * 0.1f), size.minDimension * (0.31f + pulse * 0.025f), hip)
    limb(hip, shoulder)
    limb(leftShoulder, leftElbow)
    limb(leftElbow, leftHand)
    limb(rightShoulder, rightElbow)
    limb(rightElbow, rightHand)
    limb(hip, leftKnee)
    limb(leftKnee, leftFoot)
    limb(hip, rightKnee)
    limb(rightKnee, rightFoot)
    drawCircle(figureColor, size.minDimension * 0.047f, head)
    drawCircle(accent, size.minDimension * 0.024f, leftHand)
    drawCircle(accent, size.minDimension * 0.024f, rightHand)
    drawCircle(secondary, size.minDimension * 0.018f, leftKnee)
    drawCircle(secondary, size.minDimension * 0.018f, rightKnee)

    when (family) {
        ExerciseMovementFamily.HORIZONTAL_PUSH,
        ExerciseMovementFamily.HORIZONTAL_PULL,
        ExerciseMovementFamily.SQUAT,
        ExerciseMovementFamily.OLYMPIC_LIFT,
        ExerciseMovementFamily.GENERAL_STRENGTH,
        -> drawLine(accent, leftHand, rightHand, limbWidth * 0.75f, StrokeCap.Round)
        else -> Unit
    }
}

private fun armTargets(
    family: ExerciseMovementFamily,
    progress: Float,
    shoulder: Offset,
    canvasSize: Size,
): Pair<Offset, Offset> {
    fun point(x: Float, y: Float) = Offset(canvasSize.width * x, canvasSize.height * y)
    val shoulderY = shoulder.y / canvasSize.height
    return when (family) {
        ExerciseMovementFamily.HORIZONTAL_PUSH ->
            point(0.28f + 0.13f * progress, shoulderY + 0.05f - 0.08f * progress) to
                point(0.72f - 0.13f * progress, shoulderY + 0.05f - 0.08f * progress)
        ExerciseMovementFamily.HORIZONTAL_PULL ->
            point(0.40f - 0.13f * progress, shoulderY + 0.03f) to
                point(0.60f + 0.13f * progress, shoulderY + 0.03f)
        ExerciseMovementFamily.VERTICAL_PUSH,
        ExerciseMovementFamily.OLYMPIC_LIFT,
        ExerciseMovementFamily.FULL_BODY,
        -> point(0.40f, shoulderY + 0.06f - 0.24f * progress) to
            point(0.60f, shoulderY + 0.06f - 0.24f * progress)
        ExerciseMovementFamily.VERTICAL_PULL ->
            point(0.38f, shoulderY - 0.24f + 0.2f * progress) to
                point(0.62f, shoulderY - 0.24f + 0.2f * progress)
        ExerciseMovementFamily.ARMS ->
            point(0.38f, shoulderY + 0.28f - 0.19f * progress) to
                point(0.62f, shoulderY + 0.28f - 0.19f * progress)
        ExerciseMovementFamily.SHOULDERS,
        ExerciseMovementFamily.MOBILITY,
        -> point(0.30f, shoulderY + 0.22f - 0.22f * progress) to
            point(0.70f, shoulderY + 0.22f - 0.22f * progress)
        ExerciseMovementFamily.CARDIO ->
            point(0.36f + 0.08f * progress, shoulderY + 0.22f) to
                point(0.64f - 0.08f * progress, shoulderY + 0.05f)
        else -> point(0.35f, shoulderY + 0.21f) to point(0.65f, shoulderY + 0.21f)
    }
}
