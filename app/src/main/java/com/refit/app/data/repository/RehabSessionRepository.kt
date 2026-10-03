package com.refit.app.data.repository

import com.refit.app.data.local.RehabSessionDao
import com.refit.app.data.local.RehabSessionEntity
import kotlinx.coroutines.flow.Flow

class RehabSessionRepository(
    private val dao: RehabSessionDao
) {
    fun observeAll(): Flow<List<RehabSessionEntity>> =
        dao.observeAll()

    suspend fun getLatestForExerciseAndSource(
        exerciseName: String,
        signalSource: String
    ): RehabSessionEntity? =
        dao.getLatestForExerciseAndSource(exerciseName, signalSource)

    suspend fun insert(session: RehabSessionEntity): Long =
        dao.insert(session)
}
