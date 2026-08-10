from pathlib import Path
import re
import shutil

root = Path('.')


def load(path):
    return Path(path).read_text(encoding='utf-8')


def save(path, text):
    Path(path).write_text(text, encoding='utf-8')


# Remove projects/editions that are no longer part of Liftly.
for directory in [
    'AniPresence',
    'app/src/business',
    'app/src/main/java/com/liftly/app/commercial',
]:
    shutil.rmtree(directory, ignore_errors=True)

for file_name in [
    'app/src/main/java/com/liftly/app/ui/screens/AdminRewardsScreen.kt',
    'app/src/main/java/com/liftly/app/ui/screens/BusinessScreen.kt',
    'docs/COMMERCIALIZATION_PLAN.md',
    'docs/PATCH_NOTES_BUSINESS_1.0.0.md',
]:
    Path(file_name).unlink(missing_ok=True)

# Only one APK remains in the repository after the build.
for apk in root.glob('*.apk'):
    apk.unlink()

# One Android application, version 1.5.3 / code 35.
gradle_path = Path('app/build.gradle.kts')
gradle = load(gradle_path)
gradle = re.sub(
    r'\n\s*flavorDimensions \+= "edition"\n\s*productFlavors \{.*?\n\s*\}\n\n\s*buildTypes \{',
    '\n\n    buildTypes {',
    gradle,
    flags=re.S,
)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 35', gradle, count=1)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.5.3"', gradle, count=1)
gradle = gradle.replace('    implementation("com.android.billingclient:billing-ktx:9.1.0")\n', '')
save(gradle_path, gradle)

# Profile screen: remove edition-specific entries.
profile_path = Path('app/src/main/java/com/liftly/app/ui/screens/ProfileScreen.kt')
profile = load(profile_path)
for line in [
    'import androidx.compose.material.icons.filled.BusinessCenter\n',
    'import androidx.compose.material.icons.filled.AdminPanelSettings\n',
    'import com.liftly.app.BuildConfig\n',
]:
    profile = profile.replace(line, '')
profile = profile.replace(
    'fun ProfileScreen(\n    vm: AppViewModel,\n    onOpenBusiness: () -> Unit = {},\n    onOpenRewards: () -> Unit = {},\n    onOpenRewardsAdmin: () -> Unit = {},\n)',
    'fun ProfileScreen(\n    vm: AppViewModel,\n    onOpenRewards: () -> Unit = {},\n)',
)
profile = re.sub(
    r'\n\s{12}if \(BuildConfig\.ADMIN_TOOLS\) \{\n\s{16}item \{.*?\n\s{16}\}\n\s{12}\}',
    '',
    profile,
    flags=re.S,
)
profile = re.sub(
    r'\n\s{12}if \(BuildConfig\.COMMERCIAL_EDITION\) \{\n\s{16}item \{.*?\n\s{16}\}\n\s{12}\}',
    '',
    profile,
    flags=re.S,
)
save(profile_path, profile)

# Rewards presentation: regular user experience only.
models_path = Path('app/src/main/java/com/liftly/app/ui/rewards/RewardsUiModels.kt')
models = load(models_path)
models = re.sub(r'\nenum class RewardsViewerMode \{\n.*?\n\}\n', '\n', models, flags=re.S)
models = re.sub(r'\n\s*val viewerMode: RewardsViewerMode = RewardsViewerMode\.User,', '', models)
models = re.sub(r'\n\s*val onOpenAdminPanel: \(\) -> Unit = \{\},', '', models)
save(models_path, models)

mapper_path = Path('app/src/main/java/com/liftly/app/ui/rewards/RewardsUiMapper.kt')
mapper = load(mapper_path)
mapper = re.sub(r'\n\s*viewerMode: RewardsViewerMode,', '', mapper)
mapper = re.sub(r'\n\s*viewerMode = viewerMode,', '', mapper)
mapper = re.sub(r'\n\s*"ADMIN" -> "Ajuste de teste"', '', mapper)
save(mapper_path, mapper)

