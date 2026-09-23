package com.refit.app.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.RehabSessionResult
import com.refit.app.data.repository.RehabSessionRepository
import com.refit.app.data.source.EmgDataSource
import com.refit.app.data.source.MockEmgDataSource
import com.refit.app.signal.EmgSignalProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReFitScreen {
    HOME,
    INTRO,
    LIVE,
    RESULT,
    HISTORY
}

data class SessionUiState(
    val screen: ReFitScreen = ReFitScreen.HOME,
    val connected: Boolean = false,
    val running: Boolean = false,
    val samples: List<Float> = emptyList(),
    val currentActivation: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val result: RehabSessionResult? = null,
    val history: List<RehabSessionEntity> = emptyList(),
    val isSaving: Boolean = false
)

class SessionViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val dataSource: EmgDataSource = MockEmgDataSource()

    private val repository = RehabSessionRepository(
        ReFitDatabase.getInstance(application).rehabSessionDao()
    )

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var emgJob: Job? = null
    private var timerJob: Job? = null

    private val sessionSamples = mutableListOf<Float>()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { history ->
                _uiState.value = _uiState.value.copy(
                    history = history
                )
            }
        }
    }

    fun openIntro() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.INTRO,
            result = null
        )
    }

    fun openHistory() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.HISTORY,
            result = null
        )
    }

    fun backToHome() {
        if (_uiState.value.running) return
        sessionSamples.clear()
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.HOME,
            connected = false,
            running = false,
            samples = emptyList(),
            currentActivation = 0f,
            elapsedSeconds = 0L,
            result = null,
            isSaving = false
        )
    }

    fun startSession() {
        if (_uiState.value.running || _uiState.value.isSaving) return

        sessionSamples.clear()

        emgJob = viewModelScope.launch {
            dataSource.connect()

            _uiState.value = _uiState.value.copy(
                screen = ReFitScreen.LIVE,
                connected = true,
                running = true,
                samples = emptyList(),
                currentActivation = 0f,
                elapsedSeconds = 0L,
                result = null,
                isSaving = false
            )

            dataSource.observeEmg().collect { sample ->
                sessionSamples += sample.value

                val visibleSamples =
                    (_uiState.value.samples + sample.value).takeLast(200)

                _uiState.value = _uiState.value.copy(
                    samples = visibleSamples,
                    currentActivation = sample.value
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

        val baseResult = RehabSessionResult(
            durationSeconds = duration,
            sampleCount = samplesSnapshot.size,
            rms = EmgSignalProcessor.rms(samplesSnapshot),
            mav = EmgSignalProcessor.mav(samplesSnapshot),
            maxActivation = samplesSnapshot.maxOrNull() ?: 0f
        )

        emgJob?.cancel()
        timerJob?.cancel()

        _uiState.value = _uiState.value.copy(
            connected = false,
            running = false,
            isSaving = true
        )

        viewModelScope.launch {
            dataSource.disconnect()

            val previous = repository.getLatest()

            val sessionId = repository.insert(
                RehabSessionEntity(
                    createdAt = createdAt,
                    exerciseName = "손목 폄 운동",
                    durationSeconds = duration,
                    sampleCount = samplesSnapshot.size,
                    rms = baseResult.rms,
                    mav = baseResult.mav,
                    maxActivation = baseResult.maxActivation
                )
            )

            _uiState.value = _uiState.value.copy(
                screen = ReFitScreen.RESULT,
                result = baseResult.copy(
                    previousRms = previous?.rms,
                    savedSessionId = sessionId
                ),
                isSaving = false
            )
        }
    }

    fun resetSession() {
        backToHome()
    }

    override fun onCleared() {
        emgJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }
}
