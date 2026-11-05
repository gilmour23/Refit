package com.refit.app.security

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.refit.app.BuildConfig
import com.refit.app.data.remote.SupabaseClientProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = SecurityTestApplication::class, sdk = [33])
class PhoneSecurityTest {
    @Test fun installableBuildDisablesDebugAndRemoteTestConfiguration() {
        assumeTrue(BuildConfig.BUILD_TYPE == "phone")
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(BuildConfig.DEBUG)
        assertTrue(BuildConfig.VISION_REQUIRE_TOKEN)
        assertEquals("com.refit.app.phone", BuildConfig.APPLICATION_ID)
        assertEquals("", BuildConfig.SUPABASE_URL)
        assertEquals("", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        assertFalse(SupabaseClientProvider.isConfigured)
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
    }
}
