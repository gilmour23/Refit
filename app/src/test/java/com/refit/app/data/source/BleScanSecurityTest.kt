package com.refit.app.data.source

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], shadows = [RevokedScanDevice::class])
class BleScanSecurityTest {
    @Test fun permissionRevokedDuringDeviceIdentityReadStopsScanWithoutPublishingADevice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        val scanner = BleSensorScanner(app)
        scanner.start()
        val callback = BleSensorScanner::class.java.getDeclaredField("activeCallback").apply { isAccessible = true }.get(scanner) as ScanCallback
        val device = adapter.getRemoteDevice("00:11:22:33:44:55")
        callback.onScanResult(0, ScanResult(device, null, -40, 1L))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(scanner.scanning.value)
        assertTrue(scanner.devices.value.isEmpty())
        assertNotNull(scanner.error.value)
        scanner.stop()
    }
}

@Implements(BluetoothDevice::class)
class RevokedScanDevice {
    @Implementation fun getAddress(): String = throw SecurityException("Permission revoked")
}
