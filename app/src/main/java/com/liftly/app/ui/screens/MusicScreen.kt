package com.liftly.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.liftly.app.R
import com.liftly.app.ui.components.GlassCard
import com.liftly.app.ui.components.GradientActionButton
import com.liftly.app.ui.components.NeonIcon
import com.liftly.app.integration.spotify.SpotifyPlaylistLinks
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Presentation-only contract for the Music tab.
 *
 * The playlist source remains outside this screen. [id] is used only to decide whether the
 * external action is available. Authentication and audio playback remain with Spotify.
 */
data class MusicScreenState(
    val id: String = "",
    val title: String = "Playlist do Liftly",
    val description: String = "Música escolhida para acompanhar o seu treino.",
    val thumbnailUrl: String = "",
    val enabled: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val lastUpdatedText: String = "",
    val remoteConfigured: Boolean = false,
    val errorMessage: String? = null,
    val personalPlaylists: List<PersonalSpotifyPlaylistUi> = emptyList(),
    val selectedPersonalPlaylistId: String? = null,
)

/** Playlist added manually and stored only on this device. */
data class PersonalSpotifyPlaylistUi(
    val id: String,
    val title: String,
)

private val SpotifyGreen = Color(0xFF1ED760)
private val SpotifyBlack = Color(0xFF121212)
private val SpotifyRaisedBlack = Color(0xFF242424)
private const val MAX_COVER_BYTES = 5 * 1024 * 1024
private const val MAX_DECODED_EDGE_PX = 2_048

