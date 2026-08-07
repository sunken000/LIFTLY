package com.liftly.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.min
import com.liftly.app.ui.components.NeonIcon
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.GradientActionButton

private const val STOPWATCH_REFRESH_MILLIS = 16L

/**
 * Timestamp-based stopwatch: refresh ticks only repaint the display and do not accumulate time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen() {
    var accumulatedMillis by rememberSaveable { mutableLongStateOf(0L) }
    var startedAtElapsedRealtime by rememberSaveable { mutableLongStateOf(0L) }
    var startedAtEpochMillis by rememberSaveable { mutableLongStateOf(0L) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var laps by rememberSaveable { mutableStateOf(longArrayOf()) }
    var displayedMillis by remember { mutableLongStateOf(accumulatedMillis) }

    fun currentElapsedMillis(): Long = calculateElapsedMillis(
        accumulatedMillis = accumulatedMillis,
        startedAtElapsedRealtime = startedAtElapsedRealtime,
        startedAtEpochMillis = startedAtEpochMillis,
        isRunning = isRunning,
        nowElapsedRealtime = SystemClock.elapsedRealtime(),
        nowEpochMillis = System.currentTimeMillis(),
    )

    LaunchedEffect(isRunning, accumulatedMillis, startedAtElapsedRealtime, startedAtEpochMillis) {
        if (!isRunning) {
            displayedMillis = accumulatedMillis
            return@LaunchedEffect
        }
        while (isActive) {
            displayedMillis = currentElapsedMillis()
            delay(STOPWATCH_REFRESH_MILLIS)
        }
    }

    fun startOrResume() {
        if (isRunning) return
        startedAtElapsedRealtime = SystemClock.elapsedRealtime()
        startedAtEpochMillis = System.currentTimeMillis()
        isRunning = true
    }

    fun pause() {
        if (!isRunning) return
        accumulatedMillis = currentElapsedMillis()
        displayedMillis = accumulatedMillis
        isRunning = false
        startedAtElapsedRealtime = 0L
        startedAtEpochMillis = 0L
    }

    fun reset() {
        accumulatedMillis = 0L
        displayedMillis = 0L
        startedAtElapsedRealtime = 0L
        startedAtEpochMillis = 0L
        isRunning = false
        laps = longArrayOf()
    }

    fun addLap() {
        if (!isRunning) return
        val elapsed = currentElapsedMillis()
        if (elapsed > 0L) laps = laps + elapsed
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text("Cronômetro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    NeonIcon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Cronômetro",
                        modifier = Modifier.padding(start = 16.dp),
                        selected = true,
                        intensity = 1.25f,
                        size = 34.dp,
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(Modifier.height(14.dp)) }
                item {
                    StopwatchDisplay(
                        elapsedMillis = displayedMillis,
                        isRunning = isRunning,
                    )
                }
                item {
                    StopwatchControls(
                        isRunning = isRunning,
                        hasElapsedTime = displayedMillis > 0L,
                        onStartOrResume = ::startOrResume,
                        onPause = ::pause,
                        onLap = ::addLap,
                        onReset = ::reset,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Voltas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (laps.isNotEmpty()) {
                            Text(
                                text = "${laps.size} registrada${if (laps.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (laps.isEmpty()) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                NeonIcon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    selected = true,
                                    intensity = 1.15f,
                                    size = 38.dp,
                                )
                                Text("Nenhuma volta registrada", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Com o cronômetro em andamento, toque em Volta para registrar uma parcial.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = laps.reversedArray().asList(),
                        key = { reversedIndex, _ -> laps.size - reversedIndex },
                    ) { reversedIndex, lapTotal ->
                        val originalIndex = laps.lastIndex - reversedIndex
                        val previousTotal = laps.getOrNull(originalIndex - 1) ?: 0L
                        LapRow(
                            number = originalIndex + 1,
                            intervalMillis = (lapTotal - previousTotal).coerceAtLeast(0L),
                            totalMillis = lapTotal,
                        )
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun StopwatchDisplay(elapsedMillis: Long, isRunning: Boolean) {
    val formatted = formatStopwatchTime(elapsedMillis)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (isRunning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isRunning) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = if (isRunning) "EM ANDAMENTO" else if (elapsedMillis > 0L) "PAUSADO" else "PRONTO",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Tempo ${stopwatchSpokenDescription(elapsedMillis)}"
                        stateDescription = if (isRunning) "Cronômetro em andamento" else if (elapsedMillis > 0L) "Cronômetro pausado" else "Cronômetro pronto"
                    },
                contentAlignment = Alignment.Center,
            ) {
                val preferredFontSize = when {
                    formatted.length > 8 && maxWidth < 340.dp -> 28.sp
                    formatted.length > 8 -> 36.sp
                    maxWidth < 300.dp -> 38.sp
                    else -> 48.sp
                }
                val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                // Monospace digits occupy roughly 0.62 em. Cap the requested size so the full
                // timestamp remains visible on narrow displays and with a large system font.
                val maximumFittingSp = maxWidth.value / (formatted.length * 0.64f * fontScale)
                val displayFontSize = min(preferredFontSize.value, maximumFittingSp)
                    .coerceAtLeast(12f)
                    .sp
                Text(
                    text = formatted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = displayFontSize,
                    lineHeight = displayFontSize * 1.15f,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            Text(
                text = if (isRunning) "Atualização contínua enquanto o app está aberto." else "Exibição em centésimos de segundo",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun StopwatchControls(
    isRunning: Boolean,
    hasElapsedTime: Boolean,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GradientActionButton(
            onClick = if (isRunning) onPause else onStartOrResume,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            onClickLabel = if (isRunning) "Pausar cronômetro" else if (hasElapsedTime) "Retomar cronômetro" else "Iniciar cronômetro",
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
            )
            Text(
                text = if (isRunning) "Pausar" else if (hasElapsedTime) "Retomar" else "Iniciar",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = onLap,
                enabled = isRunning,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Default.Flag, contentDescription = null)
                Text("Volta", Modifier.padding(start = 7.dp))
            }
            OutlinedButton(
                onClick = onReset,
                enabled = hasElapsedTime || isRunning,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Text("Zerar", Modifier.padding(start = 7.dp))
            }
        }
    }
}

@Composable
private fun LapRow(number: Int, intervalMillis: Long, totalMillis: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number.toString(), fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Volta $number", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Parcial ${formatStopwatchTime(intervalMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = formatStopwatchTime(totalMillis),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal fun calculateElapsedMillis(
    accumulatedMillis: Long,
    startedAtElapsedRealtime: Long,
    startedAtEpochMillis: Long,
    isRunning: Boolean,
    nowElapsedRealtime: Long,
    nowEpochMillis: Long,
): Long {
    if (!isRunning) return accumulatedMillis.coerceAtLeast(0L)
    val monotonicDelta = nowElapsedRealtime - startedAtElapsedRealtime
    val currentSegment = if (startedAtElapsedRealtime > 0L && monotonicDelta >= 0L) {
        monotonicDelta
    } else {
        // elapsedRealtime restarts after a device reboot; wall time is the best recovery available.
        (nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0L)
    }
    return (accumulatedMillis + currentSegment).coerceAtLeast(0L)
}

internal fun formatStopwatchTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val centiseconds = (safe / 10L) % 100L
    val seconds = (safe / 1_000L) % 60L
    val minutes = (safe / 60_000L) % 60L
    val hours = safe / 3_600_000L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%02d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, centiseconds)
    }
}

internal fun stopwatchSpokenDescription(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val hours = safe / 3_600_000L
    val minutes = (safe / 60_000L) % 60L
    val seconds = (safe / 1_000L) % 60L
    return buildList {
        if (hours > 0L) add("$hours hora${if (hours == 1L) "" else "s"}")
        if (minutes > 0L) add("$minutes minuto${if (minutes == 1L) "" else "s"}")
        add("$seconds segundo${if (seconds == 1L) "" else "s"}")
    }.joinToString(", ")
}
