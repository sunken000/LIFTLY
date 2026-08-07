# Arquitetura e dependências

## Visão geral

O Liftly é um aplicativo Android de módulo único (`app`) e orientação offline-first. A implementação segue MVVM com um `AppViewModel` compartilhado pelo grafo principal, um repositório como fronteira de dados e Room como fonte de verdade para os dados do usuário.

```text
Compose Screen
    │ eventos / coleta de StateFlow
    ▼
AppViewModel
    │ coroutines
    ▼
LiftlyRepository ───── PreferencesRepository ─── Discord/WorkManager (opt-in)
    │                         │
    ▼                         ▼
LiftlyDao / Room          Preferences DataStore

MusicScreen ─┬─ MusicRepository ───────────── JSON público HTTPS / Spotify oEmbed
             └─ PersonalSpotifyPlaylistRepository ─ SharedPreferences local / Spotify Embed
```

Não há backend próprio, conta Liftly, analytics ou sincronização dos dados pessoais em nuvem. A aba Música mantém a playlist pública HTTPS/oEmbed e uma biblioteca local de IDs com Embed oficial. Não existe login Spotify, OAuth, Web API ou App Remote no APK.

## Pacotes

### `com.liftly.app`

- `MainActivity`: ativa edge-to-edge, cria o `AppViewModel` e inicia a árvore Compose;
- `LiftlyApplication`: composição manual das dependências `LiftlyDatabase`, `LiftlyRepository`, `PreferencesRepository`, `MusicRepository` e `PersonalSpotifyPlaylistRepository`.

### `com.liftly.app.data`

- `Entities.kt`: nove entidades persistidas pelo Room e modelos de consulta/resumo;
- `LiftlyDao.kt`: queries, `Flow`, `Upsert` e operações transacionais;
- `LiftlyDatabase.kt`: banco `liftly.db`, versão 5, com migrações explícitas 1→2, 2→3, 3→4 e 4→5;
- `LiftlyRepository.kt`: regras de persistência, transações, seed, sessões, progressão e backup;
- `ExerciseCatalog.kt`: catálogo offline com 263 registros e identificadores estáveis;
- `PreferencesRepository.kt`: preferências simples no DataStore.

### `com.liftly.app.domain`

- `BmiCalculator`: valida peso, altura e idade; aplica curvas LMS da OMS por idade/sexo entre 5 e 19 anos, faixas adultas entre 20 e 59 e pontos de corte SISVAN a partir de 60 anos;
- `WhoBmiReference`: parâmetros LMS oficiais da OMS 2006/2007 usados pelo cálculo de escore-z de IMC para idade;
- `WorkoutCalorieEstimator`: estima o gasto bruto de cada sessão pela fórmula `MET × 3,5 × peso(kg) ÷ 200 × minutos`, ponderando os METs pela composição das séries concluídas; usa valores do 2024 Adult Compendium of Physical Activities e limita durações anormais a quatro horas;
- `WorkoutAnalyzer`: mecanismo local e determinístico que produz sugestões sem alterar o treino.
- `ProgressionCoach`: coach determinístico por progressão dupla, histórico, RIR e dor; dá prioridade a cautela e nunca aplica a sugestão sozinho;
- `TrainingMomentumCalculator`: calcula meta atual e sequências de semanas completas sem depender de rede.

A estimativa calórica usa o peso atual do perfil e o intervalo real entre início/fim. Séries por tempo ou distância dão influência proporcional a blocos longos de cardio; as demais participam pela quantidade concluída. O resultado é uma estimativa populacional de gasto bruto (inclui o componente de repouso de 1 MET), não uma medição individual por frequência cardíaca ou calorimetria indireta.

O analisador verifica configuração inválida, exercício ausente ou duplicado, movimentos redundantes, isoladores antes de compostos, descanso curto, volume alto, excesso de técnicas intensificadoras, recuperação semanal, balanço entre padrões e padrões básicos ausentes. Os limiares são heurísticas gerais configuráveis, não prescrição profissional.

