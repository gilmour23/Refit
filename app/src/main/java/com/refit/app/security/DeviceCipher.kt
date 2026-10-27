package com.refit.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM keys stay inside Android Keystore. */
class DeviceCipher(private val alias: String, private val purpose: String) {
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        check(cipher.iv.size == 12)
        return "v1:" + Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(encrypted: String): String {
        require(encrypted.startsWith("v1:")) { "Unsupported encrypted storage format" }
        val bytes = Base64.getDecoder().decode(encrypted.removePrefix("v1:"))
        require(bytes.size >= 28) { "Invalid encrypted storage" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    private fun key(create: Boolean): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) {
            return@synchronized requireNotNull(store.getKey(alias, null) as? SecretKey) { "Device key is unavailable" }
                .also { observedAliases.add(alias) }
        }
        check(create && alias !in observedAliases) { "Device key is unavailable; existing data was retained" }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey().also { observedAliases.add(alias) }
    }

    private companion object { val keyLock = Any(); val observedAliases = mutableSetOf<String>() }
}
