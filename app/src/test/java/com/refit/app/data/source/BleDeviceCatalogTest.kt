package com.refit.app.data.source

import org.junit.Assert.*
import org.junit.Test

class BleDeviceCatalogTest {
    @Test fun discoveryContainsOnlyObservedDevicesAndUpdatesRssiWithoutDuplicates() {
        val catalog = BleDeviceCatalog()
        assertTrue(catalog.snapshot().isEmpty())
        catalog.record("ReFit", "00:11:22:33:44:55", -70)
        catalog.record(null, "AA:BB:CC:DD:EE:FF", -50)
        catalog.record("ReFit", "00:11:22:33:44:55", -40)
        val list = catalog.snapshot()
        assertEquals(2, list.size)
        assertEquals("00:11:22:33:44:55", list.first().address)
        assertEquals(-40, list.first().rssi)
    }
}