### `com.liftly.app.ui`

- `AppViewModel`: converte os `Flow` do repositório em `StateFlow`, executa ações em `viewModelScope` e expõe feedback;
- `LiftlyApp`: tema, fundo animado global, `NavHost`, barra inferior translúcida e Snackbar;
- `screens`: onboarding, hoje/sessão, treinos, exercícios, cronômetro, música, calendário, progresso e perfil/saúde;
- `theme`: paletas clara/escura, tipografia, formas e cores de estado;
- `components`: componentes gerais para fundo, seções, estados, estatísticas e gráfico de linha.

### `com.liftly.app.util`

- `SearchText`: normalização compartilhada para buscas sem distinção de acentos, caixa ou ordem dos termos.

### `com.liftly.app.service`

- `WorkoutTrackingService`: serviço em primeiro plano iniciado pelo usuário; mantém a notificação do exercício atual, atualiza o descanso em `mm:ss`, persiste o prazo para recriação do processo e dispara som/vibração fora da interface;
- durante um descanso, o serviço usa um `PARTIAL_WAKE_LOCK` com timeout até o fim do intervalo mais uma margem curta; o lock é liberado ao concluir, pular, finalizar a sessão ou destruir o serviço;
- o reprodutor mantém um segundo lock independente apenas durante os 1–10 segundos selecionados para o alarme e o libera junto com áudio/foco.
- `ProgressNotificationManager`: publica progresso da meta e sequência somente quando as respectivas opções estão habilitadas.

### `com.liftly.app.integration.discord`

- valida exclusivamente endpoints HTTPS oficiais de webhook do Discord;
- cria embed com duração, séries, volume, calorias, deltas planejado→realizado e esforço;
- `WorkManager` retenta falhas de rede, HTTP 429 e 5xx; modo teste e sessão inacabada são ignorados;
- não segue redirecionamentos, não permite menções e nunca registra o token em log.

### `com.liftly.app.integration.spotify`

- valida IDs públicos de playlist, o endpoint de configuração HTTPS e a origem da capa retornada pelo Spotify;
- consulta um JSON de até 64 KB, sem redirecionamentos, com `ETag`/`If-None-Match`, timeouts e rejeição de revisões antigas;
- mantém a última configuração pública válida em `SharedPreferences` privadas e usa o oEmbed oficial somente para título e capa;
- guarda, apenas em `SharedPreferences` privadas, IDs e nomes de playlists adicionadas manualmente pelo usuário; esses dados não entram no JSON global;
- renderiza o Embed oficial em `WebView` isolada, com ID de 22 caracteres validado, sem ponte JavaScript, sem acesso a arquivos/conteúdo local e sem conteúdo misto; playlists privadas podem exigir a abertura no app Spotify;
- não implementa OAuth, Web API, App Remote, Web Playback SDK, captura de áudio ou clonagem da interface do Spotify.

### `com.liftly.app.widget`

- `TodayWorkoutWidgetProvider`: widget clássico (`RemoteViews`) com agenda explícita, recorrência semanal, descanso e estado vazio;
- atualiza a cada 30 minutos, em mudanças de data/fuso e após alterações relevantes solicitadas pelo `AppViewModel`.

### `com.liftly.app.audio`

