# Liftly

Aplicativo Android nativo, offline-first e em português para montar, organizar e acompanhar treinos de academia. O projeto usa Kotlin, Jetpack Compose, Material Design 3, MVVM, Room, Navigation Compose, Coroutines/StateFlow, DataStore e WorkManager.

## Download — Liftly Pessoal 1.5.2

**Versão atual:** `1.5.2` (`versionCode 34`)  
**Pacote:** `com.liftly.app`  
**Android mínimo:** Android 8.0 / API 26

### [Baixar Liftly Pessoal 1.5.2 (.apk)](https://github.com/sunken000/LIFTLY/raw/refs/heads/master/Liftly-Pessoal-v1.5.2.apk)

O APK também está disponível diretamente na raiz do repositório como:

```text
Liftly-Pessoal-v1.5.2.apk
```

> O APK disponibilizado no repositório é destinado à instalação e avaliação direta. Para publicação em loja, use uma build `release` assinada com uma chave de distribuição própria.

## O que há de novo na série 1.5

### 1.5.2 — Inserção de treino por texto

- a opção de importação passou a se chamar **Inserir texto**;
- as instruções da tela ficaram neutras;
- o fluxo continua permitindo analisar, revisar e salvar uma ou várias fichas antes de gravar os dados.

### 1.5.1 — Importador inteligente

Em **Treinos → Importar → Inserir texto**, o Liftly consegue interpretar fichas em texto, listas e tabelas Markdown, preservando a ordem original dos treinos e exercícios.

Quando informados, podem ser reconhecidos nome do treino, dias, exercícios, séries, repetições, carga, descanso, RIR e tipo de série. Antes de salvar, uma prévia permite revisar e corrigir os dados detectados.

### 1.5.0 — Rewards

A versão 1.5 introduziu uma economia local de progressão:

- XP permanente e níveis;
- Lift Coins;
- missões diárias, semanais e mensais;
- recompensas por treino, ficha completa, RIR e recordes pessoais;
- loja de personalização;
- inventário de cosméticos;
- temas, wallpapers, molduras, sons de descanso e títulos de perfil;
- extrato de ganhos, compras e ajustes;
- proteção contra recompensa duplicada para a mesma sessão;
- modo teste sem impacto em progresso, missões, XP ou Lift Coins.

## Edições do projeto

O projeto possui três flavors Android:

| Edição | Application ID | Versão | Finalidade |
|---|---|---:|---|
| **Personal** | `com.liftly.app` | `1.5.2` | experiência normal, sem ferramentas administrativas |
| **Business** | `com.liftly.app.business` | `1.0.0` | prévia comercial e fundação para Google Play Billing |
| **Admin** | `com.liftly.app.admin` | `1.5.2-admin` | validação da economia e simulações de teste |

Na raiz do repositório estão disponíveis os APKs **Personal 1.5.2** e **Admin 1.5.2**. A edição Business pode ser compilada a partir do código-fonte.

## Principais recursos

- onboarding curto, pulável, com treino de demonstração opcional;
- áreas principais para Hoje, Treinos, Exercícios, Progresso, Cronômetro, Música e Perfil;
- criação, edição, duplicação, arquivamento e exclusão de treinos;
- catálogo offline com 263 exercícios;
- exercícios personalizados e favoritos;
- planejamento semanal com múltiplos treinos e dias de descanso;
- importação de fichas por texto com revisão antes de salvar;
- sessão de treino persistente;
- registro de séries, repetições, carga, duração, distância, RIR e dor percebida;
- coach de progressão local com progressão dupla, histórico, RIR e salvaguardas de dor/deload;
- modo teste que executa a ficha e descarta os resultados ao finalizar;
- cronômetro de descanso flutuante com notificação, vibração e alarme opcionais;
- cronômetro geral com pausar, retomar, zerar e voltas;
- histórico detalhado de treinos;
- histórico de carga, volume, frequência e peso corporal;
- ranking pessoal por recordes/e1RM;
- metas semanais e sequência de semanas cumpridas;
- sistema de XP, Lift Coins, missões, loja e inventário;
- calculadora de IMC com referências por faixa etária;
- substituição inteligente de exercícios;
- integração opcional com Health Connect;
- foto de perfil e linha do tempo privada de fotos corporais;
- widget de tela inicial com treino do dia;
- exportação opcional de resumo de treino para Discord;
- biblioteca local de links de playlists e abertura no Spotify;
- temas Roxo Neon, Branco suave e Preto OLED, além de cosméticos desbloqueáveis;
- exportação e importação de backup JSON;
- compartilhamento de fichas por arquivo do Liftly, QR Code e PDF;
- exclusão dos dados pessoais armazenados localmente.

