package com.refit.app.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EncryptedSessionManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val legacy get() = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
    private val secure get() = context.getSharedPreferences("refit-secure-auth", Context.MODE_PRIVATE)
    private val oldKey = "sb-https:--test-supabase-co-session"
    private fun manager() = EncryptedSessionManager(context, "https://test.supabase.co")
    private fun session() = UserSession("private-access-token", "private-refresh-token", expiresIn = 3600,
        tokenType = "bearer", user = UserInfo(aud = "authenticated", id = "owner-a"))
    @Before fun clear() { legacy.edit().clear().commit(); secure.edit().clear().commit() }

    @Test fun migratesLegacySessionAndRemovesBothLegacyKeysAfterDurableSave() = runBlocking {
        val json = Json.encodeToString(UserSession.serializer(), session())
        legacy.edit().putString(oldKey, json).putString("session", json).commit()
        assertEquals("private-refresh-token", manager().loadSession().refreshToken)
        assertFalse(legacy.contains(oldKey)); assertFalse(legacy.contains("session"))
        assertFalse(secure.all.values.joinToString().contains("private-refresh-token"))
        assertEquals("owner-a", manager().loadSession().user!!.id)
    }

    @Test fun deletionPreventsLegacyRestorationAfterRecreation() = runBlocking {
        manager().saveSession(session())
        legacy.edit().putString(oldKey, Json.encodeToString(UserSession.serializer(), session())).commit()
        manager().deleteSession()
        assertNull(manager().loadSessionOrNull())
        assertFalse(legacy.contains(oldKey)); assertFalse(secure.contains("session"))
    }

    @Test fun damagedEncryptedSessionNeverFallsBackToLegacyAccount() = runBlocking {
        legacy.edit().putString(oldKey, Json.encodeToString(UserSession.serializer(), session())).commit()
        secure.edit().putString("session", "v1:corrupt").commit()
        assertNull(manager().loadSessionOrNull())
        assertEquals("v1:corrupt", secure.getString("session", null))
        assertTrue(legacy.contains(oldKey))
    }

    @Test fun committedEncryptedSessionRetriesInterruptedLegacyCleanup() = runBlocking {
        manager().saveSession(session())
        legacy.edit().putString(oldKey, Json.encodeToString(UserSession.serializer(), session())).commit()
        assertEquals("owner-a", manager().loadSession().user!!.id)
        assertFalse("A completed encrypted load must clear leftover token plaintext", legacy.contains(oldKey))
    }

    @Test fun durableLogoutMarkerPreventsStaleLegacyAccountRestoration() = runBlocking {
        legacy.edit().putString(oldKey, Json.encodeToString(UserSession.serializer(), session())).commit()
        secure.edit().remove("session").putBoolean("signed_out", true).commit()
        assertNull("An interrupted logout must not restore the legacy session", manager().loadSessionOrNull())
    }
}
