package com.refit.app.signal

class RealtimeEmgWindow(
    private val windowSize: Int
) {
    private val values = ArrayDeque<Float>()

    init {
        require(windowSize > 0) { "windowSize must be greater than 0" }
    }

    fun add(value: Float): Float {
        values.addLast(value)
        while (values.size > windowSize) {
            values.removeFirst()
        }
        return EmgSignalProcessor.rms(values.toList())
    }

    fun clear() {
        values.clear()
    }
}
