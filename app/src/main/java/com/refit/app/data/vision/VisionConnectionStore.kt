package com.refit.app.data.vision

import android.content.Context
import com.refit.app.security.DeviceCipher
import java.io.IOException
import java.security.MessageDigest

/** Connection credentials are encrypted and bound to the exact destination, never bundled with the APK. */
internal class VisionConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("refit_vision_credentials", Context.MODE_PRIVATE)
    private fun destination(server: String) = server.trim().trimEnd('/')
    private fun key(server: String) = MessageDigest.getInstance("SHA-256")
        .digest(destination(server).toByteArray()).joinToString("") { "%02x".format(it) }
    private fun cipher(server: String) = DeviceCipher("refit.vision.credential.v1", "vision:${destination(server)}")
    fun read(server: String): String? = prefs.getString(key(server), null)?.let { cipher(server).decrypt(it) }
    fun save(server: String, token: String) {
        val k = key(server)
        val previous = prefs.getString(k, null)
        if (!prefs.edit().putString(k, cipher(server).encrypt(token)).commit()) {
            prefs.edit().putString(k, previous).commit()
            throw IOException("측정 서버 연결키를 저장하지 못했어요.")
        }
    }
}
