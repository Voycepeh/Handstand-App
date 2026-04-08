package com.inversioncoach.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.canOpenResultsRoute
import com.inversioncoach.app.ui.drills.ManageDrillsScreen
import com.inversioncoach.app.ui.drillstudio.DrillStudioInitRequest
import com.inversioncoach.app.ui.drillstudio.DrillStudioScreen
import com.inversioncoach.app.ui.history.HistoryScreen
import com.inversioncoach.app.ui.home.HomeScreen
import com.inversioncoach.app.ui.live.LiveCoachingScreen
import com.inversioncoach.app.ui.historyoverview.HistoryOverviewScreen
import com.inversioncoach.app.ui.reference.DrillWorkspaceScreen
import com.inversioncoach.app.ui.results.ResultsScreen
import com.inversioncoach.app.ui.results.SessionTooShortScreen
import com.inversioncoach.app.ui.settings.DeveloperTuningScreen
import com.inversioncoach.app.ui.settings.SettingsScreen
import com.inversioncoach.app.ui.startdrill.StartDrillDestination
import com.inversioncoach.app.ui.startdrill.StartDrillScreen
import com.inversioncoach.app.ui.upload.UploadVideoScreen
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class Route(val value: String) {
    data object Home : Route("home")
    data object Start : Route("start?destination={destination}") {
        fun create(destination: StartDrillDestination = StartDrillDestination.LIVE): String =
            "start?destination=${destination.name.lowercase()}"
    }
    data object Live : Route("live/{drill}/{voice}/{record}/{skeleton}/{idealLine}/{showCenterOfGravity}/{zoomOutCamera}/{drillCameraSide}/{effectiveView}?selectedDrillId={selectedDrillId}") {
        fun create(drillType: DrillType, options: LiveSessionOptions): String =
            LiveRouteCodec.create(drillType, options)
    }
    data object Results : Route("results/{sessionId}") { fun create(sessionId: Long) = "results/$sessionId" }
    data object SessionTooShort : Route("session-too-short/{elapsedMs}/{thresholdSeconds}") {
        fun create(elapsedMs: Long, thresholdSeconds: Int) = "session-too-short/$elapsedMs/$thresholdSeconds"
    }
    data object SessionHistory : Route(SessionHistoryRoutes.routePattern) {
        fun create(drillId: String? = null, mode: SessionHistoryMode = SessionHistoryMode.HISTORY): String =
            SessionHistoryRoutes.create(drillId = drillId, mode = mode)
    }
    data object HistoryOverview : Route("history")
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
    data object DrillWorkspace : Route("drill-workspace/{drillId}") { fun create(drillId: String) = "drill-workspace/${Uri.encode(drillId)}" }
    data object ManageDrills : Route("manage-drills")
}

