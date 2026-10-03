package com.refit.app.data.model

data class RehabSessionResult(
    val durationSeconds: Long,
    val sampleCount: Int,
    val rms: Float,
    val mav: Float,
    val peakActivation: Float,
    val previousRms: Float? = null,
    val savedSessionId: Long? = null
)
