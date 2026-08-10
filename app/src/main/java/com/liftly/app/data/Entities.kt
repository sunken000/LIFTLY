package com.liftly.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises", indices = [Index("name"), Index("muscleGroup"), Index("isFavorite")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String,
    val secondaryMuscles: String = "",
    val equipment: String,
    val difficulty: String,
    val movementType: String,
    val category: String,
    val instructions: String,
    val cautions: String,
    val trackingUnit: String = "kg",
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val imageUri: String? = null,
    val archived: Boolean = false
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val color: Long = 0xFF22E5EA,
    val icon: String = "fitness",
    val weekDays: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("workoutId"), Index("exerciseId")]
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val sets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val targetLoadKg: Double = 0.0,
    val restSeconds: Int = 60,
    val notes: String = "",
    val setType: String = "Normal",
    val trackingMode: String = "Repetições"
)

@Entity(tableName = "schedule", indices = [Index(value = ["date", "workoutId"], unique = true)])
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val date: String,
    val workoutId: String,
    val status: String = "Planejado",
    val isRestDay: Boolean = false
)

@Entity(tableName = "sessions", indices = [Index("workoutId"), Index("startedAt")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val workoutName: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String = "Em andamento",
    val notes: String = "",
    /** Test sessions survive process restarts, but are discarded instead of entering history. */
    val isTestMode: Boolean = false
)

@Entity(
    tableName = "session_sets",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId"), Index(value = ["sessionId", "workoutExerciseId", "setNumber"], unique = true)]
)
data class SessionSetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val workoutExerciseId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val reps: Int,
    val loadKg: Double,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val notes: String = "",
    val durationSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val trackingMode: String = "Repetições",
    val exerciseOrder: Int = 0,
    val plannedReps: Int? = null,
    val plannedLoadKg: Double? = null,
    /** Repetições que a pessoa sentiu que ainda conseguiria fazer, de 0 a 10. */
    val rir: Int? = null,
    /** Desconforto/dor percebida durante a série, de 0 (nenhuma) a 10. */
    val painLevel: Int = 0
)

@Entity(tableName = "body_photos", indices = [Index("addedAt")])
data class BodyPhotoEntity(
    @PrimaryKey val id: String,
    /** URI persistente escolhida pelo usuário; a imagem continua sob controle do dispositivo. */
    val imageUri: String,
    val addedAt: Long,
    val notes: String = ""
)

@Entity(tableName = "body_weight", indices = [Index("measuredAt")])
data class BodyWeightEntryEntity(
    @PrimaryKey val id: String,
    val measuredAt: Long,
    val weightKg: Double,
    val notes: String = ""
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val nickname: String = "",
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val objective: String = "Saúde e bem-estar",
    val preferredUnit: String = "Métrico"
)

/** Singleton balance. XP never decreases when coins are spent. */
@Entity(tableName = "reward_wallet")
data class RewardWalletEntity(
    @PrimaryKey val id: Int = 1,
    val lifetimeXp: Long = 0L,
    val coinBalance: Long = 0L,
    val lifetimeCoinsEarned: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "reward_catalog",
    indices = [Index("category"), Index("slot"), Index("enabled")],
)
data class RewardCatalogItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    /** Only one owned item may be active in the same visual or behavioral slot. */
    val slot: String,
    val rarity: String,
    val priceCoins: Long,
    val requiredLevel: Int = 1,
    val assetKey: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "reward_inventory",
    foreignKeys = [
        ForeignKey(
            entity = RewardCatalogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("equippedSlot")],
)
data class RewardInventoryEntity(
    @PrimaryKey val itemId: String,
    val acquiredAt: Long,
    val equippedSlot: String? = null,
)

@Entity(
    tableName = "reward_missions",
    indices = [Index("periodStart"), Index("periodEnd"), Index("metric"), Index("completedAt")],
)
data class RewardMissionEntity(
    @PrimaryKey val id: String,
    val period: String,
    val metric: String,
    val title: String,
    val description: String,
    val target: Int,
    val progress: Int = 0,
    val xpReward: Long,
    val coinReward: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val completedAt: Long? = null,
    val sortOrder: Int = 0,
)

/** Immutable source of truth for every credit and debit. */
@Entity(
    tableName = "reward_ledger",
    indices = [Index("createdAt"), Index("sourceType"), Index("sourceId")],
)
data class RewardLedgerEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val sourceId: String,
    val deltaXp: Long,
    val deltaCoins: Long,
    val createdAt: Long,
    val description: String,
)

data class ExerciseProgressPoint(
    val date: Long,
    val maxLoad: Double,
    val volume: Double
)

data class SessionSummary(
    val sessionId: String,
    val workoutName: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val completedSets: Int,
    val totalSets: Int,
    val volume: Double,
    val isTestMode: Boolean = false,
    val rewardXp: Long = 0L,
    val rewardCoins: Long = 0L,
    val completedRewardMissions: List<String> = emptyList(),
    val adaptiveChanges: Int = 0,
)
