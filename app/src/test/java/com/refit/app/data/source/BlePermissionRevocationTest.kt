package com.refit.app.data.source

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], shadows = [RevokedConnectDevice::class])
class BlePermissionRevocationTest {
    @Test
    fun permissionRevokedBeforeGattConnectBecomesRecoverableError() {
        val source = BleEmgDataSource(
            ApplicationProvider.getApplicationContext(),
            BleEmgConfig(UUID.randomUUID(), UUID.randomUUID()),
            EmgPacketParser { _, _ -> emptyList() }
        )
        val field = BleEmgDataSource::class.java.getDeclaredField("scanCallback")
        field.isAccessible = true
        val callback = field.get(source) as ScanCallback
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55")

        BleEmgDataSource::class.java.getDeclaredField("scanning").apply { isAccessible = true }.setBoolean(source, true)
        callback.onScanResult(0, ScanResult(device, null, -40, 1L))

        assertTrue(source.connectionState.value is EmgConnectionState.Error)
    }
}

@Implements(BluetoothDevice::class)
class RevokedConnectDevice {
    @Implementation
    fun connectGatt(
        context: Context,
        autoConnect: Boolean,
        callback: BluetoothGattCallback,
        transport: Int
    ): BluetoothGatt? = throw SecurityException("Connect permission revoked")
}