/** Local playlist library, Spotify hand-off and the official Spotify playlist Embed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    state: MusicScreenState,
    onRefresh: () -> Unit,
    onOpenSpotify: () -> Unit,
    onSavePersonalPlaylist: (reference: String, title: String) -> Boolean = { _, _ -> false },
    onDeletePersonalPlaylist: (String) -> Unit = {},
    onSelectPersonalPlaylist: (String) -> Unit = {},
    onOpenPersonalPlaylist: (String) -> Unit = {},
) {
    val refreshEnabled = !state.isLoading && !state.isRefreshing
    var showAddPersonalPlaylist by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Column {
                        Text("Música", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Playlists salvas neste aparelho",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = refreshEnabled,
                        modifier = Modifier.semantics {
                            contentDescription = if (state.isRefreshing) {
                                "Atualizando playlist"
                            } else {
                                "Atualizar playlist"
                            }
                        },
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            NeonIcon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                selected = false,
                                intensity = 0.9f,
                                size = 32.dp,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 14.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PersonalPlaylistLibrary(
                    playlists = state.personalPlaylists,
                    selectedId = state.selectedPersonalPlaylistId,
                    onAdd = { showAddPersonalPlaylist = true },
                    onSelect = onSelectPersonalPlaylist,
                    onDelete = onDeletePersonalPlaylist,
                    onOpenSpotify = onOpenPersonalPlaylist,
                )
            }

            item {
                SectionHeading(
                    title = "Playlist em destaque",
                    subtitle = "Disponível mesmo sem conectar uma conta.",
                )
            }

            item {
                when {
                    !state.enabled -> MusicUnavailableCard(
                        errorMessage = state.errorMessage
                            ?: "A seleção musical está pausada no momento. Tente novamente mais tarde.",
                        onRefresh = onRefresh,
                        refreshEnabled = refreshEnabled,
                    )
                    state.isLoading && state.id.isBlank() -> MusicLoadingCard()
                    state.id.isBlank() -> MusicUnavailableCard(
                        errorMessage = state.errorMessage,
                        onRefresh = onRefresh,
                        refreshEnabled = refreshEnabled,
                    )
                    else -> PlaylistCard(
                        state = state,
                        onOpenSpotify = onOpenSpotify,
                    )
                }
            }

            if (state.enabled && state.id.isNotBlank()) {
                item {
                    SyncStatusCard(
                        state = state,
                        onRefresh = onRefresh,
                        refreshEnabled = refreshEnabled,
                    )
                }
            }

            item {
                SpotifySafetyNotice()
            }
        }
    }
    if (showAddPersonalPlaylist) {
        AddPersonalPlaylistDialog(
            onDismiss = { showAddPersonalPlaylist = false },
            onSave = { reference, title ->
                if (onSavePersonalPlaylist(reference, title)) {
                    showAddPersonalPlaylist = false
                }
            },
        )
    }
}

@Composable
private fun MusicIntroCard(
    isOffline: Boolean,
    remoteConfigured: Boolean,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    NeonIcon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        selected = true,
                        intensity = 1.15f,
                        size = 38.dp,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Treine no seu ritmo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        isOffline -> "Sem internet agora. A última seleção continua disponível."
                        remoteConfigured -> "A seleção pode ser atualizada para todos os aparelhos."
                        else -> "Uma playlist padrão acompanha esta versão do aplicativo."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PersonalPlaylistLibrary(
    playlists: List<PersonalSpotifyPlaylistUi>,
    selectedId: String?,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSpotify: (String) -> Unit,
) {
    val selected = playlists.firstOrNull { it.id == selectedId } ?: playlists.firstOrNull()
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NeonIcon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                selected = true,
                intensity = 1f,
                size = 38.dp,
            )
            Column(Modifier.weight(1f)) {
                Text("Suas playlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Salvas somente neste aparelho. Sem login no Liftly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onAdd,
                modifier = Modifier.semantics {
                    contentDescription = "Adicionar playlist do Spotify"
                },
            ) {
                NeonIcon(Icons.Rounded.Add, null, selected = true, intensity = 1f, size = 32.dp)
            }
        }

        if (selected == null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Cole o link de uma playlist para ver o player oficial do Spotify aqui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            GradientActionButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar playlist")
            }
            return@GlassCard
        }

        Spacer(Modifier.height(16.dp))
        SpotifyPlaylistEmbed(selected)
        Spacer(Modifier.height(12.dp))
        Text(
            "Embed oficial do Spotify. Caso uma playlist privada não carregue, abra-a no Spotify.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { onOpenSpotify(selected.id) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir no Spotify")
            }
            IconButton(
                onClick = { onDelete(selected.id) },
                modifier = Modifier.semantics { contentDescription = "Excluir ${selected.title}" },
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
            }
        }

        if (playlists.size > 1) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    if (playlist.id == selected.id) {
                        Button(onClick = { onSelect(playlist.id) }) {
                            Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(playlist.id) }) {
                            Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifyPlaylistEmbed(playlist: PersonalSpotifyPlaylistUi) {
    val links = remember(playlist.id) { SpotifyPlaylistLinks.fromId(playlist.id) }
    val html = remember(links.embedUrl) {
        """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;background:#121212"><iframe title="Spotify Embed" style="border-radius:12px" src="${links.embedUrl}?utm_source=generator" width="100%" height="352" frameborder="0" allowfullscreen="" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture" loading="lazy"></iframe></body></html>"""
    }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(352.dp)
            .clip(RoundedCornerShape(12.dp)),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportMultipleWindows(false)
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            if (webView.tag != playlist.id) {
                webView.tag = playlist.id
                webView.loadDataWithBaseURL(
                    "https://open.spotify.com/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

@Composable
private fun AddPersonalPlaylistDialog(
    onDismiss: () -> Unit,
    onSave: (reference: String, title: String) -> Unit,
) {
    var reference by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Cole o link do Spotify, a URI spotify:playlist ou o ID da playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Link da playlist") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome no Liftly (opcional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(reference, title) }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun PlaylistCard(
    state: MusicScreenState,
    onOpenSpotify: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = SpotifyBlack,
        contentColor = Color.White,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        elevation = 10.dp,
    ) {
        SpotifyAttribution()
        Spacer(Modifier.height(18.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 620.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistCover(
                        url = state.thumbnailUrl,
                        playlistTitle = state.title,
                        onOpenSpotify = onOpenSpotify,
                        modifier = Modifier
                            .weight(0.44f)
                            .aspectRatio(1f),
                    )
                    PlaylistDetails(
                        state = state,
                        onOpenSpotify = onOpenSpotify,
                        modifier = Modifier.weight(0.56f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PlaylistCover(
                        url = state.thumbnailUrl,
                        playlistTitle = state.title,
                        onOpenSpotify = onOpenSpotify,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 430.dp)
                            .aspectRatio(1f),
                    )
                    PlaylistDetails(
                        state = state,
                        onOpenSpotify = onOpenSpotify,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotifyAttribution(
    subtitle: String = "Playlist disponibilizada no serviço oficial",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Spotify. $subtitle"
            },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.spotify_full_logo_white),
            contentDescription = "Spotify",
            modifier = Modifier
                .widthIn(min = 132.dp, max = 146.dp)
                .aspectRatio(823.46f / 225.25f),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun PlaylistDetails(
    state: MusicScreenState,
    onOpenSpotify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "PLAYLIST",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = SpotifyGreen,
        )
        Text(
            text = state.title.ifBlank { "Playlist do Liftly" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.description.isNotBlank()) {
            Text(
                text = state.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.76f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.lastUpdatedText.isNotBlank()) {
            Text(
                text = "Atualizada ${state.lastUpdatedText}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
        }

        Spacer(Modifier.height(2.dp))
        GradientActionButton(
            onClick = onOpenSpotify,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.id.isNotBlank(),
            onClickLabel = "Abrir playlist no Spotify",
            contentDescription = "Abrir a playlist ${state.title} no Spotify",
            shape = RoundedCornerShape(50),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "OUVIR NO SPOTIFY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun PlaylistCover(
    url: String,
    playlistTitle: String,
    onOpenSpotify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpotifyArtwork(
        url = url,
        description = "Capa da playlist $playlistTitle",
        modifier = modifier.clickable(
            role = Role.Button,
            onClickLabel = "Abrir playlist no Spotify",
            onClick = onOpenSpotify,
        ),
        showFallbackLabel = true,
        accentColor = SpotifyGreen,
    )
}

@Composable
private fun SpotifyArtwork(
    url: String,
    description: String,
    modifier: Modifier = Modifier,
    showFallbackLabel: Boolean = false,
    accentColor: Color = Color.Unspecified,
) {
    val resolvedUrl = spotifyArtworkHttpsUrl(url)
    val coverState by produceState<CoverLoadState>(
        initialValue = if (resolvedUrl.isBlank()) CoverLoadState.Empty else CoverLoadState.Loading,
        key1 = resolvedUrl,
    ) {
        value = if (resolvedUrl.isBlank()) {
            CoverLoadState.Empty
        } else {
            loadSecureSpotifyCover(resolvedUrl)
        }
    }
    val resolvedAccent = if (accentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        accentColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SpotifyRaisedBlack)
            .semantics {
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val result = coverState) {
            is CoverLoadState.Success -> Image(
                bitmap = result.bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
            )
            CoverLoadState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(38.dp),
                color = resolvedAccent,
                trackColor = Color.White.copy(alpha = 0.14f),
                strokeWidth = 3.dp,
            )
            CoverLoadState.Empty,
            is CoverLoadState.Error,
            -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(if (showFallbackLabel) 64.dp else 42.dp),
                    tint = resolvedAccent,
                )
                if (showFallbackLabel) {
                    Text(
                        text = if (result is CoverLoadState.Error) {
                            "Capa indisponível"
                        } else {
                            "Playlist do Liftly"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun spotifyArtworkHttpsUrl(reference: String): String {
    val trimmed = reference.trim()
    if (!trimmed.startsWith("spotify:image:")) return trimmed
    val imageId = trimmed.removePrefix("spotify:image:")
    return if (SPOTIFY_IMAGE_ID.matches(imageId)) {
        "https://i.scdn.co/image/$imageId"
    } else {
        ""
    }
}

private val SPOTIFY_IMAGE_ID = Regex("^[A-Fa-f0-9]{32,128}$")

@Composable
private fun SyncStatusCard(
    state: MusicScreenState,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
) {
    val statusTitle = when {
        state.isOffline -> "Modo offline"
        state.errorMessage != null -> "Não foi possível atualizar"
        state.remoteConfigured -> "Playlist em destaque do Liftly"
        else -> "Playlist padrão"
    }
    val statusMessage = when {
        state.isOffline -> "Você está vendo a última playlist salva neste aparelho."
        state.errorMessage != null -> state.errorMessage
        state.remoteConfigured -> "É uma playlist pública escolhida pelo administrador do app. Ela não altera suas playlists salvas."
        else -> "Esta é a playlist padrão do Liftly; suas playlists salvas não são sincronizadas."
    }
    val statusIcon = when {
        state.isOffline -> Icons.Rounded.CloudOff
        else -> Icons.Rounded.Sync
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NeonIcon(
                imageVector = statusIcon,
                contentDescription = null,
                selected = !state.isOffline && state.errorMessage == null,
                intensity = if (state.isOffline) 0.25f else 1f,
                size = 38.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = refreshEnabled,
                modifier = Modifier.semantics {
                    contentDescription = "Tentar atualizar a playlist"
                },
            ) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SpotifySafetyNotice() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        elevation = 3.dp,
    ) {
        Text(
            text = "Reprodução segura",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "As playlists salvas ficam somente neste aparelho. O card usa o embed oficial do " +
                "Spotify; ao abrir a playlist, música e login continuam no Spotify.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MusicLoadingCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Buscando a playlist…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Isso deve levar apenas alguns segundos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MusicUnavailableCard(
    errorMessage: String?,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NeonIcon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                selected = true,
                intensity = 1.2f,
                size = 68.dp,
            )
            Text(
                text = "Não foi possível carregar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = errorMessage
                    ?: "Atualize para buscar novamente a seleção musical do Liftly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = refreshEnabled,
                modifier = Modifier
                    .height(48.dp)
                    .semantics { role = Role.Button },
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Text("Atualizar", Modifier.padding(start = 8.dp))
            }
        }
    }
}

private sealed interface CoverLoadState {
    data object Empty : CoverLoadState
    data object Loading : CoverLoadState
    data class Success(val bitmap: ImageBitmap) : CoverLoadState
    data class Error(val reason: String) : CoverLoadState
}

/** Downloads only Spotify CDN artwork, keeps it in Compose memory, and never writes to disk. */
private suspend fun loadSecureSpotifyCover(url: String): CoverLoadState = withContext(Dispatchers.IO) {
    val uri = runCatching { URI(url) }.getOrNull()
        ?: return@withContext CoverLoadState.Error("Endereço de imagem inválido")
    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
    val trustedHost = host == "i.scdn.co" ||
        (host.endsWith(".scdn.co") && host.length > ".scdn.co".length) ||
        (host.endsWith(".spotifycdn.com") && host.length > ".spotifycdn.com".length)
    if (
        uri.scheme?.lowercase(Locale.ROOT) != "https" ||
        !trustedHost ||
        uri.rawUserInfo != null ||
        uri.port !in setOf(-1, 443)
    ) {
        return@withContext CoverLoadState.Error("Origem de imagem não permitida")
    }

    var connection: HttpURLConnection? = null
    try {
        currentCoroutineContext().ensureActive()
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 9_000
            instanceFollowRedirects = false
            useCaches = false
            requestMethod = "GET"
            setRequestProperty("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.8")
            setRequestProperty("User-Agent", "Liftly-Android/1.0")
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            return@withContext CoverLoadState.Error("Imagem respondeu com código $responseCode")
        }
        val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        if (!contentType.startsWith("image/")) {
            return@withContext CoverLoadState.Error("Conteúdo remoto não é uma imagem")
        }
        val declaredLength = connection.contentLengthLong
        if (declaredLength > MAX_COVER_BYTES) {
            return@withContext CoverLoadState.Error("Imagem maior que 5 MB")
        }

        val bytes = connection.inputStream.use { input ->
            val initialSize = when {
                declaredLength in 1..MAX_COVER_BYTES.toLong() -> declaredLength.toInt()
                else -> 64 * 1024
            }
            val output = ByteArrayOutputStream(initialSize)
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_COVER_BYTES) {
                    return@withContext CoverLoadState.Error("Imagem maior que 5 MB")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        if (bytes.isEmpty()) {
            return@withContext CoverLoadState.Error("Imagem vazia")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext CoverLoadState.Error("Formato de imagem não reconhecido")
        }
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_DECODED_EDGE_PX ||
            bounds.outHeight / sampleSize > MAX_DECODED_EDGE_PX
        ) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return@withContext CoverLoadState.Error("Não foi possível decodificar a imagem")

        CoverLoadState.Success(bitmap.asImageBitmap())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CoverLoadState.Error("Não foi possível baixar a capa")
    } finally {
        connection?.disconnect()
    }
}
