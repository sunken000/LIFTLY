# Banco de dados Room

## Configuração

- arquivo interno: `liftly.db`;
- versão: `5`;
- entidades: `9`;
- `exportSchema = true`;
- criação: `Room.databaseBuilder`, sem migração destrutiva automática;
- acesso: `LiftlyDao` com Coroutines, `Flow`, `@Upsert` e `@Transaction`.

Mapeamento de tipos usado pelo Room:

- Kotlin `String` → SQLite `TEXT`;
- Kotlin `Int`, `Long` e `Boolean` → SQLite `INTEGER`;
- Kotlin `Double` → SQLite `REAL`;
- tipos Kotlin com `?` aceitam `NULL`; os demais são `NOT NULL`.

Os valores padrão abaixo são padrões dos construtores Kotlin. Como as entidades não usam `@ColumnInfo(defaultValue = ...)`, eles não devem ser tratados como cláusulas `DEFAULT` do SQLite.

## Relações

```text
workouts 1 ── N workout_exercises N ── 1 exercises
sessions 1 ── N session_sets

schedule.workoutId       referência lógica, sem ForeignKey
sessions.workoutId       referência/snapshot lógico, sem ForeignKey
session_sets.exerciseId  referência/snapshot lógico, sem ForeignKey
```

Excluir um treino remove seus itens por `CASCADE`. Excluir um exercício referenciado por `workout_exercises` é bloqueado por `RESTRICT`. Excluir uma sessão remove suas séries por `CASCADE`. Referências históricas sem `ForeignKey` conservam os snapshots mesmo que o template mude.

## Tabela `exercises`

Entidade `ExerciseEntity`.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | TEXT | não | chave primária; `builtin.*` ou `custom.*` |
| `name` | TEXT | não | nome pesquisável |
| `muscleGroup` | TEXT | não | grupo muscular principal |
| `secondaryMuscles` | TEXT | não | lista textual; vazio |
| `equipment` | TEXT | não | equipamento |
| `difficulty` | TEXT | não | dificuldade |
| `movementType` | TEXT | não | padrão/tipo de movimento |
| `category` | TEXT | não | musculação, cardio etc. |
| `instructions` | TEXT | não | instruções resumidas |
| `cautions` | TEXT | não | cuidados e erros comuns |
| `trackingUnit` | TEXT | não | `kg` |
| `isCustom` | INTEGER | não | Boolean; `false` |
| `isFavorite` | INTEGER | não | Boolean; `false` |
| `imageUri` | TEXT | sim | URI opcional |
| `archived` | INTEGER | não | Boolean; `false` |

Índices não únicos: `name`, `muscleGroup`, `isFavorite`.

Favoritos e exercícios personalizados usam a mesma tabela. Um personalizado não referenciado pode ser removido fisicamente; se estiver em um treino ou histórico, é marcado como arquivado.

## Tabela `workouts`

Entidade `WorkoutEntity`.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | TEXT | não | chave primária UUID ou ID estável de demo |
| `name` | TEXT | não | nome do treino |
| `description` | TEXT | não | vazio |
| `color` | INTEGER | não | cor ARGB; `0xFF22E5EA` |
| `icon` | TEXT | não | chave de ícone; `fitness` |
| `weekDays` | TEXT | não | dias ISO separados por vírgula; vazio |
| `archived` | INTEGER | não | Boolean; `false` |
| `createdAt` | INTEGER | não | epoch milliseconds |

Não há índices secundários.

## Tabela `workout_exercises`

Entidade `WorkoutExerciseEntity`. Representa a associação ordenada e configurável entre treino e exercício.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | TEXT | não | chave primária UUID |
| `workoutId` | TEXT | não | FK para `workouts.id` |
| `exerciseId` | TEXT | não | FK para `exercises.id` |
| `orderIndex` | INTEGER | não | posição baseada em zero |
| `sets` | INTEGER | não | `3` |
| `repMin` | INTEGER | não | `8` |
| `repMax` | INTEGER | não | `12` |
| `targetLoadKg` | REAL | não | `0.0` |
| `restSeconds` | INTEGER | não | `60` |
| `notes` | TEXT | não | vazio |
| `setType` | TEXT | não | `Normal` |
| `trackingMode` | TEXT | não | `Repetições` |

Chaves estrangeiras:

- `workoutId` → `workouts.id`, `ON DELETE CASCADE`;
- `exerciseId` → `exercises.id`, `ON DELETE RESTRICT`.

Índices não únicos: `workoutId`, `exerciseId`.

## Tabela `schedule`

Entidade `ScheduleEntity`.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | TEXT | não | chave primária UUID; descanso pode usar `rest-AAAA-MM-DD` |
| `date` | TEXT | não | data ISO-8601 `AAAA-MM-DD` |
| `workoutId` | TEXT | não | ID lógico; vazio em dia de descanso |
| `status` | TEXT | não | `Planejado`; também descanso/conclusão/parcial |
| `isRestDay` | INTEGER | não | Boolean; `false` |

Índice único composto: (`date`, `workoutId`). Isso permite vários treinos na mesma data, mas impede repetir o mesmo treino no dia. Não existe `ForeignKey` nessa tabela.

## Tabela `sessions`

Entidade `SessionEntity`.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | TEXT | não | chave primária UUID |
| `workoutId` | TEXT | não | referência lógica ao treino |
| `workoutName` | TEXT | não | snapshot do nome |
| `startedAt` | INTEGER | não | epoch milliseconds |
| `finishedAt` | INTEGER | sim | epoch milliseconds |
| `status` | TEXT | não | `Em andamento`, `Concluído` ou `Parcial` |
| `notes` | TEXT | não | vazio |
| `isTestMode` | INTEGER | não | Boolean; `false`; sessão descartável que não entra no histórico |

