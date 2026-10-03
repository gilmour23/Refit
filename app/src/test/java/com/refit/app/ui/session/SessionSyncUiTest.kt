package com.refit.app.ui.session

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.local.RehabSessionDao
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.EmgSample
import com.refit.app.data.model.SyncStatus
import com.refit.app.data.remote.RehabSessionRemoteDataSource
import com.refit.app.data.repository.RehabSessionRepository
import com.refit.app.data.repository.SessionSyncCoordinator
import com.refit.app.data.source.EmgConnectionState
import com.refit.app.data.source.EmgDataSource
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class SessionSyncUiTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val owner = "11111111-1111-1111-1111-111111111111"

    private suspend fun client(engine: MockEngine) = createSupabaseClient("https://test.supabase.co", "public-test-key") {
        httpEngine = engine
        install(Auth) {
            enableLifecycleCallbacks = false; alwaysAutoRefresh = false; autoSaveToStorage = false
            sessionManager = MemorySessionManager(); codeVerifierCache = MemoryCodeVerifierCache()
        }
        install(Postgrest)
    }.also {
        it.auth.awaitInitialization()
        it.auth.importSession(UserSession(accessToken = "test-access", refreshToken = "test-refresh",
            expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = owner)), autoRefresh = false)
    }

    @Test(timeout = 30000) fun resultIsVisibleWhileUploadWaitsAndManualRetryRecoversFailure() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(application, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao())
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var requests = 0
        val c = client(MockEngine {
            requests++
            if (requests == 1) { started.complete(Unit); release.await(); throw IOException("offline") }
            respond("""[{"id":"33333333-3333-3333-3333-333333333333"}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val store = ViewModelStore()
        try {
            val source = Source()
            val vm = SessionViewModel(application, owner, source, repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(c)))
            store.put("session", vm)
            withTimeout(5000) { vm.uiState.first { !it.isSyncing } }
            vm.startSession(); source.samples.emit(EmgSample(1, -4f)); source.samples.emit(EmgSample(2, 2f))
            vm.stopSession()
            withTimeout(5000) { started.await() }
            val result = vm.uiState.value.result!!
            assertEquals(2, result.sampleCount)
            assertEquals(4f, result.peakActivation, 0f)
            assertFalse(vm.uiState.value.isSaving)
            assertEquals(SyncStatus.SYNCING, repository.getById(result.savedSessionId!!, owner)!!.syncStatus)
            release.complete(Unit)
            withTimeout(5000) { repository.observeAll(owner).first { it.singleOrNull()?.syncStatus == SyncStatus.FAILED } }
            assertEquals(result, vm.uiState.value.result)
            withTimeout(5000) { vm.uiState.first { !it.isSyncing } }
            vm.retrySync()
            withTimeout(5000) { vm.uiState.first { !it.isSyncing && it.history.singleOrNull()?.syncStatus == SyncStatus.SYNCED } }
            assertEquals(2, requests)
            assertEquals(result, vm.uiState.value.result)
        } finally { release.complete(Unit); store.clear(); c.close(); db.close(); Dispatchers.resetMain() }
    }

    @Test(timeout = 30000) fun retryCallsCoalesceAndLogoutCancelsPendingUpload() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(application, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao())
        val id = repository.insert(RehabSessionEntity(userId = owner, createdAt = 1, exerciseName = "wrist",
            durationSeconds = 5, sampleCount = 100, rms = 2f, mav = 1f, peakActivation = 4f))
        val started = CompletableDeferred<Unit>(); var requests = 0
        val c = client(MockEngine { requests++; started.complete(Unit); awaitCancellation() })
        val store = ViewModelStore()
        try {
            val vm = SessionViewModel(application, owner, Source(), repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(c)))
            store.put("session", vm)
            withTimeout(5000) { started.await() }
            assertTrue(vm.uiState.value.isSyncing)
            vm.retrySync(); vm.retrySync()
            assertEquals(1, requests)
            store.clear()
            withTimeout(5000) { repository.observeAll(owner).first { it.single().syncStatus == SyncStatus.FAILED } }
            assertTrue(vm.uiState.value.history.isEmpty())
            assertFalse(vm.uiState.value.isSyncing)
            assertEquals(SyncStatus.FAILED, repository.getById(id, owner)!!.syncStatus)
        } finally { store.clear(); c.close(); db.close(); Dispatchers.resetMain() }
    }

    @Test(timeout = 30000) fun completedSessionDuringPreviousUploadIsQueued() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(application, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao())
        repository.insert(RehabSessionEntity(userId = owner, createdAt = 1, exerciseName = "previous",
            durationSeconds = 5, sampleCount = 100, rms = 2f, mav = 1f, peakActivation = 4f))
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var requests = 0
        val c = client(MockEngine {
            requests++
            if (requests == 1) { started.complete(Unit); release.await() }
            respond("""[{"id":"33333333-3333-3333-3333-333333333333"}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val store = ViewModelStore()
        try {
            val source = Source()
            val vm = SessionViewModel(application, owner, source, repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(c)))
            store.put("session", vm)
            withTimeout(5000) { started.await() }
            vm.startSession(); source.samples.emit(EmgSample(1, 2f)); vm.stopSession()
            withTimeout(5000) { vm.uiState.first { it.result != null } }
            assertEquals(2, repository.observeAll(owner).first().size)
            release.complete(Unit)
            withTimeout(5000) { vm.uiState.first { !it.isSyncing && it.history.size == 2 && it.history.all { row -> row.syncStatus == SyncStatus.SYNCED } } }
            assertEquals(2, requests)
        } finally { release.complete(Unit); store.clear(); c.close(); db.close(); Dispatchers.resetMain() }
    }

    @OptIn(SupabaseInternal::class)
    @Test(timeout = 30000) fun sameOwnerAuthRestorationRetriesAfterForegroundInitializingState() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(application, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao())
        val id = repository.insert(RehabSessionEntity(userId = owner, createdAt = 1, exerciseName = "wrist",
            durationSeconds = 5, sampleCount = 100, rms = 2f, mav = 1f, peakActivation = 4f))
        var requests = 0
        val c = client(MockEngine {
            requests++
            respond("""[{"id":"33333333-3333-3333-3333-333333333333"}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val session = c.auth.currentSessionOrNull()!!
        c.auth.setSessionStatus(SessionStatus.Initializing)
        val store = ViewModelStore()
        try {
            val vm = SessionViewModel(application, owner, Source(), repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(c)))
            store.put("session", vm)
            withTimeout(5000) { vm.uiState.first { !it.isSyncing } }
            assertEquals(0, requests)
            c.auth.importSession(session, autoRefresh = false)
            withTimeout(5000) { repository.observeAll(owner).first { it.single().syncStatus == SyncStatus.SYNCED } }
            assertEquals(1, requests)
            assertEquals(SyncStatus.SYNCED, repository.getById(id, owner)!!.syncStatus)
        } finally { store.clear(); c.close(); db.close(); Dispatchers.resetMain() }
    }

    @OptIn(SupabaseInternal::class)
    @Test(timeout = 30000) fun authReadyDuringFinalPendingReadIsNotLost() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(application, ReFitDatabase::class.java).build()
        val actual = db.rehabSessionDao()
        actual.insert(RehabSessionEntity(userId = owner, createdAt = 1, exerciseName = "wrist",
            durationSeconds = 5, sampleCount = 100, rms = 2f, mav = 1f, peakActivation = 4f))
        val readFinished = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var reads = 0
        // Real Room query/result; pause only delivery of the first status read.
        val delayedDao = object : RehabSessionDao by actual {
            override suspend fun getPendingSync(userId: String): List<RehabSessionEntity> {
                val rows = actual.getPendingSync(userId)
                if (++reads == 1) { readFinished.complete(Unit); release.await() }
                return rows
            }
        }
        val repository = RehabSessionRepository(delayedDao)
        var requests = 0
        val c = client(MockEngine {
            requests++
            respond("""[{"id":"33333333-3333-3333-3333-333333333333"}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val session = c.auth.currentSessionOrNull()!!
        c.auth.setSessionStatus(SessionStatus.Initializing)
        val store = ViewModelStore()
        try {
            val vm = SessionViewModel(application, owner, Source(), repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(c)))
            store.put("session", vm)
            withTimeout(5000) { readFinished.await() }
            assertTrue(vm.uiState.value.isSyncing)
            c.auth.importSession(session, autoRefresh = false)
            vm.retrySync() // Foreground request while final status query is suspended.
            release.complete(Unit)
            withTimeout(5000) { actual.observeAll(owner).first { it.single().syncStatus == SyncStatus.SYNCED } }
            assertEquals(1, requests)
        } finally { release.complete(Unit); store.clear(); c.close(); db.close(); Dispatchers.resetMain() }
    }

    private class Source : EmgDataSource {
        override val connectionState = MutableStateFlow<EmgConnectionState>(EmgConnectionState.Disconnected)
        val samples = MutableSharedFlow<EmgSample>()
        override suspend fun connect() { }
        override suspend fun disconnect() { }
        override fun observeEmg() = samples
    }
}
