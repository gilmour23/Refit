package com.refit.app.ui.session

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.remote.RehabSessionRemoteDataSource
import com.refit.app.data.repository.RehabSessionRepository
import com.refit.app.data.repository.SessionSyncCoordinator
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
            val application = ApplicationProvider.getApplicationContext<Application>()
            val repository = RehabSessionRepository(ReFitDatabase.getInstance(application).rehabSessionDao())
            // This acquisition/logout test uses real Room and the coordinator in
            // offline mode; SDK networking and lifecycle are tested separately.
            val coordinator = SessionSyncCoordinator(repository, RehabSessionRemoteDataSource(null))
            val vm = SessionViewModel(application, "owner-a", source,
                repository = repository, syncCoordinator = coordinator)
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
