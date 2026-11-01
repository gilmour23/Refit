package com.refit.app.ui.fitting

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.security.MessageDigest

/** Only synthetic setup metadata is stored here; no photos or measured body dimensions. */
internal class BraceSetupStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

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
        val previousMode = prefs.getString("$key.mode", null)
        val previousSide = prefs.getString("$key.side", null)
        if (!prefs.edit().putString("$key.mode", "demo").putString("$key.side", side).commit()) {
            // Android changes its shared in-memory map even when disk commit fails.
            // Restore the old record so recreating the activity cannot unlock a failed setup.
            prefs.edit().putString("$key.mode", previousMode).putString("$key.side", previousSide).commit()
            throw IOException("Brace setup was not saved")
        }
    }

    private fun key(owner: String): String = MessageDigest.getInstance("SHA-256")
        .digest(owner.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object { const val PREFS_NAME = "refit_brace_setup_v1" }
}
