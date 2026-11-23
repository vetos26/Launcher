package dev.utouch.launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.utouch.launcher.ui.LauncherScreen
import dev.utouch.launcher.ui.theme.UtouchTheme

/**
 * The HOME activity. It is deliberately a single Compose screen: everything (drawer, gestures,
 * sheets) is drawn in one window, like the Unity 8 shell.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            UtouchTheme {
                LauncherScreen()
            }
        }
    }
}
