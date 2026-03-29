package com.inversioncoach.app.storage.db

import androidx.room.TypeConverter
import com.inversioncoach.app.model.AlignmentStrictness
import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.CleanupStatus
import com.inversioncoach.app.model.CompressionStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.RetainedAssetType
import com.inversioncoach.app.model.SessionSource

class Converters {
    @TypeConverter
    fun drillTypeFromString(raw: String): DrillType = DrillType.fromStoredName(raw) ?: DrillType.FREESTYLE

    @TypeConverter
    fun drillTypeToString(value: DrillType): String = value.name


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
        when (raw) {
            "PROCESSING" -> AnnotatedExportStatus.PROCESSING
            "READY" -> AnnotatedExportStatus.ANNOTATED_READY
            "FAILED" -> AnnotatedExportStatus.ANNOTATED_FAILED
            "EXPORTING", "EXPORTED_MASTER", "COMPRESSING_FINAL" -> AnnotatedExportStatus.PROCESSING
            "ANNOTATED_FINAL_READY", "FALLBACK_RAW_FINAL_READY" -> AnnotatedExportStatus.ANNOTATED_READY
            else -> runCatching { AnnotatedExportStatus.valueOf(raw) }.getOrDefault(AnnotatedExportStatus.NOT_STARTED)
        }

    @TypeConverter
    fun annotatedExportStatusToString(value: AnnotatedExportStatus): String = value.name


    @TypeConverter
    fun annotatedExportStageFromString(raw: String): AnnotatedExportStage =
        runCatching { AnnotatedExportStage.valueOf(raw) }.getOrDefault(AnnotatedExportStage.QUEUED)

    @TypeConverter
    fun annotatedExportStageToString(value: AnnotatedExportStage): String = value.name

    @TypeConverter
    fun rawPersistStatusFromString(raw: String): RawPersistStatus =
        runCatching { RawPersistStatus.valueOf(raw) }.getOrDefault(RawPersistStatus.NOT_STARTED)

    @TypeConverter
    fun rawPersistStatusToString(value: RawPersistStatus): String = value.name

    @TypeConverter
    fun compressionStatusFromString(raw: String): CompressionStatus =
        runCatching { CompressionStatus.valueOf(raw) }.getOrDefault(CompressionStatus.NOT_STARTED)

    @TypeConverter
    fun compressionStatusToString(value: CompressionStatus): String = value.name

    @TypeConverter
    fun cleanupStatusFromString(raw: String): CleanupStatus =
        runCatching { CleanupStatus.valueOf(raw) }.getOrDefault(CleanupStatus.NOT_STARTED)

    @TypeConverter
    fun cleanupStatusToString(value: CleanupStatus): String = value.name

    @TypeConverter
    fun retainedAssetTypeFromString(raw: String): RetainedAssetType =
        runCatching { RetainedAssetType.valueOf(raw) }.getOrDefault(RetainedAssetType.NONE)

    @TypeConverter
    fun retainedAssetTypeToString(value: RetainedAssetType): String = value.name

    @TypeConverter
    fun sessionSourceFromString(raw: String): SessionSource =
        runCatching { SessionSource.valueOf(raw) }.getOrDefault(SessionSource.LIVE_COACHING)

    @TypeConverter
    fun sessionSourceToString(value: SessionSource): String = value.name

}
