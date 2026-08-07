# Música e Spotify no Liftly

## O que existe no app

A aba **Música** possui dois recursos independentes:

- **Suas playlists:** biblioteca local em que cada pessoa cola um link, URI `spotify:playlist:...` ou ID de 22 caracteres;
- **Playlist em destaque:** seleção pública definida pelo administrador do Liftly.

O APK não contém login Spotify, OAuth, Client ID, callback, Web API, acesso às playlists da conta ou Spotify App Remote.

## Suas playlists

O Liftly guarda somente o ID e o nome escolhido em `SharedPreferences` privadas do próprio aparelho. Esses dados não são enviados para um servidor, não aparecem para outras pessoas e não exigem conta Liftly.

A playlist selecionada é mostrada pelo **Spotify Embed oficial** dentro da tela. O link é validado antes de criar a WebView; o aplicativo não injeta o texto fornecido no HTML, não usa ponte JavaScript e bloqueia acesso a arquivos locais, conteúdo local e conteúdo misto.

O Embed é mais confiável para playlists públicas ou compartilháveis. Uma playlist privada pode não carregar porque a sessão da WebView é separada da sessão do aplicativo Spotify. Nesse caso, **Abrir no Spotify** envia o link ao aplicativo ou navegador oficial.

## Playlist global em destaque

Alterar as músicas da mesma playlist no Spotify mantém o mesmo ID e atualiza o conteúdo pelo próprio Spotify, sujeito ao cache normal da plataforma.

Para trocar o ID da playlist em destaque sem publicar outro APK, compile uma vez com `LIFTLY_MUSIC_CONFIG_URL` apontando para um JSON HTTPS público e estável. Depois disso, altere somente o conteúdo do JSON.

Modelo disponível em [`music-config/playlist.json`](../music-config/playlist.json):

```json
{
  "revision": 1,
  "spotifyPlaylistId": "7jOh9hQGVTDjtWyIfYe5OY",
  "enabled": true,
  "updatedAt": "2026-07-22T00:00:00-03:00"
}
```

Configure antes da compilação:

```powershell
$env:LIFTLY_MUSIC_CONFIG_URL = "https://SEU-DOMINIO/playlist.json"
.\gradlew.bat assembleDebug
```

O endpoint remoto aceita somente HTTPS público, limita o tamanho da resposta, não segue redirecionamentos e rejeita revisões antigas ou IDs inválidos. A última configuração válida fica em cache para funcionamento offline.

## O que significa sincronização global

A sincronização global altera exclusivamente a **playlist em destaque** para todos os aparelhos que usam a mesma URL de configuração. Ela nunca lê, envia, substitui ou compartilha as playlists salvas em **Suas playlists**.

## Checklist

- O link pessoal é de uma playlist Spotify e contém um ID válido de 22 caracteres.
- A playlist é pública/compartilhável para aparecer de forma confiável no Embed.
- Se o Embed não carregar, use **Abrir no Spotify**.
- O JSON global usa HTTPS, sintaxe válida e `revision` crescente.
- Nenhum Client ID, Client Secret, senha, token ou cookie Spotify é necessário no APK.
