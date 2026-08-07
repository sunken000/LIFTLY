package com.liftly.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LiftlyDao {
    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun exercise(id: String): ExerciseEntity?

    @Upsert
    suspend fun upsertExercise(exercise: ExerciseEntity)

    @Upsert
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Query("UPDATE exercises SET name = :name WHERE id = :id")
    suspend fun renameExercise(id: String, name: String): Int

    @Query("UPDATE session_sets SET exerciseName = :name WHERE exerciseId = :exerciseId AND sessionId IN (SELECT id FROM sessions WHERE status = 'Em andamento')")
    suspend fun renameExerciseInActiveSessions(exerciseId: String, name: String)

    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1 AND id NOT IN (SELECT exerciseId FROM workout_exercises) AND id NOT IN (SELECT exerciseId FROM session_sets)")
    suspend fun deleteUnusedCustomExercise(id: String): Int

    @Query("UPDATE exercises SET archived = 1 WHERE id = :id AND isCustom = 1")
    suspend fun archiveCustomExercise(id: String)

    @Query("SELECT * FROM workouts WHERE archived = 0 ORDER BY createdAt ASC, id ASC")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY createdAt ASC, id ASC")
    suspend fun allWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workout(id: String): WorkoutEntity?

    @Upsert
    suspend fun upsertWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Query("UPDATE workouts SET archived = 1 WHERE id = :id")
    suspend fun archiveWorkout(id: String)

    @Query("SELECT * FROM workout_exercises ORDER BY workoutId, orderIndex")
    fun observeWorkoutExercises(): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercises ORDER BY workoutId, orderIndex")
    suspend fun allWorkoutExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY orderIndex")
    suspend fun workoutExercises(workoutId: String): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercises WHERE id = :id LIMIT 1")
    suspend fun workoutExercise(id: String): WorkoutExerciseEntity?

    @Upsert
    suspend fun upsertWorkoutExercise(item: WorkoutExerciseEntity)

    @Upsert
    suspend fun upsertWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: String)

    @Query("UPDATE workout_exercises SET orderIndex = :newIndex WHERE id = :id")
    suspend fun updateOrder(id: String, newIndex: Int)

    @Query("SELECT * FROM schedule ORDER BY date")
    fun observeSchedule(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedule ORDER BY date")
    suspend fun allSchedule(): List<ScheduleEntity>

    @Upsert
    suspend fun upsertSchedule(item: ScheduleEntity)

    @Upsert
    suspend fun upsertSchedules(items: List<ScheduleEntity>)

    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun deleteSchedule(id: String)

    @Query("DELETE FROM schedule WHERE date = :date")
    suspend fun deleteScheduleForDate(date: String)

    @Query("DELETE FROM schedule WHERE date = :date AND isRestDay = 1")
    suspend fun deleteRestForDate(date: String)

    @Query("DELETE FROM schedule WHERE workoutId = :workoutId")
    suspend fun deleteScheduleForWorkout(workoutId: String)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE isTestMode = 0 ORDER BY startedAt DESC")
    suspend fun allExportableSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun session(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'Em andamento' ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeSession(): SessionEntity?

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    /** Only finished sessions may be removed through the history API. Child sets cascade. */
    @Query("DELETE FROM sessions WHERE id = :sessionId AND finishedAt IS NOT NULL")
    suspend fun deleteHistoricalSession(sessionId: String): Int

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String): Int

    /** Delete every finished session started in a local-day epoch interval. Child sets cascade. */
    @Query("DELETE FROM sessions WHERE finishedAt IS NOT NULL AND startedAt >= :startInclusive AND startedAt < :endExclusive")
    suspend fun deleteHistoricalSessionsBetween(startInclusive: Long, endExclusive: Long): Int

    @Query("SELECT * FROM session_sets ORDER BY sessionId, exerciseOrder, setNumber, workoutExerciseId")
    fun observeSessionSets(): Flow<List<SessionSetEntity>>

    @Query("SELECT * FROM session_sets ORDER BY sessionId, exerciseOrder, setNumber, workoutExerciseId")
    suspend fun allSessionSets(): List<SessionSetEntity>

    @Query("SELECT session_sets.* FROM session_sets INNER JOIN sessions ON sessions.id = session_sets.sessionId WHERE sessions.isTestMode = 0 ORDER BY session_sets.sessionId, session_sets.exerciseOrder, session_sets.setNumber, session_sets.workoutExerciseId")
    suspend fun allExportableSessionSets(): List<SessionSetEntity>

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY exerciseOrder, setNumber, workoutExerciseId")
    suspend fun sessionSets(sessionId: String): List<SessionSetEntity>

    @Query("SELECT * FROM session_sets WHERE id = :id LIMIT 1")
    suspend fun sessionSet(id: String): SessionSetEntity?

    @Query("SELECT MAX(session_sets.loadKg) FROM session_sets INNER JOIN sessions ON sessions.id = session_sets.sessionId WHERE session_sets.exerciseId = :exerciseId AND session_sets.completed = 1 AND sessions.isTestMode = 0")
    suspend fun personalRecord(exerciseId: String): Double?

    @Query("SELECT session_sets.loadKg FROM session_sets INNER JOIN sessions ON sessions.id = session_sets.sessionId WHERE session_sets.exerciseId = :exerciseId AND session_sets.completed = 1 AND sessions.isTestMode = 0 ORDER BY session_sets.completedAt DESC LIMIT 1")
    suspend fun lastLoad(exerciseId: String): Double?

    @Query("SELECT MAX(session_sets.loadKg) FROM session_sets INNER JOIN sessions ON sessions.id = session_sets.sessionId WHERE session_sets.exerciseId = :exerciseId AND session_sets.sessionId != :excludedSessionId AND session_sets.completed = 1 AND sessions.isTestMode = 0")
    suspend fun personalRecordExcludingSession(exerciseId: String, excludedSessionId: String): Double?

    @Upsert
    suspend fun upsertSessionSet(set: SessionSetEntity)

    @Upsert
    suspend fun upsertSessionSets(sets: List<SessionSetEntity>)

    @Query("SELECT * FROM body_photos ORDER BY addedAt DESC")
    fun observeBodyPhotos(): Flow<List<BodyPhotoEntity>>

    @Query("SELECT * FROM body_photos ORDER BY addedAt DESC")
    suspend fun allBodyPhotos(): List<BodyPhotoEntity>

    @Upsert
    suspend fun upsertBodyPhoto(photo: BodyPhotoEntity)

    @Query("DELETE FROM body_photos WHERE id = :id")
    suspend fun deleteBodyPhoto(id: String): Int

    @Query("SELECT * FROM body_weight ORDER BY measuredAt")
    fun observeWeights(): Flow<List<BodyWeightEntryEntity>>

    @Query("SELECT * FROM body_weight ORDER BY measuredAt")
    suspend fun allWeights(): List<BodyWeightEntryEntity>

    @Upsert
    suspend fun upsertWeight(entry: BodyWeightEntryEntity)

    @Query("DELETE FROM body_weight WHERE id = :id")
    suspend fun deleteWeight(id: String)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun profile(): UserProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM reward_wallet WHERE id = 1")
    fun observeRewardWallet(): Flow<RewardWalletEntity?>

    @Query("SELECT * FROM reward_wallet WHERE id = 1")
    suspend fun rewardWallet(): RewardWalletEntity?

    @Upsert
    suspend fun upsertRewardWallet(wallet: RewardWalletEntity)

    @Query("UPDATE reward_wallet SET lifetimeXp = lifetimeXp + :xp, coinBalance = coinBalance + :coins, lifetimeCoinsEarned = lifetimeCoinsEarned + CASE WHEN :coins > 0 THEN :coins ELSE 0 END, updatedAt = :updatedAt WHERE id = 1")
    suspend fun incrementRewardWallet(xp: Long, coins: Long, updatedAt: Long): Int

    @Query("UPDATE reward_wallet SET coinBalance = coinBalance - :coins, updatedAt = :updatedAt WHERE id = 1 AND coinBalance >= :coins")
    suspend fun spendRewardCoins(coins: Long, updatedAt: Long): Int

    @Query("SELECT * FROM reward_catalog WHERE enabled = 1 ORDER BY sortOrder, title COLLATE NOCASE")
    fun observeRewardCatalog(): Flow<List<RewardCatalogItemEntity>>

    @Query("SELECT * FROM reward_catalog ORDER BY sortOrder, id")
    suspend fun allRewardCatalog(): List<RewardCatalogItemEntity>

    @Query("SELECT * FROM reward_catalog WHERE id = :id LIMIT 1")
    suspend fun rewardCatalogItem(id: String): RewardCatalogItemEntity?

    @Upsert
    suspend fun upsertRewardCatalog(items: List<RewardCatalogItemEntity>)

    @Query("SELECT * FROM reward_inventory ORDER BY acquiredAt")
    fun observeRewardInventory(): Flow<List<RewardInventoryEntity>>

    @Query("SELECT * FROM reward_inventory ORDER BY acquiredAt")
    suspend fun allRewardInventory(): List<RewardInventoryEntity>

    @Query("SELECT * FROM reward_inventory WHERE itemId = :itemId LIMIT 1")
    suspend fun rewardInventoryItem(itemId: String): RewardInventoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRewardInventory(item: RewardInventoryEntity): Long

    @Query("UPDATE reward_inventory SET equippedSlot = NULL WHERE equippedSlot = :slot")
    suspend fun unequipRewardSlot(slot: String)

    @Query("UPDATE reward_inventory SET equippedSlot = :slot WHERE itemId = :itemId")
    suspend fun equipRewardItem(itemId: String, slot: String): Int

    @Query("SELECT * FROM reward_missions ORDER BY periodEnd DESC, sortOrder, id")
    fun observeRewardMissions(): Flow<List<RewardMissionEntity>>

    @Query("SELECT * FROM reward_missions ORDER BY periodStart, sortOrder, id")
    suspend fun allRewardMissions(): List<RewardMissionEntity>

    @Query("SELECT * FROM reward_missions WHERE id = :id LIMIT 1")
    suspend fun rewardMission(id: String): RewardMissionEntity?

    @Query("SELECT * FROM reward_missions WHERE metric = :metric AND periodStart <= :at AND periodEnd > :at ORDER BY sortOrder")
    suspend fun activeRewardMissions(metric: String, at: Long): List<RewardMissionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRewardMissions(missions: List<RewardMissionEntity>): List<Long>

    @Upsert
    suspend fun upsertRewardMission(mission: RewardMissionEntity)

    @Query("UPDATE reward_missions SET progress = MIN(target, progress + :amount) WHERE id = :id AND completedAt IS NULL")
    suspend fun incrementRewardMission(id: String, amount: Int): Int

    @Query("UPDATE reward_missions SET completedAt = :completedAt, progress = target WHERE id = :id AND completedAt IS NULL AND progress >= target")
    suspend fun completeRewardMissionIfReady(id: String, completedAt: Long): Int

    @Query("UPDATE reward_missions SET progress = MIN(target, MAX(0, :progress)), completedAt = NULL WHERE id = :id")
    suspend fun adminSetRewardMissionProgress(id: String, progress: Int): Int

    @Query("UPDATE reward_missions SET progress = 0, completedAt = NULL WHERE periodStart <= :at AND periodEnd > :at")
    suspend fun adminResetCurrentRewardMissions(at: Long): Int

    @Query("SELECT * FROM reward_ledger ORDER BY createdAt DESC LIMIT 60")
    fun observeRecentRewardLedger(): Flow<List<RewardLedgerEntity>>

    @Query("SELECT * FROM reward_ledger ORDER BY createdAt, id")
    suspend fun allRewardLedger(): List<RewardLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRewardLedger(entry: RewardLedgerEntity): Long

    @Transaction
    suspend fun replaceWorkoutItems(workoutId: String, items: List<WorkoutExerciseEntity>) {
        deleteItemsForWorkout(workoutId)
        upsertWorkoutExercises(items)
    }

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteItemsForWorkout(workoutId: String)

    @Transaction
    suspend fun clearUserData() {
        clearSessionSets()
        clearSessions()
        clearSchedule()
        clearWorkoutItems()
        clearWorkouts()
        clearWeights()
        clearBodyPhotos()
        clearProfile()
        clearRewardInventory()
        clearRewardMissions()
        clearRewardLedger()
        clearRewardWallet()
        clearRewardCatalog()
        clearCustomExercises()
        clearFavoriteFlags()
    }

    @Transaction
    suspend fun clearTrainingProgress() {
        clearSessionSets()
        clearSessions()
    }

    @Query("DELETE FROM session_sets") suspend fun clearSessionSets()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM schedule") suspend fun clearSchedule()
    @Query("DELETE FROM workout_exercises") suspend fun clearWorkoutItems()
    @Query("DELETE FROM workouts") suspend fun clearWorkouts()
    @Query("DELETE FROM body_weight") suspend fun clearWeights()
    @Query("DELETE FROM body_photos") suspend fun clearBodyPhotos()
    @Query("DELETE FROM user_profile") suspend fun clearProfile()
    @Query("DELETE FROM reward_inventory") suspend fun clearRewardInventory()
    @Query("DELETE FROM reward_missions") suspend fun clearRewardMissions()
    @Query("DELETE FROM reward_ledger") suspend fun clearRewardLedger()
    @Query("DELETE FROM reward_wallet") suspend fun clearRewardWallet()
    @Query("DELETE FROM reward_catalog") suspend fun clearRewardCatalog()
    @Query("DELETE FROM exercises WHERE isCustom = 1") suspend fun clearCustomExercises()
    @Query("UPDATE exercises SET isFavorite = 0") suspend fun clearFavoriteFlags()
}
