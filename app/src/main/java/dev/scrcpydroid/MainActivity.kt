package dev.scrcpydroid

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.scrcpydroid.data.SettingsRepository
import dev.scrcpydroid.navigation.AppNavHost
import dev.scrcpydroid.ui.theme.ScrcpyDroidTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val isDynamicColor by settingsRepository.isDynamicColor().collectAsStateWithLifecycle(initialValue = true)
            val themeMode by settingsRepository.getDarkThemeMode().collectAsStateWithLifecycle(initialValue = "system")
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            ScrcpyDroidTheme(
                darkTheme = darkTheme,
                dynamicColor = isDynamicColor
            ) {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
