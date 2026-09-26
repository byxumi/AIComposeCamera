package com.aicamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aicamera.ui.CameraScreen
import com.aicamera.ui.SettingsScreen
import com.aicamera.ui.theme.NeoAccent
import com.aicamera.ui.theme.NeoBackground
import com.aicamera.ui.theme.NeoBackgroundCard
import com.aicamera.ui.theme.NeoTextPrimary
import com.aicamera.ui.theme.NeoTextSecondary

class MainActivity : ComponentActivity() {

    private val darkScheme = darkColorScheme(
        primary = NeoAccent,
        onPrimary = NeoBackground,
        primaryContainer = NeoBackgroundCard,
        secondary = NeoAccent,
        background = NeoBackground,
        surface = NeoBackgroundCard,
        onBackground = NeoTextPrimary,
        onSurface = NeoTextPrimary,
        onSurfaceVariant = NeoTextSecondary
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkScheme) {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else {
        CameraScreen(
            onOpenSettings = { showSettings = true },
            modifier = Modifier.fillMaxSize()
        )
    }
}