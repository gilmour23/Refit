package com.refit.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RehabSessionUpsertDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("client_local_id")
    val clientLocalId: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("exercise_type")
    val exerciseType: String,
    @SerialName("duration_seconds")
    val durationSeconds: Long,
    @SerialName("sample_count")
    val sampleCount: Int,
    val rms: Float,
    val mav: Float,
    @SerialName("peak_activation")
    val peakActivation: Float,
    @SerialName("updated_at")
    val updatedAt: String
)

@Serializable
data class RemoteSessionId(
    val id: String
)

