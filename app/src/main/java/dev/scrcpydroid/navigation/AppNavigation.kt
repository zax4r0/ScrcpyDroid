package dev.scrcpydroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.scrcpydroid.ui.home.HomeScreen
import dev.scrcpydroid.ui.mirror.MirrorScreen
import dev.scrcpydroid.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class MirrorRoute(val host: String, val port: Int)

@Serializable
data object SettingsRoute

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToMirror = { host, port ->
                    navController.navigate(MirrorRoute(host = host, port = port))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                }
            )
        }

        composable<MirrorRoute> {
            MirrorScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
