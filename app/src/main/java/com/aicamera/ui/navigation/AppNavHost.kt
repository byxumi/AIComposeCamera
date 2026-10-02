package com.aicamera.ui.navigation

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aicamera.core.design.CamColors
import com.aicamera.domain.model.OverlayState
import com.aicamera.ui.camera.CameraScreen
import com.aicamera.ui.editor.EditorScreen
import com.aicamera.ui.gallery.GalleryScreen
import com.aicamera.ui.profile.ProfileScreen
import com.aicamera.ui.settings.SettingsScreen

object Routes {
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    fun editor(uri: String) = "editor/$uri"
}

/** mola 主框架: 底部 3 tab = 相机 / 相册 / 我的 */
@Composable
fun AppNavHost() {
    val nav: NavHostController = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.CAMERA ||
        currentRoute == Routes.GALLERY ||
        currentRoute == Routes.PROFILE

    Scaffold(
        containerColor = CamColors.Black,
        bottomBar = {
            if (showBottomBar) {
                MolaBottomBar(
                    current = currentRoute ?: Routes.CAMERA,
                    onSelect = { route ->
                        nav.navigate(route) {
                            popUpTo(Routes.CAMERA) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.CAMERA,
            modifier = Modifier
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
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenGallery = { nav.navigate(Routes.GALLERY) },
                    onShare = { /* 预留分享 */ }
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
}

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val bottomTabs = listOf(
    BottomTab("相机", Icons.Filled.PhotoCamera, Routes.CAMERA),
    BottomTab("相册", Icons.Filled.PhotoLibrary, Routes.GALLERY),
    BottomTab("我的", Icons.Filled.Person, Routes.PROFILE)
)

/** mola 风格底部 tab: 黑金, 选中项金色胶囊 + 文字上浮 */
@Composable
private fun MolaBottomBar(
    current: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CamColors.Surface)
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        bottomTabs.forEach { tab ->
            val selected = current == tab.route
            val bg by animateColorAsState(
                targetValue = if (selected) CamColors.AccentDim else CamColors.Surface,
                label = "tabBg"
            )
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.86f,
                label = "tabScale"
            )
            val tint by animateColorAsState(
                targetValue = if (selected) CamColors.Accent else CamColors.TertiaryText,
                label = "tabText"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(tab.route) }
                    .width(72.dp)
                    .height(52.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.size(20.dp * scale)) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = tab.label,
                    color = tint,
                    fontSize = 10.sp
                )
            }
        }
    }
}