package com.aicamera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aicamera.core.design.CamButton
import com.aicamera.core.design.CamColors
import com.aicamera.core.design.CamDialog
import com.aicamera.core.design.CamIconButton
import com.aicamera.core.design.CamShapes
import com.aicamera.core.design.CamShutter
import com.aicamera.core.design.CamTextButton
import com.aicamera.core.design.CamType
import com.aicamera.core.util.LutRepository
import com.aicamera.core.util.MolaFilter
import com.aicamera.ai.AiGuideEngine
import com.aicamera.ai.LocalAiRules
import com.aicamera.domain.model.AiPhase
import com.aicamera.domain.model.AspectRatio
import com.aicamera.domain.model.FilterStyle
import com.aicamera.domain.model.FlashState
import com.aicamera.domain.model.FrameStyle
import com.aicamera.domain.model.OverlayState
import com.aicamera.domain.model.ShootingMode
import com.aicamera.domain.model.SilkFlowMode

/**
 * v5.2 mola 相机主页 — 按 rj0.java 逐元素复刻。
 * 底部: 调色盘(弹出层) + 变焦行 + AI 辅助/快门/AI 摄影师 + 模式栏。
 * 顶部: 画幅 / 闪光 / 定时 / 曝光 / 切换 / 设置。
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val overlay by viewModel.overlayState.collectAsState()
    val toast by viewModel.toastMessage.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val cameraReady by viewModel.cameraReady.collectAsState()
    val zoom by viewModel.zoom.collectAsState()
    val isFront by viewModel.isFront.collectAsState()

    var showAiDialog by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    var showPalette by remember { mutableStateOf(false) }

    // 预览视图
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // 相机绑定
    LaunchedEffect(Unit) {
        viewModel.bindCamera(lifecycleOwner)
        viewModel.bindPreview(previewView.surfaceProvider, previewView)
    }

    // Toast: 顶部黑底白字浮层
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2200)
            viewModel.onToastShown()
        }
    }

    // 照片保存后打开相册
    val lastSaved by viewModel.lastSavedUri.collectAsState()
    LaunchedEffect(lastSaved) {
        if (lastSaved != null) onOpenGallery()
    }

    // 权限
    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.bindCamera(lifecycleOwner)
    }
    LaunchedEffect(Unit) {
        if (!hasCamera) permLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(CamColors.Black)) {
        // ── 预览 ──
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // ── 覆盖层 (网格/主体/AI 目标圈/评分/引导) ──
        CameraOverlay(state = overlay, modifier = Modifier.fillMaxSize())

        // 点击预览: AI 引导中 → 手动选主体, 否则对焦
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (overlay.aiPhase == AiPhase.GUIDING) {
                            viewModel.manualSelectSubject(
                                offset.x / size.width,
                                offset.y / size.height
                            )
                        } else {
                            viewModel.onTapToFocus(
                                offset.x / size.width,
                                offset.y / size.height
                            )
                        }
                    }
                }
        )

        // ── Toast 浮层 ──
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
        ) {
            Text(
                text = toast ?: "",
                color = CamColors.White,
                style = CamType.BodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CamColors.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        // ── 相机错误提示 (无权限/初始化失败/绑定失败) ──
        val camError by viewModel.errorMessage.collectAsState()
        if (camError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = camError ?: "",
                    color = CamColors.Error,
                    style = CamType.BodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(CamColors.Black.copy(alpha = 0.85f))
                        .border(1.dp, CamColors.Error.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )
                Spacer(Modifier.height(12.dp))
                CamButton(
                    text = "重新尝试",
                    onClick = { viewModel.bindCamera(lifecycleOwner) },
                    modifier = Modifier
                )
            }
        }

        // ── 顶部工具栏 (mola 真实控件集): 会员 / 镜头标识(点按循环变焦) / 切换 ──
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 会员按钮 (mola: 黑底 0xFF3A2E10 + 金色文字)
            Text(
                text = "会员",
                color = CamColors.AccentLight,
                style = CamType.BodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF3A2E10))
                    .clickable { onOpenProfile() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
            // 镜头标识 (mola: 主摄/超广角/长焦/前摄, 点按循环变焦 0.6x→1x→3x)
            Text(
                text = if (isFront) "前摄" else when {
                    zoom < 0.95f -> "超广角"
                    zoom >= 1.8f -> "长焦"
                    else -> "主摄"
                },
                color = CamColors.SecondaryText,
                style = CamType.BodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(CamColors.Black.copy(alpha = 0.45f))
                    .clickable {
                        viewModel.setZoom(
                            when {
                                zoom < 0.95f -> 1f
                                zoom >= 1.8f -> 0.6f
                                else -> 3f
                            }
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
            // 切换摄像头 (mola: nk3.u() 图标 + "切换" 文字)
            CamIconButton(
                icon = Icons.Filled.Cameraswitch,
                onClick = { viewModel.switchCamera() },
                contentDescription = "切换摄像头"
            )
        }

        // ── 底部控制区 (mola: 调色盘弹出 + 变焦行 + 快门行 + 模式栏) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 快门行 (mola: [调色盘] [快门80dp] [AI辅助], 三摄改由顶部镜头标识循环)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CamTextButton(
                    text = "调色盘",
                    selected = overlay.lutFilterId != null || showPalette,
                    onClick = { showPalette = !showPalette },
                    modifier = Modifier
                )
                CamShutter(
                    isRecording = isRecording,
                    onClick = {
                        if (isRecording) {
                            viewModel.toggleVideo { }
                        } else if (overlay.shootingMode == ShootingMode.VIDEO && overlay.silkFlowMode == SilkFlowMode.NONE) {
                            viewModel.toggleVideo { }
                        } else {
                            viewModel.manualShutter()
                        }
                    },
                    holdMode = overlay.shootingMode == ShootingMode.VIDEO && overlay.silkFlowMode != SilkFlowMode.NONE,
                    holding = isRecording && overlay.shootingMode == ShootingMode.VIDEO && overlay.silkFlowMode != SilkFlowMode.NONE,
                    onHoldStart = { viewModel.startSilkFlow() },
                    onHoldEnd = { viewModel.stopSilkFlow() },
                    video = overlay.shootingMode == ShootingMode.VIDEO,
                    modifier = Modifier.size(80.dp)
                )
                CamTextButton(
                    text = if (overlay.aiPhase != AiPhase.IDLE) "AI辅助" else "AI辅助",
                    selected = overlay.aiPhase != AiPhase.IDLE,
                    onClick = {
                        showAiDialog = true
                        viewModel.openAiPhotographer()
                    },
                    modifier = Modifier
                )
            }

            Spacer(Modifier.height(10.dp))

            // 行3: 模式栏 (mola: 照片/视频/夜间 + 流光快门子模式)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShootingMode.entries.forEach { mode ->
                    CamTextButton(
                        text = if (mode == ShootingMode.VIDEO && overlay.silkFlowMode != SilkFlowMode.NONE)
                            overlay.silkFlowMode.label else mode.label,
                        selected = overlay.shootingMode == mode,
                        onClick = {
                            viewModel.selectMode(mode)
                            viewModel.selectSilkFlow(SilkFlowMode.NONE)
                        },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
                SilkFlowMode.entries.drop(1).forEach { sm ->
                    CamTextButton(
                        text = sm.label,
                        selected = overlay.silkFlowMode == sm,
                        onClick = { viewModel.selectSilkFlow(sm) },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }

            // 引导步骤条
            if (overlay.guideSteps.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    overlay.guideSteps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            step.done -> CamColors.Success
                                            step.index == overlay.guideSteps.firstOrNull { !it.done }?.index -> CamColors.Accent
                                            else -> CamColors.White.copy(alpha = 0.4f)
                                        }
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = step.title,
                                color = if (step.done) CamColors.Success else CamColors.White,
                                style = CamType.Caption,
                                maxLines = 1
                            )
                            if (step.index < overlay.guideSteps.lastIndex) {
                                Spacer(Modifier.width(10.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── 调色盘弹出覆盖层 (滤镜面板, mola 调色盘) ──
        AnimatedVisibility(
            visible = showPalette,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(CamColors.Surface.copy(alpha = 0.96f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                // 标题行: 调色盘 + 关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("调色盘", color = CamColors.Accent, style = CamType.ScreenTitle)
                    TextButton(onClick = { showPalette = false }) {
                        Text("关闭", color = CamColors.AccentStrong)
                    }
                }
                LutFilterWheel(
                    selected = overlay.lutFilterId,
                    onSelect = { viewModel.selectLutFilter(it) },
                    recommended = viewModel.recommendedLutIds.collectAsState().value,
                    onAiRecommend = { viewModel.aiRecommendLuts() },
                    onClearRecommend = { viewModel.clearRecommendedLuts() },
                    onToggleFavorite = { viewModel.toggleFavoriteLut(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (overlay.shootingMode == ShootingMode.VIDEO) {
                        "点选滤镜，满意后按录制"
                    } else {
                        "点选滤镜，满意后按快门"
                    },
                    color = CamColors.TertiaryText,
                    style = CamType.Caption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                // 二级功能入口 (mola f60 设置面板: 实况/满血/相框, 不污染主界面)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CamTextButton(
                        text = "实况",
                        selected = overlay.livePhotoMode,
                        onClick = { viewModel.toggleLivePhoto() },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    CamTextButton(
                        text = "满血",
                        selected = overlay.fullResMode,
                        onClick = { viewModel.toggleFullRes() },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    CamTextButton(
                        text = if (overlay.frameStyle == FrameStyle.NONE) "相框" else "相框 ${overlay.frameStyle.label}",
                        selected = overlay.frameStyle != FrameStyle.NONE,
                        onClick = { viewModel.cycleFrameStyle() },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        // ── AI 摄影师对话框 ──
        if (showAiDialog) {
            val phase = overlay.aiPhase
            CamDialog(
                title = "AI 摄影师",
                onDismiss = {
                    showAiDialog = false
                    viewModel.closeAiPhotographer()
                },
                confirmText = when (phase) {
                    AiPhase.PROMPT_INPUT -> "生成方案"
                    AiPhase.PLAN_READY -> "开始引导"
                    AiPhase.WELCOME -> "开始"
                    AiPhase.ANALYZING -> "思考中…"
                    else -> "关闭"
                },
                onConfirm = {
                    when (phase) {
                        AiPhase.WELCOME, AiPhase.PROMPT_INPUT -> {
                            viewModel.submitAiPrompt(aiPrompt.ifBlank { "拍一张好看的照片" })
                        }
                        AiPhase.PLAN_READY -> {
                            showAiDialog = false
                        }
                        else -> showAiDialog = false
                    }
                }
            ) {
                Column {
                    when (phase) {
                        AiPhase.WELCOME -> {
                            Text(
                                text = "欢迎来到 AI 摄影师，告诉我你想拍什么",
                                color = CamColors.SecondaryText,
                                style = CamType.Body,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            // mola ay0 三套固定方案
                            AiGuideEngine.MOLA_PLANS.forEach { plan ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CamShapes.Control)
                                        .background(CamColors.SurfaceElevated)
                                        .clickable {
                                            showAiDialog = false
                                            viewModel.selectMolaPlan(plan)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(plan.title, color = CamColors.White, style = CamType.Body)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            plan.desc,
                                            color = CamColors.TertiaryText,
                                            style = CamType.Caption,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text("使用", color = CamColors.Accent, style = CamType.Caption)
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(
                                "或自定义描述：",
                                color = CamColors.TertiaryText,
                                style = CamType.Caption,
                                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                            )
                        }
                        AiPhase.ANALYZING -> {
                            Text(
                                text = "AI 正在思考…",
                                color = CamColors.SecondaryText,
                                style = CamType.Body
                            )
                        }
                        AiPhase.PLAN_READY -> {
                            Text(
                                text = overlay.aiMessage.ifBlank {
                                    AiGuideEngine.MOLA_PLANS.firstOrNull { it.title == overlay.aiPhotographer?.userPrompt }
                                        ?.desc ?: "方案已生成，点击开始引导"
                                },
                                color = CamColors.SecondaryText,
                                style = CamType.Body,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        else -> {}
                    }
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        placeholder = { Text("想拍什么？例如：日落人像", color = CamColors.TertiaryText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = CamType.Body.copy(color = CamColors.White)
                    )
                }
            }
        }
    }
}

/** mola LUT 调色盘: 分类条 + 滤镜封面轮(151 款, 与编辑器一致) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LutFilterWheel(
    selected: String?,
    onSelect: (String?) -> Unit,
    recommended: List<String>,
    onAiRecommend: () -> Unit,
    onClearRecommend: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filters by remember { mutableStateOf<List<MolaFilter>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var favorites by remember { mutableStateOf(LutRepository.favorites(context)) }

    LaunchedEffect(Unit) {
        LutRepository.ensureLoaded(context)
        filters = LutRepository.allFilters()
        categories = listOf("全部") + LutRepository.categories()
    }

    Column(modifier) {
        // 分类条
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                CamTextButton(
                    text = cat,
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    modifier = Modifier.padding(horizontal = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 封面轮(原图 + 分类内滤镜; AI 推荐时可筛选)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (recommended.isNotEmpty()) {
                item(key = "ai-banner") {
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clip(CamShapes.Small)
                            .background(CamColors.AccentDim)
                            .clickable { onClearRecommend() }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("AI 挑了 ${recommended.size} 款", color = CamColors.Accent, style = CamType.Secondary)
                        Text(
                            "点选后开始拍摄",
                            color = CamColors.White,
                            style = CamType.Caption,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        CamTextButton(
                            text = "关闭",
                            selected = false,
                            onClick = { onClearRecommend() },
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }

            item(key = "none") {
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .clip(CamShapes.Small)
                        .background(if (selected == null) CamColors.AccentDim else CamColors.SurfaceElevated)
                        .clickable { onSelect(null) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("原图", color = CamColors.White, style = CamType.Secondary, maxLines = 1)
                }
            }
            // AI 推荐列表(若非空则只显示推荐)
            val shown = if (recommended.isNotEmpty()) {
                filters.filter { it.id in recommended }
            } else if (selectedCategory == "全部") {
                filters
            } else filters.filter { it.category == selectedCategory }
            items(shown, key = { it.id }) { f ->
                val isSelected = selected == f.id
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .clip(CamShapes.Small)
                        .background(if (isSelected) CamColors.AccentDim else Color.Transparent)
                        .combinedClickable(
                            onClick = { onSelect(if (isSelected) null else f.id) },
                            onLongClick = {
                                onToggleFavorite(f.id)
                                favorites = LutRepository.favorites(context)
                            }
                        )
                        .padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/luts/${f.coverFile}")
                                .crossfade(true)
                                .build(),
                            contentDescription = f.label,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CamShapes.Small),
                            contentScale = ContentScale.Crop
                        )
                        if (f.id in favorites) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "已收藏",
                                tint = CamColors.Accent,
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        f.label,
                        color = if (isSelected) CamColors.Accent else CamColors.White,
                        style = CamType.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}