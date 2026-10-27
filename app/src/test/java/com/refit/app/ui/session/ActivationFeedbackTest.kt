package com.refit.app.ui.session

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.refit.app.ui.components.ReFitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class ActivationFeedbackTest {
    @get:Rule val compose = createComposeRule()
    @Test fun liveMockSignalChangesFeedbackAndPauseClearsIt() {
        val state = mutableStateOf(SessionUiState(running = true, connected = true,
            signalStatus = SignalStatus.DEMO, phase = GuidePhase.ATTEMPT, samples = listOf(.01f), currentActivation = .01f))
        compose.setContent { ReFitTheme { SessionContent(state.value, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("데모 근활성").assertIsDisplayed()
        compose.onNodeWithText("힘을 주려는 신호가 감지됐어요").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(currentActivation = .3f) }
        compose.onNodeWithText("힘을 주려는 신호가 감지됐어요").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(paused = true) }
        compose.onNodeWithText("측정을 잠시 멈췄어요").assertIsDisplayed()
        compose.onNodeWithText("힘을 주려는 신호가 감지됐어요").assertDoesNotExist()
    }
    @Test fun sensorValuesNeverUseDemoDetectionOrInventACalibration() {
        compose.setContent { ReFitTheme { SessionContent(SessionUiState(running = true, connected = true,
            dataMode = "sensor", signalStatus = SignalStatus.RECEIVING, phase = GuidePhase.ATTEMPT,
            samples = listOf(100f), currentActivation = 100f), true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("표시 기준 설정이 필요해요").assertIsDisplayed()
        compose.onNodeWithText("힘을 주려는 신호가 감지됐어요").assertDoesNotExist()
    }
    @Test fun invalidOrMissingSamplesNeverShowDetection() {
        val state = mutableStateOf(SessionUiState(running = true, connected = true,
            signalStatus = SignalStatus.DEMO, phase = GuidePhase.ATTEMPT, currentActivation = .4f))
        compose.setContent { ReFitTheme { SessionContent(state.value, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("모의 신호를 기다리고 있어요").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(samples = listOf(.2f), currentActivation = Float.NaN) }
        compose.onNodeWithText("모의 신호를 기다리고 있어요").assertIsDisplayed()
        compose.onNodeWithText("힘을 주려는 신호가 감지됐어요").assertDoesNotExist()
    }
}
