package com.refit.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.refit.app.data.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RehabSessionDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun session(owner: String, time: Long) = RehabSessionEntity(
        userId = owner, createdAt = time, exerciseName = "wrist", durationSeconds = 5,
        sampleCount = 100, rms = 2f, mav = 1f, peakActivation = 4f
    )

    @Test fun historyLatestAndPendingAreSeparatedByOwner() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ReFitDatabase::class.java).build()
        try {
            val dao = db.rehabSessionDao()
            dao.insert(session("owner-a", 1))
            dao.insert(session("owner-b", 2))
            dao.insert(session("owner-a", 3))
            assertEquals(listOf(3L, 1L), dao.observeAll("owner-a").first().map { it.createdAt })
            assertEquals(listOf(2L), dao.observeAll("owner-b").first().map { it.createdAt })
            assertEquals(3L, dao.getLatest("owner-a")!!.createdAt)
            assertEquals(listOf(1L, 3L), dao.getPendingSync("owner-a").map { it.createdAt })
            assertTrue(dao.observeAll("unknown").first().isEmpty())
            assertNull(dao.getLatest("unknown"))
        } finally { db.close() }
    }

    @Test fun localRecordsSurviveDatabaseReopen() = runBlocking {
        val name = "persist-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, ReFitDatabase::class.java, name).build()
        try {
            db.rehabSessionDao().insert(session("owner-a", 42))
            db.close()
            db = Room.databaseBuilder(context, ReFitDatabase::class.java, name).build()
            assertEquals(42L, db.rehabSessionDao().getLatest("owner-a")!!.createdAt)
            assertTrue(db.rehabSessionDao().observeAll("owner-b").first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migratesV1WithoutAssigningUnownedHistoryToNewAccount() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("""CREATE TABLE rehab_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                createdAt INTEGER NOT NULL, exerciseName TEXT NOT NULL,
                durationSeconds INTEGER NOT NULL, sampleCount INTEGER NOT NULL,
                rms REAL NOT NULL, mav REAL NOT NULL, maxActivation REAL NOT NULL
            )""")
            old.execSQL("INSERT INTO rehab_sessions VALUES (1, 42, 'wrist', 5, 100, 2, 1, 4)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, ReFitDatabase::class.java, name)
            .addMigrations(ReFitDatabase.MIGRATION_1_2).build()
        try {
            val legacy = db.rehabSessionDao().observeAll("__legacy__").first().single()
            assertEquals(42L, legacy.createdAt)
            assertEquals(42L, legacy.updatedAt)
            assertEquals(4f, legacy.peakActivation, 0f)
            assertTrue(legacy.clientLocalId.isNotBlank())
            assertEquals(SyncStatus.LOCAL_ONLY, legacy.syncStatus)
            assertTrue(db.rehabSessionDao().observeAll("owner-a").first().isEmpty())
            assertTrue(db.rehabSessionDao().observeAll("owner-b").first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
