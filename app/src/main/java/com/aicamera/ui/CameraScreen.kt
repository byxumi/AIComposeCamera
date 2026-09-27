package com.aicamera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicamera.composition.GuidanceType
import com.aicamera.composition.Severity
import com.aicamera.settings.GridMode
import com.aicamera.ui.theme.GuideError
import com.aicamera.ui.theme.GuideGood
import com.aicamera.ui.theme.GuideWarn
import com.aicamera.ui.theme.IosBlue
import com.aicamera.ui.theme.IosFrostedBar
import com.aicamera.ui.theme.IosFrostedDark
import com.aicamera.ui.theme.IosGray
import com.aicamera.ui.theme.IosSystemBackground
import com.aicamera.ui.theme.IosSystemLabel
import com.aicamera.ui.theme.IosSystemSecondaryLabel
import com.aicamera.ui.theme.NeoTextPrimary
import com.aicamera.ui.theme.NeoTextSecondary
import com.aicamera.viewmodel.CompositionViewModel
import kotlinx.coroutines.delay

/**
 * iOS 风格主相机界面：
 * - 纯黑全屏预览
 * - 顶部毛玻璃工具栏（闪光灯 / 网格 / 设置）
 * - 引导气泡居中
 * - 底部毛玻璃控制区（切换 / 快门 / 缩放滑杆）
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
        viewModel.bindCamera(lifecycleOwner) { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) bindCamera()
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) bindCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(hasPermission, previewView) {
        if (hasPermission && previewView != null) {
            viewModel.bindPreview(previewView!!.surfaceProvider, previewView)
        }
        onDispose { }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(1600)
            viewModel.onToastShown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IosSystemBackground)
    ) {
        // ── 相机预览 ──
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { pv -> previewView = pv }
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
                    .background(IosSystemBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "需要相机权限才能使用 AI 构图拍摄",
                        color = IosSystemSecondaryLabel,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "点击允许后即可开始",
                        color = IosGray,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── 构图覆盖层 ──
        CompositionOverlay(state = overlayState, modifier = Modifier.fillMaxSize())

        // ── 顶部工具栏（毛玻璃）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosToolButton(
                onClick = { viewModel.toggleFlash() },
                enabled = flashEnabled,
                tintColor = if (flashEnabled) IosSystemLabel else IosGray
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "闪光灯",
                    tint = if (flashEnabled) Color.White else IosGray,
                    modifier = Modifier.size(22.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IosToolButton(
                    onClick = {
                        val next = when (settings.gridMode) {
                            GridMode.NONE -> GridMode.THIRDS
                            GridMode.THIRDS -> GridMode.GOLDEN
                            GridMode.GOLDEN -> GridMode.NONE
                        }
                        viewModel.updateGridMode(next)
                    }
                ) {
                    Icon(
                        imageVector = if (settings.gridMode != GridMode.NONE) Icons.Filled.GridOn else Icons.Filled.GridOff,
                        contentDescription = "网格",
                        tint = if (settings.gridMode != GridMode.NONE) Color.White else IosGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IosToolButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── 评分角标（右上，iOS 胶囊）──
        if (overlayState.scoreEnabled) {
            ScoreBadge(
                score = overlayState.score,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 16.dp)
            )
        }

        // ── 引导气泡 ──
        val guidance = overlayState.guidance
        if (guidance.type != GuidanceType.NONE && guidance.message.isNotEmpty()) {
            GuidanceBubble(
                message = guidance.message,
                severity = guidance.severity,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
            )
        }

        // ── Toast ──
        toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(msg, color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // ── 底部毛玻璃控制区 ──
        IosBottomBar(
            onShutter = { viewModel.manualShutter() },
            onSwitchCamera = { viewModel.switchCamera() },
            zoomRatio = zoomRatio,
            onZoomChange = { f ->
                zoomRatio = f
                viewModel.setZoomRatio(f)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/** iOS 底部控制条：切换 / 快门 / 变焦滑杆 */
@Composable
private fun IosBottomBar(
    onShutter: () -> Unit,
    onSwitchCamera: () -> Unit,
    zoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(IosFrostedBar)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 变焦滑杆
        IosSlider(
            value = zoomRatio,
            onValueChange = onZoomChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            valueRange = 1f..5f
        )
        Text(
            text = if (zoomRatio > 1.05f) "%.1fx".format(zoomRatio) else "1x",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosCircleButton(onClick = onSwitchCamera, size = 50.dp) {
                Icon(
                    imageVector = Icons.Filled.SwitchCamera,
                    contentDescription = "切换摄像头",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            IosShutterButton(onClick = onShutter)
            // 占位保持对称
            Spacer(Modifier.size(50.dp))
        }
    }
}

@Composable
private fun IosShutterButton(onClick: () -> Unit, pressed: Boolean = false) {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        label = "shutter"
    )
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun IosCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    val color = when {
        score >= 80 -> GuideGood
        score >= 60 -> GuideWarn
        else -> IosSystemSecondaryLabel
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(IosFrostedDark)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "构图 $score",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
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
        Severity.INFO -> IosSystemLabel
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(IosFrostedDark.copy(alpha = 0.9f))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = message,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}