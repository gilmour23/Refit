package com.refit.app.data.source

import com.refit.app.data.model.EmgSample
import kotlinx.coroutines.flow.Flow

interface EmgDataSource {
    suspend fun connect()
    suspend fun disconnect()
    fun observeEmg(): Flow<EmgSample>
}
