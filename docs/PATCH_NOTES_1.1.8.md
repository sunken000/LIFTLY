# Liftly 1.1.8 — Música segura e playlist global

## Novidades

- Nova aba **Música**, integrada aos temas e ao fundo animado do Liftly.
- Capa e título oficiais obtidos pelo oEmbed do Spotify, sem recorte, desfoque ou gravação da imagem em disco.
- Logo oficial e botão **OUVIR NO SPOTIFY**; a capa também abre a playlist.
- Reprodução, login e controles permanecem no aplicativo ou site oficial do Spotify.
- Playlist padrão funcional e suporte a uma configuração JSON pública para trocar o ID em todos os aparelhos sem publicar outro APK.
- Atualização ao abrir/retomar a aba, botão manual e consulta a cada 30 segundos enquanto a tela está visível.
- Cache da última configuração válida, `ETag` e modo offline.

## Segurança

- Sem WebView, iframe, player embutido, OAuth, senha ou token do Spotify.
- Apenas HTTPS; redirecionamentos desativados e respostas limitadas a 64 KB.
- JSON estrito com schema, data ISO-8601, revisão crescente e proteção contra rollback.
- IDs e hosts validados para impedir links falsos; capa aceita somente o CDN oficial do Spotify.
- Tráfego HTTP sem criptografia bloqueado no manifesto.

## Como atualizar a playlist

- Para mudar músicas ou a ordem da playlist atual, edite-a no próprio Spotify; o ID permanece igual.
- Para trocar por outra playlist globalmente, edite o `playlist.json`, aumente `revision` e publique no mesmo endereço HTTPS.
- O procedimento completo está em `docs/MUSICA_SPOTIFY.md`.

## Validação

- Versão: `1.1.8` (`versionCode 19`).
- 132 testes JVM aprovados, sem falhas; 19 deles cobrem a nova integração.
- APK compilado e assinatura debug v2 verificada.
- SHA-256: `E67901A1F2B96617939DD9AF2E856B89CA541780B7AD2065E89650C8DBF8D648`.

## Observação importante

Este APK usa uma playlist pública de demonstração porque ainda não foi fornecido um endpoint controlado pelo proprietário. Para habilitar a troca global de ID, configure uma vez `LIFTLY_MUSIC_CONFIG_URL` antes da compilação distribuída. A aba informa claramente quando está usando o modo padrão.
