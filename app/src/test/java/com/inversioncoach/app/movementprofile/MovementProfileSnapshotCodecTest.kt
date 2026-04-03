package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.MovementProfileRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementProfileSnapshotCodecTest {
    @Test
    fun toSnapshotParsesPhaseDurationsAndFeatureMaps() {
        val record = MovementProfileRecord(
            id = "profile-1",
            assetId = "asset-1",
            drillId = "drill-1",
            extractionVersion = 1,
            poseTimelineJson = "0:setup|100:hold|400:finish",
            normalizedFeatureJson = "alignment_score_mean:0.8|alignment_score_jitter:0.1|trunk_lean_mean:0.2|trunk_lean_jitter:0.05",
            repSegmentsJson = "",
            holdSegmentsJson = "",
            createdAtMs = 1,
        )

        val snapshot = MovementProfileSnapshotCodec.toSnapshot(record)

        assertEquals(100L, snapshot.phaseDurationsMs["setup"])
        assertEquals(300L, snapshot.phaseDurationsMs["hold"])
        assertEquals(0.8f, snapshot.featureMeans["alignment_score"])
        assertEquals(0.05f, snapshot.stabilityJitter["trunk_lean"])
    }

    @Test
    fun toSnapshotSkipsMalformedTokensSafely() {
        val record = MovementProfileRecord(
            id = "profile-2",
            assetId = "asset-1",
            drillId = "drill-1",
            extractionVersion = 1,
            poseTimelineJson = "bad|100:hold|200:finish",
            normalizedFeatureJson = "bad-token|alignment_score_mean:NaN|trunk_lean_jitter:0.2",
            repSegmentsJson = "",
            holdSegmentsJson = "",
            createdAtMs = 1,
        )

        val snapshot = MovementProfileSnapshotCodec.toSnapshot(record)

        assertEquals(100L, snapshot.phaseDurationsMs["hold"])
        assertEquals(0.2f, snapshot.stabilityJitter["trunk_lean"])
    }
}
