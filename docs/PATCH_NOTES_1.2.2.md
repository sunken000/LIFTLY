# Liftly 1.2.2 — Hotfix do retorno Spotify

## Corrigido

- Eliminado o carregamento infinito após tocar em **Aceito** no Spotify.
- O retorno OAuth agora usa o link exclusivo `com.liftly.app.spotify-login://auth/callback` e reabre o Liftly diretamente.
- O login não depende mais de um servidor local que poderia ser suspenso quando o navegador estivesse em primeiro plano.
- O App Remote reutiliza a autorização PKCE já concedida, evitando uma segunda tela de consentimento.
- O callback continua protegido por PKCE S256, `state`, URI exata e atividade Android dedicada.

## Alteração necessária no Dashboard

Remova `http://127.0.0.1:8888/callback` e cadastre exatamente:

```text
com.liftly.app.spotify-login://auth/callback
```

Package, SHA-1, **Web API**, **Android** e contas de **User Management** permanecem iguais.
