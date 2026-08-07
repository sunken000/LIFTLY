package com.liftly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liftly.app.ui.theme.LiftlyCustomPalette
import com.liftly.app.ui.theme.PaletteColorCodec
import com.liftly.app.ui.theme.defaultCustomPalette
import java.util.Locale
import kotlin.math.roundToInt

private data class PalettePreset(
    val name: String,
    val palette: LiftlyCustomPalette,
)

private enum class PaletteColorTarget(val label: String) {
    Primary("Destaque principal"),
    Secondary("Destaque secundário"),
    Background("Fundo"),
    Surface("Cartões e superfícies"),
    Text("Texto"),
}

private val quickColors = listOf(
    "#000000", "#FFFFFF", "#202124", "#6C3BF5", "#B45CFF", "#E6A6FF",
    "#FF4F9A", "#FF7043", "#FFC857", "#51D88A", "#35D0BA", "#45B6FE",
)

private val palettePresets = listOf(
    PalettePreset(
        "Roxo Liftly",
        LiftlyCustomPalette(
            primary = "#BCA0E8",
            secondary = "#B9B0C0",
            background = "#0D0B10",
            surface = "#171419",
            text = "#F5F1F8",
        ),
    ),
    PalettePreset(
        "Preto OLED",
        LiftlyCustomPalette(
            primary = "#D88AFF",
            secondary = "#B9A8FF",
            background = "#000000",
            surface = "#101012",
            text = "#F5F5F7",
        ),
    ),
    PalettePreset(
        "Oceano",
        LiftlyCustomPalette(
            primary = "#55D6FF",
            secondary = "#55FFD1",
            background = "#03141E",
            surface = "#0B2633",
            text = "#F0FBFF",
        ),
    ),
    PalettePreset(
        "Esmeralda",
        LiftlyCustomPalette(
            primary = "#54E39A",
            secondary = "#A5E65A",
            background = "#04150D",
            surface = "#10281C",
            text = "#F1FFF7",
        ),
    ),
    PalettePreset(
        "Claro elegante",
        LiftlyCustomPalette(
            primary = "#71319D",
            secondary = "#A24E7D",
            background = "#FAF7FC",
            surface = "#FFFFFF",
            text = "#211D23",
        ),
    ),
)

/**
 * Complete five-color editor. [onApply] always receives normalized, opaque #RRGGBB values.
 * Apply and restore close the dialog after invoking their respective callbacks.
 */
