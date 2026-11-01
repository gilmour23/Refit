package com.refit.app.data.source

data class BleSensorDevice(val name: String?, val address: String, val rssi: Int)

/** Only observations populate the catalog; no demo entries are injected into real scans. */
class BleDeviceCatalog {
    private val observations = linkedMapOf<String, BleSensorDevice>()
    @Synchronized fun record(name: String?, address: String, rssi: Int) {
        observations[address] = BleSensorDevice(name ?: observations[address]?.name, address, rssi)
    }
    @Synchronized fun snapshot(): List<BleSensorDevice> = observations.values.sortedByDescending { it.rssi }
    @Synchronized fun clear() = observations.clear()
}
