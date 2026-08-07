package com.anipresence.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anipresence.app.BuildConfig
import com.anipresence.app.data.discord.DiscordConnectionState
import com.anipresence.app.domain.model.PlaybackState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
    state: MainUiState,
    onToggleDetection: () -> Unit,
    onGrantAccess: () -> Unit,
    onConfigureWebhook: (String) -> Unit,
    onTestWebhook: () -> Unit,
    onSaveCorrection: (String, Int?, Int?) -> Unit,
    onConfirmPublish: () -> Unit,
    onSimulate: (String, String, String, PlaybackState, Long?, Long?) -> Unit,
) {
    var correctionOpen by remember { mutableStateOf(false) }
    var webhookOpen by remember { mutableStateOf(false) }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("AniPresence", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Detecta mídia localmente e publica somente com sua configuração.")
            }
            item {
                StatusCard(
                    title = "Detecção",
                    status = if (state.detectionEnabled) "Ativada" else "Desativada",
                ) {
                    Switch(checked = state.detectionEnabled, onCheckedChange = { onToggleDetection() })
                }
            }
            item {
                PermissionCard(state.notificationAccess, onGrantAccess)
            }
            item {
                DiscordCard(
                    state = state.discordState,
                    onConnect = { webhookOpen = true },
                    onTest = onTestWebhook,
                )
            }
            item {
                MediaCard(
                    state = state,
                    onCorrect = { correctionOpen = true },
                    onConfirmPublish = onConfirmPublish,
                )
            }
            if (BuildConfig.DEBUG) {
                item { DebugSimulator(onSimulate) }
            }
            item {
                Text("Logs técnicos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.logs.isEmpty()) Text("Nenhum evento registrado.")
                        state.logs.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (correctionOpen) {
        CorrectionDialog(
            initialTitle = state.match?.canonicalTitle ?: state.parsed?.possibleTitle.orEmpty(),
            initialSeason = state.match?.season ?: state.parsed?.season,
            initialEpisode = state.match?.episode ?: state.parsed?.episode,
            onDismiss = { correctionOpen = false },
            onSave = { title, season, episode ->
                onSaveCorrection(title, season, episode)
                correctionOpen = false
            },
        )
    }
    if (webhookOpen) {
        WebhookDialog(
            onDismiss = { webhookOpen = false },
            onSave = {
                onConfigureWebhook(it)
                webhookOpen = false
            },
        )
    }
}

@Composable
private fun StatusCard(title: String, status: String, action: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(status)
            }
            action()
        }
    }
}

@Composable
private fun PermissionCard(granted: Boolean, onGrantAccess: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Acesso às notificações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (granted) "Concedido" else "Permissão de notificações necessária")
            Text(
                "Essa permissão é usada somente para encontrar controles e informações de mídia, " +
                    "como título e episódio. Mensagens pessoais não são armazenadas nem enviadas.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!granted) Button(onClick = onGrantAccess) { Text("Conceder acesso") }
        }
    }
}

@Composable
private fun DiscordCard(
    state: DiscordConnectionState,
    onConnect: () -> Unit,
    onTest: () -> Unit,
) {
    val label = when (state) {
        DiscordConnectionState.Disconnected -> "Desconectado"
        DiscordConnectionState.Connecting -> "Verificando…"
        is DiscordConnectionState.Connected -> "Conectado: ${state.mode}"
        is DiscordConnectionState.Unavailable -> state.reason
        is DiscordConnectionState.Error -> state.message
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Discord", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label)
            Text(
                "Rich Presence aparece no perfil. Webhook publica em um canal; são recursos diferentes. " +
                    "Este build aberto oferece o webhook funcional.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConnect) { Text("Conectar Discord") }
                OutlinedButton(
                    onClick = onTest,
                    enabled = state is DiscordConnectionState.Connected,
                ) { Text("Testar webhook") }
            }
        }
    }
}