@Composable
fun PaletteEditorDialog(
    themeMode: String,
    currentPalette: LiftlyCustomPalette,
    onApply: (LiftlyCustomPalette) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = currentPalette.normalizedOrNull() ?: defaultCustomPalette(themeMode)
    var primary by rememberSaveable(initial.primary) { mutableStateOf(initial.primary) }
    var secondary by rememberSaveable(initial.secondary) { mutableStateOf(initial.secondary) }
    var background by rememberSaveable(initial.background) { mutableStateOf(initial.background) }
    var surface by rememberSaveable(initial.surface) { mutableStateOf(initial.surface) }
    var text by rememberSaveable(initial.text) { mutableStateOf(initial.text) }
    var editingTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    val editingTarget = editingTargetName?.let { savedName ->
        PaletteColorTarget.entries.firstOrNull { it.name == savedName }
    }

    val draft = LiftlyCustomPalette(
        enabled = true,
        primary = primary,
        secondary = secondary,
        background = background,
        surface = surface,
        text = text,
    )
    val normalized = draft.normalizedOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 760.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Cores da interface",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Personalize destaques, fundo, cartões e textos. O Liftly corrige automaticamente combinações ilegíveis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    palettePresets.forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                primary = preset.palette.primary
                                secondary = preset.palette.secondary
                                background = preset.palette.background
                                surface = preset.palette.surface
                                text = preset.palette.text
                            },
                        ) { Text(preset.name) }
                    }
                }

                PaletteHexField(
                    label = PaletteColorTarget.Primary.label,
                    value = primary,
                    onValueChange = { primary = it },
                    onChooseColor = { editingTargetName = PaletteColorTarget.Primary.name },
                )
                PaletteHexField(
                    label = PaletteColorTarget.Secondary.label,
                    value = secondary,
                    onValueChange = { secondary = it },
                    onChooseColor = { editingTargetName = PaletteColorTarget.Secondary.name },
                )
                PaletteHexField(
                    label = PaletteColorTarget.Background.label,
                    value = background,
                    onValueChange = { background = it },
                    onChooseColor = { editingTargetName = PaletteColorTarget.Background.name },
                )
                PaletteHexField(
                    label = PaletteColorTarget.Surface.label,
                    value = surface,
                    onValueChange = { surface = it },
                    onChooseColor = { editingTargetName = PaletteColorTarget.Surface.name },
                )
                PaletteHexField(
                    label = PaletteColorTarget.Text.label,
                    value = text,
                    onValueChange = { text = it },
                    onChooseColor = { editingTargetName = PaletteColorTarget.Text.name },
                )

                Text(
                    text = "Prévia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                PalettePreview(normalized)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onRestore()
                            onDismiss()
                        },
                    ) { Text("Restaurar tema") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Button(
                            enabled = normalized != null,
                            onClick = {
                                normalized?.let(onApply)
                                onDismiss()
                            },
                        ) { Text("Aplicar") }
                    }
                }
            }
        }
    }

    editingTarget?.let { target ->
        val currentValue = when (target) {
            PaletteColorTarget.Primary -> primary
            PaletteColorTarget.Secondary -> secondary
            PaletteColorTarget.Background -> background
            PaletteColorTarget.Surface -> surface
            PaletteColorTarget.Text -> text
        }
        ColorPickerDialog(
            label = target.label,
            initialHex = currentValue,
            onConfirm = { selectedHex ->
                when (target) {
                    PaletteColorTarget.Primary -> primary = selectedHex
                    PaletteColorTarget.Secondary -> secondary = selectedHex
                    PaletteColorTarget.Background -> background = selectedHex
                    PaletteColorTarget.Surface -> surface = selectedHex
                    PaletteColorTarget.Text -> text = selectedHex
                }
                editingTargetName = null
            },
            onDismiss = { editingTargetName = null },
        )
    }
}

