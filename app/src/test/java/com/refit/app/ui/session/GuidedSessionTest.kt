package com.refit.app.ui.session

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.model.EmgSample
import com.refit.app.data.remote.RehabSessionRemoteDataSource
import com.refit.app.data.repository.RehabSessionRepository
import com.refit.app.data.repository.SessionSyncCoordinator
import com.refit.app.data.source.EmgConnectionState
import com.refit.app.data.source.EmgDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class GuidedSessionTest {
    @Test fun measuredGuidanceWaitsForItsFirstFiniteSample() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repo = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        try {
            val source = Source(seedOnConnect = false)
            val vm = SessionViewModel(app, "silent-owner", source, repository = repo,
                syncCoordinator = SessionSyncCoordinator(repo, RehabSessionRemoteDataSource(null)))
            store.put("session", vm); vm.configureWristSide("left"); vm.configureGuidance(1, 1, 1)
            vm.startSession()
            source.samples.emit(EmgSample(1, Float.NaN))
            advanceTimeBy(4000); runCurrent()
            assertEquals(0L, vm.uiState.value.elapsedSeconds)
            assertEquals(0, vm.uiState.value.completedAttempts)
            assertEquals(3, vm.uiState.value.phaseSecondsRemaining)
            assertEquals(SignalStatus.WAITING, vm.uiState.value.signalStatus)
            source.samples.emit(EmgSample(2, .1f))
            advanceTimeBy(3000); runCurrent()
            assertEquals(GuidePhase.ATTEMPT, vm.uiState.value.phase)
            assertEquals(0L, vm.uiState.value.elapsedSeconds)
            advanceTimeBy(1000); runCurrent()
            assertEquals(1, vm.uiState.value.completedAttempts)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun initialConnectionTimeoutAllowsAnotherStart() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repo = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        var connections = 0
        try {
            val source = Source { if (++connections == 1) withTimeout(1000) { awaitCancellation() } }
            val vm = SessionViewModel(app, "initial-timeout", source, repository = repo,
                syncCoordinator = SessionSyncCoordinator(repo, RehabSessionRemoteDataSource(null)))
            store.put("session", vm); vm.configureWristSide("left"); vm.startSession()
            advanceTimeBy(1000); runCurrent()
            assertFalse(vm.uiState.value.isConnecting)
            assertFalse(vm.uiState.value.running)
            assertNotNull(vm.uiState.value.message)
            vm.startSession()
            assertTrue(vm.uiState.value.running)
            assertTrue(vm.uiState.value.connected)
            assertEquals(2, connections)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun reconnectTimeoutRetainsThePauseAndAllowsAnotherRetry() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repo = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        var connections = 0
        try {
            val source = Source { if (++connections == 2) withTimeout(1000) { awaitCancellation() } }
            val vm = SessionViewModel(app, "retry-timeout", source, repository = repo,
                syncCoordinator = SessionSyncCoordinator(repo, RehabSessionRemoteDataSource(null)))
            store.put("session", vm); vm.configureWristSide("left"); vm.startSession()
            advanceTimeBy(4000); runCurrent()
            source.connectionState.value = EmgConnectionState.Disconnected
            vm.retryConnection(); advanceTimeBy(1000); runCurrent()
            assertFalse(vm.uiState.value.isConnecting)
            assertTrue(vm.uiState.value.running)
            assertTrue(vm.uiState.value.paused)
            assertEquals(1L, vm.uiState.value.elapsedSeconds)
            vm.retryConnection()
            assertTrue(vm.uiState.value.connected)
            assertTrue(vm.uiState.value.paused)
            vm.setPaused(false)
            assertFalse(vm.uiState.value.paused)
            assertEquals(3, connections)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun queuedSamplesCannotClaimReceptionAfterDisconnection() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repo = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        try {
            val source = Source()
            val vm = SessionViewModel(app, "buffered-owner", source, repository = repo,
                syncCoordinator = SessionSyncCoordinator(repo, RehabSessionRemoteDataSource(null)))
            store.put("session", vm); vm.configureWristSide("left"); vm.startSession()
            source.connectionState.value = EmgConnectionState.Disconnected
            source.samples.emit(EmgSample(1, .1f))
            assertEquals(SignalStatus.INTERRUPTED, vm.uiState.value.signalStatus)
            assertFalse(vm.uiState.value.connected)
            assertTrue(vm.uiState.value.paused)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun readyTimeAndSamplesDoNotCountAsExercise() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("guide-ready-owner", 0).edit().putString("side", "left").commit()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        try {
            val source = Source()
            val vm = SessionViewModel(app, "ready-owner", source, repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(null)))
            store.put("session", vm)
            vm.configureWristSide("left"); vm.startSession()
            source.samples.emit(EmgSample(1, 100f))
            scheduler.advanceTimeBy(2000); scheduler.runCurrent()
            assertTrue(vm.uiState.value.running)
            assertEquals("READY", vm.uiState.value.phase.name)
            assertEquals(0L, vm.uiState.value.elapsedSeconds)
            scheduler.advanceTimeBy(1000); scheduler.runCurrent()
            source.samples.emit(EmgSample(2, 3f))
            scheduler.advanceTimeBy(1000); scheduler.runCurrent()
            vm.stopSession()
            val result = withTimeout(5000) { vm.uiState.first { it.result != null }.result!! }
            assertEquals(1L, result.durationSeconds)
            assertEquals(1, result.sampleCount)
            assertEquals(3f, result.peakActivation, 0f)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun disconnectedSensorPausesGuidanceWithoutAddingAnAttempt() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("guide-disconnected-owner", 0).edit().putString("side", "left").commit()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        try {
            val source = Source()
            val vm = SessionViewModel(app, "disconnected-owner", source, repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(null)))
            store.put("session", vm)
            vm.configureWristSide("left"); vm.startSession(); source.samples.emit(EmgSample(1, .1f))
            advanceTimeBy(3000); runCurrent()
            source.connectionState.value = EmgConnectionState.Disconnected
            advanceTimeBy(2000); runCurrent()
            assertTrue(vm.uiState.value.paused)
            assertEquals(0L, vm.uiState.value.elapsedSeconds)
            assertEquals(0, vm.uiState.value.completedAttempts)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }
    @Test fun backgroundPauseDuringConnectionIsPreservedWhenConnectionCompletes() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao())
        val store = ViewModelStore()
        val connected = CompletableDeferred<Unit>()
        try {
            val vm = SessionViewModel(app, "connecting-owner", Source { connected.await() }, repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(null)))
            store.put("session", vm)
            vm.configureWristSide("left"); vm.startSession()
            assertTrue(vm.uiState.value.isConnecting)
            vm.setPaused(true)
            connected.complete(Unit)
            advanceTimeBy(5000); runCurrent()
            assertTrue(vm.uiState.value.running)
            assertTrue(vm.uiState.value.paused)
            assertEquals(0L, vm.uiState.value.elapsedSeconds)
            vm.setPaused(false)
            advanceTimeBy(4000); runCurrent()
            assertEquals(1L, vm.uiState.value.elapsedSeconds)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }
    @Test fun guidanceFinishesWithoutReportingAnEndlessExercise() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val application = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(application, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao())
        val store = ViewModelStore()
        try {
            val vm = SessionViewModel(application, "guided-owner", Source(), repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(null)))
            store.put("session", vm)
            vm.configureWristSide("left"); vm.startSession()
            advanceTimeBy(34_000)
            runCurrent()
            assertFalse("Timed guidance must end instead of counting forever", vm.uiState.value.running)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun pausedTimeAndSamplesAreExcludedAndResultMetadataSurvivesReopen() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "guided-persistence.db"
        app.deleteDatabase(name)
        var db = Room.databaseBuilder(app, ReFitDatabase::class.java, name).build()
        val store = ViewModelStore()
        try {
            val repository = RehabSessionRepository(db.rehabSessionDao())
            val source = Source()
            val vm = SessionViewModel(app, "paused-owner", source, repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(null)))
            store.put("session", vm)
            assertTrue(vm.configureGuidance(2, 3, 2))
            vm.configureWristSide("left"); vm.startSession()
            scheduler.advanceTimeBy(3000); scheduler.runCurrent()
            source.samples.emit(EmgSample(1, 2f))
            scheduler.advanceTimeBy(2000); scheduler.runCurrent()
            assertEquals(1, vm.uiState.value.completedAttempts)
            assertEquals(GuidePhase.REST, vm.uiState.value.phase)
            vm.setPaused(true)
            source.samples.emit(EmgSample(2, 100f))
            scheduler.advanceTimeBy(20000); scheduler.runCurrent()
            assertEquals(2L, vm.uiState.value.elapsedSeconds)
            assertEquals(3, vm.uiState.value.phaseSecondsRemaining)
            vm.setPaused(false)
            source.samples.emit(EmgSample(3, -2f))
            scheduler.advanceTimeBy(8000); scheduler.runCurrent()
            val result = withTimeout(5000) { vm.uiState.first { it.result != null }.result!! }
            assertEquals(10L, result.durationSeconds)
            assertEquals(2, result.sampleCount)
            assertEquals(2f, result.peakActivation, 0f)
            assertEquals(2, result.completedAttempts)
            assertEquals(false, result.endedEarly)
            vm.recordComfort("불편했어요")
            vm.recordSymptoms("있어요", "피곤해요")
            withTimeout(5000) { vm.uiState.first { it.result?.pain == "있어요" && it.result?.fatigue == "피곤해요" } }
            val id = result.savedSessionId!!
            store.clear(); db.close()
            db = Room.databaseBuilder(app, ReFitDatabase::class.java, name).build()
            val row = db.rehabSessionDao().getById(id, "paused-owner")!!
            assertEquals(2, row.completedAttempts)
            assertEquals(2, row.plannedAttempts)
            assertEquals("불편했어요", row.comfort)
            assertEquals("있어요", row.pain)
            assertEquals("피곤해요", row.fatigue)
            assertEquals("left", row.wristSide)
            db.rehabSessionDao().updatePain(id, "other-owner", "없어요", "괜찮아요")
            db.rehabSessionDao().updateFatigue(id, "other-owner", "괜찮아요")
            assertEquals("있어요", db.rehabSessionDao().getById(id, "paused-owner")!!.pain)
            assertEquals("피곤해요", db.rehabSessionDao().getById(id, "paused-owner")!!.fatigue)
            assertEquals(2, row.attemptSeconds)
            assertEquals(3, row.restSeconds)
            assertNull(db.rehabSessionDao().getById(id, "other-owner"))
            db.rehabSessionDao().updateComfort(id, "other-owner", "괜찮아요")
            assertEquals("불편했어요", db.rehabSessionDao().getById(id, "paused-owner")!!.comfort)
        } finally { store.clear(); db.close(); app.deleteDatabase(name); Dispatchers.resetMain() }
    }

    @Test fun stoppingDuringAnAttemptDoesNotInventACompletedMovement() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repository = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        try {
            val source = Source()
            val vm = SessionViewModel(app, "early-owner", source, repository = repository,
                syncCoordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(null)))
            store.put("session", vm)
            vm.configureGuidance(4,6,3); vm.configureWristSide("left"); vm.startSession()
            scheduler.advanceTimeBy(3000); scheduler.runCurrent()
            source.samples.emit(EmgSample(1, 100f))
            scheduler.advanceTimeBy(2000); scheduler.runCurrent(); vm.stopSession()
            val result = withTimeout(5000) { vm.uiState.first { it.result != null }.result!! }
            assertEquals(0, result.completedAttempts)
            assertEquals(true, result.endedEarly)
            assertEquals(2L, result.durationSeconds)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun heldRestDoesNotAddTimeOrAttemptsAndContinuesTheRemainingRest() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repo = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        try {
            val vm = SessionViewModel(app, "held-rest", Source(), repository = repo,
                syncCoordinator = SessionSyncCoordinator(repo, RehabSessionRemoteDataSource(null)))
            store.put("session", vm); vm.configureWristSide("right"); vm.configureGuidance(2, 3, 2)
            vm.startSession(); advanceTimeBy(5000); runCurrent()
            assertEquals(GuidePhase.REST, vm.uiState.value.phase)
            vm.extendRest(); advanceTimeBy(20000); runCurrent()
            assertEquals(2L, vm.uiState.value.elapsedSeconds)
            assertEquals(1, vm.uiState.value.completedAttempts)
            assertEquals(3, vm.uiState.value.phaseSecondsRemaining)
            assertTrue(vm.uiState.value.restHeld)
            vm.setPaused(false); advanceTimeBy(3000); runCurrent()
            assertEquals(GuidePhase.ATTEMPT, vm.uiState.value.phase)
            assertEquals(5L, vm.uiState.value.elapsedSeconds)
            assertFalse(vm.uiState.value.restHeld)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun staleAndNonFiniteSamplesCannotResumeAMeasuredSession() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repo = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        try {
            val source = Source()
            val vm = SessionViewModel(app, "stale-owner", source, repository = repo,
                syncCoordinator = SessionSyncCoordinator(repo, RehabSessionRemoteDataSource(null)))
            store.put("session", vm); vm.configureWristSide("left"); vm.startSession()
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(6))
            source.samples.emit(EmgSample(1, Float.NaN))
            advanceTimeBy(1000); runCurrent()
            assertTrue(vm.uiState.value.paused)
            vm.setPaused(false); assertTrue(vm.uiState.value.paused)
            source.samples.emit(EmgSample(2, .1f)); vm.setPaused(false)
            assertFalse(vm.uiState.value.paused)
            advanceTimeBy(4000); runCurrent()
            assertEquals(1L, vm.uiState.value.elapsedSeconds)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun wristAndVoicePreferencesStayWithTheirAccount() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("guide-pref-a", 0).edit().clear().commit()
        app.getSharedPreferences("guide-pref-b", 0).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repo = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        fun create(owner: String) = SessionViewModel(app, owner, Source(), repository = repo,
            syncCoordinator = SessionSyncCoordinator(repo, RehabSessionRemoteDataSource(null)))
        try {
            val first = create("pref-a"); store.put("first", first)
            first.configureWristSide("right"); first.setVoiceEnabled(false)
            val reopened = create("pref-a"); store.put("reopened", reopened)
            val other = create("pref-b"); store.put("other", other)
            assertEquals("right", reopened.uiState.value.wristSide)
            assertFalse(reopened.uiState.value.voiceEnabled)
            assertNull(other.uiState.value.wristSide)
            assertTrue(other.uiState.value.voiceEnabled)
            assertFalse(reopened.configureWristSide("invalid"))
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    private class Source(private val seedOnConnect: Boolean = true, private val onConnect: suspend () -> Unit = {}) : EmgDataSource {
        override val connectionState = MutableStateFlow<EmgConnectionState>(EmgConnectionState.Disconnected)
        val samples = MutableSharedFlow<EmgSample>(replay = 1)
        override suspend fun connect() { onConnect(); connectionState.value = EmgConnectionState.Connected("test"); if (seedOnConnect) samples.tryEmit(EmgSample(0, 0f)) }
        override suspend fun disconnect() { }
        override fun observeEmg() = samples
    }
}
