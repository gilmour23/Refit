package com.refit.app.data.vision

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.refit.app.security.SecurityTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = SecurityTestApplication::class, sdk = [33])
class VisionSecurityTest {
    @Test fun destinationCredentialsAreEncryptedAndNotReusedForAnotherServer() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("refit_vision_credentials", 0).edit().clear().commit()
        val token = "Z".repeat(48)
        val store = VisionConnectionStore(app)
        store.save("https://measurement.example", token)
        assertEquals(token, store.read("https://measurement.example/"))
        assertNull(store.read("https://another.example"))
        assertTrue(app.getSharedPreferences("refit_vision_credentials", 0).all.values.none { it.toString().contains(token) })
    }

    @Test fun missingKeyOrInsecureDestinationIsRejectedBeforeAnyPhotoIsOpened() = runBlocking {
        var opened = false
        val photo = MeasurementPhoto("top.jpg", "image/jpeg") { opened = true; ByteArrayInputStream(byteArrayOf(1)) }
        try {
            BraceMeasurementClient(requireToken = true).measure("https://measurement.example", photo, photo)
            fail("Missing credential accepted")
        } catch (_: IOException) { }
        try {
            BraceMeasurementClient(requireToken = true).measure("http://measurement.example", photo, photo, "Z".repeat(48))
            fail("Cleartext upload accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            BraceMeasurementClient().measure("https://measurement.example", photo, photo, "token\r\nInjected: bad")
            fail("Header injection accepted")
        } catch (_: IllegalArgumentException) { }
        assertFalse(opened)
    }
}
