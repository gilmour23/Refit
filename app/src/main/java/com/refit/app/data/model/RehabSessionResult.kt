package com.refit.app.data.model

data class RehabSessionResult(
    val durationSeconds: Long,
    val sampleCount: Int,
    val rms: Float,
    val mav: Float,
    val peakActivation: Float,
    val previousRms: Float? = null,
    val savedSessionId: Long? = null,
    val completedAttempts: Int? = null,
    val plannedAttempts: Int? = null,
    val attemptSeconds: Int? = null,
    val restSeconds: Int? = null,
    val endedEarly: Boolean? = null,
    val dataMode: String = "unknown",
    val comfort: String? = null,
    val wristSide: String? = null,
    val pain: String? = null,
    val fatigue: String? = null
)
