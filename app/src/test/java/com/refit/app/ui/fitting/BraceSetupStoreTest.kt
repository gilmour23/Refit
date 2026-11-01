package com.refit.app.ui.fitting

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
