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
import com.inversioncoach.app.ui.calibration.CalibrationScreen
import com.inversioncoach.app.ui.results.ResultsScreen
import com.inversioncoach.app.ui.results.SessionTooShortScreen
import com.inversioncoach.app.ui.settings.DeveloperTuningScreen
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
            )
        }
        composable(Route.Calibration.value) {
            CalibrationScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.DevTuning.value) { DeveloperTuningScreen(onBack = { navController.popBackStack() }) }
        composable(Route.UploadVideo.value) {
            UploadVideoScreen(
                onBack = { navController.popBackStack() },
                onOpenResults = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
            )
        }
    }
}
