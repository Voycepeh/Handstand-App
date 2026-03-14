package com.inversioncoach.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.ui.history.HistoryScreen
import com.inversioncoach.app.ui.home.HomeScreen
import com.inversioncoach.app.ui.live.LiveCoachingScreen
import com.inversioncoach.app.ui.results.ResultsScreen
import com.inversioncoach.app.ui.settings.SettingsScreen
import com.inversioncoach.app.ui.startdrill.StartDrillScreen

sealed class Route(val value: String) {
    data object Home : Route("home")
    data object Start : Route("start")
    data object Live : Route("live/{drill}") {
        fun create(drillType: DrillType) = "live/${drillType.name}"
    }
    data object Results : Route("results")
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
                onStart = { navController.navigate(Route.Live.create(it)) },
            )
        }
        composable(Route.Live.value, arguments = listOf(navArgument("drill") { type = NavType.StringType })) { backStack ->
            val drill = DrillType.valueOf(backStack.arguments?.getString("drill") ?: DrillType.CHEST_TO_WALL_HANDSTAND.name)
            LiveCoachingScreen(
                drillType = drill,
                onStop = { navController.navigate(Route.Results.value) },
            )
        }
        composable(Route.Results.value) { ResultsScreen(onDone = { navController.popBackStack(Route.Home.value, false) }) }
        composable(Route.History.value) { HistoryScreen(onBack = { navController.popBackStack() }) }
        composable(Route.Settings.value) { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
