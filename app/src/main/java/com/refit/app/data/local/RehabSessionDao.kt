package com.refit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.refit.app.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface RehabSessionDao {
    @Insert suspend fun insertRow(row: EncryptedSessionRow): Long
    @Query("SELECT * FROM rehab_sessions WHERE ownerTag = :ownerTag")
    fun observeRows(ownerTag: String): Flow<List<EncryptedSessionRow>>
    @Query("SELECT * FROM rehab_sessions WHERE ownerTag = :ownerTag")
    suspend fun getRows(ownerTag: String): List<EncryptedSessionRow>
    @Query("SELECT * FROM rehab_sessions WHERE id = :id AND ownerTag = :ownerTag LIMIT 1")
    suspend fun getRow(id: Long, ownerTag: String): EncryptedSessionRow?
    @Query("UPDATE rehab_sessions SET record = :record WHERE id = :id AND ownerTag = :ownerTag")
    suspend fun updateRow(id: Long, ownerTag: String, record: RehabSessionEntity)

    suspend fun insert(session: RehabSessionEntity): Long =
        insertRow(EncryptedSessionRow(session.id, EncryptedSessionRow.ownerTag(session.userId), EncryptedSessionRow.clientTag(session.clientLocalId), session))

    fun observeAll(userId: String): Flow<List<RehabSessionEntity>> =
        observeRows(EncryptedSessionRow.ownerTag(userId)).map { rows -> rows.map { it.decoded() }
            .sortedWith(compareByDescending<RehabSessionEntity> { it.createdAt }.thenByDescending { it.id }) }

    suspend fun getLatest(userId: String): RehabSessionEntity? =
        getRows(EncryptedSessionRow.ownerTag(userId)).map { it.decoded() }
            .maxWithOrNull(compareBy<RehabSessionEntity> { it.createdAt }.thenBy { it.id })

    suspend fun getById(localId: Long, userId: String): RehabSessionEntity? =
        getRow(localId, EncryptedSessionRow.ownerTag(userId))?.decoded()

    suspend fun getPendingSync(userId: String): List<RehabSessionEntity> =
        getRows(EncryptedSessionRow.ownerTag(userId)).map { it.decoded() }
            .filter { it.syncStatus in setOf(SyncStatus.LOCAL_ONLY, SyncStatus.FAILED, SyncStatus.SYNCING) }
            .sortedWith(compareBy<RehabSessionEntity> { it.createdAt }.thenBy { it.id })

    @Transaction suspend fun updatePain(localId: Long, userId: String, pain: String, comfort: String) {
        getById(localId, userId)?.let { updateRow(localId, EncryptedSessionRow.ownerTag(userId), it.copy(pain = pain, comfort = comfort).edited()) }
    }
    @Transaction suspend fun updateFatigue(localId: Long, userId: String, fatigue: String) {
        getById(localId, userId)?.let { updateRow(localId, EncryptedSessionRow.ownerTag(userId), it.copy(fatigue = fatigue).edited()) }
    }
    @Transaction suspend fun updateComfort(localId: Long, userId: String, comfort: String) {
        getById(localId, userId)?.let { updateRow(localId, EncryptedSessionRow.ownerTag(userId), it.copy(comfort = comfort).edited()) }
    }
    @Transaction suspend fun markSyncedIfUnchanged(localId: Long, userId: String, expectedUpdatedAt: Long, remoteId: String): Boolean {
        val current = getById(localId, userId) ?: return false
        val unchanged = current.updatedAt == expectedUpdatedAt
        updateRow(localId, EncryptedSessionRow.ownerTag(userId), current.copy(
            syncStatus = if (unchanged) SyncStatus.SYNCED else SyncStatus.LOCAL_ONLY,
            remoteId = remoteId))
        return unchanged
    }

    @Transaction suspend fun updateSyncState(localId: Long, status: SyncStatus, remoteId: String?, userId: String) {
        getById(localId, userId)?.let { updateRow(localId, EncryptedSessionRow.ownerTag(userId), it.copy(syncStatus = status, remoteId = remoteId)) }
    }
}


private fun RehabSessionEntity.edited() = copy(syncStatus = SyncStatus.LOCAL_ONLY,
    updatedAt = maxOf(System.currentTimeMillis(), updatedAt + 1))
