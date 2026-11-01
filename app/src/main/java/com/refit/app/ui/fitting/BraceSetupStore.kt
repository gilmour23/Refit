package com.refit.app.ui.fitting

import android.content.Context
import java.io.IOException
import java.security.MessageDigest

/** Only synthetic setup metadata is stored here; no photos or measured body dimensions. */
internal class BraceSetupStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readDemoSide(owner: String): String? {
        val key = key(owner)
        if (!prefs.contains("$key.mode")) return null
        val mode = prefs.getString("$key.mode", null)
        val side = prefs.getString("$key.side", null)
        if (mode != "demo" || side !in listOf("left", "right")) throw IOException("Invalid brace setup")
        return side
    }

    fun registerDemo(owner: String, side: String) {
        require(side == "left" || side == "right")
        val key = key(owner)
        if (!prefs.edit().putString("$key.mode", "demo").putString("$key.side", side).commit()) {
            throw IOException("Brace setup was not saved")
        }
    }

    private fun key(owner: String): String = MessageDigest.getInstance("SHA-256")
        .digest(owner.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object { const val PREFS_NAME = "refit_brace_setup_v1" }
}
