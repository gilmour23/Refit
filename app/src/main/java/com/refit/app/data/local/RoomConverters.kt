package com.refit.app.data.local

import androidx.room.TypeConverter
import com.refit.app.data.model.SyncStatus
import com.refit.app.security.DeviceCipher
import kotlinx.serialization.json.Json

class RoomConverters {
    private val cipher = DeviceCipher("refit.records.v1", "refit:rehab-record:v1")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }

    @TypeConverter
    fun recordToCiphertext(record: RehabSessionEntity): String =
        cipher.encrypt(json.encodeToString(RehabSessionEntity.serializer(), record))

    @TypeConverter
    fun ciphertextToRecord(value: String): RehabSessionEntity =
        json.decodeFromString(RehabSessionEntity.serializer(), cipher.decrypt(value))

    @TypeConverter
    fun syncStatusToString(status: SyncStatus): String = status.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus =
        SyncStatus.valueOf(value)
}

