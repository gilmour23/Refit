package com.refit.app.data.source

sealed interface EmgConnectionState {
    data object Disconnected : EmgConnectionState
    data object Scanning : EmgConnectionState

    data class Connecting(
        val deviceName: String?
    ) : EmgConnectionState

    data class Connected(
        val deviceName: String?
    ) : EmgConnectionState

    data class Error(
        val message: String
    ) : EmgConnectionState
}
