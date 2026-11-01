package com.refit.app.data.remote

import com.refit.app.data.local.RehabSessionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class RehabSessionUpsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("client_local_id") val clientLocalId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("exercise_type") val exerciseType: String,
    @SerialName("duration_seconds") val durationSeconds: Long,
    @SerialName("sample_count") val sampleCount: Int,
    val rms: Float,
    val mav: Float,
    @SerialName("peak_activation") val peakActivation: Float,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("planned_attempts") val plannedAttempts: Int? = null,
    @SerialName("completed_attempts") val completedAttempts: Int? = null,
    @SerialName("attempt_seconds") val attemptSeconds: Int? = null,
    @SerialName("rest_seconds") val restSeconds: Int? = null,
    @SerialName("ended_early") val endedEarly: Boolean? = null,
    @SerialName("comfort_response") val comfortResponse: String? = null,
    @SerialName("pain_response") val painResponse: String? = null,
    @SerialName("fatigue_response") val fatigueResponse: String? = null,
    @SerialName("wrist_side") val wristSide: String? = null,
    @SerialName("data_mode") val dataMode: String = "unknown",
    @SerialName("source_device") val sourceDevice: String? = null,
    @SerialName("source_app_version") val sourceAppVersion: String? = null
)

@Serializable data class RemoteSessionId(val id: String)

@Serializable
data class RehabSignalUpsertDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("sample_rate_hz") val sampleRateHz: Double,
    val channel: String,
    val unit: String,
    val samples: List<Float>,
    @SerialName("captured_samples") val capturedSamples: Int,
    val truncated: Boolean
)

private fun RehabSessionEntity.cloudDataMode(): String = when (dataMode) {
    "demo" -> "demo"
    "sensor", "device" -> "device"
    else -> "unknown"
}

fun RehabSessionEntity.toUpsertDto() = RehabSessionUpsertDto(
    userId, clientLocalId, Instant.ofEpochMilli(createdAt).toString(), exerciseName,
    durationSeconds, sampleCount, rms, mav, peakActivation, Instant.ofEpochMilli(updatedAt).toString(),
    plannedAttempts, completedAttempts, attemptSeconds, restSeconds, endedEarly,
    comfort, pain, fatigue, wristSide, cloudDataMode(), sourceDevice, sourceAppVersion
)

fun RehabSessionEntity.toSignalDto(remoteId: String): RehabSignalUpsertDto? =
    rawEmg?.takeIf { cloudDataMode() in setOf("device", "demo") && it.isValid() }?.let {
        RehabSignalUpsertDto(remoteId, it.sampleRateHz, it.channel, it.unit,
            it.samples, it.capturedSamples, it.truncated)
    }
