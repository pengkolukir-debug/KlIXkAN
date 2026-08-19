package com.cowalskiiw2026.autoklix.data

import androidx.room.TypeConverter
import com.cowalskiiw2026.autoklix.model.PointType

class Converters {
    @TypeConverter
    fun fromPointType(value: PointType): String = value.name

    @TypeConverter
    fun toPointType(value: String): PointType = PointType.valueOf(value)
}
