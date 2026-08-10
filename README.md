# Liftly

Aplicativo Android nativo, offline-first e em português para montar, organizar e acompanhar treinos de academia. O projeto usa Kotlin, Jetpack Compose, Material Design 3, MVVM, Room, Navigation Compose, Coroutines/StateFlow, DataStore e WorkManager.

## Versão atual — 1.5.3

- **versionName:** `1.5.3`
- **versionCode:** `35`
- **applicationId:** `com.liftly.app`
- **Android mínimo:** Android 8.0 / API 26
- **targetSdk / compileSdk:** 36

O repositório mantém uma única aplicação Android: **Liftly**.

## Novidades da 1.5.3

- consolidação do projeto em uma única aplicação;
- atualização do código Android para `versionName 1.5.3` e `versionCode 35`;
- simplificação da configuração Gradle para uma única variante principal;
- limpeza de artefatos e documentação que não fazem parte do aplicativo atual;
- manutenção dos recursos introduzidos na série 1.5, incluindo Rewards e importação de treinos por texto.

## Recursos principais

- onboarding curto, pulável, com treino de demonstração opcional;
- áreas principais para Hoje, Treinos, Exercícios, Progresso, Cronômetro, Música e Perfil;
- criação, edição, duplicação, arquivamento e exclusão de treinos;
- catálogo offline com 263 exercícios;
- busca sem exigir acentos ou correspondência de maiúsculas/minúsculas;
- exercícios personalizados e favoritos;
- planejamento semanal com múltiplos treinos e dias de descanso;
- importação de fichas por texto, listas e tabelas Markdown com revisão antes de salvar;
- sessão de treino persistente;
- registro de séries, repetições, carga, duração, distância, RIR e dor percebida;
- coach de progressão local com progressão dupla, histórico, RIR e salvaguardas de dor/deload;
- modo teste que executa o treino e descarta a sessão ao finalizar;
- cronômetro de descanso flutuante com notificação, vibração e alarme opcionais;
- cronômetro geral com pausar, retomar, zerar e voltas;
- histórico detalhado de treinos;
- histórico de carga, volume, frequência e peso corporal;
- ranking por recordes/e1RM;
- metas semanais e sequência de semanas cumpridas;
- sistema de XP, Lift Coins, missões, loja e inventário;
- calculadora de IMC com referências por faixa etária;
- substituição inteligente de exercícios;
- integração opcional com Health Connect;
- foto de perfil e linha do tempo privada de fotos corporais;
- widget de tela inicial com treino do dia;
- envio opcional de resumo de treino para Discord;
- biblioteca local de links de playlists e abertura no Spotify;
- temas e cosméticos desbloqueáveis;
- exportação e importação de backup JSON;
- compartilhamento de fichas por arquivo, QR Code e PDF;
- exclusão dos dados pessoais armazenados localmente.

As sugestões de treino, o IMC e as estimativas de calorias são informações gerais e não substituem avaliação profissional.

## Rewards

A série 1.5 inclui uma economia local de progressão:

- XP permanente e níveis;
- Lift Coins;
- missões diárias, semanais e mensais;
- recompensas por conclusão de treino, ficha completa, RIR e recordes pessoais;
- loja de personalização;
- inventário de cosméticos;
- temas, wallpapers, molduras, sons de descanso e títulos de perfil;
- extrato de ganhos e compras;
- proteção contra recompensa duplicada para a mesma sessão;
- modo teste sem impacto em progresso, missões, XP ou Lift Coins.

## Importar treinos por texto

Abra **Treinos → Importar → Inserir texto**. O Liftly interpreta formatos comuns em português, incluindo listas e tabelas Markdown.

Quando presentes no texto, podem ser reconhecidos:

- nome do treino;
- dias;
- exercícios;
- séries;
- repetições;
- carga;
- descanso;
- RIR;
- tipo de série.

Antes de salvar, uma prévia permite revisar e corrigir os dados detectados.

## Privacidade e funcionamento offline

Os dados de treino e saúde permanecem no dispositivo. Conta e conexão com a internet não são necessárias para as funções principais.

