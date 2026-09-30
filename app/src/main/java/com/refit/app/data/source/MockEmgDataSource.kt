package com.refit.app.data.source

import com.refit.app.data.model.EmgSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.sin

enum class MockEmgProfile {
    REST,
    LIGHT_CONTRACTION,
    EXERCISE
}

class MockEmgDataSource : EmgDataSource {

    override val mode: EmgSourceMode = EmgSourceMode.MOCK

    private var connected = false
    private var profile: MockEmgProfile = MockEmgProfile.REST

    override suspend fun connect() {
        connected = true
    }

    override suspend fun disconnect() {
        connected = false
    }

    fun setProfile(profile: MockEmgProfile) {
        this.profile = profile
    }

    override fun observeEmg(): Flow<EmgSample> = flow {
        var t = 0.0

        while (true) {
            if (!connected) {
                delay(50)
                continue
            }

            val value = when (profile) {
                MockEmgProfile.REST -> restSignal(t)
                MockEmgProfile.LIGHT_CONTRACTION -> lightContractionSignal(t)
                MockEmgProfile.EXERCISE -> exerciseSignal(t)
            }

            emit(
                EmgSample(
                    timestamp = System.currentTimeMillis(),
                    value = value
                )
            )

            t += 0.02
            delay(20)
        }
    }

    private fun restSignal(t: Double): Float {
        val baseline = 0.025f
        val ripple = abs(sin(t * 17.0)).toFloat() * 0.025f
        val drift = ((sin(t * 1.4) + 1.0) * 0.004).toFloat()
        return (baseline + ripple + drift).coerceIn(0f, 0.08f)
    }

    private fun lightContractionSignal(t: Double): Float {
        val envelope = 0.20f + ((sin(t * 1.2) + 1.0) * 0.035).toFloat()
        val ripple = abs(sin(t * 19.0)).toFloat() * envelope
        val baseline = 0.055f
        return (baseline + ripple).coerceIn(0f, 0.36f)
    }

    private fun exerciseSignal(t: Double): Float {
        val repetitionEnvelope = ((sin(t * 1.15) + 1.0) / 2.0).toFloat()
        val envelope = 0.08f + 0.46f * repetitionEnvelope
        val ripple = abs(sin(t * 21.0)).toFloat() * envelope
        val baseline = 0.035f
        return (baseline + ripple).coerceIn(0f, 0.62f)
    }
}
