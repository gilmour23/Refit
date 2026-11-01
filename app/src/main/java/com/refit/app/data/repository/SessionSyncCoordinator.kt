package com.refit.app.data.repository

import com.refit.app.data.model.SyncStatus
import com.refit.app.data.remote.RehabSessionRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionSyncCoordinator(
    private val repository: RehabSessionRepository,
    private val remote: RehabSessionRemoteDataSource = RehabSessionRemoteDataSource()
) {
    val syncOwner = remote.syncOwner

    suspend fun syncPending(userId: String) {
        if (!remote.isConfigured || !remote.canSync(userId)) return
        repository.getPendingSync(userId).forEach { syncSession(userId, it.id) }
    }

    suspend fun syncSession(userId: String, localId: Long): Boolean = uploadMutex.withLock {
        if (!remote.isConfigured || !remote.canSync(userId)) return@withLock false
        val session = repository.getById(localId, userId) ?: return@withLock false
        if (session.syncStatus == SyncStatus.SYNCED) return@withLock true

        try {
            repository.updateSyncState(localId, userId, SyncStatus.SYNCING, session.remoteId)
            val remoteId = remote.upsert(session)
            currentCoroutineContext().ensureActive()
            repository.markSyncedIfUnchanged(localId, userId, session.updatedAt, remoteId)
        } catch (cancelled: CancellationException) {
            // Logout cancels account-owned work. Reset the durable state before
            // propagating cancellation; process death is recovered by pending query.
            withContext(NonCancellable) {
                repository.updateSyncState(localId, userId, SyncStatus.FAILED, session.remoteId)
            }
            throw cancelled
        } catch (_: Exception) {
            repository.updateSyncState(localId, userId, SyncStatus.FAILED, session.remoteId)
            false
        }
    }

    companion object {
        // Foreground, manual retry, and session completion can overlap. Sharing
        // this lock also covers recreated coordinators within this app process.
        private val uploadMutex = Mutex()
    }
}