rewards_path = Path('app/src/main/java/com/liftly/app/ui/rewards/RewardsScreen.kt')
rewards = load(rewards_path)
rewards = rewards.replace(
    ' * Room, a remote account or the admin simulator without duplicating business rules here.\n',
    ' * Room without duplicating persistence or presentation rules here.\n',
)
rewards = re.sub(
    r'\n\s{12}if \(state\.viewerMode == RewardsViewerMode\.AdminPreview\) \{.*?\n\s{12}\}',
    '',
    rewards,
    flags=re.S,
)
rewards = re.sub(
    r'\n@Composable\nprivate fun AdminPreviewBanner\(.*?\n@Composable\nprivate fun FeaturedRewardCard',
    '\n@Composable\nprivate fun FeaturedRewardCard',
    rewards,
    flags=re.S,
)
save(rewards_path, rewards)

# Main navigation: remove edition-specific routes and screens.
app_path = Path('app/src/main/java/com/liftly/app/ui/LiftlyApp.kt')
app = load(app_path)
app = '\n'.join(
    line for line in app.split('\n')
    if not any(token in line for token in [
        'AdminMissionSimulation',
        'AdminRewardsScreen',
        'AdminRewardsUiState',
        'AdminWorkoutSimulation',
        'BusinessScreen',
        'RewardsViewerMode',
        'com.liftly.app.BuildConfig',
        'com.liftly.app.commercial.',
    ])
)
app = app.replace('                    onOpenBusiness = { navController.navigate("business") },\n', '')
app = app.replace('                    onOpenRewardsAdmin = { navController.navigate("rewards-admin") },\n', '')
app = re.sub(
    r'            composable\("rewards"\) \{.*?            composable\("calendar"\)',
    '            composable("rewards") {\n'
    '                RewardsDestination(\n'
    '                    vm = vm,\n'
    '                    onBack = { navController.popBackStack() },\n'
    '                )\n'
    '            }\n'
    '            composable("calendar")',
    app,
    flags=re.S,
)
app = re.sub(
    r'\n@Composable\nprivate fun BusinessDestination\(.*?\n@Composable\nprivate fun RewardsDestination',
    '\n@Composable\nprivate fun RewardsDestination',
    app,
    flags=re.S,
)
app = app.replace(
    'private fun RewardsDestination(\n    vm: AppViewModel,\n    viewerMode: RewardsViewerMode,\n    onOpenAdminPanel: () -> Unit,\n    onBack: () -> Unit,\n)',
    'private fun RewardsDestination(\n    vm: AppViewModel,\n    onBack: () -> Unit,\n)',
)
app = re.sub(r'\n\s*viewerMode = viewerMode,', '', app)
app = re.sub(r'\n\s*onOpenAdminPanel = onOpenAdminPanel,', '', app)
app = re.sub(
    r'\n@Composable\nprivate fun RewardsAdminDestination\(.*?\nprivate fun sessionRoute',
    '\nprivate fun sessionRoute',
    app,
    flags=re.S,
)
save(app_path, app)

# Remove reward mutation helpers that existed only for the retired test edition.
vm_path = Path('app/src/main/java/com/liftly/app/ui/AppViewModel.kt')
vm = load(vm_path)
vm = vm.replace('import com.liftly.app.domain.WorkoutRewardMetrics\n', '')
vm = re.sub(
    r'\n\s*fun adminGrantRewards\(.*?\n\s*fun updateWeight',
    '\n    fun updateWeight',
    vm,
    flags=re.S,
)
save(vm_path, vm)

repo_path = Path('app/src/main/java/com/liftly/app/data/LiftlyRepository.kt')
repo = load(repo_path)
repo = re.sub(
    r'\n\s*suspend fun adminGrantRewards\(.*?\n\s*suspend fun saveWeight',
    '\n    suspend fun saveWeight',
    repo,
    flags=re.S,
)
save(repo_path, repo)

