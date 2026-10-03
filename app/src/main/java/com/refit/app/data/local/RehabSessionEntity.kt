package com.refit.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.refit.app.data.model.SyncStatus
import java.util.UUID

@Entity(
    tableName = "rehab_sessions",
    indices = [
        Index(value = ["userId", "createdAt"]),
        Index(value = ["userId", "clientLocalId"], unique = true)
    ]
)
data class RehabSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val remoteId: String? = null,
    val clientLocalId: String = UUID.randomUUID().toString(),
    val userId: String,
    val createdAt: Long,
    val exerciseName: String,
    val durationSeconds: Long,
    val sampleCount: Int,
    val rms: Float,
    val mav: Float,
    @ColumnInfo(name = "maxActivation")
    val peakActivation: Float,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val updatedAt: Long = createdAt
)
