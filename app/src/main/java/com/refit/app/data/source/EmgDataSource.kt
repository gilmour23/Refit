package com.refit.app.data.source

import com.refit.app.data.model.EmgSample
import kotlinx.coroutines.flow.Flow

enum class EmgSourceMode(
    val displayName: String,
    val isMock: Boolean
) {
    MOCK(
        displayName = "예시 데이터",
        isMock = true
    ),
    BLE(
        displayName = "BLE 센서",
        isMock = false
    )
}

interface EmgDataSource {
    val mode: EmgSourceMode

    suspend fun connect()
    suspend fun disconnect()
    fun observeEmg(): Flow<EmgSample>
}
