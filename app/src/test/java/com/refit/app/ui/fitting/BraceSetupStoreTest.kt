package com.refit.app.ui.fitting

import android.app.Application
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BraceSetupStoreTest {
    @Test fun registrationPersistsInANewStoreAndDoesNotUnlockAnotherOwner() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences(BraceSetupStore.PREFS_NAME, 0).edit().clear().commit()
        val store = BraceSetupStore(app)
        assertNull(store.readDemoSide("owner-a"))
        store.registerDemo("owner-a", "right")
        assertEquals("right", BraceSetupStore(app).readDemoSide("owner-a"))
        assertNull(BraceSetupStore(app).readDemoSide("owner-b"))
        assertTrue(app.getSharedPreferences(BraceSetupStore.PREFS_NAME, 0).all.keys.none { it.contains("owner-a") })
    }

    @Test fun failedCommitCannotBecomeRegisteredThroughANewStore() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences(BraceSetupStore.PREFS_NAME, 0)
        prefs.edit().clear().commit()
        var failNext = true
        val failing = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val actual = prefs.edit()
                return object : SharedPreferences.Editor by actual {
                    override fun commit(): Boolean {
                        // Expose Android's memory-before-result behavior, with actual shared state.
                        val result = actual.commit()
                        if (failNext) { failNext = false; return false }
                        return result
                    }
                }
            }
        }
        try { BraceSetupStore(failing).registerDemo("owner-a", "left"); fail("Must report failure") }
        catch (_: IOException) { }
        assertNull(BraceSetupStore(app).readDemoSide("owner-a"))
        BraceSetupStore(app).registerDemo("owner-a", "right")
        failNext = true
        try { BraceSetupStore(failing).registerDemo("owner-a", "left"); fail("Must report failure") }
        catch (_: IOException) { }
        assertEquals("right", BraceSetupStore(app).readDemoSide("owner-a"))
    }
}
