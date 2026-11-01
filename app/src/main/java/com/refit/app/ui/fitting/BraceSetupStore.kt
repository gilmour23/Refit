package com.refit.app.ui.fitting

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.security.MessageDigest

/** Owner-scoped registration; actual dimensions are authenticated/encrypted, report photos are not retained. */
internal class BraceSetupStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun readDemoSide(owner: String): String? {
        val key = key(owner)
        if (!prefs.contains("$key.mode")) return null
        val mode = prefs.getString("$key.mode", null)
        val side = prefs.getString("$key.side", null)
        if (mode !in listOf("demo", "measured") || side !in listOf("left", "right")) throw IOException("Invalid brace setup")
        return side
    }

    fun readMeasurement(owner: String): String? {
        val key = key(owner)
        if (prefs.getString("$key.mode", null) != "measured") return null
        val encrypted = prefs.getString("$key.measurement", null) ?: throw IOException("Missing measurement")
        val json = cipher(key).decrypt(encrypted)
        com.refit.app.data.vision.BraceMeasurement.parse(json)
        return json
    }

    fun registerMeasurement(owner: String, side: String, json: String) {
        com.refit.app.data.vision.BraceMeasurement.parse(json)
        // Do not persist the report's original-body photographs in preferences.
        val clean = kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
        val withoutPhotos = kotlinx.serialization.json.JsonObject(clean.filterKeys {
            it !in listOf("report_jpg_base64", "overlay_top_jpg_base64", "overlay_side_jpg_base64")
        }).toString()
        register(owner, side, "measured", cipher(key(owner)).encrypt(withoutPhotos))
    }

    fun registerDemo(owner: String, side: String) = register(owner, side, "demo", null)

    private fun cipher(ownerHash: String) = com.refit.app.security.DeviceCipher("refit_measurement_v1", "measurement:$ownerHash")

    private fun register(owner: String, side: String, mode: String, measurement: String?) {
        require(side == "left" || side == "right")
        val key = key(owner)
        val previousMode = prefs.getString("$key.mode", null)
        val previousSide = prefs.getString("$key.side", null)
        val previousMeasurement = prefs.getString("$key.measurement", null)
        if (!prefs.edit().putString("$key.mode", mode).putString("$key.side", side)
            .putString("$key.measurement", measurement).commit()) {
            prefs.edit().putString("$key.mode", previousMode).putString("$key.side", previousSide)
                .putString("$key.measurement", previousMeasurement).commit()
            throw IOException("Brace setup was not saved")
        }
    }

    private fun key(owner: String): String = MessageDigest.getInstance("SHA-256")
        .digest(owner.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object { const val PREFS_NAME = "refit_brace_setup_v1" }
}

