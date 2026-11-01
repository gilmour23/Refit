package com.refit.app.data.source

import java.util.UUID

data class BleEmgConfig(
    val serviceUuid: UUID,
    val notifyCharacteristicUuid: UUID,
    val deviceNamePrefix: String? = null,
    val scanTimeoutMillis: Long = 10_000L,
    val captureMetadata: com.refit.app.data.model.EmgCaptureMetadata? = null,
    val sourceDevice: String? = null
) {
    init {
        require(scanTimeoutMillis > 0L) {
            "scanTimeoutMillis must be greater than 0"
        }
    }
}


