package com.refit.app.data.source

import com.refit.app.data.model.EmgSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class MockEmgDataSource : EmgDataSource {

    private var connected = false

    override suspend fun connect() {
        connected = true
    }

    override suspend fun disconnect() {
        connected = false
    }

    override fun observeEmg(): Flow<EmgSample> = flow {
        var t = 0.0

        while (true) {
            if (!connected) {
                delay(50)
                continue
            }

            val envelope = 0.15f + 0.45f * ((sin(t) + 1.0) / 2.0).toFloat()
            val ripple = abs(sin(t * 13.0)).toFloat() * envelope
            val noise = (Random.nextFloat() - 0.5f) * 0.08f
            val value = (ripple + noise).coerceAtLeast(0f)

            emit(
                EmgSample(
                    timestamp = System.currentTimeMillis(),
                    value = value
                )
            )

            t += 0.08
            delay(20)
        }
    }
}
