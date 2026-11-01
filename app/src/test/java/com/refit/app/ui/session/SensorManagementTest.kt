package com.refit.app.ui.session

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.remote.RehabSessionRemoteDataSource
import com.refit.app.data.repository.RehabSessionRepository
import com.refit.app.data.repository.SessionSyncCoordinator
import com.refit.app.data.source.EmgConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@OptIn(ExperimentalCoroutinesApi::class)
class SensorManagementTest {
    @Test fun failedRealConnectionDoesNotBecomeDemoSuccessAndExplicitDemoSwitchIsRetained() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, ReFitDatabase::class.java).build()
        val repo = RehabSessionRepository(db.rehabSessionDao()); val store = ViewModelStore()
        try {
            val vm = SessionViewModel(app, "sensor-owner", repository = repo,
                syncCoordinator = SessionSyncCoordinator(repo, RehabSessionRemoteDataSource(null)))
            store.put("sensor", vm)
            vm.connectSensor("00:11:22:33:44:55", "not-a-uuid", "not-a-uuid", true)
            assertFalse(vm.uiState.value.connected)
            assertNotNull(vm.uiState.value.sensorMessage)
            vm.connectSensor("00:11:22:33:44:55", "0000180d-0000-1000-8000-00805f9b34fb",
                "00002a37-0000-1000-8000-00805f9b34fb", true)
            runCurrent()
            assertEquals("sensor", vm.uiState.value.dataMode)
            assertFalse(vm.uiState.value.connected)
            assertFalse(vm.uiState.value.isConnecting)
            assertFalse(vm.uiState.value.sensorState is EmgConnectionState.Connected)
            assertTrue(vm.uiState.value.sensorDevices.isEmpty())
            vm.resetSession()
            assertEquals("sensor", vm.uiState.value.dataMode)
            vm.useDemoSensor(); runCurrent(); vm.resetSession()
            assertEquals("demo", vm.uiState.value.dataMode)
            assertFalse(vm.uiState.value.connected)
            assertNull(vm.uiState.value.sensorAddress)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }
}
