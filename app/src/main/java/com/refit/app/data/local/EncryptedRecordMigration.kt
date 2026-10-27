package com.refit.app.data.local

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.refit.app.data.model.SyncStatus

/** Room wraps this conversion in a transaction: any encryption failure rolls back. */
class EncryptedRecordMigration : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.query("PRAGMA secure_delete=ON").use { c ->
            check(c.moveToFirst() && c.getInt(0) == 1) { "Secure database deletion is unavailable" }
        }
        database.execSQL("CREATE TABLE rehab_sessions_encrypted (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerTag TEXT NOT NULL, clientTag TEXT NOT NULL, record TEXT NOT NULL)")
        val converter = RoomConverters()
        database.query("SELECT * FROM rehab_sessions").use { c ->
            while (c.moveToNext()) {
                val row = RehabSessionEntity(
                    id = c.number("id"), remoteId = c.optionalText("remoteId"),
                    clientLocalId = c.text("clientLocalId"), userId = c.text("userId"),
                    createdAt = c.number("createdAt"), exerciseName = c.text("exerciseName"),
                    durationSeconds = c.number("durationSeconds"), sampleCount = c.number("sampleCount").toInt(),
                    rms = c.getFloat(c.getColumnIndexOrThrow("rms")),
                    mav = c.getFloat(c.getColumnIndexOrThrow("mav")),
                    peakActivation = c.getFloat(c.getColumnIndexOrThrow("maxActivation")),
                    syncStatus = SyncStatus.valueOf(c.text("syncStatus")), updatedAt = c.number("updatedAt"),
                    completedAttempts = c.optionalInt("completedAttempts"), plannedAttempts = c.optionalInt("plannedAttempts"),
                    attemptSeconds = c.optionalInt("attemptSeconds"), restSeconds = c.optionalInt("restSeconds"),
                    endedEarly = c.optionalInt("endedEarly")?.let { it != 0 }, dataMode = c.text("dataMode"),
                    comfort = c.optionalText("comfort"), wristSide = c.optionalText("wristSide"),
                    pain = c.optionalText("pain"), fatigue = c.optionalText("fatigue")
                )
                val encrypted = converter.recordToCiphertext(row)
                check(converter.ciphertextToRecord(encrypted) == row) { "Record encryption verification failed" }
                database.execSQL("INSERT INTO rehab_sessions_encrypted (id, ownerTag, clientTag, record) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(row.id, EncryptedSessionRow.ownerTag(row.userId), EncryptedSessionRow.clientTag(row.clientLocalId), encrypted))
            }
        }
        database.execSQL("DROP TABLE rehab_sessions")
        database.execSQL("ALTER TABLE rehab_sessions_encrypted RENAME TO rehab_sessions")
        database.execSQL("CREATE UNIQUE INDEX index_rehab_sessions_ownerTag_clientTag ON rehab_sessions(ownerTag, clientTag)")
    }

    private fun Cursor.number(name: String) = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.text(name: String): String = getString(getColumnIndexOrThrow(name))
    private fun Cursor.optionalText(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.optionalInt(name: String): Int? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getInt(it) }
}
