package com.refit.app.data.source

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleSensorScanner(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val catalog = BleDeviceCatalog()
    private val _devices = MutableStateFlow<List<BleSensorDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var activeCallback: ScanCallback? = null
    private val finish = Runnable { stop() }

    fun start() {
        stop()
        _error.value = null
        catalog.clear(); _devices.value = emptyList()
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissions.any { app.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            _error.value = "기기를 검색하려면 블루투스 권한을 허용해 주세요."; return
        }
        try {
            val adapter = manager?.adapter
            if (adapter == null) { _error.value = "이 기기는 블루투스를 지원하지 않아요."; return }
            if (!adapter.isEnabled) { _error.value = "블루투스를 켠 뒤 다시 검색해 주세요."; return }
            val scanner = adapter.bluetoothLeScanner ?: error("Scanner unavailable")
            val callback = object : ScanCallback() {
                override fun onScanResult(type: Int, result: ScanResult) {
                    if (activeCallback !== this || !_scanning.value) return
                    val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
                    catalog.record(name, result.device.address, result.rssi)
                    _devices.value = catalog.snapshot()
                }
                override fun onScanFailed(code: Int) {
                    if (activeCallback !== this) return
                    stop(); _error.value = "기기를 검색하지 못했어요 ($code). 잠시 뒤 다시 검색해 주세요."
                }
            }
            activeCallback = callback; _scanning.value = true
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            handler.postDelayed(finish, 15_000)
        } catch (_: SecurityException) { stop(); _error.value = "블루투스 권한을 다시 허용해 주세요." }
        catch (_: Exception) { stop(); _error.value = "기기를 검색하지 못했어요. 블루투스 상태를 확인해 주세요." }
    }

    fun stop() {
        handler.removeCallbacks(finish)
        val previous = activeCallback
        activeCallback = null; _scanning.value = false
        if (previous != null) runCatching { manager?.adapter?.bluetoothLeScanner?.stopScan(previous) }
    }
}
