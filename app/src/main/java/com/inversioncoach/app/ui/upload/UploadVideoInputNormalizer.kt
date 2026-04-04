package com.inversioncoach.app.ui.upload

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val NORMALIZER_TAG = "UploadVideoNormalizer"

data class UploadVideoFormatDetails(
    val containerMime: String?,
    val videoMime: String?,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameRate: Int,
    val bitDepth: Int?,
    val colorTransfer: Int?,
    val hdrStaticInfoPresent: Boolean,
    val videoTrackCount: Int,
    val audioTrackCount: Int,
    val metadataTrackCount: Int,
)

data class CanonicalVideoSpec(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val videoMime: String,
    val bitDepth: Int,
    val dynamicRange: String,
    val rotationDegrees: Int = 0,
    val videoTrackCount: Int = 1,
)

data class UploadVideoNormalizationResult(
    val sourceUri: Uri,
    val workingUri: Uri,
    val source: UploadVideoFormatDetails,
    val canonical: CanonicalVideoSpec,
    val normalizationRequired: Boolean,
    val normalizationAttempted: Boolean,
    val normalizationSucceeded: Boolean,
    val reasons: Set<String>,
    val failureStage: String? = null,
)

interface UploadVideoInputNormalizer {
    suspend fun normalize(sourceUri: Uri): UploadVideoNormalizationResult
}

interface UploadVideoFormatInspector {
    fun inspect(sourceUri: Uri): UploadVideoFormatDetails
}

interface UploadVideoTranscoder {
    suspend fun transcodeToCanonical(sourceUri: Uri, source: UploadVideoFormatDetails, target: CanonicalVideoSpec): Uri?
}

internal class AndroidUploadVideoFormatInspector(private val context: Context) : UploadVideoFormatInspector {
    override fun inspect(sourceUri: Uri): UploadVideoFormatDetails {
        val retriever = MediaMetadataRetriever()
        var containerMime: String? = null
        var width = 0
        var height = 0
        var rotation = 0
        var frameRate = 30
        try {
            retriever.setDataSource(context, sourceUri)
            containerMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.toInt() ?: 30
        } finally {
            runCatching { retriever.release() }
        }

        var videoMime: String? = null
        var bitDepth: Int? = null
        var colorTransfer: Int? = null
        var hdrStaticInfoPresent = false
        var videoTracks = 0
        var audioTracks = 0
        var metadataTracks = 0

        runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, sourceUri, emptyMap())
                repeat(extractor.trackCount) { index ->
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    when {
                        mime?.startsWith("video/") == true -> {
                            videoTracks += 1
                            if (videoMime == null) videoMime = mime
                            if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                                width = format.getInteger(MediaFormat.KEY_WIDTH)
                            }
                            if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                                height = format.getInteger(MediaFormat.KEY_HEIGHT)
                            }
                            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                            }
                            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                                val bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                                if (bitDepth == null && width > 0 && height > 0 && frameRate > 0) {
                                    val bitsPerPixel = bitrate.toDouble() / (width.toDouble() * height.toDouble() * frameRate.toDouble())
                                    bitDepth = if (bitsPerPixel > 0.24) 10 else 8
                                }
                            }
                            if (android.os.Build.VERSION.SDK_INT >= 24 && format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                                colorTransfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                            }
                            hdrStaticInfoPresent = hdrStaticInfoPresent || format.containsKey("hdr-static-info")
                        }
                        mime?.startsWith("audio/") == true -> audioTracks += 1
                        else -> metadataTracks += 1
                    }
                }
            } finally {
                runCatching { extractor.release() }
            }
        }.onFailure {
            Log.w(NORMALIZER_TAG, "inspect_extractor_failed uri=$sourceUri message=${it.message}")
        }

        return UploadVideoFormatDetails(
            containerMime = containerMime,
            videoMime = videoMime,
            width = width,
            height = height,
            rotationDegrees = normalizeRotation(rotation),
            frameRate = frameRate.coerceAtLeast(1),
            bitDepth = bitDepth,
            colorTransfer = colorTransfer,
            hdrStaticInfoPresent = hdrStaticInfoPresent,
            videoTrackCount = videoTracks.coerceAtLeast(1),
            audioTrackCount = audioTracks,
            metadataTrackCount = metadataTracks,
        )
    }

    private fun normalizeRotation(raw: Int): Int = ((raw % 360) + 360) % 360
}

internal class NoopUploadVideoTranscoder : UploadVideoTranscoder {
    override suspend fun transcodeToCanonical(sourceUri: Uri, source: UploadVideoFormatDetails, target: CanonicalVideoSpec): Uri? = null
}

class DefaultUploadVideoInputNormalizer(
    private val inspector: UploadVideoFormatInspector,
    private val transcoder: UploadVideoTranscoder = NoopUploadVideoTranscoder(),
) : UploadVideoInputNormalizer {
    constructor(
        context: Context,
        transcoder: UploadVideoTranscoder = NoopUploadVideoTranscoder(),
    ) : this(
        inspector = AndroidUploadVideoFormatInspector(context),
        transcoder = transcoder,
    )
    override suspend fun normalize(sourceUri: Uri): UploadVideoNormalizationResult = withContext(Dispatchers.IO) {
        val source = inspector.inspect(sourceUri)
        val reasons = mutableSetOf<String>()
        if (source.rotationDegrees != 0) reasons += "rotation_metadata"
        if (source.videoMime?.contains("hevc", ignoreCase = true) == true || source.videoMime?.contains("h265", ignoreCase = true) == true) {
            reasons += "hevc_source"
        }
        if ((source.bitDepth ?: 8) > 8) reasons += "ten_bit_source"
        if (source.hdrStaticInfoPresent || looksHdrTransfer(source.colorTransfer)) reasons += "hdr_metadata"
        if (source.metadataTrackCount > 0 || source.videoTrackCount > 1) reasons += "noncanonical_tracks"

        val portraitWidth = minOf(source.width, source.height).coerceAtLeast(1)
        val portraitHeight = maxOf(source.width, source.height).coerceAtLeast(1)
        val canonical = CanonicalVideoSpec(
            width = portraitWidth,
            height = portraitHeight,
            frameRate = source.frameRate.coerceIn(24, 30),
            videoMime = "video/avc",
            bitDepth = 8,
            dynamicRange = "SDR",
            rotationDegrees = 0,
            videoTrackCount = 1,
        )

        if (reasons.isEmpty()) {
            return@withContext UploadVideoNormalizationResult(
                sourceUri = sourceUri,
                workingUri = sourceUri,
                source = source,
                canonical = canonical,
                normalizationRequired = false,
                normalizationAttempted = false,
                normalizationSucceeded = true,
                reasons = emptySet(),
            )
        }

        val transcoded = runCatching { transcoder.transcodeToCanonical(sourceUri, source, canonical) }
            .onFailure { Log.w(NORMALIZER_TAG, "normalize_attempt_failed uri=$sourceUri message=${it.message}", it) }
            .getOrNull()

        UploadVideoNormalizationResult(
            sourceUri = sourceUri,
            workingUri = transcoded ?: sourceUri,
            source = source,
            canonical = canonical,
            normalizationRequired = true,
            normalizationAttempted = true,
            normalizationSucceeded = transcoded != null,
            reasons = reasons,
            failureStage = if (transcoded == null) "transcode_to_canonical" else null,
        )
    }

    private fun looksHdrTransfer(colorTransfer: Int?): Boolean {
        if (colorTransfer == null) return false
        return colorTransfer == 6 || colorTransfer == 7
    }
}
