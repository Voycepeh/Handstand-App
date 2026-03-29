package com.inversioncoach.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.ui.drilldetail.DrillDetailScreen
import com.inversioncoach.app.ui.drillstudio.DrillStudioInitRequest
import com.inversioncoach.app.ui.drillstudio.DrillStudioScreen
import com.inversioncoach.app.ui.history.HistoryScreen
import com.inversioncoach.app.ui.home.HomeScreen
import com.inversioncoach.app.ui.live.LiveCoachingScreen
import com.inversioncoach.app.ui.progress.ProgressScreen
import com.inversioncoach.app.ui.reference.ReferenceTemplatePickerScreen
import com.inversioncoach.app.ui.reference.ReferenceTrainingScreen
import com.inversioncoach.app.ui.calibration.CalibrationScreen
import com.inversioncoach.app.ui.results.ResultsScreen
import com.inversioncoach.app.ui.results.SessionTooShortScreen
import com.inversioncoach.app.ui.settings.DeveloperTuningScreen
import com.inversioncoach.app.ui.drills.DrillStudioScreen
import com.inversioncoach.app.ui.settings.SettingsScreen
import com.inversioncoach.app.ui.startdrill.StartDrillScreen
import com.inversioncoach.app.ui.upload.UploadVideoScreen

private fun parseDrillTypeOrDefault(rawValue: String?, fallback: DrillType): DrillType =
    rawValue?.let(DrillType::fromStoredName) ?: fallback

