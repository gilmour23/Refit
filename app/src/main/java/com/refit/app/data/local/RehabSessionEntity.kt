package com.refit.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rehab_sessions")
data class RehabSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val createdAt: Long,
    val exerciseName: String,
    val durationSeconds: Long,
    val sampleCount: Int,
    val rms: Float,
    val mav: Float,
    val maxActivation: Float
)
