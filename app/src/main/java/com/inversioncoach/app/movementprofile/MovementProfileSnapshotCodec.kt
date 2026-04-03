package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.MovementProfileRecord

object MovementProfileSnapshotCodec {
    fun toSnapshot(record: MovementProfileRecord): StoredProfileSnapshot {
        val phaseDurations = parsePhaseDurations(record.poseTimelineJson)
        val features = parseFloatMap(record.normalizedFeatureJson)
        val means = features
            .filterKeys { it.endsWith("_mean") }
            .mapKeys { (key, _) -> key.removeSuffix("_mean") }
        val jitters = features
            .filterKeys { it.endsWith("_jitter") }
            .mapKeys { (key, _) -> key.removeSuffix("_jitter") }
        return StoredProfileSnapshot(phaseDurations, means, jitters)
    }

    private fun parsePhaseDurations(raw: String): Map<String, Long> {
        val events = raw.split('|').mapNotNull { token ->
            val parts = token.split(':')
            if (parts.size < 2) null else parts[0].toLongOrNull()?.let { ts -> ts to parts[1] }
        }.sortedBy { it.first }
        val out = linkedMapOf<String, Long>()
        for (i in 0 until events.lastIndex) {
            val current = events[i]
            val next = events[i + 1]
            out[current.second] = (out[current.second] ?: 0L) + (next.first - current.first).coerceAtLeast(0L)
        }
        return out
    }

    private fun parseFloatMap(raw: String): Map<String, Float> =
        raw.split('|').mapNotNull { token ->
            val idx = token.indexOf(':')
            if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1).toFloatOrNull()
        }.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
}