store_path = Path('app/src/main/java/com/liftly/app/data/LiftlyRewardsStore.kt')
store = load(store_path)
store = store.replace('import java.util.UUID\n', '')
store = re.sub(
    r'\n\s*suspend fun adminGrant\(.*?\n\s*internal suspend fun initializeInTransaction',
    '\n    internal suspend fun initializeInTransaction',
    store,
    flags=re.S,
)
save(store_path, store)

dao_path = Path('app/src/main/java/com/liftly/app/data/LiftlyDao.kt')
dao = load(dao_path)
dao = re.sub(
    r'\n\s*@Query\("UPDATE reward_missions SET progress = MIN\(target, MAX\(0, :progress\)\), completedAt = NULL WHERE id = :id"\)\n\s*suspend fun adminSetRewardMissionProgress\(id: String, progress: Int\): Int',
    '',
    dao,
)
dao = re.sub(
    r'\n\s*@Query\("UPDATE reward_missions SET progress = 0, completedAt = NULL WHERE periodStart <= :at AND periodEnd > :at"\)\n\s*suspend fun adminResetCurrentRewardMissions\(at: Long\): Int',
    '',
    dao,
)
save(dao_path, dao)

# Canonical docs: one app and one APK name.
readme = '''# Liftly

Aplicativo Android nativo, offline-first e em português para montar, organizar e acompanhar treinos de academia. Usa Kotlin, Jetpack Compose, Material Design 3, MVVM, Room, Navigation Compose, Coroutines/StateFlow, DataStore e WorkManager.

## Download — Liftly 1.5.3

**Versão:** `1.5.3` (`versionCode 35`)  
**Pacote:** `com.liftly.app`  
**Android mínimo:** Android 8.0 / API 26

### [Baixar Liftly 1.5.3 (.apk)](https://github.com/sunken000/LIFTLY/raw/refs/heads/master/Liftly-v1.5.3.apk)

Arquivo publicado na raiz do repositório:

```text
Liftly-v1.5.3.apk
```

## 1.5.3

- consolidação do projeto em um único aplicativo Liftly;
- atualização para `versionCode 35` e `versionName 1.5.3`;
- remoção de edições paralelas e de código que não pertence ao aplicativo principal;
- remoção do projeto independente de detecção de mídia que coexistia no repositório;
- manutenção de Rewards, importação por texto e demais recursos do Liftly.

## Principais recursos

- onboarding com treino de demonstração opcional;
- criação, edição, duplicação, arquivamento e exclusão de treinos;
- catálogo offline, exercícios personalizados e favoritos;
- planejamento semanal e calendário;
- importação de fichas por texto com revisão antes de salvar;
- registro de séries, repetições, carga, duração, distância, RIR e dor percebida;
- coach de progressão local;
- modo teste descartável;
- cronômetro de descanso e cronômetro geral;
- histórico, volume, frequência, peso corporal e recordes/e1RM;
- XP, Lift Coins, missões, loja e inventário;
- Health Connect opcional;
- fotos corporais privadas e foto de perfil;
- widget de treino do dia;
- integração opcional com Discord;
- biblioteca local de playlists e abertura no Spotify;
- exportação/importação de backup JSON;
- compartilhamento de fichas por arquivo, QR Code e PDF.

## Compilar

Windows PowerShell:

```powershell
.\\gradlew.bat assembleDebug
```

macOS/Linux:

```bash
./gradlew assembleDebug
```

APK gerado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Testes

```powershell
.\\gradlew.bat testDebugUnitTest
.\\gradlew.bat lintDebug
```

## Privacidade

Os dados principais permanecem no dispositivo. Conta e internet não são necessárias para as funções centrais. Discord, Música e Health Connect são integrações opcionais. Não há anúncios, analytics ou telemetria.

Consulte [Backup e privacidade](docs/BACKUP_E_PRIVACIDADE.md) e [Testes e limitações](docs/TESTES_E_LIMITACOES.md).

## Documentação

- [Arquitetura](docs/ARQUITETURA.md)
- [Banco de dados](docs/BANCO_DE_DADOS.md)
- [Backup e privacidade](docs/BACKUP_E_PRIVACIDADE.md)
- [Música e Spotify](docs/MUSICA_SPOTIFY.md)
- [Patch notes 1.5.0](docs/PATCH_NOTES_1.5.0.md)
- [Patch notes 1.5.1](docs/PATCH_NOTES_1.5.1.md)
- [Patch notes 1.5.2](docs/PATCH_NOTES_1.5.2.md)
- [Patch notes 1.5.3](docs/PATCH_NOTES_1.5.3.md)
'''
save('README.md', readme)

