package com.refit.app.data.source

import android.bluetooth.*
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class BleLateCallbackTest {
    @Test fun binderCallbackCannotPublishConnectedUntilMainAndCannotResurrectClosedGatt() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = BleEmgDataSource(context, BleEmgConfig(UUID.randomUUID(), UUID.randomUUID()), EmgPacketParser { _, _ -> emptyList() })
        val callback = BleEmgDataSource::class.java.getDeclaredField("gattCallback").apply { isAccessible = true }.get(source) as BluetoothGattCallback
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55")
        val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {})
        assertNotNull(gatt)
        BleEmgDataSource::class.java.getDeclaredField("gatt").apply { isAccessible = true }.set(source, gatt)
        val descriptor = BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_WRITE)
        try {
            val thread = Thread { callback.onDescriptorWrite(gatt, descriptor, BluetoothGatt.GATT_SUCCESS) }
            thread.start(); thread.join(2000)
            assertFalse("Binder callback must wait for Main", source.connectionState.value is EmgConnectionState.Connected)
            source.disconnect()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(EmgConnectionState.Disconnected, source.connectionState.value)
        } finally { source.disconnect(); Dispatchers.resetMain() }
    }
}
