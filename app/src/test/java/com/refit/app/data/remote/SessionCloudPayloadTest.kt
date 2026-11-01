package com.refit.app.data.remote

import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.RawEmgCapture
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SessionCloudPayloadTest {
    private fun record() = RehabSessionEntity(userId = "owner", createdAt = 1700000000000,
        exerciseName = "손목 폄 시도", durationSeconds = 20, sampleCount = 4,
        rms = 1f, mav = 1f, peakActivation = 2f)

    @Test fun uploadsGuidanceAndOriginalResponsesWithoutInventingNumericScores() {
        val dto = record().copy(dataMode = "demo", completedAttempts = 2, plannedAttempts = 3,
            attemptSeconds = 4, restSeconds = 6, endedEarly = true, wristSide = "left",
            pain = "많이 아파요", fatigue = "조금 힘듦", comfort = "불편했어요").toUpsertDto()
        val body = Json.encodeToJsonElement(dto).jsonObject
        assertEquals(2, body["completed_attempts"]!!.jsonPrimitive.int)
        assertEquals("demo", body["data_mode"]!!.jsonPrimitive.content)
        assertEquals("많이 아파요", body["pain_response"]!!.jsonPrimitive.content)
        assertEquals("조금 힘듦", body["fatigue_response"]!!.jsonPrimitive.content)
        assertEquals("불편했어요", body["comfort_response"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("pain"))
        assertFalse(body.containsKey("fatigue"))
    }

    @Test fun oldUnknownRecordsNeverGainRawSignalOrAssumedDevice() {
        val row = record()
        assertEquals("unknown", row.toUpsertDto().dataMode)
        assertNull(row.toSignalDto("session-id"))
        assertNull(row.toUpsertDto().sourceDevice)
    }

    @Test fun rawIsOnlySentForKnownCapturedSources() {
        val raw = RawEmgCapture(50.0, "emg", "arbitrary", listOf(0.5f, 1f), 3, true)
        val row = record().copy(rawEmg = raw)
        assertNull(row.toSignalDto("session-id"))
        val dto = row.copy(dataMode = "demo").toSignalDto("session-id")!!
        assertEquals("session-id", dto.sessionId)
        assertEquals(listOf(0.5f, 1f), dto.samples)
        assertEquals(3, dto.capturedSamples)
        assertTrue(dto.truncated)
    }
}
