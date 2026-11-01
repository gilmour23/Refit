package com.refit.app.ui.session

import android.app.Application
import android.os.SystemClock
import com.refit.app.data.source.EmgConnectionState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.RehabSessionResult
import com.refit.app.data.model.SyncStatus
import com.refit.app.data.repository.RehabSessionRepository
import com.refit.app.data.repository.SessionSyncCoordinator
import com.refit.app.data.source.EmgDataSource
import com.refit.app.data.source.MockEmgDataSource
import com.refit.app.signal.RealtimeEmgWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class SessionUiState(
    val storageAvailable: Boolean = true,
    val connected: Boolean = false,
    val running: Boolean = false,
    val isConnecting: Boolean = false,
    val paused: Boolean = false,
    val dataMode: String = "demo",
    val guideTiming: GuideTiming = GuideTiming(),
    val appliedPlan: com.refit.app.data.remote.ExercisePlanDto? = null,
    val completedAttempts: Int = 0,
    val phase: GuidePhase = GuidePhase.READY,
    val wristSide: String? = null,
    val voiceEnabled: Boolean = true,
    val signalStatus: SignalStatus = SignalStatus.WAITING,
    val restHeld: Boolean = false,
    val phaseSecondsRemaining: Int = 3,
    val samples: List<Float> = emptyList(),
    val currentActivation: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val result: RehabSessionResult? = null,
    val history: List<RehabSessionEntity> = emptyList(),
    val showHistory: Boolean = false,
    val isSaving: Boolean = false,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val message: String? = null
)

