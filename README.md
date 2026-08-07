# Liftly

Aplicativo Android nativo, offline-first e em português para montar, organizar e acompanhar treinos de academia. Usa Kotlin, Jetpack Compose, Material Design 3, MVVM, Room, Navigation Compose, Coroutines/StateFlow, DataStore e WorkManager.

A interface 1.3.5 adota uma direção esportiva sóbria: superfícies sólidas, hierarquia tipográfica clara, roxo como destaque e movimento ambiental discreto.

O projeto possui duas edições instaláveis lado a lado: **Liftly Pessoal** (`com.liftly.app`), sem paywall, e **Liftly Business** (`com.liftly.app.business`), uma prévia comercial com catálogo de planos e fundação segura para Google Play Billing.

Os dados de treino e saúde permanecem no dispositivo. Conta e conexão com a internet não são necessárias para as funções principais. A rede é usada pela exportação opcional para Discord e pela aba Música, que aceita links de playlists sem login no Liftly ou conexão de conta Spotify.

## Recursos do MVP

- onboarding curto, pulável, com treino de demonstração opcional;
- sete áreas principais: Hoje, Treinos, Exercícios, Progresso, Cronômetro, Música e Perfil;
- criação, edição, duplicação, arquivamento e exclusão de treinos;
- catálogo offline com 263 exercícios de musculação, peso corporal, funcional, cardio, levantamento olímpico, pliometria e mobilidade;
- busca de exercícios sem exigir acentos ou correspondência de maiúsculas/minúsculas;
- exercícios personalizados e favoritos;
- planejamento semanal com múltiplos treinos e dias de descanso;
- sessão de treino persistente, registro de séries, repetições, carga, duração, distância, RIR e dor percebida;
- coach de progressão local com progressão dupla, histórico, RIR e salvaguardas de dor/deload; nenhuma sugestão altera o treino automaticamente;
- modo teste persistente: executa o treino completo e descarta sessão/séries ao finalizar, sem alterar Progresso ou Histórico;
- cronômetro de descanso flutuante durante a sessão, com notificação persistente em `mm:ss`, vibração e alarme opcionais mesmo fora do app; o alarme reduz temporariamente a música e oferece quatro sons e duração de 1–10 segundos;
- cronômetro geral com pausar, retomar, zerar e registrar voltas;
- aba Música com biblioteca local de links de playlists, embed oficial do Spotify e abertura no aplicativo Spotify; a playlist pública global permanece como destaque configurável;
- histórico detalhado de treinos, com metas planejadas, valores realizados, deltas por série, carga, tempo, distância e calorias estimadas pela combinação de METs;
- exclusão de um treino específico tanto no histórico quanto em **Progresso > Gerenciar**, além da exclusão do dia inteiro e do reset integral;
- histórico de carga, volume, frequência, peso corporal e ranking pessoal por recordes/e1RM;
- meta semanal, sequência de semanas cumpridas e notificações opcionais de progresso;
- calculadora de IMC por idade e sexo, com curvas OMS para 5–19 anos, faixas adultas e referência SISVAN para pessoas idosas;
- sugestões locais e não obrigatórias para a montagem do treino;
- substituição inteligente por grupo muscular, padrão de movimento, equipamento, dificuldade e segurança;
- integração opcional com Health Connect para importar peso/sono e exportar treinos reais concluídos;
- foto de perfil e linha do tempo privada de fotos corporais, com data e comparação lado a lado;
- widget de tela inicial com treino do dia, exercícios, descanso e rotina semanal;
- envio automático opcional do resumo do treino ao Discord, com teste de webhook e retentativas confiáveis;
- fundo animado discreto com parallax, superfícies sólidas e brilho reservado aos estados selecionados;
- temas Roxo Neon animado, Branco suave e Preto OLED sólido;
- exportação e importação de backup JSON;
- exclusão dos dados pessoais do Room e das preferências do DataStore.

As sugestões de treino, o IMC e as calorias são informações gerais. A estimativa calórica usa METs populacionais, peso e duração; não substitui medição fisiológica nem avaliação de profissionais de educação física ou saúde.

## Requisitos de desenvolvimento

