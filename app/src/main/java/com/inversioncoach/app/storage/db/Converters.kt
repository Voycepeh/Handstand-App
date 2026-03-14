package com.inversioncoach.app.storage.db

import androidx.room.TypeConverter
import com.inversioncoach.app.model.CueStyle
import com.inversioncoach.app.model.DrillType

class Converters {
    @TypeConverter
    fun drillTypeFromString(raw: String): DrillType = DrillType.valueOf(raw)

    @TypeConverter
    fun drillTypeToString(value: DrillType): String = value.name

    @TypeConverter
    fun cueStyleFromString(raw: String): CueStyle = CueStyle.valueOf(raw)

    @TypeConverter
    fun cueStyleToString(value: CueStyle): String = value.name
}
