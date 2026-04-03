package com.inversioncoach.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.ui.calibration.CalibrationScreen
import com.inversioncoach.app.ui.drilldetail.DrillDetailScreen
import com.inversioncoach.app.ui.drills.ManageDrillsScreen
import com.inversioncoach.app.ui.drillstudio.DrillStudioInitRequest
import com.inversioncoach.app.ui.drillstudio.DrillStudioScreen
import com.inversioncoach.app.ui.history.HistoryScreen
import com.inversioncoach.app.ui.home.HomeScreen
import com.inversioncoach.app.ui.live.LiveCoachingScreen
import com.inversioncoach.app.ui.progress.HomeHistoryScreen
import com.inversioncoach.app.ui.reference.DrillWorkspaceScreen
import com.inversioncoach.app.ui.results.ResultsScreen
import com.inversioncoach.app.ui.results.SessionTooShortScreen
import com.inversioncoach.app.ui.settings.DeveloperTuningScreen
import com.inversioncoach.app.ui.settings.SettingsScreen
import com.inversioncoach.app.ui.startdrill.StartDrillDestination
import com.inversioncoach.app.ui.startdrill.StartDrillScreen
import com.inversioncoach.app.ui.upload.UploadVideoScreen

private fun parseDrillTypeOrDefault(rawValue: String?, fallback: DrillType): DrillType =
    rawValue?.let(DrillType::fromStoredName) ?: fallback

sealed class Route(val value: String) {
    data object Home : Route("home")
    data object Start : Route("start?destination={destination}") {
        fun create(destination: StartDrillDestination = StartDrillDestination.LIVE): String =
            "start?destination=${destination.name.lowercase()}"
    }
    data object DrillDetail : Route("drillDetail/{drill}") {
        fun create(drillType: DrillType): String = "drillDetail/${drillType.name}"
    }
    data object Live : Route("live/{drill}/{voice}/{record}/{skeleton}/{idealLine}/{showCenterOfGravity}/{zoomOutCamera}/{drillCameraSide}/{effectiveView}?selectedDrillId={selectedDrillId}") {
        fun create(drillType: DrillType, options: LiveSessionOptions): String =
            LiveRouteCodec.create(drillType, options)
    }
    data object Results : Route("results/{sessionId}") { fun create(sessionId: Long) = "results/$sessionId" }
    data object SessionTooShort : Route("session-too-short/{elapsedMs}/{thresholdSeconds}") {
        fun create(elapsedMs: Long, thresholdSeconds: Int) = "session-too-short/$elapsedMs/$thresholdSeconds"
    }
    data object SessionHistory : Route("session-history?drillId={drillId}&mode={mode}") {
        fun create(drillId: String? = null, mode: String = "history"): String =
            "session-history?drillId=${Uri.encode(drillId ?: "")}&mode=${Uri.encode(mode)}"
    }
    data object History : Route("history")
    data object DrillStudio : Route("drill-studio?mode={mode}&drillId={drillId}&templateId={templateId}") {
        fun createNew(): String = "drill-studio?mode=create&drillId=&templateId="
        fun createForDrill(drillId: String): String = "drill-studio?mode=drill&drillId=${Uri.encode(drillId)}&templateId="
        fun createForTemplate(drillId: String, templateId: String): String =
            "drill-studio?mode=drill&drillId=${Uri.encode(drillId)}&templateId=${Uri.encode(templateId)}"
    }
    data object Settings : Route("settings")
    data object DevTuning : Route("settings/dev-tuning")
    data object UploadVideo : Route("upload-video")
    data object UploadVideoForDrill : Route("upload-video?drillId={drillId}&referenceTemplateId={referenceTemplateId}&isReference={isReference}&createNewDrillFromReference={createNewDrillFromReference}") {
        fun create(drillId: String?, templateId: String?, isReference: Boolean, createNewDrillFromReference: Boolean = false): String =
            "upload-video?drillId=${Uri.encode(drillId ?: "")}&referenceTemplateId=${Uri.encode(templateId ?: "")}&isReference=$isReference&createNewDrillFromReference=$createNewDrillFromReference"
    }
    data object Calibration : Route("calibration")
    data object DrillWorkspace : Route("drill-workspace/{drillId}") { fun create(drillId: String) = "drill-workspace/${Uri.encode(drillId)}" }
    data object ManageDrills : Route("manage-drills")
    data object DrillPackageDetail : Route("drill-package-detail/{drillId}") { fun create(drillId: String) = "drill-package-detail/${Uri.encode(drillId)}" }
}