Índices não únicos: `workoutId`, `startedAt`. Não existe `ForeignKey` para `workouts`, de modo que o histórico pode permanecer após alterações no template.

A migração 3→4 adiciona `isTestMode INTEGER NOT NULL DEFAULT 0`, preservando todas as sessões anteriores como normais. Ao finalizar uma sessão teste, a linha é excluída e o `CASCADE` remove suas séries.

## Tabela `session_sets`

Entidade `SessionSetEntity`. Cada linha é uma série planejada/realizada e também a fonte do histórico de cargas.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | TEXT | não | chave primária UUID |
| `sessionId` | TEXT | não | FK para `sessions.id` |
| `workoutExerciseId` | TEXT | não | snapshot lógico do item do treino |
| `exerciseId` | TEXT | não | snapshot lógico do exercício |
| `exerciseName` | TEXT | não | snapshot do nome |
| `setNumber` | INTEGER | não | número da série, iniciando em 1 |
| `reps` | INTEGER | não | repetições registradas |
| `loadKg` | REAL | não | carga registrada em kg |
| `completed` | INTEGER | não | Boolean; `false` |
| `completedAt` | INTEGER | sim | epoch milliseconds |
| `notes` | TEXT | não | vazio |
| `durationSeconds` | INTEGER | não | duração realizada quando o modo é tempo |
| `distanceMeters` | REAL | não | distância realizada quando o modo é distância |
| `trackingMode` | TEXT | não | `Repetições`, `Tempo` ou `Distância` |
| `exerciseOrder` | INTEGER | não | snapshot da posição do exercício no início da sessão |
| `plannedReps` | INTEGER | sim | snapshot da meta inicial de reps, segundos ou metros; nulo em histórico legado |
| `plannedLoadKg` | REAL | sim | snapshot da carga inicial; nulo em histórico legado |
| `rir` | INTEGER | sim | repetições em reserva percebidas, de 0 a 10; opcional |
| `painLevel` | INTEGER | não | dor percebida no movimento, de 0 a 10; `0` |

Chave estrangeira: `sessionId` → `sessions.id`, `ON DELETE CASCADE`.

Índices:

- não único em `sessionId`;
- não único em `exerciseId`;
- único em (`sessionId`, `workoutExerciseId`, `setNumber`).

A melhor carga é `MAX(loadKg)` entre séries concluídas. A última carga usa a série concluída com `completedAt` mais recente. O volume exibido é a soma de `loadKg × reps` das séries concluídas; duração e distância permanecem em suas colunas próprias. Os campos `planned*` permitem ao histórico comparar a configuração existente no início da sessão com o valor efetivamente registrado, sem depender do treino atual.

## Migrações

- `1 → 2`: adiciona modo de acompanhamento, duração, distância e ordem histórica dos exercícios;
- `2 → 3`: adiciona os snapshots opcionais `plannedReps` e `plannedLoadKg`; registros anteriores permanecem válidos com esses campos nulos;
- `3 → 4`: adiciona `sessions.isTestMode` com padrão `0`, preservando todo o histórico existente.
- `4 → 5`: adiciona `session_sets.rir`, `session_sets.painLevel` e cria `body_photos` com índice de data.

## Tabela `body_weight`

Entidade `BodyWeightEntryEntity`.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | TEXT | não | chave primária UUID |
| `measuredAt` | INTEGER | não | epoch milliseconds |
| `weightKg` | REAL | não | validado pela aplicação entre 20 e 500 kg |
| `notes` | TEXT | não | vazio |

Índice não único: `measuredAt`.

## Tabela `body_photos`

Entidade `BodyPhotoEntity`. O Room guarda a linha do tempo e uma referência ao documento escolhido; o arquivo da imagem não é copiado para o banco.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | TEXT | não | chave primária UUID |
| `imageUri` | TEXT | não | URI `content://` com leitura persistente concedida pelo Android |
| `addedAt` | INTEGER | não | data/hora da inclusão em epoch milliseconds |
| `notes` | TEXT | não | texto opcional, limitado a 240 caracteres |

Índice não único: `addedAt`.

## Tabela `user_profile`

Entidade `UserProfileEntity`. O aplicativo usa uma única linha com `id = 1`.

| Coluna | SQLite | Nulo | Observação/padrão Kotlin |
|---|---|---:|---|
| `id` | INTEGER | não | chave primária; `1` |
| `nickname` | TEXT | não | vazio |
| `birthYear` | INTEGER | sim | ano de nascimento |
| `heightCm` | REAL | sim | altura em centímetros |
| `currentWeightKg` | REAL | sim | atualizado ao registrar peso |
| `objective` | TEXT | não | `Saúde e bem-estar` |
| `preferredUnit` | TEXT | não | `Métrico` |

Não há índices secundários.

## Modelos que não são tabelas

- `ExerciseProgressPoint`: data, maior carga e volume calculados para visualização;
- `SessionSummary`: resumo transitório retornado ao finalizar uma sessão, incluindo se ela era um teste descartável;
- `UserPreferences`: projeção do Preferences DataStore, fora do Room.

## Limpeza de dados

`clearUserData()` é transacional e executa nesta ordem:

1. remove séries e sessões;
2. remove programação, itens de treino e treinos;
3. remove peso, fotos corporais e perfil;
4. remove exercícios personalizados;
5. limpa favoritos dos exercícios nativos.

Depois, o repositório reinsere o catálogo nativo. Preferências do DataStore não fazem parte dessa transação.
