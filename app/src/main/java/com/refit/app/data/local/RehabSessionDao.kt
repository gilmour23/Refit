package com.refit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.refit.app.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RehabSessionDao {
    @Query("UPDATE rehab_sessions SET pain = :pain, comfort = :comfort WHERE id = :localId AND userId = :userId")
    suspend fun updatePain(localId: Long, userId: String, pain: String, comfort: String)

    @Query("UPDATE rehab_sessions SET fatigue = :fatigue WHERE id = :localId AND userId = :userId")
    suspend fun updateFatigue(localId: Long, userId: String, fatigue: String)

    @Query("UPDATE rehab_sessions SET comfort = :comfort WHERE id = :localId AND userId = :userId")
    suspend fun updateComfort(localId: Long, userId: String, comfort: String)

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
