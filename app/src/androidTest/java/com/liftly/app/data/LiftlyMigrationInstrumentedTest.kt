package com.liftly.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiftlyMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftlyDatabase::class.java,
    )

    @Test
    fun migrationFourToFiveAddsCoachFieldsAndPhotoTable() {
        helper.createDatabase(DATABASE_NAME, 4).close()

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            5,
            true,
            LiftlyDatabase.MIGRATION_4_5,
        ).use { database ->
            val setColumns = database.query("PRAGMA table_info(`session_sets`)").use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
            }
            assertTrue("rir" in setColumns)
            assertTrue("painLevel" in setColumns)
            val photoTableExists = database.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='body_photos'"
            ).use { it.moveToFirst() }
            assertTrue(photoTableExists)
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-4-5-test"
    }
}
