package com.cowalskiiw2026.autoklix.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cowalskiiw2026.autoklix.model.PointType

@Entity(
    tableName = "click_points",
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("presetId")]
)
data class ClickPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val order: Int,
    val type: PointType,
    val x: Float,
    val y: Float,
    val x2: Float?,
    val y2: Float?,
    val actionDurationMs: Long,
    val delayAfterMs: Long,
    val pointSizeOverride: Float?,
    val pointOpacityOverride: Float?
)