@Composable
private fun MediaCard(
    state: MainUiState,
    onCorrect: () -> Unit,
    onConfirmPublish: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mídia atual", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val media = state.media
            when {
                !state.detectionEnabled -> Text("Ative a detecção para começar.")
                !state.notificationAccess -> Text("Permissão de notificações necessária")
                media == null -> Text("Nenhum conteúdo sendo reproduzido")
                media.rawTitle.isNullOrBlank() ->
                    Text("O aplicativo atual não fornece informações sobre o vídeo")
                state.match == null && !state.resolving ->
                    Text("Conteúdo detectado, mas não reconhecido como anime")
            }
            if (media != null) {
                HorizontalDivider()
                InfoRow("Aplicativo", media.appName ?: media.packageName)
                InfoRow("Pacote", media.packageName)
                InfoRow("Título bruto", media.rawTitle ?: "Não informado")
                InfoRow("Anime", state.match?.canonicalTitle ?: "Não identificado")
                InfoRow("Temporada", (state.match?.season ?: state.parsed?.season)?.toString() ?: "Não informada")
                InfoRow("Episódio", (state.match?.episode ?: state.parsed?.episode)?.toString() ?: "Não informado")
                InfoRow("Estado", media.playbackState.label())
                InfoRow("Confiança", state.match?.let { "${it.confidence}% · ${it.source}" } ?: "0%")
                InfoRow(
                    "Última atualização",
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(media.updatedAt),
                )
                if (state.excludedAsMusic) {
                    Text("Player de música excluído da publicação automática.", color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCorrect,
                        enabled = !media.rawTitle.isNullOrBlank(),
                    ) { Text("Corrigir identificação") }
                }
                if (state.match != null && state.match.confidence < 75 && !state.excludedAsMusic) {
                    Button(onClick = onConfirmPublish) { Text("Confirmar e publicar") }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value)
    }
}

@Composable
private fun CorrectionDialog(
    initialTitle: String,
    initialSeason: Int?,
    initialEpisode: Int?,
    onDismiss: () -> Unit,
    onSave: (String, Int?, Int?) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var season by remember { mutableStateOf(initialSeason?.toString().orEmpty()) }
    var episode by remember { mutableStateOf(initialEpisode?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corrigir identificação") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título correto") })
                OutlinedTextField(season, { season = it.filter(Char::isDigit) }, label = { Text("Temporada") })
                OutlinedTextField(episode, { episode = it.filter(Char::isDigit) }, label = { Text("Episódio") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, season.toIntOrNull(), episode.toIntOrNull()) }) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WebhookDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Webhook do Discord") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cole uma URL criada nas integrações do seu próprio servidor. Ela será criptografada no aparelho.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL do webhook") },
                    singleLine = true,
                )
                Text("Deixe vazio e salve para remover.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DebugSimulator(
    onSimulate: (String, String, String, PlaybackState, Long?, Long?) -> Unit,
) {
    var packageName by remember { mutableStateOf("com.example.video") }
    var title by remember { mutableStateOf("Frieren - Episode 8") }
    var subtitle by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("1440000") }
    var position by remember { mutableStateOf("120000") }
    var playback by remember { mutableStateOf(PlaybackState.PLAYING) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Simulador debug", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(packageName, { packageName = it }, label = { Text("Pacote") }, singleLine = true)
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
            OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtítulo") }, singleLine = true)
            OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("Duração (ms)") })
            OutlinedTextField(position, { position = it.filter(Char::isDigit) }, label = { Text("Posição (ms)") })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaybackState.entries.forEach { option ->
                    OutlinedButton(onClick = { playback = option }, enabled = playback != option) {
                        Text(option.label())
                    }
                }
            }
            Button(
                onClick = {
                    onSimulate(
                        packageName,
                        title,
                        subtitle,
                        playback,
                        duration.toLongOrNull(),
                        position.toLongOrNull(),
                    )
                }
            ) { Text("Simular mídia") }
        }
    }
}

private fun PlaybackState.label() = when (this) {
    PlaybackState.PLAYING -> "Reproduzindo"
    PlaybackState.PAUSED -> "Pausado"
    PlaybackState.STOPPED -> "Parado"
}
