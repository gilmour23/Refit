package com.refit.app.data.source

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import com.refit.app.data.model.EmgSample
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BleEmgDataSource(
    context: Context,
    private val config: BleEmgConfig,
    private val packetParser: EmgPacketParser
) : EmgDataSource {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(BluetoothManager::class.java)

    private val _connectionState =
        MutableStateFlow<EmgConnectionState>(EmgConnectionState.Disconnected)
    override val connectionState: StateFlow<EmgConnectionState> =
        _connectionState.asStateFlow()

    private val samples = MutableSharedFlow<EmgSample>(
        extraBufferCapacity = 1024
    )

    private var gatt: BluetoothGatt? = null
    private var connectContinuation: CancellableContinuation<Unit>? = null
    private var scanning = false
    private var selectedDeviceName: String? = null

    private val scanner
        get() = bluetoothManager.adapter?.bluetoothLeScanner

    override suspend fun connect() {
        if (_connectionState.value is EmgConnectionState.Connected) return

        requireBlePermissions()

        val adapter = bluetoothManager.adapter
            ?: throw IllegalStateException("Bluetooth adapter is not available.")

        if (!adapter.isEnabled) {
            throw IllegalStateException("Bluetooth is turned off.")
        }

        withTimeout(config.scanTimeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                connectContinuation = continuation
                continuation.invokeOnCancellation {
                    stopScan()
                    closeGatt()
                }
                startScan()
            }
        }
    }

    override suspend fun disconnect() {
        stopScan()
        closeGatt()
        connectContinuation = null
        selectedDeviceName = null
        _connectionState.value = EmgConnectionState.Disconnected
    }

    override fun observeEmg(): Flow<EmgSample> =
        samples.asSharedFlow()

    private fun startScan() {
        try {
            val bleScanner = scanner
                ?: return failConnection("BLE scanner is not available.")

            _connectionState.value = EmgConnectionState.Scanning

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(config.serviceUuid))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanning = true
            bleScanner.startScan(
                listOf(filter),
                settings,
                scanCallback
            )
        } catch (_: SecurityException) {
            failConnection("Bluetooth scan permission was revoked.")
        }
    }

    private fun stopScan() {
        if (!scanning) return

        runCatching {
            if (hasScanPermission()) {
                scanner?.stopScan(scanCallback)
            }
        }
        scanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            try {
                val device = result.device
                val advertisedName = result.scanRecord?.deviceName
                val name = advertisedName ?: runCatching { device.name }.getOrNull()

                val prefix = config.deviceNamePrefix
                if (prefix != null && name?.startsWith(prefix) != true) {
                    return
                }

                stopScan()
                selectedDeviceName = name
                _connectionState.value =
                    EmgConnectionState.Connecting(name)

                gatt = device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } catch (_: SecurityException) {
                failConnection("Bluetooth connection permission was revoked.")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            failConnection("BLE scan failed with code ${errorCode}.")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("BLE connection failed with status ${status}.")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try {
                        if (!gatt.discoverServices()) {
                            failConnection("Could not start GATT service discovery.")
                        }
                    } catch (_: SecurityException) {
                        failConnection("Bluetooth connection permission was revoked.")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = EmgConnectionState.Disconnected
                    if (connectContinuation?.isActive == true) {
                        failConnection("BLE device disconnected during setup.")
                    }
                }
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("GATT service discovery failed with status ${status}.")
                return
            }

            val service = gatt.getService(config.serviceUuid)
                ?: return failConnection("Configured EMG service was not found.")

            val characteristic =
                service.getCharacteristic(config.notifyCharacteristicUuid)
                    ?: return failConnection(
                        "Configured EMG characteristic was not found."
                    )

            enableNotifications(gatt, characteristic)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handlePayload(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handlePayload(value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("Could not enable EMG notifications.")
                return
            }

            _connectionState.value =
                EmgConnectionState.Connected(selectedDeviceName)

            connectContinuation?.let { continuation ->
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            connectContinuation = null
        }
    }

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        try {
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                failConnection("Could not enable characteristic notifications.")
                return
            }

            val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID)
                ?: return failConnection(
                    "EMG characteristic has no client configuration descriptor."
                )

            val supportsIndicate =
                characteristic.properties and
                    BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

            val value = if (supportsIndicate) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt.writeDescriptor(descriptor, value)
                if (result != BluetoothStatusCodes.SUCCESS) {
                    failConnection("Could not write notification descriptor.")
                }
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value

                @Suppress("DEPRECATION")
                if (!gatt.writeDescriptor(descriptor)) {
                    failConnection("Could not write notification descriptor.")
                }
            }
        } catch (_: SecurityException) {
            failConnection("Bluetooth connection permission was revoked.")
        }
    }

    private fun handlePayload(payload: ByteArray) {
        val parsed = runCatching {
            packetParser.parse(
                receivedAtMillis = System.currentTimeMillis(),
                payload = payload
            )
        }.getOrElse { error ->
            _connectionState.value = EmgConnectionState.Error(
                error.message ?: "Malformed EMG packet."
            )
            return
        }

        parsed.forEach(samples::tryEmit)
    }

    private fun failConnection(message: String) {
        stopScan()
        _connectionState.value = EmgConnectionState.Error(message)

        connectContinuation?.let { continuation ->
            if (continuation.isActive) {
                continuation.resumeWithException(
                    IllegalStateException(message)
                )
            }
        }
        connectContinuation = null
        closeGatt()
    }

    private fun closeGatt() {
        gatt?.let { current ->
            runCatching {
                if (hasConnectPermission()) {
                    current.disconnect()
                }
            }
            runCatching { current.close() }
        }
        gatt = null
    }

    private fun requireBlePermissions() {
        if (!hasScanPermission() || !hasConnectPermission()) {
            throw SecurityException(
                "Bluetooth permissions are required before connecting the EMG sensor."
            )
        }
    }

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.checkSelfPermission(
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            appContext.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            appContext.checkSelfPermission(
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }

    companion object {
        private val CLIENT_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

