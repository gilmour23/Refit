package com.refit.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.refit.app.ui.history.HistoryScreen
import com.refit.app.ui.home.HomeScreen
import com.refit.app.ui.result.ResultScreen

@Composable
fun SessionScreen(
    viewModel: SessionViewModel
) {
    val state by viewModel.uiState.collectAsState()

    when (state.screen) {
        ReFitScreen.HOME -> {
            HomeScreen(
                history = state.history,
                onStartRehab = viewModel::openSensorSetup,
                onOpenHistory = viewModel::openHistory
            )
        }

        ReFitScreen.SENSOR_SETUP -> {
            SensorSetupScreen(
                orthosisProfile = state.orthosisProfile,
                onBack = viewModel::backToHome,
                onContinue = viewModel::confirmSensorSetup
            )
        }

        ReFitScreen.CALIBRATION -> {
            CalibrationScreen(
                state = state,
                onBack = viewModel::backToSensorSetup,
                onStartCalibration = viewModel::startCalibration,
                onContinue = viewModel::continueAfterCalibration
            )
        }

        ReFitScreen.INTRO -> {
            ExerciseIntroScreen(
                errorMessage = state.errorMessage,
                onBack = viewModel::backToCalibration,
                onStart = viewModel::startSession
            )
        }

        ReFitScreen.LIVE -> {
            LiveSessionScreen(
                state = state,
                onStop = viewModel::stopSession
            )
        }

        ReFitScreen.RESULT -> {
            state.result?.let { result ->
                ResultScreen(
                    result = result,
                    onDone = viewModel::resetSession
                )
            }
        }

        ReFitScreen.HISTORY -> {
            HistoryScreen(
                sessions = state.history,
                onBack = viewModel::backToHome
            )
        }
    }
}
