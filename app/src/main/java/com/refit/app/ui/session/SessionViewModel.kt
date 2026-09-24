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
    SENSOR_SETUP,
    CALIBRATION,
    INTRO,
    LIVE,
    RESULT,
    HISTORY
}

enum class CalibrationPhase {
    IDLE,
    REST,
    CONTRACT,
    COMPLETE
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
    val isSaving: Boolean = false,
    val orthosisProfile: String = "맞춤 손목 보조기",
    val setupConfirmed: Boolean = false,
    val calibrationPhase: CalibrationPhase = CalibrationPhase.IDLE,
    val calibrationProgress: Float = 0f,
    val calibrationRest: Float? = null,
    val calibrationActive: Float? = null
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
    private var calibrationJob: Job? = null

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

    fun openSensorSetup() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.SENSOR_SETUP,
            setupConfirmed = false,
            calibrationPhase = CalibrationPhase.IDLE,
            calibrationProgress = 0f,
            result = null
        )
    }

    fun confirmSensorSetup() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.CALIBRATION,
            setupConfirmed = true,
            calibrationPhase = CalibrationPhase.IDLE,
            calibrationProgress = 0f
        )
    }

    fun startCalibration() {
        if (calibrationJob?.isActive == true || _uiState.value.running) return

        calibrationJob = viewModelScope.launch {
            dataSource.connect()

            val collected = mutableListOf<Float>()

            val collectorJob = launch {
                dataSource.observeEmg().collect { sample ->
                    collected += sample.value
                    _uiState.value = _uiState.value.copy(
                        connected = true,
                        currentActivation = sample.value
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                calibrationPhase = CalibrationPhase.REST,
                calibrationProgress = 0f
            )

            repeat(30) { index ->
                delay(100)
                _uiState.value = _uiState.value.copy(
                    calibrationProgress = (index + 1) / 30f
                )
            }

            val restCount = collected.size
            val restSamples = collected.toList()
            val restBaseline = restSamples
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat()
                ?: 0f

            _uiState.value = _uiState.value.copy(
                calibrationPhase = CalibrationPhase.CONTRACT,
                calibrationProgress = 0f,
                calibrationRest = restBaseline
            )

            repeat(30) { index ->
                delay(100)
                _uiState.value = _uiState.value.copy(
                    calibrationProgress = (index + 1) / 30f
                )
            }

            val activeSamples = collected.drop(restCount)
            val activeBaseline = activeSamples
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat()
                ?: restBaseline

            collectorJob.cancel()
            dataSource.disconnect()

            _uiState.value = _uiState.value.copy(
                connected = false,
                calibrationPhase = CalibrationPhase.COMPLETE,
                calibrationProgress = 1f,
                calibrationRest = restBaseline,
                calibrationActive = activeBaseline
            )
        }
    }

    fun continueAfterCalibration() {
        if (_uiState.value.calibrationPhase != CalibrationPhase.COMPLETE) return

        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.INTRO
        )
    }

    fun backToCalibration() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.CALIBRATION
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
        calibrationJob?.cancel()
        calibrationJob = null

        sessionSamples.clear()

        viewModelScope.launch {
            dataSource.disconnect()
        }

        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.HOME,
            connected = false,
            running = false,
            samples = emptyList(),
            currentActivation = 0f,
            elapsedSeconds = 0L,
            result = null,
            isSaving = false,
            setupConfirmed = false,
            calibrationPhase = CalibrationPhase.IDLE,
            calibrationProgress = 0f
        )
    }

    fun backToSensorSetup() {
        if (_uiState.value.running) return

        calibrationJob?.cancel()
        calibrationJob = null

        viewModelScope.launch {
            dataSource.disconnect()
        }

        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.SENSOR_SETUP,
            connected = false,
            calibrationPhase = CalibrationPhase.IDLE,
            calibrationProgress = 0f
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
        val orthosisProfile = _uiState.value.orthosisProfile
        val restBaseline = _uiState.value.calibrationRest
        val activeBaseline = _uiState.value.calibrationActive

        val baseResult = RehabSessionResult(
            durationSeconds = duration,
            sampleCount = samplesSnapshot.size,
            rms = EmgSignalProcessor.rms(samplesSnapshot),
            mav = EmgSignalProcessor.mav(samplesSnapshot),
            maxActivation = samplesSnapshot.maxOrNull() ?: 0f,
            orthosisProfile = orthosisProfile,
            calibrationRest = restBaseline,
            calibrationActive = activeBaseline
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
                    maxActivation = baseResult.maxActivation,
                    orthosisProfile = orthosisProfile,
                    calibrationRest = restBaseline,
                    calibrationActive = activeBaseline
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
        calibrationJob?.cancel()
        super.onCleared()
    }
}
