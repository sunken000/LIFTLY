# Liftly 1.2.0 — Patch Notes

## 🎵 Spotify — beta pessoal

- Nova aba Música com identidade visual inspirada no ecossistema Spotify e atribuição oficial.
- Login seguro pela página oficial do Spotify usando OAuth 2.0 Authorization Code + PKCE.
- Exibição das playlists públicas pertencentes à conta conectada.
- Player remoto com capa, faixa, artista, álbum, progresso, play/pause, anterior e próxima.
- A reprodução continua no aplicativo oficial Spotify; o Liftly apenas envia comandos pelo App Remote.
- Tokens protegidos por AES-GCM com chave não exportável do Android Keystore.
- Renovação de access token e novo login obrigatório após 6 meses.
- Botão para desconectar e apagar a sessão Spotify armazenada no aparelho.

## 🔄 Playlist atualizada sem novo APK

- Mantido o sistema de playlist global por JSON remoto.
- É possível trocar músicas diretamente na mesma playlist do Spotify sem alterar o APK.
- Também é possível trocar a playlist de todos os aparelhos publicando outro `spotifyId` no endpoint configurado.
- Cache local e controle por `revision` mantêm a última configuração válida e evitam regressões.

## 🎨 Novo ícone do Liftly

- Novo símbolo roxo com o “L” e halteres em destaque.
- Margem preta externa removida para o ícone preencher corretamente o launcher.
- Cantos externos transparentes sem apagar o interior escuro do cartão.
- Recursos próprios para ícone quadrado, redondo, adaptive icon, tema monocromático e splash.
- Todas as densidades Android atualizadas: mdpi, hdpi, xhdpi, xxhdpi e xxxhdpi.

## ✅ Verificação

- 149 testes JVM aprovados, sem falhas.
- APK debug 1.2.0 compilado com os recursos de ícone do launcher e splash validados.

## ⚙️ Configuração necessária para a beta

- Package Android: `com.liftly.app`.
- Redirect URI recomendada: `http://127.0.0.1/callback`.
- Propriedades da build: `LIFTLY_SPOTIFY_CLIENT_ID` e `LIFTLY_SPOTIFY_REDIRECT_URI`.
- Spotify instalado e conta Premium adicionada à allowlist do aplicativo.
- Development Mode limitado a até 5 usuários autorizados.

Consulte o guia completo em [`MUSICA_SPOTIFY.md`](MUSICA_SPOTIFY.md).

> Esta integração ainda é uma beta pessoal. Não deve ser usada para venda, distribuição comercial ou reprodução pública em academia sem aprovação e modalidade de acesso adequadas do Spotify.
