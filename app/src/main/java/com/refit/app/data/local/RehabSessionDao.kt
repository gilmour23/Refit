package com.refit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.refit.app.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RehabSessionDao {

    @Insert
    suspend fun insert(session: RehabSessionEntity): Long

    @Query(
        "SELECT * FROM rehab_sessions " +
            "WHERE userId = :userId " +
            "ORDER BY createdAt DESC"
    )
    fun observeAll(userId: String): Flow<List<RehabSessionEntity>>

    @Query(
        "SELECT * FROM rehab_sessions " +
            "WHERE userId = :userId " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatest(userId: String): RehabSessionEntity?

    @Query("SELECT * FROM rehab_sessions WHERE id = :localId AND userId = :userId LIMIT 1")
    suspend fun getById(localId: Long, userId: String): RehabSessionEntity?

    @Query(
        "SELECT * FROM rehab_sessions " +
            "WHERE userId = :userId " +
            "AND syncStatus IN ('LOCAL_ONLY', 'FAILED', 'SYNCING') " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getPendingSync(userId: String): List<RehabSessionEntity>

    @Query(
        "UPDATE rehab_sessions SET " +
            "syncStatus = :status, " +
            "remoteId = :remoteId " +
            "WHERE id = :localId AND userId = :userId"
    )
    suspend fun updateSyncState(
        localId: Long,
        status: SyncStatus,
        remoteId: String?,
        userId: String
    )
}

