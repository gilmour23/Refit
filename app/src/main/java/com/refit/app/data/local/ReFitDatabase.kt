package com.refit.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RehabSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ReFitDatabase : RoomDatabase() {

    abstract fun rehabSessionDao(): RehabSessionDao

    companion object {
        @Volatile
        private var INSTANCE: ReFitDatabase? = null

        fun getInstance(context: Context): ReFitDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReFitDatabase::class.java,
                    "refit.db"
                ).build().also { database ->
                    INSTANCE = database
                }
            }
        }
    }
}
