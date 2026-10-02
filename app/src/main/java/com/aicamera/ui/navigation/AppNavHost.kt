package com.aicamera.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aicamera.ui.camera.CameraScreen
import com.aicamera.ui.editor.EditorScreen
import com.aicamera.ui.gallery.GalleryScreen
import com.aicamera.ui.settings.SettingsScreen

object Routes {
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    fun editor(uri: String) = "editor/$uri"
}

@Composable
fun AppNavHost() {
    val nav: NavHostController = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Routes.CAMERA
    ) {
        composable(Routes.CAMERA) {
            CameraScreen(
                onOpenGallery = { nav.navigate(Routes.GALLERY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.GALLERY) {
            GalleryScreen(
                onBack = { nav.popBackStack() },
                onOpenEditor = { uri ->
                    nav.navigate(Routes.editor(Uri.encode(uri)))
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.EDITOR + "/{uri}"
        ) { entry ->
            val uri = entry.arguments?.getString("uri")?.let { Uri.decode(it) } ?: ""
            EditorScreen(
                imageUri = uri,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