- Android Studio compatível com Android Gradle Plugin 9.2.1;
- JDK 17 ou superior;
- Android SDK 36 instalado;
- dispositivo ou emulador com Android 8.0/API 26 ou superior;
- acesso à internet somente na primeira resolução das dependências Gradle.

O projeto inclui Gradle Wrapper 9.4.1. Não é necessário instalar o Gradle globalmente.

## Abrir e executar no Android Studio

1. Abra a pasta raiz `LIFTLY` no Android Studio.
2. Confirme que o Gradle está usando o JDK 17, normalmente o JetBrains Runtime incluído no Android Studio.
3. Aguarde a sincronização do Gradle.
4. Selecione a configuração `app` e um dispositivo com API 26 ou superior.
5. Clique em **Run**.

## Compilar pela linha de comando

No Windows PowerShell:

```powershell
.\gradlew.bat assemblePersonalDebug assembleBusinessDebug
```

No macOS ou Linux:

```bash
./gradlew assemblePersonalDebug assembleBusinessDebug
```

O APK de depuração instalável é esperado em:

```text
app/build/outputs/apk/personal/debug/app-personal-debug.apk
app/build/outputs/apk/business/debug/app-business-debug.apk
```

Esta entrega inclui duas cópias prontas na raiz:

- `Liftly-Pessoal-v1.3.5.apk`;
- `Liftly-Business-v1.0.0-preview.apk`.

O APK `debug` é apropriado para avaliação local. Uma distribuição pública deve usar uma configuração de assinatura própria e uma compilação `release`.

## Instalar o APK

Com ADB e a depuração USB ativada:

```powershell
adb install -r Liftly-Pessoal-v1.3.5.apk
adb install -r Liftly-Business-v1.0.0-preview.apk
```

Também é possível copiar `app-debug.apk` para o dispositivo, abrir o arquivo e autorizar a instalação pela origem escolhida quando o Android solicitar.

## Publicar atualizações no Discord

O projeto inclui `tools/discord_publish.ps1`. Ele usa um incoming webhook vinculado ao canal escolhido e envia uma mensagem com versão, data, SHA-256 e os arquivos que couberem no limite configurado. O URL é lido somente da variável de ambiente `LIFTLY_DISCORD_WEBHOOK_URL`; não o coloque no código, no APK ou no Git.

Na mesma sessão do PowerShell, configure o segredo e publique:

```powershell
$env:LIFTLY_DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/SEU_ID/SEU_TOKEN"
.\tools\discord_publish.ps1 -Message "Liftly Pessoal 1.3.5." -Files @("Liftly-Pessoal-v1.3.5.apk", "README.md")
Remove-Item Env:LIFTLY_DISCORD_WEBHOOK_URL
```

Antes de enviar, valide a mensagem sem fazer rede:

```powershell
.\tools\discord_publish.ps1 -DryRun -Message "Teste do canal"
```

O script usa 10 MB como limite padrão e pula anexos maiores, informando isso na mensagem. O APK atual tem aproximadamente 22 MB; se o servidor/conta aceitar um limite maior, use `-MaxUploadMb 25` ou defina `LIFTLY_DISCORD_MAX_UPLOAD_MB`. Caso contrário, publique apenas a atualização e passe um link externo com `-ArtifactUrl`.

## Como usar

1. Conclua ou pule o onboarding. Na última etapa, escolha se deseja adicionar o treino de demonstração.
2. Em **Treinos**, crie uma rotina, escolha os dias e adicione exercícios do catálogo. Ajuste séries, repetições, carga, descanso, tipo e observações.
3. Use **Analisar treino** para consultar recomendações gerais. As recomendações não modificam o treino automaticamente.
4. Em **Calendário**, associe treinos a datas, defina descanso ou copie a programação de uma semana.
5. Em **Hoje**, inicie o treino programado ou use **Testar sem salvar**. Autorize notificações para acompanhar o exercício atual e o descanso fora do app. No modo normal, registre carga, repetições e, opcionalmente, RIR/dor; o coach passa a sugerir manutenção, aumento, redução, deload ou cautela. No modo teste, tudo é descartado ao encerrar.
6. Use **Cronômetro** para medir intervalos livres e registrar voltas sem interromper o treino.
7. Consulte cargas, volume, ranking de recordes, meta e sequência em **Progresso**; em **Gerenciar**, expanda uma data para excluir somente um treino, excluir o dia inteiro ou resetar tudo.
8. Em **Música**, use **Suas playlists** para salvar qualquer link/URI/ID de playlist e ver o embed oficial. As playlists ficam só no aparelho; se uma privada não carregar no embed, use **Abrir no Spotify**.
9. Em **Perfil**, adicione foto de perfil e fotos corporais datadas, compare duas imagens, registre peso, consulte o histórico e configure a integração opcional do Discord.
10. Para adicionar o widget, mantenha pressionada a tela inicial do Android, escolha **Widgets > Liftly > Treino de hoje** e arraste-o para a tela.

