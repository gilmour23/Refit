package com.refit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RehabSessionDao {

    @Insert
    suspend fun insert(session: RehabSessionEntity): Long

    @Query("SELECT * FROM rehab_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RehabSessionEntity>>

    @Query("""
        SELECT * FROM rehab_sessions
        WHERE exerciseName = :exerciseName
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    suspend fun getLatestForExercise(exerciseName: String): RehabSessionEntity?
}
