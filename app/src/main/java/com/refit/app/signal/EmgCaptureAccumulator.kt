package com.refit.app.signal

import com.refit.app.data.model.EmgCaptureMetadata
import com.refit.app.data.model.RawEmgCapture
import kotlin.math.abs
import kotlin.math.sqrt

/** Retains a bounded raw prefix; summary metrics include all accepted finite samples. */
class EmgCaptureAccumulator {
    private val retained = ArrayList<Float>()
    var sampleCount: Int = 0
        private set
    private var sumSquares = 0.0
    private var sumAbsolute = 0.0
    var peak: Float = 0f
        private set
    val rms: Float get() = if (sampleCount == 0) 0f else sqrt(sumSquares / sampleCount).toFloat()
    val mav: Float get() = if (sampleCount == 0) 0f else (sumAbsolute / sampleCount).toFloat()

    fun add(value: Float) {
        if (!value.isFinite() || sampleCount == Int.MAX_VALUE) return
        sampleCount++
        val absolute = abs(value)
        sumSquares += value.toDouble() * value
        sumAbsolute += absolute
        peak = maxOf(peak, absolute)
        if (retained.size < 20000) retained.add(value)
    }

    fun raw(metadata: EmgCaptureMetadata?): RawEmgCapture? =
        metadata?.takeIf { retained.isNotEmpty() }?.let {
            RawEmgCapture(it.sampleRateHz, it.channel, it.unit, retained.toList(),
                sampleCount, sampleCount > retained.size)
        }

    fun clear() {
        retained.clear()
        sampleCount = 0
        sumSquares = 0.0
        sumAbsolute = 0.0
        peak = 0f
    }
}
