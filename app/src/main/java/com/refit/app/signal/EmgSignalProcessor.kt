package com.refit.app.signal

import kotlin.math.abs
import kotlin.math.sqrt

object EmgSignalProcessor {

    fun rms(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f

        val meanSquare = samples
            .map { it * it }
            .average()

        return sqrt(meanSquare).toFloat()
    }

    fun mav(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f

        return samples
            .map { abs(it) }
            .average()
            .toFloat()
    }

    fun peakMagnitude(samples: List<Float>): Float {
        return samples.maxOfOrNull { abs(it) } ?: 0f
    }

    fun rollingRms(samples: List<Float>, windowSize: Int): Float {
        require(windowSize > 0) { "windowSize must be greater than 0" }
        if (samples.isEmpty()) return 0f

        return rms(samples.takeLast(windowSize))
    }
}