- `RestAlertPlayer`: sintetiza localmente os quatro alarmes em PCM, monta o buffer completo de 1–10 segundos e reproduz pelo `AudioTrack` com atributos de mídia; se a saída PCM falhar, tenta um tom nativo do sistema como fallback;
- antes do alarme, solicita foco transitório `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, permitindo ao Android reduzir temporariamente a música; ao terminar, cancelar ou perder foco, libera `AudioTrack` e devolve o foco imediatamente.

## Estado e concorrência

- Room emite `Flow` para exercícios, treinos, itens, programação, sessões, séries, pesos, fotos corporais e perfil.
- `AppViewModel` usa `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ...)`.
- Alterações são funções `suspend` disparadas no `viewModelScope`; edição e conclusão de uma série são mescladas e persistidas em uma única transação para impedir sobrescritas concorrentes.
- Operações de várias tabelas, como duplicar treino, iniciar/finalizar sessão, copiar semana e importar backup, usam transações do Room.
- Identificadores criados pelo usuário são UUID em texto. Registros internos de demonstração e catálogo usam IDs estáveis.

## Navegação

Rotas atuais do `NavHost`:

| Rota | Destino |
|---|---|
| `onboarding` | apresentação inicial e opção de dados de demonstração |
| `today` | treino do dia |
| `workouts` | montagem e organização de treinos |
| `exercises` | catálogo e exercícios personalizados |
| `stopwatch` | cronômetro livre e registro local de voltas |
| `music` | playlist global e biblioteca local com Embed oficial do Spotify |
| `progress` | histórico e indicadores |
| `profile` | perfil, saúde, preferências, backup e privacidade |
| `calendar` | programação semanal |
| `session/{sessionId}` | execução de uma sessão |

A barra inferior aparece somente nas sete rotas principais. Calendário e sessão são destinos secundários. A navegação entre abas preserva e restaura estado por `saveState`/`restoreState`.

## Persistência

Room armazena o domínio principal. DataStore armazena preferências de interface e onboarding. Essa separação evita que opções de apresentação se misturem ao histórico de treino.

Preferências atuais:

| Chave | Tipo | Padrão |
|---|---|---|
| `onboarding_done` | Boolean | `false` |
| `theme` | String | `Roxo Neon` |
| `haptics` | Boolean | `true` |
| `rest_timer` | Boolean | `true` |
| `rest_end_vibration` | Boolean | `true` |
| `rest_end_sound` | Boolean | `true` |
| `rest_end_sound_type` | String | `ascendente` |
| `rest_end_sound_duration_seconds` | Int | `2` |
| `demo_enabled` | Boolean | `false` |
| `exercise_filters` | String | vazio |
| `profile_photo_uri` | String | vazio |
| `weekly_workout_goal` | Int | `3` |
| `goal_notifications` | Boolean | `true` |
| `streak_notifications` | Boolean | `true` |
| `discord_webhook_enabled` | Boolean | `false` |
| `discord_webhook_url` | String | vazio |

Os novos valores de `theme` são `Roxo Neon`, `Branco` e `Preto`; valores legados são mapeados de forma compatível. O padrão de novas instalações é `Roxo Neon`.

O estado operacional da notificação ativa fica separado em `SharedPreferences` privadas do serviço. Ele guarda apenas nomes exibidos e os prazos monotônico/civil do descanso, permitindo reconstruir a contagem depois de uma recriação do processo ou mudança de boot. Esse estado é apagado ao finalizar o treino, importar backup, resetar o progresso ou apagar os dados.

Sessões em modo teste persistem com `isTestMode = true` enquanto estão abertas. Ao finalizar, a sessão e suas séries são excluídas na mesma transação e não atualizam agenda, progresso, recordes ou backup. Exclusões por data usam o dia local de `startedAt`, igual ao agrupamento mostrado pela interface.

Em **Progresso > Gerenciar**, a interface agrupa sessões concluídas por data e oferece duas operações distintas: excluir uma sessão pelo `id` (cascade somente de suas séries) ou excluir todas as sessões concluídas no intervalo civil daquela data. Sessões em andamento e de teste não entram na lista.

O esquema relacional completo está em [BANCO_DE_DADOS.md](BANCO_DE_DADOS.md).

## Catálogo inicial

`ExerciseCatalog` cria 263 exercícios offline nas categorias musculação, peso corporal, funcional, cardio, levantamento olímpico, pliometria e mobilidade. Cada item inclui grupo principal, músculos secundários, equipamento, dificuldade, tipo de movimento, instruções, cuidados e unidade de acompanhamento.

Os IDs têm o prefixo `builtin.` e são estáveis. A inicialização insere o catálogo quando não encontra exercícios nativos. Exercícios personalizados usam o prefixo `custom.` seguido de UUID.

## Bibliotecas e versões

Versões abaixo extraídas de `build.gradle.kts`, `app/build.gradle.kts` e `gradle-wrapper.properties`.

### Ferramentas de build

| Item | Versão/configuração |
|---|---|
| Gradle Wrapper | 9.4.1 |
| Android Gradle Plugin | 9.2.1 |
| Kotlin integrado ao AGP | 2.3.x |
| Kotlin Compose Compiler Plugin | 2.3.21 |
| Kotlin Symbol Processing (KSP) | 2.3.9 |
| Room Gradle Plugin | 2.8.4 |
| Java/JVM target | 17 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 26 |

### Aplicação

| Biblioteca | Versão |
|---|---|
| Compose BOM | 2026.06.00 |
| Compose UI, Foundation, Material 3 e UI tooling preview | gerenciada pelo BOM |
| Material Icons Extended | gerenciada pelo BOM |
| AndroidX Core KTX | 1.17.0 |
| Activity Compose | 1.13.0 |
| Navigation Compose | 2.9.8 |
| Lifecycle Runtime Compose | 2.10.0 |
| Lifecycle ViewModel Compose/KTX | 2.10.0 |
| Preferences DataStore | 1.2.1 |
| Room Runtime/KTX/Compiler | 2.8.4 |
| WorkManager Runtime | 2.11.2 |

O compilador Room usa KSP e exporta o schema versionado em `app/schemas`. A serialização de backup usa `org.json`, disponível na plataforma Android, sem biblioteca externa.

## Referência da estimativa calórica

- Herrmann SD et al. *2024 Adult Compendium of Physical Activities: A third update of the energy costs of human activities*. Journal of Sport and Health Science 13(1):6–12. DOI: [10.1016/j.jshs.2023.10.010](https://doi.org/10.1016/j.jshs.2023.10.010).
- [Tabela oficial do 2024 Adult Compendium](https://pacompendium.com/wp-content/uploads/2024/02/1_2024-adult-compendium_1_2024.pdf), usada para os METs de musculação, peso corporal, caminhada, corrida, bicicleta, remo, elíptico, escada, corda, natação e atividades funcionais.

O Compêndio padroniza `1 MET = 3,5 mL O₂/kg/min`. A fórmula do app converte esse consumo para gasto bruto aproximado em kcal. A ponderação de uma sessão mista é uma inferência do Liftly sobre as séries registradas; ela não é uma alegação de precisão individual.

## Referências do coach de progressão

- [ACSM — Position Stand 2026 sobre prescrição de treino resistido](https://acsm.org/science-spotlight-acsm-releases-new-position-stand-on-resistance-training/);
- [Lovegrove et al. — confiabilidade do RIR para prescrever carga](https://pubmed.ncbi.nlm.nih.gov/36135029/);
- [Plotkin et al. — progressão por carga ou por repetições](https://pubmed.ncbi.nlm.nih.gov/36199287/).

O coach usa essas ideias como orientação geral, mas os limiares conservadores, a exigência de repetição do desempenho e as salvaguardas de dor são regras determinísticas do Liftly, não uma avaliação clínica individual. O app mostra esse limite na própria sessão.

### Testes

| Biblioteca | Versão |
|---|---|
| JUnit 4 | 4.13.2 |
| kotlinx-coroutines-test | 1.10.2 |
| AndroidX Test Ext JUnit | 1.3.0 |
| Espresso Core | 3.7.0 |
| Compose UI Test JUnit4/manifest | gerenciada pelo BOM 2026.06.00 |

## Diretrizes para evolução

- Toda alteração de entidade depois da versão 1 deve incluir uma `Migration` explícita e teste de migração.
- Dados históricos devem conservar nomes e valores de sessão; não devem depender somente do template atual.
- Novas fontes de dados devem manter o repositório como fronteira e Room como fonte local de verdade.
- Sincronização futura deve usar IDs estáveis, timestamps e resolução explícita de conflito, sem tornar conta obrigatória.
- Regras do analisador devem continuar puras e cobertas por testes unitários.
