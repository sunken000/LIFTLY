# Liftly 1.3.1 — Integração simplificada

## Spotify

- Removida completamente a integração pessoal de conta: login OAuth, callback, tokens, Web API, lista de playlists da conta e controles App Remote.
- Removidos do APK o SDK Spotify App Remote, a atividade de callback e as configurações de Client ID/Redirect URI.
- Ao atualizar uma versão antiga, tokens e chave criptográfica legados são apagados automaticamente do aparelho.
- Mantida **Suas playlists**, onde cada pessoa cola um link, URI ou ID e vê o Embed oficial.
- Mantida a playlist global em destaque, independente das playlists locais.

## Privacidade

- O Liftly não conecta contas Spotify e não guarda access token ou refresh token.
- As playlists adicionadas manualmente continuam armazenadas somente no aparelho.

## Versão

- `1.3.1` (`versionCode 25`).