A rede é usada somente por integrações opcionais, como:

- Discord;
- conteúdo da aba Música;
- configuração remota opcional da playlist pública;
- Health Connect, conforme disponibilidade e permissões do dispositivo.

O Liftly não conecta contas do Spotify nem armazena tokens do Spotify. Não há anúncios, analytics ou telemetria. O backup JSON exportado não é criptografado e deve ser guardado em local protegido.

Consulte [Backup e privacidade](docs/BACKUP_E_PRIVACIDADE.md) para os detalhes.

## Requisitos de desenvolvimento

- Android Studio compatível com Android Gradle Plugin 9.2.1;
- JDK 17 ou superior;
- Android SDK 36;
- dispositivo ou emulador com Android 8.0 / API 26 ou superior;
- acesso à internet para resolver dependências Gradle quando necessário.

O projeto inclui Gradle Wrapper 9.4.1.

## Abrir no Android Studio

1. Abra a pasta raiz `LIFTLY` no Android Studio.
2. Configure o Gradle para usar JDK 17 ou superior.
3. Aguarde a sincronização do projeto.
4. Selecione a configuração `app`.
5. Execute em um dispositivo ou emulador com API 26 ou superior.

## Compilar pela linha de comando

### Windows PowerShell

```powershell
.\gradlew.bat assembleDebug
```

### macOS / Linux

```bash
./gradlew assembleDebug
```

O APK de depuração gerado fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para uma distribuição pública, configure assinatura própria e gere uma build `release`.

## Instalar com ADB

Depois de compilar:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A opção `-r` preserva os dados somente quando a assinatura do APK é compatível com a instalação existente.

## Testes e verificações

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Testes instrumentados exigem um dispositivo ou emulador conectado:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Consulte [Testes e limitações](docs/TESTES_E_LIMITACOES.md) para o checklist do projeto.

## Publicar atualizações no Discord

O projeto inclui `tools/discord_publish.ps1`. O webhook é lido exclusivamente da variável de ambiente `LIFTLY_DISCORD_WEBHOOK_URL`.

Exemplo:

```powershell
$env:LIFTLY_DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/SEU_ID/SEU_TOKEN"
.\tools\discord_publish.ps1 -Message "Liftly 1.5.3." -Files @("README.md")
Remove-Item Env:LIFTLY_DISCORD_WEBHOOK_URL
```

Validação sem envio:

```powershell
.\tools\discord_publish.ps1 -DryRun -Message "Teste do canal"
```

Nunca coloque o webhook no código, no APK ou no Git.

## Estrutura do projeto

```text
app/src/main/java/com/liftly/app/
├── data/         Room, DAO, catálogo, repositório e DataStore
├── domain/       IMC, calorias, coach, progressão e Rewards
├── integration/ Discord, Música e integrações opcionais
├── service/      notificações, metas e descanso em segundo plano
├── widget/       widget do treino de hoje
└── ui/           ViewModel, navegação, telas, tema e componentes Compose
```

A arquitetura e o fluxo de dados estão detalhados em [docs/ARQUITETURA.md](docs/ARQUITETURA.md). O esquema Room está em [docs/BANCO_DE_DADOS.md](docs/BANCO_DE_DADOS.md).

## Documentação

- [Arquitetura e bibliotecas](docs/ARQUITETURA.md)
- [Banco de dados Room](docs/BANCO_DE_DADOS.md)
- [Backup e privacidade](docs/BACKUP_E_PRIVACIDADE.md)
- [Música e playlist remota](docs/MUSICA_SPOTIFY.md)
- [Patch notes 1.5.0](docs/PATCH_NOTES_1.5.0.md)
- [Patch notes 1.5.1](docs/PATCH_NOTES_1.5.1.md)
- [Patch notes 1.5.2](docs/PATCH_NOTES_1.5.2.md)
- [Patch notes 1.5.3](docs/PATCH_NOTES_1.5.3.md)
- [Checklist de testes e limitações](docs/TESTES_E_LIMITACOES.md)
