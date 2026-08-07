# Configuração remota da playlist

Este diretório contém o arquivo público que controla qual playlist aparece na aba Música do Liftly. O ID atual corresponde à playlist pública fornecida pelo proprietário.

O procedimento completo, inclusive hospedagem e atualização, está em [`docs/MUSICA_SPOTIFY.md`](../docs/MUSICA_SPOTIFY.md).

Regras rápidas:

- aumente `revision` a cada publicação;
- atualize `updatedAt` em ISO 8601;
- mantenha `schemaVersion` em `1` enquanto o formato não mudar;
- use somente o ID de uma playlist pública;
- nunca coloque senha, token, webhook, `client_secret` ou qualquer outro segredo neste JSON.
