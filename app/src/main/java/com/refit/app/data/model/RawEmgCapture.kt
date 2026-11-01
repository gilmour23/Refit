package com.refit.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EmgCaptureMetadata(val sampleRateHz: Double, val channel: String, val unit: String) {
    init {
        require(sampleRateHz.isFinite() && sampleRateHz > 0 && sampleRateHz <= 100000)
        require(channel.isNotBlank() && channel.length <= 80)
        require(unit.isNotBlank() && unit.length <= 40)
    }
}

@Serializable
data class RawEmgCapture(
    val sampleRateHz: Double,
    val channel: String,
    val unit: String,
    val samples: List<Float>,
    val capturedSamples: Int,
    val truncated: Boolean
) {
    fun isValid(): Boolean = runCatching { EmgCaptureMetadata(sampleRateHz, channel, unit) }.isSuccess &&
        samples.isNotEmpty() && samples.size <= 20000 && samples.all { it.isFinite() } &&
        capturedSamples >= samples.size && truncated == (capturedSamples > samples.size)
}