sealed class Route(val value: String) {
    data object Home : Route("home")
    data object Start : Route("start")
    data object DrillDetail : Route("drillDetail/{drill}") {
        fun create(drillType: DrillType): String = "drillDetail/${drillType.name}"
    }
    data object Live : Route("live/{drill}/{voice}/{record}/{skeleton}/{idealLine}/{zoomOutCamera}/{drillCameraSide}") {
        fun create(drillType: DrillType, options: LiveSessionOptions): String =
            "live/${drillType.name}/${options.voiceEnabled}/${options.recordingEnabled}/${options.showSkeletonOverlay}/${options.showIdealLine}/${options.zoomOutCamera}/${options.drillCameraSide.name}"
    }
    data object Results : Route("results/{sessionId}") {
        fun create(sessionId: Long) = "results/$sessionId"
    }
    data object SessionTooShort : Route("session-too-short/{elapsedMs}/{thresholdSeconds}") {
        fun create(elapsedMs: Long, thresholdSeconds: Int) = "session-too-short/$elapsedMs/$thresholdSeconds"
    }
    data object History : Route("history")
    data object Progress : Route("progress")
    data object DrillStudio : Route("drill-studio?mode={mode}&drillId={drillId}") {
        fun createNew(): String = "drill-studio?mode=create&drillId="
        fun createForDrill(drillId: String): String = "drill-studio?mode=drill&drillId=${Uri.encode(drillId)}"
    }
    data object Settings : Route("settings")
    data object DevTuning : Route("settings/dev-tuning")
    data object DrillStudio : Route("drill-studio?drillId={drillId}") {
        fun create(drillId: String?): String = if (drillId == null) "drill-studio" else "drill-studio?drillId=$drillId"
    }
    data object UploadVideo : Route("upload-video")
    data object Calibration : Route("calibration")
}

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Route.Home.value, modifier = modifier) {
        composable(Route.Home.value) {
            HomeScreen(
                onStart = { navController.navigate(Route.Start.value) },
                onStartFreestyle = {
                    navController.navigate(
                        Route.Live.create(
                            DrillType.FREESTYLE,
                            LiveSessionOptions.freestyleDefaults(),
                        ),
                    )
                },
                onHistory = { navController.navigate(Route.History.value) },
                onProgress = { navController.navigate(Route.Progress.value) },
                onSettings = { navController.navigate(Route.Settings.value) },
                onUploadVideo = { navController.navigate(Route.UploadVideo.value) },
                onCalibration = { navController.navigate(Route.Calibration.value) },
            )
        }
        composable(Route.Start.value) {
            StartDrillScreen(
                onBack = { navController.popBackStack() },
                onStart = { drillType, options -> navController.navigate(Route.Live.create(drillType, options)) },
                onCreateDrill = { navController.navigate(Route.DrillStudio.createNew()) },
                onEditDrill = { drillType -> navController.navigate(Route.DrillStudio.createForDrill(drillType.name)) },
            )
        }
        composable(Route.DrillDetail.value, arguments = listOf(navArgument("drill") { type = NavType.StringType })) { backStack ->
            val drill = parseDrillTypeOrDefault(
                rawValue = backStack.arguments?.getString("drill"),
                fallback = DrillType.STANDING_POSTURE_HOLD,
            )
            DrillDetailScreen(
                drillType = drill,
                onBack = { navController.popBackStack() },
                onEditDrill = { selected -> navController.navigate(Route.DrillStudio.createForDrill(selected.name)) },
            )
        }
        composable(
            Route.Live.value,
            arguments = listOf(
                navArgument("drill") { type = NavType.StringType },
                navArgument("voice") { type = NavType.BoolType },
                navArgument("record") { type = NavType.BoolType },
                navArgument("skeleton") { type = NavType.BoolType },
                navArgument("idealLine") { type = NavType.BoolType },
                navArgument("zoomOutCamera") { type = NavType.BoolType },
                navArgument("drillCameraSide") { type = NavType.StringType },
            ),
        ) { backStack ->
            val args = backStack.arguments
            val drill = parseDrillTypeOrDefault(
                rawValue = args?.getString("drill"),
                fallback = DrillType.WALL_HANDSTAND,
            )
            val options = LiveSessionOptions(
                voiceEnabled = args?.getBoolean("voice") ?: true,
                recordingEnabled = args?.getBoolean("record") ?: true,
                showSkeletonOverlay = args?.getBoolean("skeleton") ?: true,
                showIdealLine = args?.getBoolean("idealLine") ?: true,
                zoomOutCamera = args?.getBoolean("zoomOutCamera") ?: true,
                drillCameraSide = DrillCameraSide.entries.firstOrNull { it.name == args?.getString("drillCameraSide") } ?: DrillCameraSide.LEFT,
            )
            LiveCoachingScreen(
                drillType = drill,
                options = options,
                onStop = { result ->
                    if (result.wasDiscardedForShortDuration) {
                        navController.navigate(Route.SessionTooShort.create(result.elapsedSessionMs, result.validationThresholdSeconds))
                    } else {
                        navController.navigate(Route.Results.create(result.sessionId))
                    }
                },
            )
        }
        composable(Route.Results.value, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: 0L
            ResultsScreen(
                sessionId = sessionId,
                onDone = { navController.popBackStack(Route.Home.value, false) },
            )
        }
        composable(Route.History.value) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
            )
        }
        composable(Route.Progress.value) {
            ProgressScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
            )
        }
        composable(
            Route.DrillStudio.value,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "drill"
                },
                navArgument("drillId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStack ->
            val mode = backStack.arguments?.getString("mode") ?: "drill"
            val drillId = backStack.arguments?.getString("drillId")?.takeIf { it.isNotBlank() }
            DrillStudioScreen(
                onBack = { navController.popBackStack() },
                initRequest = DrillStudioInitRequest(
                    mode = mode,
                    drillId = drillId,
                ),
            )
        }
        composable(Route.SessionTooShort.value, arguments = listOf(
            navArgument("elapsedMs") { type = NavType.LongType },
            navArgument("thresholdSeconds") { type = NavType.IntType },
        )) { backStack ->
            val elapsedMs = backStack.arguments?.getLong("elapsedMs") ?: 0L
            val thresholdSeconds = backStack.arguments?.getInt("thresholdSeconds") ?: 0
            SessionTooShortScreen(
                elapsedSessionMs = elapsedMs,
                validationThresholdSeconds = thresholdSeconds,
                onBackToHome = { navController.popBackStack(Route.Home.value, false) },
            )
        }
        composable(Route.Settings.value) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onDeveloperTuning = { navController.navigate(Route.DevTuning.value) },
                onNavigateHome = { navController.popBackStack(Route.Home.value, false) },
                onDrillStudio = { navController.navigate(Route.DrillStudio.create(null)) },
            )
        }
        composable(Route.Calibration.value) {
            CalibrationScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.DevTuning.value) { DeveloperTuningScreen(onBack = { navController.popBackStack() }) }
        composable(
            Route.DrillStudio.value,
            arguments = listOf(navArgument("drillId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            DrillStudioScreen(
                onBack = { navController.popBackStack() },
                initialDrillId = it.arguments?.getString("drillId"),
            )
        }
        composable(Route.UploadVideo.value) {
            UploadVideoScreen(
                onBack = { navController.popBackStack() },
                onOpenResults = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
            )
        }
        composable(
            Route.UploadVideoForDrill.value,
            arguments = listOf(
                navArgument("drillId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("referenceTemplateId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("isReference") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            val drillId = it.arguments?.getString("drillId")
            val templateId = it.arguments?.getString("referenceTemplateId")
            val isReference = it.arguments?.getBoolean("isReference") ?: false
            UploadVideoScreen(
                onBack = { navController.popBackStack() },
                onOpenResults = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
                selectedReferenceTemplateId = templateId,
                selectedDrillId = drillId,
                isReferenceUpload = isReference,
            )
        }
        composable(Route.ReferenceTemplatePicker.value) {
            ReferenceTemplatePickerScreen(
                onBack = { navController.popBackStack() },
                onSelectDrill = { drillId ->
                    navController.navigate(Route.ReferenceTraining.create(drillId))
                },
            )
        }
        composable(Route.ManageDrills.value) {
            ManageDrillsScreen(
                onBack = { navController.popBackStack() },
                onCreateDrill = { navController.navigate(Route.EditDrill.create(null)) },
                onEditDrill = { drillId -> navController.navigate(Route.EditDrill.create(drillId)) },
                onOpenDrill = { drillId -> navController.navigate(Route.DrillPackageDetail.create(drillId)) },
                onOpenInStudio = { drillId -> navController.navigate(Route.DrillStudio.create(drillId)) },
            )
        }
        composable(
            Route.EditDrill.value,
            arguments = listOf(navArgument("drillId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            EditDrillScreen(
                drillId = it.arguments?.getString("drillId"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.DrillPackageDetail.value, arguments = listOf(navArgument("drillId") { type = NavType.StringType })) {
            val drillId = it.arguments?.getString("drillId").orEmpty()
            DrillPackageDetailScreen(
                drillId = drillId,
                onBack = { navController.popBackStack() },
                onUploadReference = { id -> navController.navigate(Route.UploadVideoForDrill.create(id, null, true)) },
                onCompareAttempt = { id -> navController.navigate(Route.ReferenceTraining.create(id)) },
                onEditCalibration = { _ -> navController.navigate(Route.Settings.value) },
            )
        }
        composable(Route.ReferenceTraining.value, arguments = listOf(navArgument("drillId") { type = NavType.StringType })) {
            val drillId = it.arguments?.getString("drillId").orEmpty()
            ReferenceTrainingScreen(
                drillId = drillId,
                onBack = { navController.popBackStack() },
                onUploadReference = { id -> navController.navigate(Route.UploadVideoForDrill.create(id, null, true)) },
                onUploadAttempt = { id, templateId -> navController.navigate(Route.UploadVideoForDrill.create(id, templateId, false)) },
            )
        }
    }
}
