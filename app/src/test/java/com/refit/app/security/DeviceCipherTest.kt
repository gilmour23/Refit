package com.refit.app.security

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import java.security.KeyStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviceCipherTest {
    @Test fun ciphertextSurvivesRecreationAndUsesFreshNonce() {
        ApplicationProvider.getApplicationContext<Application>()
        val c = DeviceCipher("cipher-test", "records")
        val first = c.encrypt("private clinical content")
        val second = c.encrypt("private clinical content")
        assertNotEquals(first, second)
        assertFalse(first.contains("clinical"))
        assertEquals("private clinical content", DeviceCipher("cipher-test", "records").decrypt(first))
        assertThrows(Exception::class.java) { DeviceCipher("cipher-test", "sessions").decrypt(first) }
        assertThrows(Exception::class.java) { c.decrypt(first.dropLast(4) + "AAAA") }
    }

    @Test fun missingKeyDoesNotReplaceExistingDataOrGenerateNewKey() {
        ApplicationProvider.getApplicationContext<Application>()
        val c = DeviceCipher("lost-key-test", "records")
        val value = c.encrypt("keep this ciphertext")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.deleteEntry("lost-key-test")
        assertThrows(Exception::class.java) { c.decrypt(value) }
        assertFalse(store.containsAlias("lost-key-test"))
        assertThrows(Exception::class.java) { DeviceCipher("lost-key-test", "records").encrypt("new data must not replace the key") }
        assertFalse(store.containsAlias("lost-key-test"))
    }
}
