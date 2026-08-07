# Liftly 1.3.0 — Playlists pessoais

## Música

- Adicionada **Suas playlists**: cada pessoa pode colar link, URI `spotify:playlist` ou ID de uma playlist e salvá-la no próprio aparelho.
- A playlist escolhida aparece no **Embed oficial do Spotify**, com capa, faixas e controles fornecidos pelo próprio Spotify.
- Incluídos seleção entre playlists salvas, edição do nome local ao salvar novamente, remoção individual e **Abrir no Spotify**.
- O app valida o ID antes de montar o embed e a WebView não recebe ponte JavaScript, acesso a arquivos locais nem conteúdo misto.

## Privacidade e sincronização

- A biblioteca pessoal não usa login Liftly, não é enviada ao servidor e não aparece para outras pessoas.
- **Sincronização global** continua sendo somente a playlist pública em destaque definida pelo administrador via `MUSIC_CONFIG_URL`; ela não modifica nem lê playlists pessoais.
- Playlists privadas podem não renderizar no embed por limitações do Spotify/WebView. Nesses casos, o botão **Abrir no Spotify** abre a playlist no aplicativo oficial, onde a conta do usuário continua autenticada.

## Versão

- `1.3.0` (`versionCode 24`).
