# Liftly 1.2.1 — Hotfix Spotify

## Correção

- Redirect URI alterada para `http://127.0.0.1:8888/callback`.
- Listener OAuth agora respeita uma porta fixa cadastrada no Spotify Dashboard.
- Mantido suporte seguro a PKCE, `state`, callback único e acesso exclusivo ao loopback local.
- Validação ampliada para impedir que uma porta fixa seja substituída durante a autorização.

## Dashboard

Cadastre exatamente a URI acima, além do package `com.liftly.app`, do SHA-1 da assinatura instalada e das APIs **Web API** e **Android**. `localhost` não deve ser usado.

## Alcance da beta

A interface aparece para todos que instalarem essa compilação, mas o Spotify Development Mode autoriza somente as contas incluídas em **User Management**, dentro do limite da plataforma. A playlist pública global continua disponível para os demais usuários.