class SessionViewModel(
    application: Application,
    private val userId: String,
    private val dataSource: EmgDataSource = MockEmgDataSource(),
    realtimeWindowSize: Int = 50,
    private val repository: RehabSessionRepository = RehabSessionRepository(
        ReFitDatabase.getInstance(application).rehabSessionDao()
    ),
    private val syncCoordinator: SessionSyncCoordinator = SessionSyncCoordinator(repository),
    private val centerRemote: com.refit.app.data.remote.PatientCenterGateway? = null
) : AndroidViewModel(application) {
    private val realtimeWindow = RealtimeEmgWindow(realtimeWindowSize)
    private val patientCenter by lazy { centerRemote ?: com.refit.app.data.remote.PatientCenterRemoteDataSource() }

    private val preferences = application.getSharedPreferences("guide-$userId", android.content.Context.MODE_PRIVATE)
    private val initialTiming = runCatching { GuideTiming(preferences.getInt("attempt", 4),
        preferences.getInt("rest", 6), preferences.getInt("count", 3)) }.getOrDefault(GuideTiming())
    private val dataMode = if (dataSource is MockEmgDataSource) "demo" else "sensor"
    private val _uiState = MutableStateFlow(SessionUiState(guideTiming = initialTiming,
        phaseSecondsRemaining = 3, dataMode = dataMode,
        wristSide = preferences.getString("side", null)?.takeIf { it in listOf("left", "right") },
        voiceEnabled = preferences.getBoolean("voice", true)))
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var emgJob: Job? = null
    private var planVerificationJob: Job? = null
    private var timerJob: Job? = null
    private var syncJob: Job? = null
    private var syncRequested = false
    private var restoreManualAfterSession = false
    private var lastSampleAt: Long? = null
    private var connectedAt = 0L
    private var sensorWatchJob: Job? = null

    private val sessionCapture = com.refit.app.signal.EmgCaptureAccumulator()

    init {
        sensorWatchJob = viewModelScope.launch {
            var seenConnected = false
            dataSource.connectionState.collect { connection ->
                when (connection) {
                    is EmgConnectionState.Connected -> {
                        seenConnected = true
                        if (_uiState.value.running || _uiState.value.isConnecting)
                            _uiState.update { it.copy(connected = true) }
                    }
                    is EmgConnectionState.Disconnected, is EmgConnectionState.Error -> {
                        if (seenConnected && _uiState.value.running) interruptSignal(disconnected = true)
                    }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            repository.observeAll(userId).catch {
                _uiState.update { it.copy(storageAvailable = false, history = emptyList(),
                    message = "저장된 기록을 열 수 없습니다. 기록은 유지됩니다. 관리자에게 문의해 주세요.") }
            }.collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }

        retrySync()
        viewModelScope.launch {
            syncCoordinator.syncOwner.collect { readyOwner ->
                if (readyOwner == userId) retrySync()
            }
        }
    }

    fun retrySync() {
        if (syncJob?.isActive == true) {
            syncRequested = true
            return
        }
        _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
        syncJob = viewModelScope.launch {
            try {
                do {
                    syncRequested = false
                    syncCoordinator.syncPending(userId)
                    val remaining = repository.getPendingSync(userId).size
                    _uiState.update { it.copy(syncMessage = if (remaining == 0) {
                        "모든 기록이 동기화되었습니다."
                    } else {
                        "미동기화 기록 ${remaining}개가 이 기기에 저장되어 있습니다. 연결을 확인한 후 다시 시도해 주세요."
                    }) }
                    // This check follows every suspension, including the final
                    // Room status read, so no active-worker trigger is dropped.
                } while (syncRequested)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(syncMessage = "동기화하지 못했습니다. 저장된 기록은 이 기기에 유지됩니다.") }
            } finally {
                if (currentCoroutineContext().isActive) {
                    _uiState.update { it.copy(isSyncing = false) }
                }
            }
        }
    }

    fun configureGuidance(attemptSeconds: Int, restSeconds: Int, attempts: Int): Boolean {
        if (_uiState.value.running || _uiState.value.isConnecting || _uiState.value.isSaving) return false
        val timing = runCatching { GuideTiming(attemptSeconds, restSeconds, attempts) }.getOrNull() ?: return false
        restoreManualAfterSession = false
        preferences.edit().putInt("attempt", attemptSeconds).putInt("rest", restSeconds).putInt("count", attempts).apply()
        _uiState.update { it.copy(guideTiming = timing, appliedPlan = null, phaseSecondsRemaining = timing.attemptSeconds) }
        return true
    }

    fun applyClinicianPlan(plan: com.refit.app.data.remote.ExercisePlanDto): Boolean {
        val state = _uiState.value
        if (state.running || state.isConnecting || state.isSaving ||
            !plan.isActiveFor(userId, java.time.LocalDate.now())) return false
        val timing = plan.timingOrNull() ?: return false
        restoreManualAfterSession = false
        _uiState.update { it.copy(guideTiming = timing, appliedPlan = plan,
            phaseSecondsRemaining = timing.attemptSeconds) }
        return true
    }

    fun reconcileClinicianPlan(activePlanId: String?) {
        val state = _uiState.value
        if (state.appliedPlan != null && state.appliedPlan.id != activePlanId) {
            val manual = runCatching { GuideTiming(preferences.getInt("attempt", 4),
                preferences.getInt("rest", 6), preferences.getInt("count", 3)) }.getOrDefault(GuideTiming())
            restoreManualAfterSession = state.running || state.isConnecting || state.isSaving
            _uiState.update { it.copy(appliedPlan = null,
                guideTiming = if (it.running || it.isConnecting || it.isSaving) it.guideTiming else manual) }
        }
    }

    fun configureWristSide(side: String): Boolean {
        if (side !in listOf("left", "right") || _uiState.value.running || _uiState.value.isConnecting || _uiState.value.isSaving) return false
        preferences.edit().putString("side", side).apply()
        _uiState.update { it.copy(wristSide = side) }
        return true
    }

    fun setVoiceEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("voice", enabled).apply()
        _uiState.update { it.copy(voiceEnabled = enabled) }
    }

    private fun restoreManualGuidanceIfNeeded() {
        if (restoreManualAfterSession && !_uiState.value.running && !_uiState.value.isConnecting && !_uiState.value.isSaving) {
            val manual = runCatching { GuideTiming(preferences.getInt("attempt", 4),
                preferences.getInt("rest", 6), preferences.getInt("count", 3)) }.getOrDefault(GuideTiming())
            _uiState.update { it.copy(guideTiming = manual) }
            restoreManualAfterSession = false
        }
    }

    fun startSession() {
        val current = _uiState.value
        if (current.running || current.isConnecting || current.isSaving) return
        _uiState.update { it.copy(paused = false) }
        val selected = current.appliedPlan
        if (selected == null) { startVerifiedSession(); return }
        _uiState.update { it.copy(isConnecting = true, message = "센터 계획을 확인하고 있어요.") }
        planVerificationJob = viewModelScope.launch {
            val active = try { patientCenter.read(userId).activePlan }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            _uiState.update { it.copy(isConnecting = false) }
            if (active?.id != selected.id || _uiState.value.appliedPlan?.id != selected.id) {
                reconcileClinicianPlan(null)
                restoreManualGuidanceIfNeeded()
                _uiState.update { it.copy(message = "센터 연결과 오늘의 계획을 다시 확인해 주세요. 현재 안내는 직접 설정한 시간으로 돌아왔어요.") }
            } else startVerifiedSession()
        }
    }

    private fun startVerifiedSession() {
        restoreManualGuidanceIfNeeded()
        _uiState.value.appliedPlan?.let {
            if (!it.isActiveFor(userId, java.time.LocalDate.now())) {
                reconcileClinicianPlan(null)
                restoreManualGuidanceIfNeeded()
                _uiState.update { state -> state.copy(message = "계획 적용 날짜가 바뀌었어요. 오늘의 계획을 다시 확인해 주세요.") }
                return
            }
        }
        if (!_uiState.value.storageAvailable) return
        if (_uiState.value.running || _uiState.value.isConnecting || _uiState.value.isSaving) return
        if (_uiState.value.wristSide == null) {
            _uiState.update { it.copy(message = "운동할 손목을 선택하세요.") }
            return
        }
        sessionCapture.clear()
        realtimeWindow.clear()
        _uiState.update { it.copy(isConnecting = true, result = null, message = null,
            signalStatus = if (dataMode == "demo") SignalStatus.DEMO else SignalStatus.WAITING, restHeld = false) }
        connectAndCollect(newSession = true)
    }

    fun retryConnection() {
        if (!_uiState.value.running || _uiState.value.isConnecting) return
        _uiState.update { it.copy(isConnecting = true, paused = true, message = null) }
        emgJob?.cancel()
        connectAndCollect(newSession = false)
    }

    private fun connectAndCollect(newSession: Boolean) {
        emgJob = viewModelScope.launch {
            try {
                if (!newSession) dataSource.disconnect()
                dataSource.connect()
                connectedAt = SystemClock.elapsedRealtime()
                lastSampleAt = null
                _uiState.update {
                    if (newSession) it.copy(connected = true, isConnecting = false, running = true,
                        samples = emptyList(), currentActivation = 0f, elapsedSeconds = 0L, completedAttempts = 0,
                        phase = GuidePhase.READY, phaseSecondsRemaining = 3, showHistory = false)
                    else it.copy(connected = true, isConnecting = false, signalStatus = SignalStatus.WAITING)
                }
                if (newSession) startTimer()
                dataSource.observeEmg().collect { sample ->
                    if (!_uiState.value.running || !sample.value.isFinite()) return@collect
                    if (dataMode != "demo" && (!_uiState.value.connected || dataSource.connectionState.value !is EmgConnectionState.Connected)) return@collect
                    lastSampleAt = SystemClock.elapsedRealtime()
                    _uiState.update { it.copy(signalStatus = if (dataMode == "demo") SignalStatus.DEMO else SignalStatus.RECEIVING) }
                    if (_uiState.value.paused || _uiState.value.phase == GuidePhase.READY) return@collect
                    sessionCapture.add(sample.value)
                    _uiState.update { it.copy(samples = (it.samples + sample.value).takeLast(200),
                        currentActivation = realtimeWindow.add(sample.value)) }
                }
                if (_uiState.value.running) interruptSignal(disconnected = true)
            } catch (_: TimeoutCancellationException) { connectionFailed() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { connectionFailed() }
        }
    }

    private fun connectionFailed() {
        if (_uiState.value.running) interruptSignal(disconnected = true)
        _uiState.update { it.copy(isConnecting = false, connected = false, message = "연결하지 못했어요. 다시 연결해 주세요.") }
    }

    private fun hasFreshSignal(state: SessionUiState): Boolean = state.connected &&
        state.signalStatus == SignalStatus.RECEIVING &&
        lastSampleAt?.let { SystemClock.elapsedRealtime() - it < 5000 } == true

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (_uiState.value.running) {
                delay(1000)
                val state = _uiState.value
                if (!state.running) break
                if (dataMode != "demo" && !state.isConnecting && SystemClock.elapsedRealtime() - (lastSampleAt ?: connectedAt) >= 5000) {
                    interruptSignal(disconnected = false)
                }
                if (_uiState.value.paused) continue
                if (dataMode != "demo" && !hasFreshSignal(_uiState.value)) continue
                if (state.phase == GuidePhase.READY) {
                    val remaining = state.phaseSecondsRemaining - 1
                    _uiState.update { it.copy(phase = if (remaining <= 0) GuidePhase.ATTEMPT else GuidePhase.READY,
                        phaseSecondsRemaining = if (remaining <= 0) it.guideTiming.attemptSeconds else remaining) }
                    continue
                }
                val elapsed = state.elapsedSeconds + 1
                val progress = state.guideTiming.progress(elapsed)
                _uiState.update { it.copy(elapsedSeconds = elapsed, phase = progress.phase,
                    completedAttempts = progress.completedAttempts, phaseSecondsRemaining = progress.remainingSeconds) }
                if (progress.phase == GuidePhase.FINISHED) stopSession()
            }
        }
    }

    private fun interruptSignal(disconnected: Boolean) {
        _uiState.update { it.copy(paused = true, connected = if (disconnected) false else it.connected,
            signalStatus = SignalStatus.INTERRUPTED, currentActivation = 0f, samples = emptyList()) }
    }

    fun setPaused(paused: Boolean) {
        val state = _uiState.value
        if (!state.running && !state.isConnecting) return
        if (!paused && dataMode != "demo" && !hasFreshSignal(state)) {
            _uiState.update { it.copy(message = "연결과 신호를 확인한 뒤 이어서 해주세요.") }
            return
        }
        realtimeWindow.clear()
        _uiState.update { it.copy(paused = paused, restHeld = if (paused) it.restHeld else false,
            currentActivation = 0f, samples = emptyList(), message = null) }
    }

    fun extendRest() {
        if (_uiState.value.running && _uiState.value.phase == GuidePhase.REST) {
            setPaused(true)
            _uiState.update { it.copy(restHeld = true) }
        }
    }

    fun stopSession() {
        val state = _uiState.value
        if (!state.running) return
        val sampleCount = sessionCapture.sampleCount
        val rms = sessionCapture.rms
        val mav = sessionCapture.mav
        val peak = sessionCapture.peak
        val rawCapture = sessionCapture.raw(dataSource.captureMetadata)
        val createdAt = System.currentTimeMillis()
        emgJob?.cancel()
        timerJob?.cancel()
        _uiState.update { it.copy(connected = false, running = false, paused = false, isSaving = true) }
        viewModelScope.launch {
            runCatching { dataSource.disconnect() }
            val result = RehabSessionResult(durationSeconds = state.elapsedSeconds, sampleCount = sampleCount,
                rms = rms, mav = mav, peakActivation = peak,
                completedAttempts = state.completedAttempts, plannedAttempts = state.guideTiming.attempts,
                attemptSeconds = state.guideTiming.attemptSeconds, restSeconds = state.guideTiming.restSeconds,
                endedEarly = state.elapsedSeconds < state.guideTiming.totalSeconds, dataMode = dataMode, wristSide = state.wristSide)
            try {
                val id = repository.insert(RehabSessionEntity(userId = userId, createdAt = createdAt,
                    exerciseName = if (dataMode == "demo") "손목 폄 시도 · 데모" else "손목 폄 시도",
                    durationSeconds = result.durationSeconds, sampleCount = result.sampleCount, rms = result.rms,
                    mav = result.mav, peakActivation = result.peakActivation, syncStatus = SyncStatus.LOCAL_ONLY,
                    updatedAt = createdAt, completedAttempts = result.completedAttempts, plannedAttempts = result.plannedAttempts,
                    attemptSeconds = result.attemptSeconds, restSeconds = result.restSeconds, endedEarly = result.endedEarly,
                    dataMode = dataMode, wristSide = state.wristSide,
                    sourceDevice = dataSource.sourceDevice, sourceAppVersion = com.refit.app.BuildConfig.VERSION_NAME,
                    rawEmg = rawCapture))
                _uiState.update { it.copy(result = result.copy(savedSessionId = id), isSaving = false) }
                restoreManualGuidanceIfNeeded()
                retrySync()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _uiState.update { it.copy(result = result, isSaving = false, message = "기록을 저장하지 못했습니다.") }
                restoreManualGuidanceIfNeeded()
            }
        }
    }

    fun recordComfort(comfort: String) {
        if (comfort !in listOf("괜찮아요", "불편했어요")) return
        val result = _uiState.value.result ?: return
        val id = result.savedSessionId ?: return
        viewModelScope.launch {
            try {
                repository.updateComfort(id, userId, comfort)
                _uiState.update { it.copy(result = it.result?.copy(comfort = comfort)) }
                retrySync()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(message = "운동 후 상태를 저장하지 못했습니다.") } }
        }
    }

    fun recordSymptoms(pain: String?, fatigue: String?) {
        if (pain != null && pain !in listOf("없어요", "있어요", "조금 아파요", "많이 아파요")) return
        if (fatigue != null && fatigue !in listOf("괜찮아요", "피곤해요", "조금 힘듦", "많이 힘듦")) return
        val id = _uiState.value.result?.savedSessionId ?: return
        viewModelScope.launch {
            try {
                val comfort = if (pain != null && pain != "없어요") "불편했어요" else "괜찮아요"
                if (pain != null) repository.updatePain(id, userId, pain, comfort)
                if (fatigue != null) repository.updateFatigue(id, userId, fatigue)
                _uiState.update { state -> state.copy(result = state.result?.copy(
                    pain = pain ?: state.result.pain, fatigue = fatigue ?: state.result.fatigue,
                    comfort = if (pain != null) comfort else state.result.comfort)) }
                retrySync()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(message = "운동 후 상태를 저장하지 못했어요.") } }
        }
    }

    fun openHistory() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            showHistory = true,
            result = null
        )
    }

    fun closeHistory() {
        _uiState.value = _uiState.value.copy(
            showHistory = false
        )
    }

    fun resetSession() {
        restoreManualGuidanceIfNeeded()
        sessionCapture.clear()
        realtimeWindow.clear()
        _uiState.value = SessionUiState(
            history = _uiState.value.history,
            isSyncing = _uiState.value.isSyncing,
            syncMessage = _uiState.value.syncMessage,
            guideTiming = _uiState.value.guideTiming,
            appliedPlan = _uiState.value.appliedPlan,
            phaseSecondsRemaining = 3,
            wristSide = _uiState.value.wristSide, voiceEnabled = _uiState.value.voiceEnabled,
            dataMode = dataMode
        )
    }

    override fun onCleared() {
        planVerificationJob?.cancel()
        emgJob?.cancel()
        timerJob?.cancel()
        sensorWatchJob?.cancel()
        sessionCapture.clear()
        realtimeWindow.clear()
        _uiState.value = SessionUiState()
        // viewModelScope has already been cancelled at this point. A separate
        // one-shot cleanup releases the sensor connection without saving an
        // abandoned exercise to a different account.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching { dataSource.disconnect() }
        }
        super.onCleared()
    }
}

