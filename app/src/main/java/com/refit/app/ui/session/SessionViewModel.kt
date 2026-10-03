package com.refit.app.ui.session

import android.app.Application
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
import com.refit.app.signal.EmgSignalProcessor
import com.refit.app.signal.RealtimeEmgWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val connected: Boolean = false,
    val running: Boolean = false,
    val samples: List<Float> = emptyList(),
    val currentActivation: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val result: RehabSessionResult? = null,
    val history: List<RehabSessionEntity> = emptyList(),
    val showHistory: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null
)

class SessionViewModel(
    application: Application,
    private val userId: String,
    private val dataSource: EmgDataSource = MockEmgDataSource(),
    realtimeWindowSize: Int = 50
) : AndroidViewModel(application) {

    private val repository = RehabSessionRepository(
        ReFitDatabase.getInstance(application).rehabSessionDao()
    )

    private val syncCoordinator = SessionSyncCoordinator(repository)
    private val realtimeWindow = RealtimeEmgWindow(realtimeWindowSize)

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var emgJob: Job? = null
    private var timerJob: Job? = null

    private val sessionSamples = mutableListOf<Float>()

    init {
        viewModelScope.launch {
            repository.observeAll(userId).collect { history ->
                _uiState.value = _uiState.value.copy(
                    history = history
                )
            }
        }

        viewModelScope.launch {
            syncCoordinator.syncPending(userId)
        }
    }

    fun startSession() {
        if (_uiState.value.running) return

        sessionSamples.clear()
        realtimeWindow.clear()

        emgJob = viewModelScope.launch {
            runCatching {
                dataSource.connect()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    connected = false,
                    running = false,
                    message = error.message ?: "센서 연결에 실패했습니다."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                connected = true,
                running = true,
                samples = emptyList(),
                currentActivation = 0f,
                elapsedSeconds = 0L,
                result = null,
                showHistory = false,
                isSaving = false,
                message = null
            )

            dataSource.observeEmg().collect { sample ->
                sessionSamples += sample.value

                val visibleSamples =
                    (_uiState.value.samples + sample.value).takeLast(200)

                _uiState.value = _uiState.value.copy(
                    samples = visibleSamples,
                    currentActivation = realtimeWindow.add(sample.value)
                )
            }
        }

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_uiState.value.running) {
                    _uiState.value = _uiState.value.copy(
                        elapsedSeconds = _uiState.value.elapsedSeconds + 1
                    )
                }
            }
        }
    }

    fun stopSession() {
        if (!_uiState.value.running) return

        val duration = _uiState.value.elapsedSeconds
        val samplesSnapshot = sessionSamples.toList()
        val createdAt = System.currentTimeMillis()

        emgJob?.cancel()
        timerJob?.cancel()

        _uiState.value = _uiState.value.copy(
            connected = false,
            running = false,
            isSaving = samplesSnapshot.isNotEmpty()
        )

        viewModelScope.launch {
            runCatching { dataSource.disconnect() }

            if (samplesSnapshot.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = "측정된 EMG 데이터가 없어 세션을 저장하지 않았습니다."
                )
                return@launch
            }

            val baseResult = RehabSessionResult(
                durationSeconds = duration,
                sampleCount = samplesSnapshot.size,
                rms = EmgSignalProcessor.rms(samplesSnapshot),
                mav = EmgSignalProcessor.mav(samplesSnapshot),
                peakActivation = EmgSignalProcessor.peakMagnitude(samplesSnapshot)
            )

            val previous = repository.getLatest(userId)

            val sessionId = repository.insert(
                RehabSessionEntity(
                    userId = userId,
                    createdAt = createdAt,
                    exerciseName = "손목 폄 운동",
                    durationSeconds = duration,
                    sampleCount = samplesSnapshot.size,
                    rms = baseResult.rms,
                    mav = baseResult.mav,
                    peakActivation = baseResult.peakActivation,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    updatedAt = createdAt
                )
            )

            _uiState.value = _uiState.value.copy(
                result = baseResult.copy(
                    previousRms = previous?.rms,
                    savedSessionId = sessionId
                ),
                isSaving = false,
                message = null
            )

            launch {
                syncCoordinator.syncSession(sessionId)
            }
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
        sessionSamples.clear()
        realtimeWindow.clear()
        _uiState.value = SessionUiState(
            history = _uiState.value.history
        )
    }

    override fun onCleared() {
        emgJob?.cancel()
        timerJob?.cancel()
        sessionSamples.clear()
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
