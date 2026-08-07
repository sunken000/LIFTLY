# AniPresence

Aplicativo Android em Kotlin que observa sessões e notificações **de mídia**, extrai
título/temporada/episódio, tenta reconhecer o anime e pode publicar a atividade em um
canal do Discord por webhook.

O projeto foi mantido separado do aplicativo Liftly que já existia no diretório pai.
O pacote é `com.anipresence.app` e o `minSdk` é 26 (Android 8).

## O que funciona

- detecção genérica via `MediaSessionManager`, obtida por um
  `NotificationListenerService`;
- callbacks de sessão, sem polling constante, wake lock ou serviço permanente em
  primeiro plano;
- fallback restrito a notificações classificadas como mídia/transport;
- título, subtítulo, artista, álbum, duração, posição e estado quando o player os
  fornece;
- parser em português e inglês, inclusive `S02E04`, `T1:E5` e temporadas ordinais;
- aliases locais e busca opcional no catálogo Jikan v4;
- cache persistente da resposta remota em DataStore (até 50 consultas);
- correções manuais persistentes por título bruto e pacote;
- exclusão local de players de música conhecidos;
- confiança de 0 a 100;
- espera de 10 segundos e confiança mínima de 75 para publicação automática;
- deduplicação de atualizações;
- remoção da mensagem após 60 segundos parado ou 2 minutos pausado;
- simulador de mídia somente no build debug;
- webhook criptografado com AES-GCM e chave no Android Keystore;
- funcionamento local mesmo sem catálogo ou Discord.

## Limitações técnicas

O Android não obriga players a exporem metadados. Alguns aplicativos fornecem apenas
“Reproduzindo”, omitem o episódio, escondem a sessão ou usam notificações próprias.
Nesses casos o AniPresence informa que não conseguiu identificar o conteúdo. O app
não usa acessibilidade, captura de tela, OCR, microfone ou engenharia reversa.

Uma notificação marcada como mídia ainda pode ter dados insuficientes. Notificações
comuns e mensagens não são processadas. Os textos brutos não são enviados ao
catálogo: somente o título normalizado usado na consulta. Nenhuma notificação completa
é enviada a servidor.

### Rich Presence versus webhook

Rich Presence aparece no perfil. Webhook publica uma mensagem em um canal. São
funcionalidades diferentes.