As sugestões de treino, o IMC e as calorias são informações gerais. As estimativas não substituem avaliação de profissionais de educação física ou saúde.

## Privacidade e funcionamento offline

Os dados de treino e saúde permanecem no dispositivo. Conta e conexão com a internet não são necessárias para as funções principais.

A rede é usada apenas em recursos opcionais, como:

- integração com Discord;
- carregamento/abertura de conteúdo da aba Música;
- configuração remota opcional da playlist pública.

O Liftly não conecta contas do Spotify nem armazena tokens do Spotify. Não há anúncios, analytics ou telemetria. O backup JSON exportado não é criptografado e deve ser guardado em local protegido.

Consulte [Backup e privacidade](docs/BACKUP_E_PRIVACIDADE.md) para os detalhes.

## Instalar o APK pessoal

### Pelo próprio Android

1. Baixe o arquivo **Liftly-Pessoal-v1.5.2.apk** pelo link no início deste README.
2. Abra o APK no dispositivo.
3. Quando solicitado, autorize a instalação de aplicativos pela origem usada para abrir o arquivo.
4. Conclua a instalação.

### Com ADB

Com a depuração USB ativada:

```bash
adb install -r Liftly-Pessoal-v1.5.2.apk
```

A opção `-r` permite atualizar uma instalação existente preservando os dados do aplicativo, desde que a assinatura seja compatível.

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
4. Selecione a variante desejada.
5. Execute em um dispositivo ou emulador com API 26 ou superior.

## Compilar pela linha de comando

### Windows PowerShell

```powershell
.\gradlew.bat assemblePersonalDebug
```

Para gerar todas as variantes de depuração:

```powershell
.\gradlew.bat assemblePersonalDebug assembleBusinessDebug assembleAdminDebug
```

### macOS / Linux

```bash
./gradlew assemblePersonalDebug
```

Para gerar todas as variantes de depuração:

```bash
./gradlew assemblePersonalDebug assembleBusinessDebug assembleAdminDebug
```

O APK Personal gerado pelo Gradle fica em:

```text
app/build/outputs/apk/personal/debug/app-personal-debug.apk
```

## Testes e verificações

Para a edição Personal:

```powershell
.\gradlew.bat testPersonalDebugUnitTest
.\gradlew.bat lintPersonalDebug
.\gradlew.bat assemblePersonalDebug
```

Testes instrumentados exigem um emulador ou dispositivo conectado. Consulte [Testes e limitações](docs/TESTES_E_LIMITACOES.md) para o checklist do projeto.

## Publicar atualizações no Discord

O projeto inclui `tools/discord_publish.ps1`. O script lê o webhook apenas da variável de ambiente `LIFTLY_DISCORD_WEBHOOK_URL`.

Exemplo:

```powershell
$env:LIFTLY_DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/SEU_ID/SEU_TOKEN"
.\tools\discord_publish.ps1 -Message "Liftly Pessoal 1.5.2." -Files @("Liftly-Pessoal-v1.5.2.apk", "README.md")
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
├── domain/       IMC, calorias, coach, progressão e rewards
├── integration/ Discord, Música e integrações opcionais
├── service/      notificações, metas e descanso em segundo plano
├── widget/       widget do treino de hoje
└── ui/           ViewModel, navegação, telas, tema e componentes Compose
```

A arquitetura e o fluxo de dados estão detalhados em [docs/ARQUITETURA.md](docs/ARQUITETURA.md). O esquema Room completo está em [docs/BANCO_DE_DADOS.md](docs/BANCO_DE_DADOS.md).

## Documentação

- [Arquitetura e bibliotecas](docs/ARQUITETURA.md)
- [Banco de dados Room](docs/BANCO_DE_DADOS.md)
- [Backup e privacidade](docs/BACKUP_E_PRIVACIDADE.md)
- [Música e playlist remota](docs/MUSICA_SPOTIFY.md)
- [Patch notes 1.5.0](docs/PATCH_NOTES_1.5.0.md)
- [Patch notes 1.5.1](docs/PATCH_NOTES_1.5.1.md)
- [Patch notes 1.5.2](docs/PATCH_NOTES_1.5.2.md)
- [Checklist de testes e limitações](docs/TESTES_E_LIMITACOES.md)

## Licença e distribuição

Antes de redistribuir ou publicar o aplicativo em lojas, revise as dependências, a política de privacidade, a assinatura da build e os requisitos da plataforma de distribuição utilizada.
