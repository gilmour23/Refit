package com.refit.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest

@Entity(tableName = "rehab_sessions", indices = [Index(value = ["ownerTag", "clientTag"], unique = true)])
data class EncryptedSessionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerTag: String,
    val clientTag: String,
    val record: RehabSessionEntity
) {
    fun decoded(): RehabSessionEntity {
        check(ownerTag == ownerTag(record.userId)) { "Record owner integrity check failed" }
        check(clientTag == clientTag(record.clientLocalId)) { "Record identity integrity check failed" }
        return record.copy(id = id)
    }

    companion object {
        fun clientTag(clientId: String): String = MessageDigest.getInstance("SHA-256")
            .digest(("refit:record-client:v1:" + clientId).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        fun ownerTag(userId: String): String = MessageDigest.getInstance("SHA-256")
            .digest(("refit:record-owner:v1:" + userId).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
