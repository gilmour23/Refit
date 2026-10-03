package com.refit.app.data.model

data class RehabSessionResult(
    val durationSeconds: Long,
    val sampleCount: Int,
    val rms: Float,
    val mav: Float,
    val maxActivation: Float,
    val previousRms: Float? = null,
    val savedSessionId: Long? = null,
    val orthosisProfile: String = "맞춤 손목 보조기",
    val calibrationRest: Float? = null,
    val calibrationActive: Float? = null,
    val saveSucceeded: Boolean = true,
    val saveErrorMessage: String? = null,
    val signalSource: String = "MOCK"
)
