package com.refit.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RehabSessionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ReFitDatabase : RoomDatabase() {

    abstract fun rehabSessionDao(): RehabSessionDao

    companion object {
        @Volatile
        private var INSTANCE: ReFitDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rehab_sessions ADD COLUMN orthosisProfile TEXT NOT NULL DEFAULT '맞춤 손목 보조기'"
                )
                db.execSQL(
                    "ALTER TABLE rehab_sessions ADD COLUMN calibrationRest REAL"
                )
                db.execSQL(
                    "ALTER TABLE rehab_sessions ADD COLUMN calibrationActive REAL"
                )
            }
        }

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
