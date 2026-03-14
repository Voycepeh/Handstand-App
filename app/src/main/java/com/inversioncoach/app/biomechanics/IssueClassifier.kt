package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.DrillType
import kotlin.math.abs

class IssueClassifier {
    fun classify(
        drill: DrillType,
        metrics: DerivedMetrics,
        profile: DrillThresholdProfile,
        persistedFrames: Map<IssueType, Int>,
        nowMs: Long,
    ): List<IssueInstance> {
        val issues = mutableListOf<IssueInstance>()
        if (metrics.confidenceLevel == ConfidenceLevel.LOW) {
            issues += IssueInstance(IssueType.TRACKING_POOR, IssueSeverity.MAJOR, nowMs, "Low confidence: prioritize setup guidance")
            return issues
        }
        when (drill) {
            DrillType.STANDING_POSTURE_HOLD -> {
                maybeAddBanana(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddPassiveShoulder(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddSoftKnee(metrics, profile, persistedFrames, nowMs, issues)
            }
            DrillType.PUSH_UP -> {
                maybeAddDepth(metrics, persistedFrames, nowMs, issues)
                maybeAddElbowFlare(metrics, persistedFrames, nowMs, issues)
                maybeAddLockout(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddRushed(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddLineLoss(metrics, profile, persistedFrames, nowMs, issues)
            }
            DrillType.SIT_UP -> {
                maybeAddRushed(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddHipsDrifting(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddInconsistent(metrics, persistedFrames, nowMs, issues)
            }
            DrillType.CHEST_TO_WALL_HANDSTAND -> {
                maybeAddBanana(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddShoulderOpen(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddPassiveShoulder(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddSoftKnee(metrics, profile, persistedFrames, nowMs, issues)
            }
            DrillType.BACK_TO_WALL_HANDSTAND -> {
                maybeAddBanana(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddPassiveShoulder(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddHipOffStack(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddWallReliance(metrics, profile, persistedFrames, nowMs, issues)
            }
            DrillType.PIKE_PUSH_UP -> {
                maybeAddHipsLow(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddHeadForward(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddElbowFlare(metrics, persistedFrames, nowMs, issues)
                maybeAddLockout(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddRushed(metrics, profile, persistedFrames, nowMs, issues)
            }
            DrillType.ELEVATED_PIKE_PUSH_UP -> {
                maybeAddHipsDrifting(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddDepth(metrics, persistedFrames, nowMs, issues)
                maybeAddElbowFlare(metrics, persistedFrames, nowMs, issues)
                maybeAddInconsistent(metrics, persistedFrames, nowMs, issues)
                maybeAddLockout(metrics, profile, persistedFrames, nowMs, issues)
            }
            DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP -> {
                maybeAddRushed(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddLineLoss(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddHipsFolding(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddElbowFlare(metrics, persistedFrames, nowMs, issues)
                maybeAddShoulderCollapse(metrics, profile, persistedFrames, nowMs, issues)
                maybeAddHeadForward(metrics, profile, persistedFrames, nowMs, issues)
            }
            else -> Unit
        }
        return issues
    }

    private fun active(persisted: Map<IssueType, Int>, issue: IssueType, minFrames: Int): Boolean =
        (persisted[issue] ?: 0) >= minFrames

    private fun maybeAddBanana(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        val hip = abs(m.stackOffsetsNorm["hip_stack_offset"] ?: 0f)
        val shoulder = abs(m.stackOffsetsNorm["shoulder_stack_offset"] ?: 0f)
        val ankle = abs(m.stackOffsetsNorm["ankle_stack_offset"] ?: 0f)
        if (hip > p.archHipNormThreshold && hip > shoulder + p.archMarginNorm && hip > ankle + p.archMarginNorm && active(pf, IssueType.BANANA_ARCH, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.BANANA_ARCH, IssueSeverity.MODERATE, now, "Curved line with hip offset dominance")
        }
    }

    private fun maybeAddShoulderOpen(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if ((m.jointAngles["shoulder_angle_proxy"] ?: 170f) < 155f && active(pf, IssueType.SHOULDERS_NOT_OPEN, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.SHOULDERS_NOT_OPEN, IssueSeverity.MODERATE, now, "Shoulder angle below overhead target")
        }
    }

    private fun maybeAddPassiveShoulder(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if (m.scapularElevationProxyScore < 45 && active(pf, IssueType.PASSIVE_SHOULDERS, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.PASSIVE_SHOULDERS, IssueSeverity.MAJOR, now, "Insufficient shoulder elevation proxy")
        }
    }

    private fun maybeAddSoftKnee(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if ((m.jointAngles["knee_angle"] ?: 180f) < p.kneeWarnDeg && active(pf, IssueType.SOFT_KNEES, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.SOFT_KNEES, IssueSeverity.MINOR, now, "Knee extension below threshold")
        }
    }

    private fun maybeAddHipOffStack(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if (abs(m.stackOffsetsNorm["hip_stack_offset"] ?: 0f) > p.stackPoorNorm && active(pf, IssueType.HIPS_OFF_STACK, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.HIPS_OFF_STACK, IssueSeverity.MODERATE, now, "Hip offset from wrist line")
        }
    }

    private fun maybeAddWallReliance(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        val nearWall = (m.pathMetrics["ankle_wall_norm"] ?: 0.2f) < p.wallNearNorm
        if (nearWall && m.bodyLineDeviationNorm > p.bodyLineWarnNorm && active(pf, IssueType.WALL_RELIANCE, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.WALL_RELIANCE, IssueSeverity.MODERATE, now, "High wall support with non-stacked line")
        }
    }

    private fun maybeAddHipsLow(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if ((m.pathMetrics["hip_above_shoulder_norm"] ?: 0f) < p.hipAboveShoulderNormMin && active(pf, IssueType.HIPS_TOO_LOW, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.HIPS_TOO_LOW, IssueSeverity.MAJOR, now, "Pike setup not high enough")
        }
    }

    private fun maybeAddHeadForward(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if (abs(m.pathMetrics["head_forward_norm"] ?: 0f) > p.headForwardNormMax && active(pf, IssueType.HEAD_PATH_FORWARD, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.HEAD_PATH_FORWARD, IssueSeverity.MODERATE, now, "Head drifting forward of hand line")
        }
    }

    private fun maybeAddElbowFlare(m: DerivedMetrics, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if ((m.pathMetrics["elbow_flare_proxy"] ?: 0f) > 0.20f && active(pf, IssueType.ELBOWS_FLARING, 5)) {
            list += IssueInstance(IssueType.ELBOWS_FLARING, IssueSeverity.MODERATE, now, "Elbow track drifting wide")
        }
    }

    private fun maybeAddLockout(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if ((m.jointAngles["elbow_angle"] ?: 180f) < p.lockoutDeg && active(pf, IssueType.INCOMPLETE_LOCKOUT, 5)) {
            list += IssueInstance(IssueType.INCOMPLETE_LOCKOUT, IssueSeverity.MINOR, now, "Elbows not fully extended at top")
        }
    }

    private fun maybeAddRushed(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if ((m.tempoMetrics["descent_sec"] ?: p.descentGoodSec) < p.descentPoorSec && active(pf, IssueType.RUSHED_DESCENT, 5)) {
            list += IssueInstance(IssueType.RUSHED_DESCENT, IssueSeverity.MAJOR, now, "Descent too fast for controlled eccentric")
        }
    }

    private fun maybeAddHipsDrifting(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if (abs(m.pathMetrics["hip_drift_norm"] ?: 0f) > p.stackAcceptableNorm && active(pf, IssueType.HIPS_DRIFTING, 5)) {
            list += IssueInstance(IssueType.HIPS_DRIFTING, IssueSeverity.MODERATE, now, "Hips drifting backward")
        }
    }

    private fun maybeAddDepth(m: DerivedMetrics, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if ((m.pathMetrics["depth_norm"] ?: 0f) < 0.45f && active(pf, IssueType.INSUFFICIENT_DEPTH, 5)) {
            list += IssueInstance(IssueType.INSUFFICIENT_DEPTH, IssueSeverity.MODERATE, now, "Shallow depth")
        }
    }

    private fun maybeAddInconsistent(m: DerivedMetrics, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if ((m.pathMetrics["path_variance"] ?: 0f) > 0.25f && active(pf, IssueType.INCONSISTENT_PATH, 5)) {
            list += IssueInstance(IssueType.INCONSISTENT_PATH, IssueSeverity.MINOR, now, "Rep path inconsistency")
        }
    }

    private fun maybeAddLineLoss(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if (m.bodyLineDeviationNorm > p.bodyLinePoorNorm && active(pf, IssueType.LOSING_LINE_MIDWAY, 5)) {
            list += IssueInstance(IssueType.LOSING_LINE_MIDWAY, IssueSeverity.MAJOR, now, "Alignment dropped during descent")
        }
    }

    private fun maybeAddHipsFolding(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if (abs(m.stackOffsetsNorm["hip_stack_offset"] ?: 0f) > p.stackPoorNorm && active(pf, IssueType.HIPS_FOLDING, 5)) {
            list += IssueInstance(IssueType.HIPS_FOLDING, IssueSeverity.MODERATE, now, "Hip pike fold in negative")
        }
    }

    private fun maybeAddShoulderCollapse(m: DerivedMetrics, p: DrillThresholdProfile, pf: Map<IssueType, Int>, now: Long, list: MutableList<IssueInstance>) {
        if (m.scapularElevationProxyScore < 35 && active(pf, IssueType.SHOULDER_COLLAPSE, p.visualPersistFrames)) {
            list += IssueInstance(IssueType.SHOULDER_COLLAPSE, IssueSeverity.MAJOR, now, "Shoulders collapsing near bottom")
        }
    }
}
