# Liftly 1.5.3 — Consolidação do aplicativo

Versão registrada em 10 de agosto de 2026.

## Versionamento

- `versionName`: **1.5.3**
- `versionCode`: **35**
- `applicationId`: `com.liftly.app`
- Android mínimo: API 26
- target/compile SDK: 36

## Alterações desta entrega

- projeto consolidado em uma única aplicação chamada **Liftly**;
- configuração Gradle simplificada para a variante principal `debug`/`release`;
- artefatos antigos e documentação fora do escopo da aplicação atual removidos;
- estrutura do repositório reduzida para manter o foco no Liftly;
- comandos de build e testes atualizados para `assembleDebug`, `testDebugUnitTest` e `lintDebug`;
- recursos da série 1.5 preservados, incluindo XP, Lift Coins, missões, loja, inventário e importação de fichas por texto.

## Compatibilidade

O pacote Android continua sendo `com.liftly.app`. Para instalar uma nova build sobre uma instalação existente sem apagar dados, o APK precisa ser assinado com uma chave compatível com a instalação anterior.
