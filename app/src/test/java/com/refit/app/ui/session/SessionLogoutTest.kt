package com.refit.app.ui.session

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.refit.app.data.model.EmgSample
import com.refit.app.data.source.EmgConnectionState
import com.refit.app.data.source.EmgDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class SessionLogoutTest {
    @Test fun clearingAccountScopeDisconnectsAcquisitionAndRemovesResultState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val source = TrackingSource()
        val store = ViewModelStore()
        try {
            val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>(), "owner-a", source)
            store.put("session", vm)
            vm.startSession()
            assertTrue(source.connected)
            store.clear()
            assertFalse(source.connected)
            assertFalse(vm.uiState.value.running)
            assertNull(vm.uiState.value.result)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    private class TrackingSource : EmgDataSource {
        override val connectionState = MutableStateFlow<EmgConnectionState>(EmgConnectionState.Disconnected)
        var connected = false
        override suspend fun connect() { connected = true }
        override suspend fun disconnect() { connected = false }
        override fun observeEmg() = emptyFlow<EmgSample>()
    }
}
