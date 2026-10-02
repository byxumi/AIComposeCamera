package com.aicamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.aicamera.core.design.CamColors
import com.aicamera.ui.navigation.AppNavHost

/**
 * v4 — 单 Activity, 深色相机主题 (专业暗色语言)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
fun CameraTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = CamColors.Accent,
        onPrimary = CamColors.Black,
        background = CamColors.Black,
        onBackground = CamColors.White,
        surface = CamColors.Surface,
        onSurface = CamColors.White,
        surfaceVariant = CamColors.SurfaceElevated,
        onSurfaceVariant = CamColors.SecondaryText,
        error = CamColors.Error,
        onError = CamColors.White
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
