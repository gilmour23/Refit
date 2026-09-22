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
}
