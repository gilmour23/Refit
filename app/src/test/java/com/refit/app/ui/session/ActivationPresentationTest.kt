package com.refit.app.ui.session

import org.junit.Assert.*
import org.junit.Test

class ActivationPresentationTest {
    private val active = SessionUiState(running = true, connected = true, dataMode = "demo",
        signalStatus = SignalStatus.DEMO, phase = GuidePhase.ATTEMPT,
        samples = listOf(.2f), currentActivation = .2f)
    @Test fun largerMockRmsFillsMoreOfTheBarWithoutExceedingItsBounds() {
        val low = activationPresentation(active.copy(currentActivation = .05f))
        val high = activationPresentation(active.copy(currentActivation = .3f))
        assertTrue(high.fraction > low.fraction)
        assertEquals(1f, activationPresentation(active.copy(currentActivation = 10f)).fraction, 0.0001f)
        assertFalse(low.message.contains("감지됐어요"))
        assertTrue(high.message.contains("감지됐어요"))
    }
    @Test fun pauseDisconnectAndReadyDiscardTheLastDisplayedLevel() {
        for (state in listOf(active.copy(paused = true), active.copy(connected = false),
            active.copy(signalStatus = SignalStatus.INTERRUPTED), active.copy(phase = GuidePhase.READY),
            active.copy(running = false))) {
            val result = activationPresentation(state)
            assertEquals(0f, result.fraction, 0.0001f)
            assertFalse(result.message.contains("감지됐어요"))
        }
    }
    @Test fun invalidAndMissingValuesDoNotProduceProgress() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f))
            assertEquals(0f, activationPresentation(active.copy(currentActivation = value)).fraction, 0.0001f)
        assertEquals(0f, activationPresentation(active.copy(samples = emptyList())).fraction, 0.0001f)
    }
    @Test fun sensorAndRestStatesCannotClaimAttemptDetection() {
        assertEquals(0f, activationPresentation(active.copy(dataMode = "sensor", signalStatus = SignalStatus.RECEIVING)).fraction, 0.0001f)
        assertFalse(activationPresentation(active.copy(phase = GuidePhase.REST)).message.contains("감지됐어요"))
    }
}
