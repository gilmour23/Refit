package com.refit.app.signal

import com.refit.app.data.model.EmgCaptureMetadata
import org.junit.Assert.*
import org.junit.Test

class EmgCaptureAccumulatorTest {
    @Test fun rawIsBoundedWhileSummaryIncludesEveryFiniteSample() {
        val capture = EmgCaptureAccumulator()
        repeat(20001) { capture.add(if (it == 20000) 100f else 1f) }
        capture.add(Float.NaN)
        val raw = capture.raw(EmgCaptureMetadata(50.0, "emg", "raw"))!!
        assertEquals(20000, raw.samples.size)
        assertEquals(20001, raw.capturedSamples)
        assertTrue(raw.truncated)
        assertEquals(20001, capture.sampleCount)
        assertEquals(100f, capture.peak, 0f)
        assertEquals(1.00495f, capture.mav, 0.00001f)
        assertNull(capture.raw(null))
    }

    @Test fun metadataMustMatchServerLimitsAndCannotInventInvalidRates() {
        assertTrue(runCatching { EmgCaptureMetadata(Double.NaN, "emg", "raw") }.isFailure)
        assertTrue(runCatching { EmgCaptureMetadata(0.0, "emg", "raw") }.isFailure)
        assertTrue(runCatching { EmgCaptureMetadata(50.0, "x".repeat(81), "raw") }.isFailure)
        assertTrue(runCatching { EmgCaptureMetadata(50.0, "emg", "x".repeat(41)) }.isFailure)
    }

    @Test fun clearingDropsPreviousOwnerCapture() {
        val capture = EmgCaptureAccumulator()
        capture.add(8f)
        capture.clear()
        assertEquals(0, capture.sampleCount)
        assertEquals(0f, capture.rms, 0f)
        assertNull(capture.raw(EmgCaptureMetadata(50.0, "emg", "raw")))
    }
}
