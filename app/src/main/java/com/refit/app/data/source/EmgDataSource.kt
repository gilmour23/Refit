package com.refit.app.data.source

import com.refit.app.data.model.EmgSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface EmgDataSource {
    val connectionState: StateFlow<EmgConnectionState>

    suspend fun connect()
    suspend fun disconnect()
    fun observeEmg(): Flow<EmgSample>
}

