package com.refit.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.refit.app.data.source.EmgDataSource
import com.refit.app.data.source.MockEmgDataSource
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
    val elapsedSeconds: Long = 0L
)

class SessionViewModel : ViewModel() {

    private val dataSource: EmgDataSource = MockEmgDataSource()

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var emgJob: Job? = null
    private var timerJob: Job? = null

    fun startSession() {
        if (_uiState.value.running) return

        emgJob = viewModelScope.launch {
            dataSource.connect()

            _uiState.value = _uiState.value.copy(
                connected = true,
                running = true,
                elapsedSeconds = 0L,
                samples = emptyList()
            )

            dataSource.observeEmg().collect { sample ->
                val updated = (_uiState.value.samples + sample.value).takeLast(200)

                _uiState.value = _uiState.value.copy(
                    samples = updated,
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

        viewModelScope.launch {
            dataSource.disconnect()
        }

        emgJob?.cancel()
        timerJob?.cancel()

        _uiState.value = _uiState.value.copy(
            connected = false,
            running = false
        )
    }

    override fun onCleared() {
        emgJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }
}