@Composable
private fun PaletteHexField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onChooseColor: () -> Unit,
) {
    val valid = PaletteColorCodec.normalize(value) != null
    val swatch = PaletteColorCodec.parse(value)?.let { Color(it.toInt()) }
        ?: MaterialTheme.colorScheme.surfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedButton(
                onClick = onChooseColor,
                modifier = Modifier
                    .weight(1.25f)
                    .height(56.dp)
                    .semantics {
                        contentDescription = "Escolher cor para $label"
                        role = Role.Button
                    },
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(swatch, RoundedCornerShape(7.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text("Escolher cor", maxLines = 1)
            }
            OutlinedTextField(
                value = value,
                onValueChange = { candidate ->
                    val cleaned = candidate
                        .trim()
                        .uppercase(Locale.ROOT)
                        .filterIndexed { index, char ->
                            char.isDigit() || char in 'A'..'F' || (index == 0 && char == '#')
                        }
                        .take(7)
                    onValueChange(cleaned)
                },
                modifier = Modifier.weight(1f),
                label = { Text("Hex") },
                placeholder = { Text("#BD5CFF") },
                supportingText = if (valid) null else ({ Text("Use #RRGGBB") }),
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
        }
    }
}

@Composable
private fun ColorPickerDialog(
    label: String,
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialArgb = PaletteColorCodec.parse(initialHex)
        ?: requireNotNull(PaletteColorCodec.parse("#BD5CFF"))
    fun channel(shift: Int): Int = ((initialArgb shr shift) and 0xFF).toInt()

    var red by rememberSaveable(label, initialHex) { mutableStateOf(channel(16)) }
    var green by rememberSaveable(label, initialHex) { mutableStateOf(channel(8)) }
    var blue by rememberSaveable(label, initialHex) { mutableStateOf(channel(0)) }

    val selectedArgb = 0xFF000000L or
        (red.toLong() shl 16) or
        (green.toLong() shl 8) or
        blue.toLong()
    val selectedHex = PaletteColorCodec.format(selectedArgb)
    val selectedColor = Color(selectedArgb.toInt())
    val previewTextColor = Color(PaletteColorCodec.readableForeground(selectedArgb).toInt())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 12.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Escolher cor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .background(selectedColor, RoundedCornerShape(20.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(20.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = selectedHex,
                            color = previewTextColor,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = "Cores rápidas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        quickColors.forEach { hex ->
                            val argb = requireNotNull(PaletteColorCodec.parse(hex))
                            val isSelected = selectedHex == hex
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(argb.toInt()), RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .clickable {
                                        red = ((argb shr 16) and 0xFF).toInt()
                                        green = ((argb shr 8) and 0xFF).toInt()
                                        blue = (argb and 0xFF).toInt()
                                    }
                                    .semantics {
                                        contentDescription = "Selecionar cor rápida $hex"
                                        role = Role.Button
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        color = Color(PaletteColorCodec.readableForeground(argb).toInt()),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Ajuste fino (RGB)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ColorChannelSlider("Vermelho", red) { red = it }
                    ColorChannelSlider("Verde", green) { green = it }
                    ColorChannelSlider("Azul", blue) { blue = it }
                    Spacer(Modifier.height(2.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selectedHex) }) { Text("Confirmar") }
                }
            }
        }
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = value.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            steps = 254,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PalettePreview(palette: LiftlyCustomPalette?) {
    if (palette == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                "Complete as cinco cores para visualizar.",
                modifier = Modifier.padding(18.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        return
    }

    val primaryLong = requireNotNull(PaletteColorCodec.parse(palette.primary))
    val secondaryLong = requireNotNull(PaletteColorCodec.parse(palette.secondary))
    val backgroundLong = requireNotNull(PaletteColorCodec.parse(palette.background))
    val surfaceLong = requireNotNull(PaletteColorCodec.parse(palette.surface))
    val preferredTextLong = requireNotNull(PaletteColorCodec.parse(palette.text))
    val backgroundTextLong = PaletteColorCodec.readableForeground(backgroundLong, preferredTextLong)
    val surfaceTextLong = PaletteColorCodec.readableForeground(surfaceLong, preferredTextLong)
    val textWasAdjusted = backgroundTextLong != preferredTextLong || surfaceTextLong != preferredTextLong
    fun color(value: Long) = Color(value.toInt())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color(backgroundLong), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Seu treino", color = color(backgroundTextLong), fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = color(surfaceLong),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Supino reto · 3 × 8–12", color = color(surfaceTextLong))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewPill("Principal", primaryLong)
                    PreviewPill("Secundária", secondaryLong)
                }
            }
        }
        val backgroundRatio = PaletteColorCodec.contrastRatio(backgroundLong, preferredTextLong)
        val surfaceRatio = PaletteColorCodec.contrastRatio(surfaceLong, preferredTextLong)
        Text(
            text = "Contraste: fundo ${formatRatio(backgroundRatio)} · cartões ${formatRatio(surfaceRatio)}",
            color = color(backgroundTextLong),
            style = MaterialTheme.typography.bodySmall,
        )
        if (textWasAdjusted) {
            Text(
                text = "A cor de texto será ajustada para preto ou branco onde não atingir contraste 4,5:1.",
                color = color(backgroundTextLong),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PreviewPill(label: String, background: Long) {
    val foreground = PaletteColorCodec.readableForeground(background)
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color(background.toInt()),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color(foreground.toInt()),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatRatio(value: Double): String = String.format(Locale.ROOT, "%.1f:1", value)
