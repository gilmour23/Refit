package com.refit.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RehabSessionEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class ReFitDatabase : RoomDatabase() {

    abstract fun rehabSessionDao(): RehabSessionDao

    companion object {
        private const val LEGACY_USER_ID = "__legacy__"

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

        @Volatile
        private var INSTANCE: ReFitDatabase? = null

        fun getInstance(context: Context): ReFitDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReFitDatabase::class.java,
                    "refit.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { database ->
                        INSTANCE = database
                    }
            }
        }
    }
}
