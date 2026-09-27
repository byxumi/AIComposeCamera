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
import com.aicamera.ui.theme.IosBlue
import com.aicamera.ui.theme.IosGray
import com.aicamera.ui.theme.IosSystemBackground
import com.aicamera.ui.theme.IosSystemLabel
import com.aicamera.ui.theme.IosSystemSecondaryGroupedBackground
import com.aicamera.ui.theme.IosSystemSecondaryLabel

class MainActivity : ComponentActivity() {

    // iOS 深色 ColorScheme（贴近苹果设计语言）
    private val iosDarkScheme = darkColorScheme(
        primary = IosBlue,
        onPrimary = IosSystemBackground,
        primaryContainer = IosSystemSecondaryGroupedBackground,
        secondary = IosGray,
        background = IosSystemBackground,
        surface = IosSystemSecondaryGroupedBackground,
        onBackground = IosSystemLabel,
        onSurface = IosSystemLabel,
        onSurfaceVariant = IosSystemSecondaryLabel
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = iosDarkScheme) {
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