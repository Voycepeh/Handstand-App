package com.inversioncoach.app.storage.db

import androidx.room.TypeConverter
import com.inversioncoach.app.model.AlignmentStrictness
import com.inversioncoach.app.model.AnnotatedExportStatus
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

    @TypeConverter
    fun alignmentStrictnessFromString(raw: String): AlignmentStrictness = when (raw) {
        "EASY" -> AlignmentStrictness.BEGINNER
        "STRICT" -> AlignmentStrictness.ADVANCED
        else -> runCatching { AlignmentStrictness.valueOf(raw) }.getOrDefault(AlignmentStrictness.STANDARD)
    }

    @TypeConverter
    fun alignmentStrictnessToString(value: AlignmentStrictness): String = value.name


    @TypeConverter
    fun annotatedExportStatusFromString(raw: String): AnnotatedExportStatus =
        runCatching { AnnotatedExportStatus.valueOf(raw) }.getOrDefault(AnnotatedExportStatus.NOT_STARTED)

    @TypeConverter
    fun annotatedExportStatusToString(value: AnnotatedExportStatus): String = value.name

}
