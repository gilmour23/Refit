package com.refit.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordEncryptionTest {
    @Test fun clinicalFieldsAreNotPresentInDatabaseOrJournalFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "clinical-encryption-test.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, ReFitDatabase::class.java, name).build()
        try {
            val id = db.rehabSessionDao().insert(RehabSessionEntity(userId = "owner-a", createdAt = 42,
                exerciseName = "private-clinical-marker", durationSeconds = 5, sampleCount = 100,
                rms = 2f, mav = 1f, peakActivation = 4f, pain = "private-pain-marker"))
            assertEquals("private-pain-marker", db.rehabSessionDao().getById(id, "owner-a")!!.pain)
            val path = context.getDatabasePath(name)
            val contents = listOf(path, java.io.File(path.path + "-wal"), java.io.File(path.path + "-journal"))
                .filter { it.exists() }.joinToString { it.readBytes().toString(Charsets.ISO_8859_1) }
            assertFalse("Clinical fields must not be stored as plaintext", contents.contains("private-clinical-marker"))
            assertFalse(contents.contains("private-pain-marker"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun plaintextV4MigrationPreservesClinicalFieldsAndRemovesOldPages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "clinical-v4-migration.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        createV4(path)
        var db = Room.databaseBuilder(context, ReFitDatabase::class.java, name)
            .addMigrations(ReFitDatabase.MIGRATION_4_5).addCallback(ReFitDatabase.SECURE_STORAGE_CALLBACK).build()
        try {
            val row = db.rehabSessionDao().getById(7, "owner-a")!!
            assertEquals("private-old-clinical-marker", row.exerciseName)
            assertEquals("private-old-pain-marker", row.pain)
            assertEquals("client-unique", row.clientLocalId); assertEquals("remote-id", row.remoteId)
            assertEquals(42L, row.createdAt); assertEquals(43L, row.updatedAt)
            assertEquals(0.25f, row.rms, 0f); assertEquals(0.125f, row.mav, 0f); assertEquals(0.5f, row.peakActivation, 0f)
            assertEquals(2, row.completedAttempts); assertEquals(3, row.plannedAttempts)
            assertEquals(4, row.attemptSeconds); assertEquals(6, row.restSeconds); assertEquals(true, row.endedEarly)
            assertEquals("left", row.wristSide); assertEquals("high", row.fatigue); assertEquals("comfort", row.comfort)
            assertEquals(com.refit.app.data.model.SyncStatus.SYNCED, row.syncStatus)
            assertNull(db.rehabSessionDao().getById(7, "owner-b"))
            db.close()
            db = Room.databaseBuilder(context, ReFitDatabase::class.java, name).addCallback(ReFitDatabase.SECURE_STORAGE_CALLBACK).build()
            assertEquals(row, db.rehabSessionDao().getById(7, "owner-a"))
            val contents = path.parentFile!!.listFiles()!!.filter { it.name.startsWith(name) }
                .joinToString { it.readBytes().toString(Charsets.ISO_8859_1) }
            assertFalse(contents.contains("private-old-clinical-marker")); assertFalse(contents.contains("private-old-pain-marker"))
            db.rehabSessionDao().updatePain(7, "owner-b", "wrong-owner", "wrong-owner")
            db.rehabSessionDao().updateFatigue(7, "owner-a", "low")
            val updated = db.rehabSessionDao().getById(7, "owner-a")!!
            assertEquals("low", updated.fatigue); assertEquals("private-old-pain-marker", updated.pain)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun failedEncryptionRollsBackMigrationAndRetainsOriginalRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "clinical-v4-rollback.db"; context.deleteDatabase(name)
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs(); createV4(path)
        val db = Room.databaseBuilder(context, ReFitDatabase::class.java, name)
            .addMigrations(ReFitDatabase.MIGRATION_4_5).build()
        com.refit.app.security.TestKeyStoreSpi.failAccess = true
        try {
            val failure = runCatching { db.rehabSessionDao().getById(7, "owner-a") }.exceptionOrNull()
            assertNotNull(failure)
        } finally { com.refit.app.security.TestKeyStoreSpi.failAccess = false; db.close() }
        android.database.sqlite.SQLiteDatabase.openDatabase(path.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { old ->
            assertEquals(4, old.version)
            old.rawQuery("SELECT exerciseName, pain FROM rehab_sessions WHERE id=7", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("private-old-clinical-marker", c.getString(0))
                assertEquals("private-old-pain-marker", c.getString(1))
            }
        }
        context.deleteDatabase(name)
        Unit
    }

    @Test fun duplicateClientIdCannotOverwriteOrDuplicateAnOwnedRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ReFitDatabase::class.java).build()
        try {
            val row = RehabSessionEntity(userId = "owner-a", clientLocalId = "fixed-client-id", createdAt = 42,
                exerciseName = "wrist", durationSeconds = 5, sampleCount = 100, rms = 2f, mav = 1f, peakActivation = 4f)
            db.rehabSessionDao().insert(row)
            assertNotNull(runCatching { db.rehabSessionDao().insert(row.copy(pain = "replacement")) }.exceptionOrNull())
            assertNull(db.rehabSessionDao().getLatest("owner-a")!!.pain)
        } finally { db.close() }
    }

    @Test fun changedOwnerTagDoesNotExposeAnotherAccountsEncryptedPayload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ReFitDatabase::class.java).build()
        try {
            val id = db.rehabSessionDao().insert(RehabSessionEntity(userId = "owner-a", createdAt = 42,
                exerciseName = "wrist", durationSeconds = 5, sampleCount = 100, rms = 2f, mav = 1f, peakActivation = 4f))
            db.openHelper.writableDatabase.execSQL("UPDATE rehab_sessions SET ownerTag=? WHERE id=?",
                arrayOf<Any>(EncryptedSessionRow.ownerTag("owner-b"), id))
            assertNotNull(runCatching { db.rehabSessionDao().getById(id, "owner-b") }.exceptionOrNull())
            assertNull(db.rehabSessionDao().getById(id, "owner-a"))
        } finally { db.close() }
    }

    @Test fun sameOwnerCiphertextSubstitutionCannotReassignClinicalRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ReFitDatabase::class.java).build()
        try {
            val row = RehabSessionEntity(userId = "owner-a", createdAt = 42, exerciseName = "wrist",
                durationSeconds = 5, sampleCount = 100, rms = 2f, mav = 1f, peakActivation = 4f)
            val first = db.rehabSessionDao().insert(row.copy(clientLocalId = "client-one"))
            val second = db.rehabSessionDao().insert(row.copy(clientLocalId = "client-two", pain = "different-clinical-state"))
            db.openHelper.writableDatabase.execSQL("UPDATE rehab_sessions SET record=(SELECT record FROM rehab_sessions WHERE id=?) WHERE id=?", arrayOf<Any>(second, first))
            assertNotNull(runCatching { db.rehabSessionDao().getById(first, "owner-a") }.exceptionOrNull())
        } finally { db.close() }
    }

    private fun createV4(path: java.io.File) {
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("""CREATE TABLE rehab_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, remoteId TEXT, clientLocalId TEXT NOT NULL,
                userId TEXT NOT NULL, createdAt INTEGER NOT NULL, exerciseName TEXT NOT NULL,
                durationSeconds INTEGER NOT NULL, sampleCount INTEGER NOT NULL, rms REAL NOT NULL,
                mav REAL NOT NULL, maxActivation REAL NOT NULL, syncStatus TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                completedAttempts INTEGER, plannedAttempts INTEGER, attemptSeconds INTEGER, restSeconds INTEGER,
                endedEarly INTEGER, dataMode TEXT NOT NULL DEFAULT 'unknown', comfort TEXT, wristSide TEXT, pain TEXT, fatigue TEXT)""")
            old.execSQL("CREATE INDEX index_rehab_sessions_userId_createdAt ON rehab_sessions(userId, createdAt)")
            old.execSQL("CREATE UNIQUE INDEX index_rehab_sessions_userId_clientLocalId ON rehab_sessions(userId, clientLocalId)")
            old.execSQL("INSERT INTO rehab_sessions VALUES (7,'remote-id','client-unique','owner-a',42,'private-old-clinical-marker',30,123,0.25,0.125,0.5,'SYNCED',43,2,3,4,6,1,'demo','comfort','left','private-old-pain-marker','high')")
            old.version = 4
        }
    }
}
