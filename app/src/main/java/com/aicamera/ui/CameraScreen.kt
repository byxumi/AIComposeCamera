package com.aicamera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aicamera.composition.AiPhase
import com.aicamera.composition.GuidanceType
import com.aicamera.composition.Severity
import com.aicamera.composition.ShootingMode
import com.aicamera.composition.FilterStyle
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
import com.aicamera.viewmodel.CompositionViewModel
import kotlinx.coroutines.delay

/**
 * 相机主界面（一比一复刻 mola 布局）：
 * - 全屏黑色预览
 * - 顶部工具栏：画幅 / 闪光灯 / 定时 / 设置
 * - 右侧白色快门 + 变焦
 * - 底部模式栏：自动 / 人像 / 夜景 / 美食 / 风景 / 视频
 * - 底部「AI 辅助」按钮 + 右下角笑脸「AI 摄影师」
 * - 滤镜轮（横滑）
 * - AI 摄影师对话面板（弹窗）
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
    var showAiDialog by remember { mutableStateOf(false) }

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
            PermissionPlaceholder(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }

        // ── 构图覆盖层（含 AI 目标圆圈）──
        CompositionOverlay(state = overlayState, modifier = Modifier.fillMaxSize())

        // ── 顶部工具栏 ──
        TopToolbar(
            onToggleFlash = { viewModel.toggleFlash() },
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ── 评分角标（右上）──
        if (overlayState.scoreEnabled) {
            ScoreBadge(
                score = overlayState.score,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 16.dp)
            )
        }

        // ── 模式提示文案（mola：对准目标提示）──
        if (overlayState.aiAssistActive && overlayState.aiTarget != null) {
            val t = overlayState.aiTarget
            AiGuideBubble(
                text = if (t.reached) "✓ 已对准，构图很棒！" else t.label,
                reached = t.reached,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 220.dp)
            )
        }

        // ── 引导气泡（普通构图提示）──
        val guidance = overlayState.guidance
        if (!overlayState.aiAssistActive &&
            guidance.type != GuidanceType.NONE && guidance.message.isNotEmpty()
        ) {
            GuidanceBubble(
                message = guidance.message,
                severity = guidance.severity,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
            )
        }

        // ── 分步引导（AI 摄影师 GUIDING/READY）──
        val guideSteps = overlayState.guideSteps
        if (guideSteps.isNotEmpty()) {
            GuideStepsPanel(
                steps = guideSteps,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 90.dp, start = 16.dp, end = 16.dp)
            )
        }

        // ── Toast ──
        toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 240.dp),
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

        // ── 底部：模式栏 + AI 辅助 + 快门 + 滤镜轮 ──
        MolaBottomPanel(
            onShutter = { viewModel.manualShutter() },
            onSwitchCamera = { viewModel.switchCamera() },
            selectedMode = overlayState.shootingMode,
            onSelectMode = { viewModel.selectMode(it) },
            aiAssistActive = overlayState.aiAssistActive,
            onToggleAiAssist = { viewModel.toggleAiAssist() },
            onOpenAiPhotographer = {
                showAiDialog = true
                viewModel.openAiPhotographer()
            },
            selectedFilter = overlayState.filterStyle,
            onSelectFilter = { viewModel.selectFilter(it) },
            zoomRatio = zoomRatio,
            onZoomChange = { f ->
                zoomRatio = f
                viewModel.setZoomRatio(f)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }

    // ── AI 摄影师对话弹窗 ──
    val aiPhotographer = overlayState.aiPhotographer
    if (showAiDialog && aiPhotographer != null &&
        (aiPhotographer.phase == AiPhase.WELCOME || aiPhotographer.phase == AiPhase.PROMPT_INPUT || aiPhotographer.phase == AiPhase.ANALYZING || aiPhotographer.phase == AiPhase.PLAN_READY)
    ) {
        AiPhotographerDialog(
            state = aiPhotographer,
            onDismiss = {
                showAiDialog = false
                if (aiPhotographer.phase == AiPhase.PLAN_READY) {
                    // 方案就绪后进入引导
                }
            },
            onSubmit = { prompt ->
                viewModel.submitAiPrompt(prompt)
            }
        )
    }
}

/* ═══════════════ 顶部工具栏（mola：画幅/闪光/定时/设置）═══════════ */

