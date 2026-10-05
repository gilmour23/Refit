package com.refit.app.data.source

import com.refit.app.data.model.EmgSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class MockEmgDataSource : EmgDataSource {

    private var connected = false

    private val _connectionState =
        MutableStateFlow<EmgConnectionState>(EmgConnectionState.Disconnected)
    override val connectionState: StateFlow<EmgConnectionState> =
        _connectionState.asStateFlow()

    override suspend fun connect() {
        _connectionState.value = EmgConnectionState.Connecting("Mock sEMG")
        connected = true
        _connectionState.value = EmgConnectionState.Connected("Mock sEMG")
    }

    override suspend fun disconnect() {
        connected = false
        _connectionState.value = EmgConnectionState.Disconnected
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

