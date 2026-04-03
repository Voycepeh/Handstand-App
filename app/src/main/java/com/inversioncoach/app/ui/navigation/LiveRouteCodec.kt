package com.inversioncoach.app.ui.navigation

import android.net.Uri
import android.os.Bundle
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.model.canonicalizeFor
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.EffectiveView

data class LiveRouteArgs(
    val drillType: DrillType,
    val options: LiveSessionOptions,
)

object LiveRouteCodec {
    fun create(drillType: DrillType, options: LiveSessionOptions): String {
        val canonical = options.canonicalizeFor(drillType)
        return "live/${drillType.name}/${canonical.voiceEnabled}/${canonical.recordingEnabled}/${canonical.showSkeletonOverlay}/${canonical.showIdealLine}/${canonical.showCenterOfGravity}/${canonical.zoomOutCamera}/${canonical.drillCameraSide.name}/${canonical.effectiveView.name}?selectedDrillId=${Uri.encode(canonical.selectedDrillId ?: "")}" // ktlint-disable max-line-length
    }

    fun parse(arguments: Bundle?): LiveRouteArgs {
        val drillType = parseDrillTypeOrDefault(arguments?.getString("drill"), DrillType.WALL_HANDSTAND)
        val rawOptions = LiveSessionOptions(
            voiceEnabled = arguments?.getBoolean("voice") ?: true,
            recordingEnabled = arguments?.getBoolean("record") ?: true,
            showSkeletonOverlay = arguments?.getBoolean("skeleton") ?: true,
            showIdealLine = arguments?.getBoolean("idealLine") ?: true,
            showCenterOfGravity = arguments?.getBoolean("showCenterOfGravity") ?: true,
            zoomOutCamera = arguments?.getBoolean("zoomOutCamera") ?: true,
            drillCameraSide = DrillCameraSide.entries.firstOrNull { it.name == arguments?.getString("drillCameraSide") } ?: DrillCameraSide.LEFT,
            effectiveView = EffectiveView.entries.firstOrNull { it.name == arguments?.getString("effectiveView") } ?: EffectiveView.FREESTYLE,
            selectedDrillId = arguments?.getString("selectedDrillId").orEmpty().ifBlank { null },
        )
        return LiveRouteArgs(drillType = drillType, options = rawOptions.canonicalizeFor(drillType))
    }
}
