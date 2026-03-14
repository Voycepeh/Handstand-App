package com.inversioncoach.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.ui.history.HistoryScreen
import com.inversioncoach.app.ui.home.HomeScreen
import com.inversioncoach.app.ui.live.LiveCoachingScreen
import com.inversioncoach.app.ui.results.ResultsScreen
import com.inversioncoach.app.ui.settings.SettingsScreen
import com.inversioncoach.app.ui.startdrill.StartDrillScreen

sealed class Route(val value: String) {
    data object Home : Route("home")
    data object Start : Route("start")
    data object Live : Route("live/{drill}/{voice}/{record}/{skeleton}/{idealLine}/{zoomOutCamera}") {
        fun create(drillType: DrillType, options: LiveSessionOptions): String =
            "live/${drillType.name}/${options.voiceEnabled}/${options.recordingEnabled}/${options.showSkeletonOverlay}/${options.showIdealLine}/${options.zoomOutCamera}"
    }
    data object Results : Route("results/{sessionId}") {
        fun create(sessionId: Long) = "results/$sessionId"
    }
    data object History : Route("history")
    data object Settings : Route("settings")
}

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Route.Home.value, modifier = modifier) {
        composable(Route.Home.value) {
            HomeScreen(
                onStart = { navController.navigate(Route.Start.value) },
                onHistory = { navController.navigate(Route.History.value) },
                onSettings = { navController.navigate(Route.Settings.value) },
            )
        }
        composable(Route.Start.value) {
            StartDrillScreen(
                onBack = { navController.popBackStack() },
                onStart = { drillType, options -> navController.navigate(Route.Live.create(drillType, options)) },
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
            ),
        ) { backStack ->
            val args = backStack.arguments
            val drill = DrillType.valueOf(args?.getString("drill") ?: DrillType.CHEST_TO_WALL_HANDSTAND.name)
            val options = LiveSessionOptions(
                voiceEnabled = args?.getBoolean("voice") ?: true,
                recordingEnabled = args?.getBoolean("record") ?: true,
                showSkeletonOverlay = args?.getBoolean("skeleton") ?: true,
                showIdealLine = args?.getBoolean("idealLine") ?: true,
                zoomOutCamera = args?.getBoolean("zoomOutCamera") ?: true,
            )
            LiveCoachingScreen(
                drillType = drill,
                options = options,
                onStop = { sessionId -> navController.navigate(Route.Results.create(sessionId)) },
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
        composable(Route.Settings.value) { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
