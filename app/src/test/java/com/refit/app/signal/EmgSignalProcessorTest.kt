package com.refit.app.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.sqrt

class EmgSignalProcessorTest {

    @Test
    fun emptyInputReturnsZeroMetrics() {
        assertEquals(0f, EmgSignalProcessor.rms(emptyList()), 0f)
        assertEquals(0f, EmgSignalProcessor.mav(emptyList()), 0f)
        assertEquals(0f, EmgSignalProcessor.peakMagnitude(emptyList()), 0f)
        assertEquals(0f, EmgSignalProcessor.rollingRms(emptyList(), 10), 0f)
    }

    @Test
    fun peakMagnitudeUsesAbsoluteAmplitudeForBipolarSignal() {
        assertEquals(
            4f,
            EmgSignalProcessor.peakMagnitude(listOf(-4f, 3f)),
            0f
        )
    }

    @Test
    fun rollingRmsUsesOnlyMostRecentWindow() {
        val expected = sqrt((9f + 16f) / 2f)
        assertEquals(
            expected,
            EmgSignalProcessor.rollingRms(listOf(100f, 3f, 4f), windowSize = 2),
            0.0001f
        )
    }

    @Test
    fun rollingRmsRejectsNonPositiveWindow() {
        assertThrows(IllegalArgumentException::class.java) {
            EmgSignalProcessor.rollingRms(listOf(1f), windowSize = 0)
        }
    }
}

