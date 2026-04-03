package com.inversioncoach.app.ui.navigation

import android.os.Bundle
import com.inversioncoach.app.ui.startdrill.StartDrillDestination

internal data class DrillStudioRouteArgs(
    val mode: String = "drill",
    val drillId: String? = null,
    val templateId: String? = null,
)

internal data class UploadVideoRouteArgs(
    val drillId: String? = null,
    val templateId: String? = null,
    val isReferenceUpload: Boolean = false,
    val createNewDrillFromReference: Boolean = false,
)

internal object RouteArguments {
    fun parseStartDestination(arguments: Bundle?): StartDrillDestination =
        if (arguments?.getString("destination") == "workspace") StartDrillDestination.WORKSPACE else StartDrillDestination.LIVE

    fun parseDrillStudio(arguments: Bundle?): DrillStudioRouteArgs =
        DrillStudioRouteArgs(
            mode = arguments?.getString("mode") ?: "drill",
            drillId = arguments?.getString("drillId").orEmpty().ifBlank { null },
            templateId = arguments?.getString("templateId").orEmpty().ifBlank { null },
        )

    fun parseUploadVideo(arguments: Bundle?): UploadVideoRouteArgs =
        UploadVideoRouteArgs(
            drillId = arguments?.getString("drillId").orEmpty().ifBlank { null },
            templateId = arguments?.getString("referenceTemplateId").orEmpty().ifBlank { null },
            isReferenceUpload = arguments?.getBoolean("isReference") ?: false,
            createNewDrillFromReference = arguments?.getBoolean("createNewDrillFromReference") ?: false,
        )
}
