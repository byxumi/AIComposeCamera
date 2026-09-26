package com.aicamera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicamera.composition.GuidanceType
import com.aicamera.composition.Severity
import com.aicamera.settings.GridMode
import com.aicamera.ui.theme.GuideError
import com.aicamera.ui.theme.GuideGood
import com.aicamera.ui.theme.GuideWarn
import com.aicamera.ui.theme.NeoAccent
import com.aicamera.ui.theme.NeoBackground
import com.aicamera.ui.theme.NeoBackgroundCard
import com.aicamera.ui.theme.NeoTextPrimary
import com.aicamera.ui.theme.NeoTextSecondary
import com.aicamera.viewmodel.CompositionViewModel
import kotlinx.coroutines.delay

/**
 * 主相机界面：预览 + 覆盖层 + 控制栏。
 */
@Composable
fun CameraScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompositionViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayState by viewModel.overlayState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val flashEnabled by viewModel.flashEnabled.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val bindCamera: () -> Unit = {
        viewModel.bindCamera(lifecycleOwner) { surfaceProvider ->
            // Preview 的 SurfaceProvider 由 CameraManager 建立
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) bindCamera()
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) bindCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 绑定预览 Surface
    DisposableEffect(hasPermission, previewView) {
        if (hasPermission && previewView != null) {
            viewModel.bindPreview(previewView!!.surfaceProvider, previewView)
        }
        onDispose { }
    }

    // 提示消失
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(1600)
            viewModel.onToastShown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBackground)
    ) {
        // 相机预览（带点击对焦）
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { pv ->
                        previewView = pv
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            viewModel.onTapToFocus(offset.x, offset.y)
                        }
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeoBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "需要相机权限才能使用 AI 构图拍摄",
                    color = NeoTextSecondary,
                    fontSize = 15.sp
                )
            }
        }

        // 覆盖层
        CompositionOverlay(state = overlayState, modifier = Modifier.fillMaxSize())

        // ===== 顶部栏 =====
        TopBar(
            flashEnabled = flashEnabled,
            gridMode = settings.gridMode,
            onToggleFlash = { viewModel.toggleFlash() },
            onToggleGrid = {
                val next = when (settings.gridMode) {
                    GridMode.NONE -> GridMode.THIRDS
                    GridMode.THIRDS -> GridMode.GOLDEN
                    GridMode.GOLDEN -> GridMode.NONE
                }
                viewModel.updateGridMode(next)
            },
            onOpenSettings = onOpenSettings
        )

        // ===== 底部控制栏 =====
        BottomBar(
            onShutter = { viewModel.manualShutter() },
            onSwitchCamera = { viewModel.switchCamera() },
            hasFlash = flashEnabled,
            onToggleFlash = { viewModel.toggleFlash() },
            onZoomChange = { f -> viewModel.setZoomRatio(f) }
        )

        // ===== 评分角标 =====
        if (overlayState.scoreEnabled) {
            ScoreBadge(
                score = overlayState.score,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 12.dp)
            )
        }

        // ===== 引导提示气泡 =====
        val guidance = overlayState.guidance
        if (guidance.type != GuidanceType.NONE && guidance.message.isNotEmpty()) {
            GuidanceBubble(
                message = guidance.message,
                severity = guidance.severity,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp)
            )
        }

        // ===== Toast 提示 =====
        toastMessage?.let { msg ->
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                Text(
                    msg,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    flashEnabled: Boolean,
    gridMode: GridMode,
    onToggleFlash: () -> Unit,
    onToggleGrid: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleFlash, enabled = flashEnabled) {
            Icon(
                imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "闪光灯",
                tint = if (flashEnabled) NeoAccent else NeoTextSecondary
            )
        }
        Row {
            IconButton(onClick = onToggleGrid) {
                Icon(
                    imageVector = if (gridMode != GridMode.NONE) Icons.Filled.GridOn else Icons.Filled.GridOff,
                    contentDescription = "网格",
                    tint = if (gridMode != GridMode.NONE) NeoAccent else NeoTextSecondary
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = NeoTextPrimary
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    onShutter: () -> Unit,
    onSwitchCamera: () -> Unit,
    hasFlash: Boolean,
    onToggleFlash: () -> Unit,
    onZoomChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：切换镜头
        IconButton(onClick = onSwitchCamera) {
            Icon(
                imageVector = Icons.Filled.SwitchCamera,
                contentDescription = "切换摄像头",
                tint = NeoTextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        // 中：快门
        ShutterButton(onClick = onShutter)

        // 右：闪光灯（占位保持对称）
        IconButton(onClick = onToggleFlash, enabled = hasFlash) {
            Icon(
                imageVector = if (hasFlash) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "闪光灯",
                tint = if (hasFlash) NeoTextPrimary else NeoTextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    val color = when {
        score >= 80 -> GuideGood
        score >= 60 -> GuideWarn
        else -> NeoTextSecondary
    }
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = "构图 $score",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun GuidanceBubble(
    message: String,
    severity: Severity,
    modifier: Modifier = Modifier
) {
    val color = when (severity) {
        Severity.GOOD -> GuideGood
        Severity.ERROR -> GuideError
        Severity.WARN -> GuideWarn
        Severity.INFO -> NeoTextPrimary
    }
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Text(
            text = message,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}