save('CHANGELOG.md', '''# Changelog

## 1.5.3 — 2026-08-10

- Projeto consolidado em um único aplicativo Liftly.
- Versão Android atualizada para `versionCode 35` e `versionName 1.5.3`.
- Código e artefatos de edições paralelas removidos.
- Projeto independente que coexistia no repositório removido.
- Download padronizado como `Liftly-v1.5.3.apk`.

## 1.5.2

- A importação de treino por texto passou a usar o rótulo **Inserir texto**.
- Instruções da tela de importação foram neutralizadas sem alterar o fluxo de análise, revisão e salvamento.

## 1.5.1

- Importador inteligente de fichas de treino por texto, listas e tabelas Markdown.

## 1.5.0

- Sistema de XP, Lift Coins, missões, loja, inventário e recompensas cosméticas.
''')

save('docs/PATCH_NOTES_1.5.3.md', '''# Liftly 1.5.3 — Consolidação do aplicativo

## Versão

- `versionCode`: **35**
- `versionName`: **1.5.3**
- pacote: `com.liftly.app`

## Alterações

- o repositório passa a representar somente o aplicativo Liftly;
- edições paralelas, telas específicas e artefatos antigos foram retirados;
- o projeto independente que estava armazenado junto do Liftly foi removido;
- o APK publicado passa a usar o nome `Liftly-v1.5.3.apk`;
- Rewards, treino por texto, histórico, progressão e demais recursos do aplicativo permanecem.
''')

# Sanitize historical Markdown labels.
banned_markdown = re.compile(r'\b(?:Business|Admin|Pessoal|AniPresence)\b', re.I)
for md in root.rglob('*.md'):
    text = md.read_text(encoding='utf-8')
    text = text.replace('Liftly Pessoal', 'Liftly')
    text = text.replace('Liftly-Pessoal-', 'Liftly-')
    if md.name == 'PATCH_NOTES_1.5.0.md':
        text = re.sub(r'\n## 👤 APK normal.*?(?=\n## |\n---|\Z)', '\n', text, flags=re.S)
        text = re.sub(r'\n## 🧪 APK .*?(?=\n## |\n---|\Z)', '\n', text, flags=re.S)
    lines = [line for line in text.splitlines() if not banned_markdown.search(line)]
    md.write_text('\n'.join(lines).rstrip() + '\n', encoding='utf-8')

# Fail before build if retired edition identifiers remain in text files.
banned = re.compile(
    r'\b(?:Business|Admin|Pessoal|AniPresence)\b|COMMERCIAL_EDITION|ADMIN_TOOLS|com\.liftly\.app\.commercial',
    re.I,
)
leftovers = []
for path in root.rglob('*'):
    if not path.is_file() or '.git' in path.parts or path.suffix.lower() in {
        '.apk', '.jar', '.png', '.jpg', '.jpeg', '.webp'
    }:
        continue
    try:
        text = path.read_text(encoding='utf-8')
    except UnicodeDecodeError:
        continue
    for no, line in enumerate(text.splitlines(), 1):
        if banned.search(line):
            leftovers.append(f'{path}:{no}:{line.strip()}')

if leftovers:
    print('Retired identifiers still present:')
    print('\n'.join(leftovers[:100]))
    raise SystemExit(2)
