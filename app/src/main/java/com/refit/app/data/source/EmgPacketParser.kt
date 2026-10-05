package com.refit.app.data.source

import com.refit.app.data.model.EmgSample

fun interface EmgPacketParser {
    fun parse(
        receivedAtMillis: Long,
        payload: ByteArray
    ): List<EmgSample>
}

class Int16LittleEndianEmgPacketParser(
    private val scale: Float = 1f,
    private val sampleIntervalMillis: Long = 0L
) : EmgPacketParser {

    init {
        require(scale.isFinite()) { "scale must be finite" }
        require(sampleIntervalMillis >= 0L) {
            "sampleIntervalMillis must be non-negative"
        }
    }

    override fun parse(
        receivedAtMillis: Long,
        payload: ByteArray
    ): List<EmgSample> {
        require(payload.size % 2 == 0) {
            "INT16_LE payload must contain an even number of bytes"
        }

        val sampleCount = payload.size / 2
        if (sampleCount == 0) return emptyList()

        val firstTimestamp =
            receivedAtMillis - (sampleCount - 1L) * sampleIntervalMillis

        return List(sampleCount) { index ->
            val low = payload[index * 2].toInt() and 0xFF
            val high = payload[index * 2 + 1].toInt()
            val raw = ((high shl 8) or low).toShort().toInt()

            EmgSample(
                timestamp = firstTimestamp + index * sampleIntervalMillis,
                value = raw * scale
            )
        }
    }
}