## Testes e verificações

Comandos previstos:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleDebug
```

`connectedDebugAndroidTest` exige emulador ou dispositivo conectado. Consulte o [checklist de testes](docs/TESTES_E_LIMITACOES.md) para ver exatamente o que foi validado nesta entrega.

## Estrutura do projeto

```text
app/src/main/java/com/liftly/app/
├── data/       Room, DAO, catálogo, repositório e DataStore
├── domain/     IMC, calorias, coach e metas/sequências determinísticas
├── integration/ webhook do Discord, playlist pública e biblioteca local de links do Spotify
├── service/    notificações, metas e descanso em segundo plano
├── widget/     widget clássico do treino de hoje
└── ui/         ViewModel, navegação, telas, tema e componentes Compose
```

A arquitetura e o fluxo de dados estão detalhados em [docs/ARQUITETURA.md](docs/ARQUITETURA.md). O esquema Room completo está em [docs/BANCO_DE_DADOS.md](docs/BANCO_DE_DADOS.md).

## Documentação

- [Arquitetura e bibliotecas](docs/ARQUITETURA.md)
- [Banco de dados Room](docs/BANCO_DE_DADOS.md)
- [Backup e privacidade](docs/BACKUP_E_PRIVACIDADE.md)
- [Música e playlist remota](docs/MUSICA_SPOTIFY.md)
- [Patch notes 1.1.8](docs/PATCH_NOTES_1.1.8.md)
- [Patch notes 1.1.9](docs/PATCH_NOTES_1.1.9.md)
- [Patch notes 1.2.0](docs/PATCH_NOTES_1.2.0.md)
- [Patch notes 1.2.1](docs/PATCH_NOTES_1.2.1.md)
- [Patch notes 1.2.2](docs/PATCH_NOTES_1.2.2.md)
- [Patch notes 1.3.0](docs/PATCH_NOTES_1.3.0.md)
- [Patch notes 1.3.1](docs/PATCH_NOTES_1.3.1.md)
- [Patch notes 1.3.2](docs/PATCH_NOTES_1.3.2.md)
- [Patch notes 1.3.3](docs/PATCH_NOTES_1.3.3.md)
- [Patch notes 1.3.4](docs/PATCH_NOTES_1.3.4.md)
- [Patch notes 1.3.5](docs/PATCH_NOTES_1.3.5.md)
- [Patch notes 1.5.0](docs/PATCH_NOTES_1.5.0.md)
- [Patch notes 1.5.1](docs/PATCH_NOTES_1.5.1.md)
- [Patch notes 1.5.2](docs/PATCH_NOTES_1.5.2.md)
- [Checklist de testes e limitações](docs/TESTES_E_LIMITACOES.md)

## Privacidade

O manifesto solicita internet para a integração opcional com Discord e para a aba Música. O Liftly não conecta contas do Spotify nem guarda tokens: armazena localmente apenas IDs e nomes das playlists adicionadas pelo usuário. Ao atualizar uma versão que possuía OAuth, as credenciais legadas são apagadas automaticamente. Não há anúncios, analytics ou telemetria. O backup automático do Android está desativado, e fotos/backup usam o seletor de documentos sem acesso amplo ao armazenamento. O JSON exportado não é criptografado; guarde-o em local protegido. Leia a [política de backup e privacidade](docs/BACKUP_E_PRIVACIDADE.md) antes de distribuir o aplicativo.
