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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

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
    val calibrationActive: Float? = null,
    val errorMessage: String? = null
)

class SessionViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val exerciseName = "손목 폄 운동"

    private val dataSource: EmgDataSource = MockEmgDataSource()

    private val repository = RehabSessionRepository(
        ReFitDatabase.getInstance(application).rehabSessionDao()
    )

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var emgJob: Job? = null
    private var timerJob: Job? = null
    private var calibrationJob: Job? = null

    private var aggregateCount = 0
    private var aggregateSumSquares = 0.0
    private var aggregateSumAbs = 0.0
    private var aggregateMax = 0f

    init {
        viewModelScope.launch {
            repository.observeAll()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "재활 기록을 불러오지 못했습니다."
                    )
                }
                .collect { history ->
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
            calibrationRest = null,
            calibrationActive = null,
            result = null,
            errorMessage = null
        )
    }

    fun confirmSensorSetup() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.CALIBRATION,
            setupConfirmed = true,
            calibrationPhase = CalibrationPhase.IDLE,
            calibrationProgress = 0f,
            errorMessage = null
        )
    }

    fun startCalibration() {
        if (calibrationJob?.isActive == true || _uiState.value.running) return

        _uiState.value = _uiState.value.copy(errorMessage = null)

        calibrationJob = viewModelScope.launch {
            var collectorJob: Job? = null

            try {
                dataSource.connect()

                val collected = mutableListOf<Float>()

                collectorJob = launch {
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
                val restBaseline = collected
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

                _uiState.value = _uiState.value.copy(
                    calibrationPhase = CalibrationPhase.COMPLETE,
                    calibrationProgress = 1f,
                    calibrationRest = restBaseline,
                    calibrationActive = activeBaseline,
                    errorMessage = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    calibrationPhase = CalibrationPhase.IDLE,
                    calibrationProgress = 0f,
                    calibrationRest = null,
                    calibrationActive = null,
                    errorMessage = "기준 측정 중 오류가 발생했습니다. 다시 시도해주세요."
                )
            } finally {
                collectorJob?.cancel()
                runCatching { dataSource.disconnect() }
                _uiState.value = _uiState.value.copy(connected = false)
            }
        }
    }

    fun continueAfterCalibration() {
        if (_uiState.value.calibrationPhase != CalibrationPhase.COMPLETE) return

        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.INTRO,
            errorMessage = null
        )
    }

    fun backToCalibration() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.CALIBRATION,
            errorMessage = null
        )
    }

    fun openHistory() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.HISTORY,
            result = null,
            errorMessage = null
        )
    }

    fun backToHome() {
        if (_uiState.value.running) return

        calibrationJob?.cancel()
        calibrationJob = null
        resetSessionMetrics()

        viewModelScope.launch {
            runCatching { dataSource.disconnect() }
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
            calibrationProgress = 0f,
            calibrationRest = null,
            calibrationActive = null,
            errorMessage = null
        )
    }

    fun backToSensorSetup() {
        if (_uiState.value.running) return

        calibrationJob?.cancel()
        calibrationJob = null

        viewModelScope.launch {
            runCatching { dataSource.disconnect() }
        }

        _uiState.value = _uiState.value.copy(
            screen = ReFitScreen.SENSOR_SETUP,
            connected = false,
            calibrationPhase = CalibrationPhase.IDLE,
            calibrationProgress = 0f,
            calibrationRest = null,
            calibrationActive = null,
            errorMessage = null
        )
    }

    fun startSession() {
        if (_uiState.value.running || _uiState.value.isSaving) return

        resetSessionMetrics()
        _uiState.value = _uiState.value.copy(errorMessage = null)

        emgJob = viewModelScope.launch {
            try {
                dataSource.connect()

                _uiState.value = _uiState.value.copy(
                    screen = ReFitScreen.LIVE,
                    connected = true,
                    running = true,
                    samples = emptyList(),
                    currentActivation = 0f,
                    elapsedSeconds = 0L,
                    result = null,
                    isSaving = false,
                    errorMessage = null
                )

                timerJob = launch {
                    while (true) {
                        delay(1000)
                        if (_uiState.value.running) {
                            _uiState.value = _uiState.value.copy(
                                elapsedSeconds = _uiState.value.elapsedSeconds + 1
                            )
                        }
                    }
                }

                dataSource.observeEmg().collect { sample ->
                    recordSessionSample(sample.value)

                    val visibleSamples =
                        (_uiState.value.samples + sample.value).takeLast(200)

                    _uiState.value = _uiState.value.copy(
                        samples = visibleSamples,
                        currentActivation = sample.value
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                timerJob?.cancel()
                runCatching { dataSource.disconnect() }
                _uiState.value = _uiState.value.copy(
                    screen = ReFitScreen.INTRO,
                    connected = false,
                    running = false,
                    samples = emptyList(),
                    currentActivation = 0f,
                    errorMessage = "sEMG 신호를 시작하지 못했습니다. 다시 시도해주세요."
                )
            }
        }
    }

    fun stopSession() {
        if (!_uiState.value.running) return

        val duration = _uiState.value.elapsedSeconds
        val createdAt = System.currentTimeMillis()
        val orthosisProfile = _uiState.value.orthosisProfile
        val restBaseline = _uiState.value.calibrationRest
        val activeBaseline = _uiState.value.calibrationActive

        val countSnapshot = aggregateCount
        val rmsSnapshot =
            if (countSnapshot > 0) sqrt(aggregateSumSquares / countSnapshot).toFloat() else 0f
        val mavSnapshot =
            if (countSnapshot > 0) (aggregateSumAbs / countSnapshot).toFloat() else 0f
        val maxSnapshot = aggregateMax

        val baseResult = RehabSessionResult(
            durationSeconds = duration,
            sampleCount = countSnapshot,
            rms = rmsSnapshot,
            mav = mavSnapshot,
            maxActivation = maxSnapshot,
            orthosisProfile = orthosisProfile,
            calibrationRest = restBaseline,
            calibrationActive = activeBaseline
        )

        emgJob?.cancel()
        timerJob?.cancel()

        _uiState.value = _uiState.value.copy(
            connected = false,
            running = false,
            isSaving = true,
            errorMessage = null
        )

        viewModelScope.launch {
            runCatching { dataSource.disconnect() }

            var previousRms: Float? = null

            try {
                previousRms = repository
                    .getLatestForExercise(exerciseName)
                    ?.rms

                val sessionId = repository.insert(
                    RehabSessionEntity(
                        createdAt = createdAt,
                        exerciseName = exerciseName,
                        durationSeconds = duration,
                        sampleCount = countSnapshot,
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
                        previousRms = previousRms,
                        savedSessionId = sessionId,
                        saveSucceeded = true,
                        saveErrorMessage = null
                    ),
                    isSaving = false
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    screen = ReFitScreen.RESULT,
                    result = baseResult.copy(
                        previousRms = previousRms,
                        savedSessionId = null,
                        saveSucceeded = false,
                        saveErrorMessage = "기기 내 기록 저장에 실패했습니다."
                    ),
                    isSaving = false,
                    errorMessage = "기기 내 기록 저장에 실패했습니다."
                )
            }
        }
    }

    fun resetSession() {
        backToHome()
    }

    private fun resetSessionMetrics() {
        aggregateCount = 0
        aggregateSumSquares = 0.0
        aggregateSumAbs = 0.0
        aggregateMax = 0f
    }

    private fun recordSessionSample(value: Float) {
        aggregateCount += 1
        aggregateSumSquares += value.toDouble() * value.toDouble()
        aggregateSumAbs += abs(value.toDouble())
        if (value > aggregateMax) {
            aggregateMax = value
        }
    }

    override fun onCleared() {
        emgJob?.cancel()
        timerJob?.cancel()
        calibrationJob?.cancel()
        super.onCleared()
    }
}
