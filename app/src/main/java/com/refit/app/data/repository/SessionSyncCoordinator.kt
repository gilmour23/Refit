package com.refit.app.data.repository

import com.refit.app.data.model.SyncStatus
import com.refit.app.data.remote.RehabSessionRemoteDataSource

class SessionSyncCoordinator(
    private val repository: RehabSessionRepository,
    private val remote: RehabSessionRemoteDataSource =
        RehabSessionRemoteDataSource()
) {

    suspend fun syncPending(userId: String) {
        if (!remote.isConfigured) return

        repository.getPendingSync(userId)
            .forEach { session ->
                syncSession(session.id)
            }
    }

    suspend fun syncSession(localId: Long): Boolean {
        if (!remote.isConfigured) return false

        val session = repository.getById(localId)
            ?: return false

        if (session.syncStatus == SyncStatus.SYNCED) {
            return true
        }

        repository.updateSyncState(
            localId = localId,
            status = SyncStatus.SYNCING,
            remoteId = session.remoteId
        )

        return runCatching {
            remote.upsert(session)
        }.fold(
            onSuccess = { remoteId ->
                repository.updateSyncState(
                    localId = localId,
                    status = SyncStatus.SYNCED,
                    remoteId = remoteId
                )
                true
            },
            onFailure = {
                repository.updateSyncState(
                    localId = localId,
                    status = SyncStatus.FAILED,
                    remoteId = session.remoteId
                )
                false
            }
        )
    }
}