@Composable
private fun TopToolbar(
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：画幅（4:3 默认）+ 闪光
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IosToolButton(onClick = {}, tintColor = Color.White) {
                Text("4:3", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IosToolButton(onClick = onToggleFlash, tintColor = Color.White) {
                Icon(Icons.Filled.FlashOff, "闪光灯", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        // 右：定时 + 设置
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IosToolButton(onClick = {}, tintColor = Color.White) {
                Icon(Icons.Filled.Timer, "定时", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IosToolButton(onClick = onOpenSettings, tintColor = Color.White) {
                Icon(Icons.Filled.Settings, "设置", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/* ═══════════════ 底部面板（模式栏 + AI 辅助 + 快门 + 滤镜轮）═══════════ */

@Composable
private fun MolaBottomPanel(
    onShutter: () -> Unit,
    onSwitchCamera: () -> Unit,
    selectedMode: ShootingMode,
    onSelectMode: (ShootingMode) -> Unit,
    aiAssistActive: Boolean,
    onToggleAiAssist: () -> Unit,
    onOpenAiPhotographer: () -> Unit,
    selectedFilter: FilterStyle,
    onSelectFilter: (FilterStyle) -> Unit,
    zoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(IosFrostedBar)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 滤镜轮（横滑）──
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(FilterStyle.entries.toList()) { f ->
                FilterChip(
                    style = f,
                    selected = f == selectedFilter,
                    onClick = { onSelectFilter(f) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── 中间行：AI 辅助（左） + 快门（中） + 笑脸/AI摄影师（右）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：AI 辅助按钮
            AiAssistButton(
                active = aiAssistActive,
                onClick = onToggleAiAssist
            )

            // 中：快门
            IosShutterButton(onClick = onShutter)

            // 右：AI 摄影师笑脸
            AiPhotographerButton(onClick = onOpenAiPhotographer)
        }

        Spacer(Modifier.height(6.dp))

        // ── 底部模式栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ShootingMode.entries.forEach { mode ->
                ModeIcon(
                    mode = mode,
                    selected = mode == selectedMode,
                    onClick = { onSelectMode(mode) }
                )
            }
        }
    }
}

/** 滤镜选择胶囊 */
@Composable
private fun FilterChip(
    style: FilterStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = style.label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** 拍摄模式图标（mola 模式栏） */
@Composable
private fun ModeIcon(
    mode: ShootingMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = mode.icon,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = mode.label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

/** AI 辅助按钮（mola 底部） */
@Composable
private fun AiAssistButton(
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) IosBlue.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.SmartToy,
            contentDescription = "AI 辅助",
            tint = if (active) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = "AI辅助",
            color = if (active) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** AI 摄影师笑脸按钮（mola 右下角笑脸） */
@Composable
private fun AiPhotographerButton(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.SentimentSatisfied,
            contentDescription = "AI 摄影师",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(26.dp)
        )
        Text(
            text = "AI摄影师",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp
        )
    }
}

/* ═══════════════ AI 目标引导气泡 ═══════════════ */

@Composable
private fun AiGuideBubble(
    text: String,
    reached: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (reached) GuideGood.copy(alpha = 0.85f) else IosFrostedDark.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/* ═══════════════ AI 分步引导面板 ═══════════════ */

@Composable
private fun GuideStepsPanel(
    steps: List<com.aicamera.composition.GuideStep>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(IosFrostedDark.copy(alpha = 0.85f))
            .padding(12.dp)
    ) {
        Text(
            "AI 推荐的引导步骤",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        steps.forEach { s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (s.done) GuideGood else Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (s.done) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    } else {
                        Text("${s.index}", color = Color.White, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = s.instruction,
                    color = Color.White.copy(alpha = if (s.done) 0.6f else 0.95f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

/* ═══════════════ AI 摄影师对话弹窗 ═══════════════ */

@Composable
private fun AiPhotographerDialog(
    state: com.aicamera.composition.AiPhotographerState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    val phase = state.phase

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SentimentSatisfied, null, tint = IosBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI 摄影师", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                when (phase) {
                    AiPhase.WELCOME, AiPhase.PROMPT_INPUT -> {
                        Text(
                            "欢迎来到 AI 摄影师～告诉我你想拍什么画面的感觉，我来帮你拍好。",
                            color = IosSystemSecondaryLabel,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        // 快捷场景建议
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("人像", "美食", "夜景", "风景").forEach { s ->
                                TextButton(onClick = { onSubmit(s) }) {
                                    Text(s, color = IosBlue, fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            placeholder = { Text("例如：暖暖的阳光从左边洒过来，安静文艺", color = IosGray, fontSize = 13.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AiPhase.ANALYZING -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AI 正在思考…", color = Color.White, fontSize = 15.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("分析画面与你的拍摄意图", color = IosSystemSecondaryLabel, fontSize = 12.sp)
                            }
                        }
                    }
                    AiPhase.PLAN_READY -> {
                        Text(
                            state.suggestionText,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    else -> {
                        Text("AI 摄影师", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            when (phase) {
                AiPhase.WELCOME, AiPhase.PROMPT_INPUT -> {
                    Button(
                        onClick = { onSubmit(prompt.ifBlank { "自动" }) },
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue)
                    ) {
                        Text("生成方案", color = Color.White)
                    }
                }
                AiPhase.ANALYZING -> {
                    // 无按钮
                }
                AiPhase.PLAN_READY -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue)
                    ) {
                        Text("开始引导拍摄", color = Color.White)
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = IosGray)
            }
        }
    )
}

/* ═══════════════ 基础组件 ═══════════════ */

@Composable
private fun PermissionPlaceholder(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSystemBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("需要相机权限才能使用 AI 构图拍摄", color = IosSystemSecondaryLabel, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = IosBlue)) {
                Text("允许相机权限", color = Color.White)
            }
        }
    }
}

@Composable
private fun IosShutterButton(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        label = "shutter"
    )
    // 按下后自动回弹
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(120)
            pressed = false
        }
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    pressed = true
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
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