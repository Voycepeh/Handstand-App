package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.CueStyle
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs

abstract class BaseDrillAnalyzer(
    protected val drillType: DrillType,
    protected val profile: DrillThresholdProfile = DrillProfiles.defaults.getValue(drillType),
    private val smoother: PoseSmoother = PoseSmoother(),
    private val normalizer: PoseNormalization = PoseNormalization(),
    private val commonMetrics: CommonMetricsCalculator = CommonMetricsCalculator(),
    private val drillMetrics: DrillMetricsCalculator = DrillMetricsCalculator(),
    private val issueClassifier: IssueClassifier = IssueClassifier(),
    private val scoreEngine: ScoreEngine = ScoreEngine(),
    private val cueEngine: CueEngine = CueEngine(),
    private val summaryGenerator: SummaryGenerator = SummaryGenerator(),
    private val recommendationEngine: RecommendationEngine = RecommendationEngine(),
) : DrillAnalyzer {

    private val frameScores = mutableListOf<Pair<Long, Int>>()
    private val debugFrames = mutableListOf<DebugFrameData>()
    private val allIssues = mutableListOf<IssueInstance>()
    private val timeline = mutableListOf<IssueTimelineRange>()
    private val repAnalyses = mutableListOf<RepAnalysis>()

    private var issueState = mutableMapOf<IssueType, Pair<Long, IssueSeverity>>()
    private var currentRepStart: Long? = null
    private var topTs: Long = 0
    private var bottomTs: Long = 0
    private var worstTs: Long = 0
    private var worstScore: Int = 101
    private val headPath = mutableListOf<Pair<Float, Float>>()
    private val shoulderPath = mutableListOf<Pair<Float, Float>>()
    private val hipPath = mutableListOf<Pair<Float, Float>>()
    private var lastHeadY: Float? = null
    private var descentStartTs: Long? = null
    private var repPathVariance = 0.15f
    private var lastCueTrace = "none"

    override fun analyzeFrame(frame: PoseFrame): FrameAnalysis {
        val smoothed = smoother.smooth(frame)
        val normalized = normalizer.normalize(smoothed)
        val path = framePathMetrics(normalized)
        val tempo = frameTempo(normalized)
        val derived = commonMetrics.calculate(normalized, tempo, path)

        cueEngine.registerObservedIssues(candidateIssues(derived))
        val issues = issueClassifier.classify(drillType, derived, profile, cueEngine.persisted(), frame.timestampMs)
        updateIssueTimeline(frame.timestampMs, issues)

        val sub = drillMetrics.computeSubscores(drillType, derived, profile)
        val score = scoreEngine.score(drillType, sub)
        val cue = cueEngine.decide(profile, derived.confidenceLevel, issues, CueStyle.CONCISE, frame.timestampMs)
        if (cue != null) lastCueTrace = "${cue.category}:${cue.text}"

        val debug = DebugFrameData(
            timestampMs = frame.timestampMs,
            rawJointMap = normalized.joints.mapValues { it.value.x to it.value.y },
            confidences = normalized.joints.mapValues { it.value.visibility },
            derivedAngles = derived.jointAngles,
            normalizedOffsets = derived.stackOffsetsNorm,
            classifiedIssues = issues.map { it.type },
            cueTrace = lastCueTrace,
            score = score.overall,
        )

        frameScores += frame.timestampMs to score.overall
        debugFrames += debug
        allIssues += issues
        updateRepState(normalized, derived, score.overall)

        return FrameAnalysis(normalized, derived, issues, score, cue, debug)
    }

    override fun finalizeRep(timestampMs: Long): RepAnalysis? {
        val start = currentRepStart ?: return null
        val end = timestampMs
        val descentSec = ((bottomTs - descentStartTs.orEmpty(start)) / 1000f).coerceAtLeast(0f)
        val metrics = mapOf(
            "descent_sec" to descentSec,
            "path_variance" to repPathVariance,
            "depth_norm" to depthNorm(),
        )
        val sub = drillMetrics.computeSubscores(drillType, latestDerived(), profile)
        val score = scoreEngine.score(drillType, sub)
        val issues = allIssues.filter { it.sinceTimestampMs in start..end }
        val rep = RepAnalysis(
            repIndex = repAnalyses.size + 1,
            startTimestampMs = start,
            endTimestampMs = end,
            topFrameTs = topTs,
            bottomFrameTs = bottomTs,
            worstAlignmentFrameTs = worstTs,
            metrics = metrics,
            score = score,
            issues = issues,
            headPath = headPath.toList(),
            shoulderPath = shoulderPath.toList(),
            hipPath = hipPath.toList(),
            alignmentLossPercent = alignmentLossPercent(start, end),
        )
        repAnalyses += rep
        resetRepBuffers()
        return rep
    }

    override fun finalizeSession(): SessionAnalysis {
        closeOpenTimeline(frameScores.lastOrNull()?.first ?: System.currentTimeMillis())
        val allScores = frameScores.map { it.second }
        val best = frameScores.maxByOrNull { it.second }?.first
        val worst = frameScores.minByOrNull { it.second }?.first
        val baseScore = scoreEngine.score(drillType, averageSubScores())
        val issueFreq = allIssues.groupingBy { it.type }.eachCount()
        val recommendation = recommendationEngine.recommend(issueFreq)
        val summary = summaryGenerator.generate(
            drill = drillType,
            score = baseScore,
            issues = allIssues,
            bestRepOrWindow = "Best window at ${best ?: 0}ms",
            worstRepOrWindow = "Lowest-quality window at ${worst ?: 0}ms",
            trendDelta = null,
            recommendation = recommendation,
        )
        return SessionAnalysis(
            drillType = drillType,
            score = baseScore,
            reps = repAnalyses.toList(),
            hold = holdAnalysis(),
            issueTimeline = timeline.toList(),
            bestFrameTimestampMs = best,
            worstFrameTimestampMs = worst,
            mostCommonIssue = issueFreq.maxByOrNull { it.value }?.key,
            consistencyScore = scoreEngine.consistencyScore(allScores),
            summary = summary,
            recommendation = recommendation,
            debugFrames = debugFrames.toList(),
        )
    }

    protected open fun candidateIssues(metrics: DerivedMetrics): List<IssueType> = buildList {
        if (metrics.bananaProxyScore > 55) add(IssueType.BANANA_ARCH)
        if (metrics.scapularElevationProxyScore < 45) add(IssueType.PASSIVE_SHOULDERS)
        if ((metrics.jointAngles["knee_angle"] ?: 180f) < profile.kneeWarnDeg) add(IssueType.SOFT_KNEES)
        if ((metrics.pathMetrics["head_forward_norm"] ?: 0f) > profile.headForwardNormMax) add(IssueType.HEAD_PATH_FORWARD)
        if ((metrics.tempoMetrics["descent_sec"] ?: 2f) < profile.descentPoorSec) add(IssueType.RUSHED_DESCENT)
    }

    private fun framePathMetrics(n: NormalizedPose): Map<String, Float> {
        val side = n.dominantSide.name.lowercase()
        val j = n.joints
        val wrist = n.midpoints["wrist_mid"] ?: j["${side}_wrist"]
        val head = j["nose"]
        val shoulder = n.midpoints["shoulder_mid"] ?: j["${side}_shoulder"]
        val hip = n.midpoints["hip_mid"] ?: j["${side}_hip"]
        val ankle = n.midpoints["ankle_mid"] ?: j["${side}_ankle"]
        if (head != null) headPath += head.x to head.y
        if (shoulder != null) shoulderPath += shoulder.x to shoulder.y
        if (hip != null) hipPath += hip.x to hip.y
        val headForward = if (wrist != null && head != null) LandmarkMath.normalizeByTorsoLength(head.x - wrist.x, n.torsoLength) else 0f
        val hipAboveShoulder = if (hip != null && shoulder != null) LandmarkMath.normalizeByTorsoLength(shoulder.y - hip.y, n.torsoLength) else 0f
        val ankleWall = abs((ankle?.x ?: 0.5f) - wallX()) / n.torsoLength
        val elbowFlare = elbowFlareProxy(j, side)
        return mapOf(
            "head_forward_norm" to headForward,
            "hip_above_shoulder_norm" to hipAboveShoulder,
            "ankle_wall_norm" to ankleWall,
            "elbow_flare_proxy" to elbowFlare,
            "path_variance" to repPathVariance,
            "depth_norm" to depthNorm(),
            "hip_drift_norm" to hipDriftNorm(),
        )
    }

    private fun frameTempo(n: NormalizedPose): Map<String, Float> {
        val head = n.joints["nose"]?.y ?: return emptyMap()
        val last = lastHeadY
        lastHeadY = head
        if (last == null) return emptyMap()
        val descending = head > last
        if (descending && descentStartTs == null) descentStartTs = n.timestampMs
        if (!descending && descentStartTs != null && bottomTs == 0L) bottomTs = n.timestampMs
        val descentSec = if (descentStartTs == null || bottomTs == 0L) 0f else (bottomTs - descentStartTs!!)/1000f
        return mapOf("descent_sec" to descentSec)
    }

    private fun updateRepState(n: NormalizedPose, d: DerivedMetrics, score: Int) {
        currentRepStart = currentRepStart ?: n.timestampMs
        if ((d.jointAngles["elbow_angle"] ?: 0f) >= profile.lockoutDeg && topTs == 0L) topTs = n.timestampMs
        if (score < worstScore) {
            worstScore = score
            worstTs = n.timestampMs
        }
    }

    private fun updateIssueTimeline(ts: Long, issues: List<IssueInstance>) {
        val active = issues.associate { it.type to it.severity }
        issueState.keys.filter { it !in active.keys }.toList().forEach { closed ->
            val (start, sev) = issueState.remove(closed) ?: return@forEach
            timeline += IssueTimelineRange(closed, sev, start, ts)
        }
        active.forEach { (type, severity) ->
            if (type !in issueState) issueState[type] = ts to severity
        }
    }

    private fun closeOpenTimeline(endTs: Long) {
        issueState.forEach { (issue, info) -> timeline += IssueTimelineRange(issue, info.second, info.first, endTs) }
        issueState.clear()
    }

    private fun holdAnalysis(): HoldAnalysis? {
        if (
            drillType == com.inversioncoach.app.model.DrillType.PIKE_PUSH_UP ||
            drillType == com.inversioncoach.app.model.DrillType.ELEVATED_PIKE_PUSH_UP ||
            drillType == com.inversioncoach.app.model.DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP ||
            drillType == com.inversioncoach.app.model.DrillType.PUSH_UP ||
            drillType == com.inversioncoach.app.model.DrillType.SIT_UP
        ) return null
        if (frameScores.isEmpty()) return null
        val start = frameScores.first().first
        val end = frameScores.last().first
        val green = frameScores.filter { it.second >= 75 }
        val greenPct = (green.size.toFloat() / frameScores.size * 100).toInt()
        val best3s = bestWindowScore(3000)
        val longest = longestGreenStreakMs()
        return HoldAnalysis(start, end, end - start, best3s, longest, greenPct)
    }

    private fun bestWindowScore(windowMs: Long): Int {
        if (frameScores.isEmpty()) return 0
        var best = 0
        for (i in frameScores.indices) {
            val start = frameScores[i].first
            val scores = frameScores.drop(i).takeWhile { it.first - start <= windowMs }.map { it.second }
            if (scores.isNotEmpty()) best = maxOf(best, scores.average().toInt())
        }
        return best
    }

    private fun longestGreenStreakMs(): Long {
        var best = 0L
        var start: Long? = null
        frameScores.forEach { (ts, score) ->
            if (score >= 75) {
                if (start == null) start = ts
                best = maxOf(best, ts - (start ?: ts))
            } else start = null
        }
        return best
    }

    private fun averageSubScores(): Map<String, Int> {
        if (debugFrames.isEmpty()) return emptyMap()
        // Keep stable keys from latest derived score labels by drill, proxy through recomputation at session end.
        return DrillMetricsCalculator().computeSubscores(drillType, latestDerived(), profile)
    }

    private fun latestDerived(): DerivedMetrics {
        val latest = debugFrames.lastOrNull() ?: return DerivedMetrics(0, emptyMap(), emptyMap(), emptyMap(), 1f, 0, 0, 0, 0, 0, emptyMap(), emptyMap(), ConfidenceLevel.LOW, 0f)
        return DerivedMetrics(
            latest.timestampMs,
            latest.derivedAngles,
            emptyMap(),
            latest.normalizedOffsets,
            latest.normalizedOffsets.values.map { abs(it) }.average().toFloat(),
            50,
            50,
            50,
            50,
            50,
            mapOf("descent_sec" to 1.1f),
            mapOf("path_variance" to 0.2f, "depth_norm" to 0.6f),
            ConfidenceLevel.MEDIUM,
            0.7f,
        )
    }

    private fun resetRepBuffers() {
        currentRepStart = null
        topTs = 0
        bottomTs = 0
        worstTs = 0
        worstScore = 101
        headPath.clear()
        shoulderPath.clear()
        hipPath.clear()
        descentStartTs = null
    }

    private fun alignmentLossPercent(start: Long, end: Long): Int? {
        if (drillType != com.inversioncoach.app.model.DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP) return null
        val repFrames = frameScores.filter { it.first in start..end }
        if (repFrames.isEmpty()) return null
        val idx = repFrames.indexOfFirst { it.second < 60 }
        if (idx < 0) return 100
        return ((idx.toFloat() / repFrames.size) * 100).toInt()
    }

    protected open fun wallX(): Float = 0.95f
    private fun Long?.orEmpty(fallback: Long): Long = this ?: fallback
    private fun depthNorm(): Float {
        if (headPath.isEmpty()) return 0.5f
        val ys = headPath.map { it.second }
        val range = ys.maxOrNull()!! - ys.minOrNull()!!
        return (range * 2f).coerceIn(0f, 1f)
    }
    private fun hipDriftNorm(): Float {
        if (hipPath.size < 2) return 0f
        return abs(hipPath.last().first - hipPath.first().first)
    }
    private fun elbowFlareProxy(j: Map<String, com.inversioncoach.app.model.JointPoint>, side: String): Float {
        val elbow = j["${side}_elbow"] ?: return 0f
        val shoulder = j["${side}_shoulder"] ?: return 0f
        val wrist = j["${side}_wrist"] ?: return 0f
        val upperArmDx = abs(elbow.x - shoulder.x)
        val forearmDx = abs(wrist.x - elbow.x)
        return (upperArmDx + forearmDx) / 2f
    }
}
