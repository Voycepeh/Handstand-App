package com.inversioncoach.app.calibration.storage

import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.calibration.HoldTemplate
import com.inversioncoach.app.calibration.RepTemplate
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.model.DrillType
import org.json.JSONObject

class DrillMovementProfileJson {
    fun encode(profile: DrillMovementProfile): String = JSONObject().apply {
        put("drillType", profile.drillType.name)
        put("profileVersion", profile.profileVersion)
        put("createdAtMs", profile.createdAtMs)
        put("updatedAtMs", profile.updatedAtMs)
        put("userBodyProfile", profile.userBodyProfile?.let { bodyProfileJson(it) })
        put("holdTemplate", profile.holdTemplate?.let { holdTemplateJson(it) })
        put("repTemplate", profile.repTemplate?.let { repTemplateJson(it) })
    }.toString()

    fun decode(raw: String): DrillMovementProfile {
        val obj = JSONObject(raw)
        val drillType = DrillType.valueOf(obj.getString("drillType"))
        return DrillMovementProfile(
            drillType = drillType,
            profileVersion = obj.optInt("profileVersion", 1),
            userBodyProfile = obj.optJSONObject("userBodyProfile")?.let(::decodeUserBodyProfile),
            holdTemplate = obj.optJSONObject("holdTemplate")?.let { decodeHoldTemplate(it, drillType) },
            repTemplate = obj.optJSONObject("repTemplate")?.let { decodeRepTemplate(it, drillType) },
            createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
            updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis()),
        )
    }

    private fun bodyProfileJson(body: UserBodyProfile): JSONObject = JSONObject().apply {
        put("version", body.version)
        put("shoulderWidthNormalized", body.shoulderWidthNormalized)
        put("hipWidthNormalized", body.hipWidthNormalized)
        put("torsoLengthNormalized", body.torsoLengthNormalized)
        put("upperArmLengthNormalized", body.upperArmLengthNormalized)
        put("forearmLengthNormalized", body.forearmLengthNormalized)
        put("femurLengthNormalized", body.femurLengthNormalized)
        put("shinLengthNormalized", body.shinLengthNormalized)
        put("leftRightConsistency", body.leftRightConsistency)
    }

    private fun holdTemplateJson(hold: HoldTemplate): JSONObject = JSONObject().apply {
        put("drillType", hold.drillType.name)
        put("profileVersion", hold.profileVersion)
        put("targetDurationMs", hold.targetDurationMs)
        put("alignmentTarget", hold.alignmentTarget)
        put("stabilityTarget", hold.stabilityTarget)
    }

    private fun repTemplateJson(rep: RepTemplate): JSONObject = JSONObject().apply {
        put("drillType", rep.drillType.name)
        put("profileVersion", rep.profileVersion)
        put("targetRepCount", rep.targetRepCount)
        put("depthTarget", rep.depthTarget)
        put("tempoSeconds", rep.tempoSeconds)
    }

    private fun decodeUserBodyProfile(obj: JSONObject): UserBodyProfile = UserBodyProfile(
        version = obj.optInt("version", 1),
        shoulderWidthNormalized = obj.optDouble("shoulderWidthNormalized", 0.0).toFloat(),
        hipWidthNormalized = obj.optDouble("hipWidthNormalized", 0.0).toFloat(),
        torsoLengthNormalized = obj.optDouble("torsoLengthNormalized", 0.0).toFloat(),
        upperArmLengthNormalized = obj.optDouble("upperArmLengthNormalized", 0.0).toFloat(),
        forearmLengthNormalized = obj.optDouble("forearmLengthNormalized", 0.0).toFloat(),
        femurLengthNormalized = obj.optDouble("femurLengthNormalized", 0.0).toFloat(),
        shinLengthNormalized = obj.optDouble("shinLengthNormalized", 0.0).toFloat(),
        leftRightConsistency = obj.optDouble("leftRightConsistency", 0.0).toFloat(),
    )

    private fun decodeHoldTemplate(obj: JSONObject, fallbackDrillType: DrillType): HoldTemplate = HoldTemplate(
        drillType = obj.optString("drillType").takeIf { it.isNotBlank() }?.let(DrillType::valueOf) ?: fallbackDrillType,
        profileVersion = obj.optInt("profileVersion", 1),
        targetDurationMs = obj.optLong("targetDurationMs", 3000L),
        alignmentTarget = obj.optInt("alignmentTarget", 80),
        stabilityTarget = obj.optInt("stabilityTarget", 80),
    )

    private fun decodeRepTemplate(obj: JSONObject, fallbackDrillType: DrillType): RepTemplate = RepTemplate(
        drillType = obj.optString("drillType").takeIf { it.isNotBlank() }?.let(DrillType::valueOf) ?: fallbackDrillType,
        profileVersion = obj.optInt("profileVersion", 1),
        targetRepCount = if (obj.has("targetRepCount") && !obj.isNull("targetRepCount")) obj.optInt("targetRepCount") else null,
        depthTarget = if (obj.has("depthTarget") && !obj.isNull("depthTarget")) obj.optDouble("depthTarget").toFloat() else null,
        tempoSeconds = if (obj.has("tempoSeconds") && !obj.isNull("tempoSeconds")) obj.optDouble("tempoSeconds").toFloat() else null,
    )
}
