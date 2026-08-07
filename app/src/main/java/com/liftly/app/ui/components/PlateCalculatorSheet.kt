package com.liftly.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liftly.app.domain.BarbellPlateCalculator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlateCalculatorSheet(
    initialTotalKg: Double,
    onDismiss: () -> Unit,
) {
    var totalText by rememberSaveable(initialTotalKg) {
        mutableStateOf(initialTotalKg.takeIf { it > 0.0 }?.plateLabel().orEmpty())
    }
    var barText by rememberSaveable { mutableStateOf("20") }
    var selectedPlates by remember {
        mutableStateOf(BarbellPlateCalculator.defaultAvailablePlatesKg.toSet())
    }
    val total = totalText.decimalOrNull()
    val bar = barText.decimalOrNull()
    val result = remember(total, bar, selectedPlates) {
        if (total == null || bar == null || selectedPlates.isEmpty()) null
        else runCatching {
            BarbellPlateCalculator.calculate(total, bar, selectedPlates.sortedDescending())
        }.getOrNull()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        Icons.Outlined.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Calculadora de anilhas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("A carga informada inclui a barra.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it.decimalInput(7) },
                    label = { Text("Carga total (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = barText,
                    onValueChange = { barText = it.decimalInput(5) },
                    label = { Text("Barra (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Anilhas disponíveis", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarbellPlateCalculator.defaultAvailablePlatesKg.forEach { plate ->
                        FilterChip(
                            selected = plate in selectedPlates,
                            onClick = {
                                selectedPlates = if (plate in selectedPlates) selectedPlates - plate else selectedPlates + plate
                            },
                            label = { Text("${plate.plateLabel()} kg") },
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        total == null || bar == null -> Text("Informe a carga total e o peso da barra.")
                        selectedPlates.isEmpty() -> Text("Selecione ao menos um tamanho de anilha.")
                        result == null -> Text("Não foi possível calcular esta montagem.")
                        else -> {
                            Text("Em cada lado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(
                                if (result.platesPerSide.isEmpty()) "Sem anilhas" else result.platesPerSide.joinToString(" + ") { "${it.plateLabel()} kg" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("${result.plateWeightPerSideKg.plateLabel()} kg por lado + barra de ${result.barWeightKg.plateLabel()} kg")
                            if (!result.isExact) {
                                Text(
                                    "Com as anilhas selecionadas: ${result.achievedTotalKg.plateLabel()} kg. Diferença de ${kotlin.math.abs(result.differenceKg).plateLabel()} kg.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun String.decimalInput(maxLength: Int): String =
    filter { it.isDigit() || it == ',' || it == '.' }.take(maxLength)

private fun String.decimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

private fun Double.plateLabel(): String = if (this % 1.0 == 0.0) {
    toInt().toString()
} else {
    String.format(Locale.forLanguageTag("pt-BR"), "%.2f", this).trimEnd('0').trimEnd(',')
}
