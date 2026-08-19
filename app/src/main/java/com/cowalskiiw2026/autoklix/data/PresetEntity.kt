package com.cowalskiiw2026.autoklix.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val repeatCount: Int = 0,
    val repeatDurationMs: Long = 0
)
