package com.refit.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EncryptedSessionRow::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class ReFitDatabase : RoomDatabase() {

    abstract fun rehabSessionDao(): RehabSessionDao

    companion object {
        private const val LEGACY_USER_ID = "__legacy__"
        val MIGRATION_4_5 = EncryptedRecordMigration()

        val SECURE_STORAGE_CALLBACK = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.query("PRAGMA secure_delete=ON").use { c ->
                    check(c.moveToFirst() && c.getInt(0) == 1) { "Secure database deletion is unavailable" }
                }
                // Remove deleted plaintext pages and old WAL frames after migration.
                val freePages = db.query("PRAGMA freelist_count").use { c -> c.moveToFirst(); c.getInt(0) }
                if (freePages > 0) db.execSQL("VACUUM")
                db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c ->
                    if (c.moveToFirst()) check(c.getInt(0) == 0) { "Encrypted database cleanup is busy" }
                }
                val converter = RoomConverters()
                db.query("SELECT id, ownerTag, clientTag, record FROM rehab_sessions").use { c ->
                    while (c.moveToNext()) EncryptedSessionRow(c.getLong(0), c.getString(1), c.getString(2), converter.ciphertextToRecord(c.getString(3))).decoded()
                }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rehab_sessions ADD COLUMN remoteId TEXT"
                )
                database.execSQL(
                    "ALTER TABLE rehab_sessions " +
                        "ADD COLUMN clientLocalId TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE rehab_sessions " +
                        "ADD COLUMN userId TEXT NOT NULL DEFAULT '$LEGACY_USER_ID'"
                )
                database.execSQL(
                    "ALTER TABLE rehab_sessions " +
                        "ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'"
                )
                database.execSQL(
                    "ALTER TABLE rehab_sessions " +
                        "ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE rehab_sessions " +
                        "SET clientLocalId = lower(hex(randomblob(16))) " +
                        "WHERE clientLocalId = ''"
                )
                database.execSQL(
                    "UPDATE rehab_sessions SET updatedAt = createdAt " +
                        "WHERE updatedAt = 0"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_rehab_sessions_userId_createdAt " +
                        "ON rehab_sessions(userId, createdAt)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_rehab_sessions_userId_clientLocalId " +
                        "ON rehab_sessions(userId, clientLocalId)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf("completedAttempts", "plannedAttempts", "attemptSeconds", "restSeconds", "endedEarly").forEach {
                    database.execSQL("ALTER TABLE rehab_sessions ADD COLUMN $it INTEGER")
                }
                database.execSQL("ALTER TABLE rehab_sessions ADD COLUMN dataMode TEXT NOT NULL DEFAULT 'unknown'")
                database.execSQL("ALTER TABLE rehab_sessions ADD COLUMN comfort TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf("wristSide", "pain", "fatigue").forEach {
                    database.execSQL("ALTER TABLE rehab_sessions ADD COLUMN $it TEXT")
                }
            }
        }

        @Volatile
        private var INSTANCE: ReFitDatabase? = null

        fun getInstance(context: Context): ReFitDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReFitDatabase::class.java,
                    "refit.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(SECURE_STORAGE_CALLBACK)
                    .build()
                    .also { database ->
                        INSTANCE = database
                    }
            }
        }
    }
}
