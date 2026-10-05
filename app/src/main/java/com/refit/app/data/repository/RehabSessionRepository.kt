package com.refit.app.data.repository

import com.refit.app.data.local.RehabSessionDao
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

class RehabSessionRepository(
    private val dao: RehabSessionDao
) {
    suspend fun updatePain(localId: Long, userId: String, pain: String, comfort: String) = dao.updatePain(localId, userId, pain, comfort)
    suspend fun updateFatigue(localId: Long, userId: String, fatigue: String) = dao.updateFatigue(localId, userId, fatigue)
    suspend fun updateComfort(localId: Long, userId: String, comfort: String) = dao.updateComfort(localId, userId, comfort)

    fun observeAll(userId: String): Flow<List<RehabSessionEntity>> =
        dao.observeAll(userId)

    suspend fun getLatest(userId: String): RehabSessionEntity? =
        dao.getLatest(userId)

    suspend fun getById(localId: Long, userId: String): RehabSessionEntity? =
        dao.getById(localId, userId)

    suspend fun getPendingSync(userId: String): List<RehabSessionEntity> =
        dao.getPendingSync(userId)

    suspend fun insert(session: RehabSessionEntity): Long =
        dao.insert(session)

    suspend fun updateSyncState(
        localId: Long,
        userId: String,
        status: SyncStatus,
        remoteId: String? = null
    ) {
        dao.updateSyncState(
            localId = localId,
            status = status,
            remoteId = remoteId,
            userId = userId
        )
    }
}