@Composable
fun AppNavHost(modifier: Modifier = Modifier, initialSessionId: Long? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = ServiceLocator.repository(context)
    val scope = rememberCoroutineScope()
    val openResultsIfAllowed: (Long) -> Unit = { sessionId ->
        scope.launch {
            val session = repository.observeSession(sessionId).firstOrNull()
            if (session == null || session.canOpenResultsRoute()) {
                navController.navigate(Route.Results.create(sessionId))
            }
        }
    }
    LaunchedEffect(initialSessionId) {
        initialSessionId?.let(openResultsIfAllowed)
    }
    NavHost(navController = navController, startDestination = Route.Home.value, modifier = modifier) {
        composable(Route.Home.value) {
            HomeScreen(
                onStartFreestyle = { navController.navigate(Route.Live.create(DrillType.FREESTYLE, LiveSessionOptions.freestyleDefaults())) },
                onHistory = { navController.navigate(Route.HistoryOverview.value) },
                onDrills = { navController.navigate(Route.Start.create(StartDrillDestination.LIVE)) },
                onSettings = { navController.navigate(Route.Settings.value) },
            )
        }
        composable(Route.Start.value, arguments = listOf(navArgument("destination") { type = NavType.StringType; defaultValue = "live" })) {
            val destination = RouteArguments.parseStartDestination(it.arguments)
            StartDrillScreen(
                onBack = { navController.popBackStack() },
                onStart = { drillType, options -> navController.navigate(Route.Live.create(drillType, options)) },
                destination = destination,
                onOpenWorkspace = { drillId -> navController.navigate(Route.DrillWorkspace.create(drillId)) },
            )
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
                navArgument(SessionHistoryRoutes.drillIdArg) { type = NavType.StringType; defaultValue = "" },
                navArgument(SessionHistoryRoutes.modeArg) { type = NavType.StringType; defaultValue = SessionHistoryMode.HISTORY.routeValue },
            ),
        ) {
            val drillId = it.arguments?.getString(SessionHistoryRoutes.drillIdArg).orEmpty().ifBlank { null }
            val mode = SessionHistoryMode.fromRoute(it.arguments?.getString(SessionHistoryRoutes.modeArg))
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = openResultsIfAllowed,
                drillIdFilter = drillId,
                comparisonMode = mode == SessionHistoryMode.COMPARE,
            )
        }
        composable(Route.HistoryOverview.value) { HistoryOverviewScreen(onBack = { navController.popBackStack() }, onOpenSession = openResultsIfAllowed) }
        composable(Route.DrillStudio.value, arguments = listOf(
            navArgument("mode") { type = NavType.StringType; defaultValue = "drill" },
            navArgument("drillId") { type = NavType.StringType; defaultValue = "" },
            navArgument("templateId") { type = NavType.StringType; defaultValue = "" },
        )) {
            val args = RouteArguments.parseDrillStudio(it.arguments)
            DrillStudioScreen(
                onBack = { navController.popBackStack() },
                onSaveSuccess = {
                    navController.navigate(Route.ManageDrills.value) {
                        popUpTo(Route.ManageDrills.value) { inclusive = true }
                    }
                },
                initRequest = DrillStudioInitRequest(
                    mode = args.mode,
                    drillId = args.drillId,
                    templateId = args.templateId,
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
                onManageDrills = { navController.navigate(Route.ManageDrills.value) },
            )
        }
        composable(Route.DevTuning.value) { DeveloperTuningScreen(onBack = { navController.popBackStack() }) }
        composable(Route.UploadVideo.value) { UploadVideoScreen(onBack = { navController.popBackStack() }, onOpenResults = openResultsIfAllowed) }
        composable(Route.UploadVideoForDrill.value, arguments = listOf(
            navArgument("drillId") { type = NavType.StringType; defaultValue = "" },
            navArgument("referenceTemplateId") { type = NavType.StringType; defaultValue = "" },
            navArgument("isReference") { type = NavType.BoolType; defaultValue = false },
            navArgument("createNewDrillFromReference") { type = NavType.BoolType; defaultValue = false },
        )) {
            val args = RouteArguments.parseUploadVideo(it.arguments)
            UploadVideoScreen(
                onBack = { navController.popBackStack() },
                onOpenResults = openResultsIfAllowed,
                onOpenDrillStudio = { drillId, templateId ->
                    navController.navigate(
                        if (templateId.isNullOrBlank()) Route.DrillStudio.createForDrill(drillId)
                        else Route.DrillStudio.createForTemplate(drillId, templateId),
                    )
                },
                selectedDrillId = args.drillId,
                selectedReferenceTemplateId = args.templateId,
                isReferenceUpload = args.isReferenceUpload,
                createDrillFromReferenceUpload = args.createNewDrillFromReference,
            )
        }
        composable(Route.DrillWorkspace.value, arguments = listOf(navArgument("drillId") { type = NavType.StringType })) {
            val drillId = it.arguments?.getString("drillId").orEmpty()
            DrillWorkspaceScreen(
                drillId = drillId,
                onBack = { navController.popBackStack() },
                onUploadAttempt = { id -> navController.navigate(Route.UploadVideoForDrill.create(id, null, false)) },
                onCompareAttempts = { selectedDrillId ->
                    navController.navigate(Route.SessionHistory.create(selectedDrillId, mode = SessionHistoryMode.COMPARE))
                },
                onOpenSession = openResultsIfAllowed,
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
    }
}