O [Discord Social SDK](https://docs.discord.com/developers/discord-social-sdk/) é o
caminho oficial para Rich Presence em aplicações nativas e suporta Android, OAuth2 e
PKCE. Porém, o SDK é distribuído para download dentro do Discord Developer Portal,
não como dependência pública Maven que este projeto possa resolver sozinho. Por isso:

- `DiscordSocialSdkClient` é um ponto de integração isolado e declara
  “indisponível”; não há integração falsa;
- o build aberto e reproduzível inclui o webhook oficial e funcional;
- nenhum token pessoal, self-bot, senha ou token do cliente Discord é aceito.

## Requisitos

- Android Studio compatível com AGP 9.2.1;
- JDK 17 ou superior aceito pelo Gradle;
- Android SDK 36 e Build Tools 36;
- aparelho/emulador Android 8 ou superior.

## Abrir e compilar

1. No Android Studio, escolha **Open** e selecione a pasta `AniPresence`.
2. Aguarde a sincronização do Gradle.
3. Se necessário, defina `sdk.dir` no `local.properties`.
4. Execute a configuração `app`.

No terminal, a partir desta pasta:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

O APK será criado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para um build release local:

```powershell
.\gradlew.bat assembleRelease
```

Configure uma assinatura própria para distribuição. Keystores e
`secrets.properties` estão ignorados pelo Git.

## Conceder acesso às notificações

1. Abra o app.
2. Leia a explicação de privacidade.
3. Toque em **Conceder acesso**.
4. Na tela do Android, habilite **Detecção de mídia do AniPresence**.
5. Volte e ative **Detecção**.

A permissão serve para consultar sessões ativas e filtrar notificações de mídia.
Mensagens pessoais não são armazenadas nem enviadas.

## Catálogo de anime

O MVP usa a [Jikan REST API v4](https://docs.api.jikan.moe/), uma API pública sem
segredo no APK. A chamada tem timeout de 3 segundos. Falha, indisponibilidade ou
limite de requisições não interrompem a detecção; o título bruto continua na tela.

Aliases locais ficam em:

```text
app/src/main/java/com/anipresence/app/data/anime/LocalAnimeResolver.kt
```

Para adicionar um alias, inclua uma nova `Entry` ou acrescente o texto à lista de
aliases de uma entrada existente. A comparação ignora caixa, acentos e pontuação,
mas preserva números como `86` e `91 Days`.

## Configurar o webhook

1. Em seu servidor Discord, abra **Configurações do servidor → Integrações →
   Webhooks**.
2. Crie um webhook no canal desejado e copie a URL.
3. No AniPresence, toque em **Conectar Discord**.
4. Cole a URL e salve.
5. Aguarde o estado **Webhook verificado** e toque em **Testar webhook**. Uma
   mensagem deve aparecer imediatamente no canal, sem depender da detecção.

O app valida domínio HTTPS oficial do Discord e criptografa a URL no aparelho. A URL
nunca aparece nos logs. A primeira atualização cria uma mensagem com `wait=true`; as
seguintes editam a mesma mensagem. Ao encerrar a reprodução pelo tempo configurado, a
mensagem é excluída. Salvar o campo vazio remove a configuração local.

## Preparar o Discord Social SDK oficial

Esta seção documenta a preparação para quem tiver acesso ao pacote oficial; o build
padrão não o inclui.

1. Crie uma aplicação no
   [Discord Developer Portal](https://discord.com/developers/applications).
2. Copie o **Application ID** (Client ID).
3. Em **OAuth2**, ative **Public Client** se a troca de código ocorrer no aparelho.
4. Adicione a redirect URI móvel:

   ```text
   discord-SEU_APPLICATION_ID:/authorize/callback
   ```

5. Use OAuth2 Authorization Code com PKCE (`S256`). Em esquema móvel personalizado,
   PKCE é obrigatório segundo a
   [documentação oficial de account linking](https://docs.discord.com/developers/discord-social-sdk/development-guides/account-linking-on-mobile).
6. Solicite os escopos retornados por `Client::GetDefaultPresenceScopes`
   (`openid` e `sdk.social_layer_presence`, conforme a versão do SDK).
7. Baixe o pacote Android na seção **Discord Social SDK** da aplicação no portal e
   siga as instruções oficiais de instalação do pacote baixado.
8. Defina apenas o identificador público, nunca um segredo:

   ```properties
   ANIPRESENCE_DISCORD_CLIENT_ID=SEU_APPLICATION_ID
   ```

   Ele pode ficar no `local.properties`, variável de ambiente ou propriedade Gradle.
   O valor chega ao código como `BuildConfig.DISCORD_CLIENT_ID`.
9. Substitua a implementação isolada de `DiscordSocialSdkClient` pela ligação ao
   artefato oficial, mantendo a interface `DiscordPresenceClient`.

Não coloque Client Secret no APK. A integração deve usar PKCE como cliente público
ou trocar o código em backend controlado.

## Testes

Execute:

```powershell
.\gradlew.bat testDebugUnitTest
```

Os testes cobrem:

- formatos de título solicitados;
- temporada e episódio;
- preservação de `86` e `91 Days`;
- similaridade/confiança;
- correção manual;
- exclusão de música;
- espera mínima e deduplicação;
- limpeza após parada e pausa;
- validação do webhook.

## Simulador debug

Compile/execute a variante `debug`, ative a detecção e use o cartão **Simulador
debug**. Preencha pacote, título, subtítulo, estado, duração e posição. O evento passa
pelo mesmo parser, resolvedores, confiança e cliente Discord usados pela detecção
real. O cartão é removido em builds release por `BuildConfig.DEBUG`.

## Dados lidos

- pacote e nome amigável do aplicativo;
- metadados de sessões Android: título, display title/subtitle, artista, álbum,
  duração, posição e estado;
- em notificações exclusivamente de mídia: título, texto, subtexto, info text e
  linhas adicionais;
- horário da atualização.

## Dados não coletados

- mensagens comuns;
- conteúdo visual/tela;
- áudio ou microfone;
- dados de acessibilidade;
- senhas ou token pessoal do Discord;
- analytics, identificadores publicitários ou telemetria.

O app não registra URL de webhook, tokens ou notificações brutas nos logs técnicos.

## Estrutura principal

```text
app/src/main/java/com/anipresence/app/
  data/detection/      sessões e NotificationListenerService
  data/anime/          parser, aliases, similaridade e Jikan
  data/discord/        abstração, stub oficial e webhook
  data/preferences/    DataStore e Android Keystore
  domain/model/        modelos
  domain/usecase/      política temporal/deduplicação
  ui/                  Activity, ViewModel e tela Compose
```
