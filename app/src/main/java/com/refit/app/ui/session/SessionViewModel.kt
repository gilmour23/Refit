package com.refit.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.refit.app.data.model.RehabSessionResult
import com.refit.app.data.source.EmgDataSource
import com.refit.app.data.source.MockEmgDataSource
import com.refit.app.signal.EmgSignalProcessor
import kotlinx.coroutines.Job
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
    val result: RehabSessionResult? = null
)

class SessionViewModel : ViewModel() {

    private val dataSource: EmgDataSource = MockEmgDataSource()

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var emgJob: Job? = null
    private var timerJob: Job? = null

    private val sessionSamples = mutableListOf<Float>()

    fun startSession() {
        if (_uiState.value.running) return

        sessionSamples.clear()

        emgJob = viewModelScope.launch {
            dataSource.connect()

            _uiState.value = SessionUiState(
                connected = true,
                running = true
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

        val result = RehabSessionResult(
            durationSeconds = duration,
            sampleCount = samplesSnapshot.size,
            rms = EmgSignalProcessor.rms(samplesSnapshot),
            mav = EmgSignalProcessor.mav(samplesSnapshot),
            maxActivation = samplesSnapshot.maxOrNull() ?: 0f
        )

        emgJob?.cancel()
        timerJob?.cancel()

        viewModelScope.launch {
            dataSource.disconnect()
        }

        _uiState.value = _uiState.value.copy(
            connected = false,
            running = false,
            result = result
        )
    }

    fun resetSession() {
        sessionSamples.clear()
        _uiState.value = SessionUiState()
    }

    override fun onCleared() {
        emgJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }
}
