package com.refit.app.data.local

import androidx.room.TypeConverter
import com.refit.app.data.model.SyncStatus

class RoomConverters {

    @TypeConverter
    fun syncStatusToString(status: SyncStatus): String = status.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus =
        SyncStatus.valueOf(value)
}