@Composable
fun AppNavHost(modifier: Modifier = Modifier, initialSessionId: Long? = null) {
    val navController = rememberNavController()
    LaunchedEffect(initialSessionId) {
        initialSessionId?.let { navController.navigate(Route.Results.create(it)) }
    }
    NavHost(navController = navController, startDestination = Route.Home.value, modifier = modifier) {
        composable(Route.Home.value) {
            HomeScreen(
                onStart = { navController.navigate(Route.Start.create(StartDrillDestination.LIVE)) },
                onStartFreestyle = { navController.navigate(Route.Live.create(DrillType.FREESTYLE, LiveSessionOptions.freestyleDefaults())) },
                onHistory = { navController.navigate(Route.History.value) },
                onDrills = { navController.navigate(Route.Start.create(StartDrillDestination.WORKSPACE)) },
                onSettings = { navController.navigate(Route.Settings.value) },
                onUploadVideo = { navController.navigate(Route.UploadVideo.value) },
                onCalibration = { navController.navigate(Route.Calibration.value) },
            )
        }
        composable(Route.Start.value, arguments = listOf(navArgument("destination") { type = NavType.StringType; defaultValue = "live" })) {
            val destination = if (it.arguments?.getString("destination") == "workspace") StartDrillDestination.WORKSPACE else StartDrillDestination.LIVE
            StartDrillScreen(
                onBack = { navController.popBackStack() },
                onStart = { drillType, options -> navController.navigate(Route.Live.create(drillType, options)) },
                destination = destination,
                onOpenWorkspace = { drillId -> navController.navigate(Route.DrillWorkspace.create(drillId)) },
            )
        }
        composable(Route.DrillDetail.value, arguments = listOf(navArgument("drill") { type = NavType.StringType })) {
            val drill = parseDrillTypeOrDefault(it.arguments?.getString("drill"), DrillType.STANDING_POSTURE_HOLD)
            DrillDetailScreen(drillType = drill, onBack = { navController.popBackStack() }, onEditDrill = { selected -> navController.navigate(Route.DrillStudio.createForDrill(selected.name)) })
        }
        composable(Route.Live.value, arguments = listOf(
            navArgument("drill") { type = NavType.StringType },
            navArgument("voice") { type = NavType.BoolType },
            navArgument("record") { type = NavType.BoolType },
            navArgument("skeleton") { type = NavType.BoolType },
            navArgument("idealLine") { type = NavType.BoolType },
            navArgument("showCenterOfGravity") { type = NavType.BoolType },
            navArgument("zoomOutCamera") { type = NavType.BoolType },
            navArgument("drillCameraSide") { type = NavType.StringType },
            navArgument("effectiveView") { type = NavType.StringType },
            navArgument("selectedDrillId") { type = NavType.StringType; defaultValue = "" },
        )) { backStack ->
            val liveRouteArgs = LiveRouteCodec.parse(backStack.arguments)
            LiveCoachingScreen(drillType = liveRouteArgs.drillType, options = liveRouteArgs.options, onStop = { result ->
                if (result.wasDiscardedForShortDuration) navController.navigate(Route.SessionTooShort.create(result.elapsedSessionMs, result.validationThresholdSeconds))
                else navController.navigate(Route.Results.create(result.sessionId))
            })
        }
        composable(Route.Results.value, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) {
            ResultsScreen(sessionId = it.arguments?.getLong("sessionId") ?: 0L, onDone = { navController.popBackStack(Route.Home.value, false) })
        }
        composable(
            Route.SessionHistory.value,
            arguments = listOf(
                navArgument("drillId") { type = NavType.StringType; defaultValue = "" },
                navArgument("mode") { type = NavType.StringType; defaultValue = "history" },
            ),
        ) {
            val drillId = it.arguments?.getString("drillId").orEmpty().ifBlank { null }
            val mode = it.arguments?.getString("mode").orEmpty()
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
                drillIdFilter = drillId,
                comparisonMode = mode == "compare",
            )
        }
        composable(Route.History.value) { HomeHistoryScreen(onBack = { navController.popBackStack() }, onOpenSession = { sessionId -> navController.navigate(Route.Results.create(sessionId)) }) }
        composable(Route.DrillStudio.value, arguments = listOf(
            navArgument("mode") { type = NavType.StringType; defaultValue = "drill" },
            navArgument("drillId") { type = NavType.StringType; defaultValue = "" },
            navArgument("templateId") { type = NavType.StringType; defaultValue = "" },
        )) {
            DrillStudioScreen(
                onBack = { navController.popBackStack() },
                onSaveSuccess = {
                    navController.navigate(Route.ManageDrills.value) {
                        popUpTo(Route.ManageDrills.value) { inclusive = true }
                    }
                },
                initRequest = DrillStudioInitRequest(
                    mode = it.arguments?.getString("mode") ?: "drill",
                    drillId = it.arguments?.getString("drillId")?.takeIf { id -> id.isNotBlank() },
                    templateId = it.arguments?.getString("templateId")?.takeIf { id -> id.isNotBlank() },
                ),
            )
        }
        composable(Route.SessionTooShort.value, arguments = listOf(navArgument("elapsedMs") { type = NavType.LongType }, navArgument("thresholdSeconds") { type = NavType.IntType })) {
            SessionTooShortScreen(elapsedSessionMs = it.arguments?.getLong("elapsedMs") ?: 0L, validationThresholdSeconds = it.arguments?.getInt("thresholdSeconds") ?: 0, onBackToHome = { navController.popBackStack(Route.Home.value, false) })
        }
        composable(Route.Settings.value) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onDeveloperTuning = { navController.navigate(Route.DevTuning.value) },
                onNavigateHome = { navController.popBackStack(Route.Home.value, false) },
                onDrillStudio = { navController.navigate(Route.DrillStudio.createNew()) },
            )
        }
        composable(Route.Calibration.value) { CalibrationScreen(onBack = { navController.popBackStack() }) }
        composable(Route.DevTuning.value) { DeveloperTuningScreen(onBack = { navController.popBackStack() }) }
        composable(Route.UploadVideo.value) { UploadVideoScreen(onBack = { navController.popBackStack() }, onOpenResults = { sessionId -> navController.navigate(Route.Results.create(sessionId)) }) }
        composable(Route.UploadVideoForDrill.value, arguments = listOf(
            navArgument("drillId") { type = NavType.StringType; defaultValue = "" },
            navArgument("referenceTemplateId") { type = NavType.StringType; defaultValue = "" },
            navArgument("isReference") { type = NavType.BoolType; defaultValue = false },
            navArgument("createNewDrillFromReference") { type = NavType.BoolType; defaultValue = false },
        )) {
            val drillId = it.arguments?.getString("drillId").orEmpty().ifBlank { null }
            val templateId = it.arguments?.getString("referenceTemplateId").orEmpty().ifBlank { null }
            val isReference = it.arguments?.getBoolean("isReference") ?: false
            val createNewDrillFromReference = it.arguments?.getBoolean("createNewDrillFromReference") ?: false
            UploadVideoScreen(
                onBack = { navController.popBackStack() },
                onOpenResults = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
                onOpenDrillStudio = { drillId, templateId ->
                    navController.navigate(
                        if (templateId.isNullOrBlank()) Route.DrillStudio.createForDrill(drillId)
                        else Route.DrillStudio.createForTemplate(drillId, templateId),
                    )
                },
                selectedDrillId = drillId,
                selectedReferenceTemplateId = templateId,
                isReferenceUpload = isReference,
                createDrillFromReferenceUpload = createNewDrillFromReference,
            )
        }
        composable(Route.DrillWorkspace.value, arguments = listOf(navArgument("drillId") { type = NavType.StringType })) {
            val drillId = it.arguments?.getString("drillId").orEmpty()
            DrillWorkspaceScreen(
                drillId = drillId,
                onBack = { navController.popBackStack() },
                onUploadAttempt = { id -> navController.navigate(Route.UploadVideoForDrill.create(id, null, false)) },
                onCompareAttempts = { selectedDrillId -> navController.navigate(Route.SessionHistory.create(selectedDrillId, mode = "compare")) },
                onOpenSession = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
                onStartLiveSession = { drillType ->
                    navController.navigate(Route.Live.create(drillType, LiveSessionOptions()))
                },
                onManageDrill = { selectedDrillId ->
                    navController.navigate(Route.DrillStudio.createForDrill(selectedDrillId))
                },
            )
        }
        composable(Route.ManageDrills.value) {
            ManageDrillsScreen(
                onBack = { navController.popBackStack() },
                onCreateDrill = { navController.navigate(Route.DrillStudio.createNew()) },
                onOpenDrill = { drillId -> navController.navigate(Route.DrillStudio.createForDrill(drillId)) },
            )
        }
        composable(Route.DrillPackageDetail.value, arguments = listOf(navArgument("drillId") { type = NavType.StringType })) {
            val drillId = it.arguments?.getString("drillId").orEmpty()
            DrillPackageDetailUnavailableScreen(
                drillId = drillId,
                onBack = { navController.popBackStack() },
                onOpenDrillWorkspace = { navController.navigate(Route.DrillWorkspace.create(drillId)) },
                onUploadAttempt = { navController.navigate(Route.UploadVideoForDrill.create(drillId, null, false)) },
            )
        }
    }
}


@Composable
private fun DrillPackageDetailUnavailableScreen(
    drillId: String,
    onBack: () -> Unit,
    onOpenDrillWorkspace: () -> Unit,
    onUploadAttempt: () -> Unit,
) {
    com.inversioncoach.app.ui.components.ScaffoldedScreen(title = "Drill Package Detail", onBack = onBack) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Drill package detail is temporarily unavailable for drill: $drillId")
            Text("TODO: Restore dedicated DrillPackageDetail screen behavior without rerouting product flow.")
            Button(onClick = onUploadAttempt, modifier = Modifier.fillMaxWidth()) { Text("Upload Attempt") }
            Button(onClick = onOpenDrillWorkspace, modifier = Modifier.fillMaxWidth()) { Text("Open Drill Workspace") }
        }
    }
}
