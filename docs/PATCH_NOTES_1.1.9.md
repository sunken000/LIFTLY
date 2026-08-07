# Liftly 1.1.9 — Playlist do proprietário

## Alterações

- A playlist pública `7jOh9hQGVTDjtWyIfYe5OY` agora é a seleção padrão da aba Música.
- Título oficial confirmado pelo Spotify: `침몰한`.
- O JSON remoto de exemplo foi atualizado para a revisão `2` com a playlist fornecida.
- A validação de imagens passou a aceitar também o domínio oficial `*.spotifycdn.com`, usado pela capa desta playlist.
- Hosts parecidos ou falsos continuam bloqueados.

## Segurança e funcionamento

- O iframe recebido não foi incorporado.
- Reprodução e login continuam no aplicativo/site do Spotify.
- O Liftly lê apenas o título e a capa oficiais via oEmbed.
- Alterações nas músicas e na ordem da mesma playlist aparecem sem novo APK, pois o ID permanece igual.
- Trocar para outro ID globalmente ainda requer o endpoint HTTPS estável descrito em `docs/MUSICA_SPOTIFY.md`.

## Validação

- Versão `1.1.9` (`versionCode 20`).
- 132 testes JVM aprovados, sem falhas.
- APK compilado e assinatura debug v2 verificada.
- SHA-256: `5B194F6C662FA534FF3408F5E7F9AA9D90EF56CB455A44DFD14DD9BDC6872C58`.
