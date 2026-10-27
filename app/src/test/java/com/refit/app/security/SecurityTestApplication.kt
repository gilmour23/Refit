package com.refit.app.security

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.*
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

/** JVM substitute for hardware key storage only; AES-GCM and app storage remain real. */
class SecurityTestApplication : Application() {
    override fun onCreate() {
        // Each Android SDK sandbox has its own KeyGenParameterSpec classloader.
        Security.removeProvider("AndroidKeyStore")
        Security.addProvider(TestKeyStoreProvider())
        super.onCreate()
    }
}
class TestKeyStoreProvider : Provider("AndroidKeyStore", 1.0, "JVM test key storage") {
    init {
        put("KeyStore.AndroidKeyStore", TestKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", TestKeyGeneratorSpi::class.java.name)
    }
}
class TestKeyStoreSpi : KeyStoreSpi() {
    override fun engineGetKey(alias: String, password: CharArray?): Key? {
        check(!failAccess) { "Simulated unavailable hardware key" }; return keys[alias]
    }
    override fun engineContainsAlias(alias: String) = keys.containsKey(alias)
    override fun engineDeleteEntry(alias: String) { keys.remove(alias) }
    override fun engineAliases() = Collections.enumeration(keys.keys)
    override fun engineSize() = keys.size
    override fun engineIsKeyEntry(alias: String) = keys.containsKey(alias)
    override fun engineIsCertificateEntry(alias: String) = false
    override fun engineGetCreationDate(alias: String) = Date(0)
    override fun engineGetCertificate(alias: String): Certificate? = null
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
    override fun engineGetCertificateAlias(cert: Certificate): String? = null
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) { keys[alias] = key as SecretKey }
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) { error("Unsupported") }
    override fun engineSetCertificateEntry(alias: String, cert: Certificate) { error("Unsupported") }
    override fun engineStore(stream: OutputStream?, password: CharArray?) {}
    override fun engineLoad(stream: InputStream?, password: CharArray?) {}
    companion object { val keys = ConcurrentHashMap<String, SecretKey>(); var failAccess = false }
}
class TestKeyGeneratorSpi : KeyGeneratorSpi() {
    private lateinit var spec: KeyGenParameterSpec
    override fun engineInit(params: AlgorithmParameterSpec, random: SecureRandom?) { spec = params as KeyGenParameterSpec }
    override fun engineInit(random: SecureRandom?) { error("Explicit parameters required") }
    override fun engineInit(keysize: Int, random: SecureRandom?) { error("Explicit parameters required") }
    override fun engineGenerateKey(): SecretKey =
        KeyGenerator.getInstance("AES", "SunJCE").apply { check(!TestKeyStoreSpi.failAccess); init(spec.keySize) }.generateKey().also { TestKeyStoreSpi.keys[spec.keystoreAlias] = it }
}
