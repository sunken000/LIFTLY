package com.liftly.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        ScheduleEntity::class,
        SessionEntity::class,
        SessionSetEntity::class,
        BodyWeightEntryEntity::class,
        BodyPhotoEntity::class,
        UserProfileEntity::class,
        RewardWalletEntity::class,
        RewardCatalogItemEntity::class,
        RewardInventoryEntity::class,
        RewardMissionEntity::class,
        RewardLedgerEntity::class,
    ],
    version = 6,
    exportSchema = true
)
abstract class LiftlyDatabase : RoomDatabase() {
    abstract fun dao(): LiftlyDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val workoutTrackingAdded = db.addColumnIfMissing("workout_exercises", "trackingMode", "TEXT NOT NULL DEFAULT 'Repetições'")
                val durationAdded = db.addColumnIfMissing("session_sets", "durationSeconds", "INTEGER NOT NULL DEFAULT 0")
                val distanceAdded = db.addColumnIfMissing("session_sets", "distanceMeters", "REAL NOT NULL DEFAULT 0.0")
                val sessionTrackingAdded = db.addColumnIfMissing("session_sets", "trackingMode", "TEXT NOT NULL DEFAULT 'Repetições'")
                db.addColumnIfMissing("session_sets", "exerciseOrder", "INTEGER NOT NULL DEFAULT 0")
                if (workoutTrackingAdded) {
                    db.execSQL(
                        """UPDATE workout_exercises SET trackingMode = CASE
                            WHEN lower(COALESCE((SELECT trackingUnit FROM exercises WHERE exercises.id = workout_exercises.exerciseId), '')) LIKE '%tempo%' THEN 'Tempo'
                            WHEN lower(COALESCE((SELECT trackingUnit FROM exercises WHERE exercises.id = workout_exercises.exerciseId), '')) LIKE '%dist%' THEN 'Distância'
                            ELSE 'Repetições' END""".trimIndent()
                    )
                }
                if (sessionTrackingAdded) {
                    db.execSQL(
                        """UPDATE session_sets SET trackingMode = CASE
                            WHEN lower(COALESCE((SELECT trackingUnit FROM exercises WHERE exercises.id = session_sets.exerciseId), '')) LIKE '%tempo%' THEN 'Tempo'
                            WHEN lower(COALESCE((SELECT trackingUnit FROM exercises WHERE exercises.id = session_sets.exerciseId), '')) LIKE '%dist%' THEN 'Distância'
                            ELSE 'Repetições' END""".trimIndent()
                    )
                }
                if (durationAdded) db.execSQL("UPDATE session_sets SET durationSeconds = reps WHERE trackingMode = 'Tempo'")
                if (distanceAdded) db.execSQL("UPDATE session_sets SET distanceMeters = CAST(reps AS REAL) WHERE trackingMode = 'Distância'")
                val orderUpdates = mutableListOf<Triple<String, String, Int>>()
                db.query("SELECT sessionId, workoutExerciseId FROM session_sets ORDER BY sessionId, rowid").use { cursor ->
                    val sessionIndex = cursor.getColumnIndexOrThrow("sessionId")
                    val itemIndex = cursor.getColumnIndexOrThrow("workoutExerciseId")
                    var currentSession: String? = null
                    val seenItems = mutableSetOf<String>()
                    while (cursor.moveToNext()) {
                        val sessionId = cursor.getString(sessionIndex)
                        val itemId = cursor.getString(itemIndex)
                        if (sessionId != currentSession) {
                            currentSession = sessionId
                            seenItems.clear()
                        }
                        if (seenItems.add(itemId)) orderUpdates += Triple(sessionId, itemId, seenItems.size - 1)
                    }
                }
                orderUpdates.forEach { (sessionId, itemId, order) ->
                    db.execSQL(
                        "UPDATE session_sets SET exerciseOrder = ? WHERE sessionId = ? AND workoutExerciseId = ?",
                        arrayOf<Any?>(order, sessionId, itemId)
                    )
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.addColumnIfMissing("session_sets", "plannedReps", "INTEGER")
                db.addColumnIfMissing("session_sets", "plannedLoadKg", "REAL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.addColumnIfMissing("sessions", "isTestMode", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.addColumnIfMissing("session_sets", "rir", "INTEGER")
                db.addColumnIfMissing("session_sets", "painLevel", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `body_photos` (
                        `id` TEXT NOT NULL,
                        `imageUri` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_photos_addedAt` ON `body_photos` (`addedAt`)")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reward_wallet` (
                        `id` INTEGER NOT NULL,
                        `lifetimeXp` INTEGER NOT NULL,
                        `coinBalance` INTEGER NOT NULL,
                        `lifetimeCoinsEarned` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reward_catalog` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `slot` TEXT NOT NULL,
                        `rarity` TEXT NOT NULL,
                        `priceCoins` INTEGER NOT NULL,
                        `requiredLevel` INTEGER NOT NULL,
                        `assetKey` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_catalog_category` ON `reward_catalog` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_catalog_slot` ON `reward_catalog` (`slot`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_catalog_enabled` ON `reward_catalog` (`enabled`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reward_inventory` (
                        `itemId` TEXT NOT NULL,
                        `acquiredAt` INTEGER NOT NULL,
                        `equippedSlot` TEXT,
                        PRIMARY KEY(`itemId`),
                        FOREIGN KEY(`itemId`) REFERENCES `reward_catalog`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_inventory_equippedSlot` ON `reward_inventory` (`equippedSlot`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reward_missions` (
                        `id` TEXT NOT NULL,
                        `period` TEXT NOT NULL,
                        `metric` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `target` INTEGER NOT NULL,
                        `progress` INTEGER NOT NULL,
                        `xpReward` INTEGER NOT NULL,
                        `coinReward` INTEGER NOT NULL,
                        `periodStart` INTEGER NOT NULL,
                        `periodEnd` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_missions_periodStart` ON `reward_missions` (`periodStart`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_missions_periodEnd` ON `reward_missions` (`periodEnd`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_missions_metric` ON `reward_missions` (`metric`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_missions_completedAt` ON `reward_missions` (`completedAt`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reward_ledger` (
                        `id` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceId` TEXT NOT NULL,
                        `deltaXp` INTEGER NOT NULL,
                        `deltaCoins` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_ledger_createdAt` ON `reward_ledger` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_ledger_sourceType` ON `reward_ledger` (`sourceType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reward_ledger_sourceId` ON `reward_ledger` (`sourceId`)")
                db.execSQL(
                    "INSERT OR IGNORE INTO `reward_wallet` (`id`, `lifetimeXp`, `coinBalance`, `lifetimeCoinsEarned`, `updatedAt`) VALUES (1, 0, 0, 0, 0)"
                )
            }
        }

        private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, column: String, definition: String): Boolean {
            val exists = query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) {
                        found = true
                        break
                    }
                }
                found
            }
            if (exists) return false
            execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
            return true
        }

        fun create(context: Context): LiftlyDatabase = Room.databaseBuilder(
            context.applicationContext,
            LiftlyDatabase::class.java,
            "liftly.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }
}
