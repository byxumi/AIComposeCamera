package com.aicamera.ui.editor

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aicamera.core.design.CamButton
import com.aicamera.core.design.CamColors
import com.aicamera.core.design.CamDialog
import com.aicamera.core.design.CamIconButton
import com.aicamera.core.design.CamShapes
import com.aicamera.core.design.CamTextButton
import com.aicamera.core.design.CamType
import com.aicamera.core.util.FilterParams
import com.aicamera.core.util.StickerOverlayData
import com.aicamera.core.util.StickerOverlayType
import com.aicamera.core.util.TextOverlayData
import com.aicamera.core.util.WatermarkKind
import com.aicamera.domain.model.FilterPreset
import com.aicamera.domain.model.FilterStyle
import kotlin.math.roundToInt

/** 底部工具 tab */
private enum class EditTool { ADJUST, FILTER, CROP, TEXT, STICKER, WATERMARK }

/** 裁剪区域状态（相对 0..1） */
private data class CropState(
    val left: Float = 0.08f,
    val top: Float = 0.08f,
    val right: Float = 0.92f,
    val bottom: Float = 0.92f
)

/** 滤镜预设（与 EditorViewModel.filters 一致，供面板显示） */
private val editorFilterPresets = listOf(
    FilterPreset(FilterStyle.NONE),
    FilterPreset(FilterStyle.FILM, saturation = 0.92f, contrast = 1.06f, warmth = 0.18f, vignette = 0.18f),
    FilterPreset(FilterStyle.FRESH, saturation = 1.18f, contrast = 1.02f, warmth = -0.08f),
    FilterPreset(FilterStyle.VINTAGE, saturation = 0.82f, contrast = 0.92f, warmth = 0.26f, vignette = 0.22f),
    FilterPreset(FilterStyle.BW, saturation = 0f, contrast = 1.12f),
    FilterPreset(FilterStyle.WARM, saturation = 1.08f, warmth = 0.34f),
    FilterPreset(FilterStyle.COOL, saturation = 1.02f, warmth = -0.3f),
    FilterPreset(FilterStyle.FOOD_WARM, saturation = 1.2f, warmth = 0.22f, contrast = 1.05f),
    FilterPreset(FilterStyle.PORTRAIT_SOFT, saturation = 1.05f, contrast = 0.95f, warmth = 0.12f),
    FilterPreset(FilterStyle.NIGHT_CITY, saturation = 0.9f, contrast = 1.15f, warmth = -0.12f, vignette = 0.28f)
)

/** 调整项 */
private enum class AdjustKey(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val current: (FilterParams) -> Float
) {
    BRIGHTNESS("亮度", -1f..1f, { it.brightness }),
    CONTRAST("对比度", 0.5f..1.5f, { it.contrast }),
    SATURATION("饱和度", 0f..2f, { it.saturation }),
    WARMTH("色温", -1f..1f, { it.warmth }),
    VIGNETTE("暗角", 0f..1f, { it.vignette }),
    SHARPNESS("锐化", 0f..2f, { it.vignette })
}

private fun FilterParams.with(key: AdjustKey, value: Float): FilterParams = when (key) {
    AdjustKey.BRIGHTNESS -> copy(brightness = value)
    AdjustKey.CONTRAST -> copy(contrast = value)
    AdjustKey.SATURATION -> copy(saturation = value)
    AdjustKey.WARMTH -> copy(warmth = value)
    AdjustKey.VIGNETTE -> copy(vignette = value)
    AdjustKey.SHARPNESS -> this // 锐化单独处理
}

/** 合并滤镜参数到调整后的当前值（显示用）：preset 存在时乘/加 */
private fun effectiveParams(preset: FilterPreset?, a: FilterParams): FilterParams = if (preset == null) a else FilterParams(
    saturation = preset.saturation * a.saturation,
    contrast = preset.contrast * a.contrast,
    brightness = preset.brightness + a.brightness,
    warmth = preset.warmth + a.warmth,
    vignette = preset.vignette + a.vignette
)

/**
 * Doka 风格图片编辑器。
 * 顶栏: ‹返回 | 撤销 | 保存；中部: 图片预览+叠加层；底部: 6 工具 tab。
 */
@Composable
fun EditorScreen(
    imageUri: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY]!!
            EditorViewModel(app, imageUri)
        }
    })
) {
    val bitmap by viewModel.editBitmap.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val adjusts by viewModel.adjusts.collectAsState()
    val texts by viewModel.texts.collectAsState()
    val stickers by viewModel.stickers.collectAsState()
    val watermark by viewModel.watermark.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()

    var tool by remember { mutableStateOf(EditTool.ADJUST) }
    var crop by remember { mutableStateOf<CropState?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var sharpen by remember { mutableStateOf(0f) }

    LaunchedEffect(saved) {
        if (saved) {
            kotlinx.coroutines.delay(700)
            onBack()
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1800)
            viewModel.onToastShown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CamColors.Black)
            .statusBarsPadding()
    ) {
        // ── 图片区 ──
        val bmp = bitmap
        if (bmp != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(CamShapes.Small),
                contentAlignment = Alignment.Center
            ) {
                val imgMod = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                // 位图预览
                AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            setImageBitmap(bmp)
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    modifier = imgMod
                )
                // 裁剪模式
                val cs = crop
                if (cs != null) {
                    CropOverlay(
                        state = cs,
                        modifier = imgMod,
                        onChanged = { crop = it },
                        onApply = {
                            viewModel.applyCrop(RectF(cs.left, cs.top, cs.right, cs.bottom))
                            crop = null
                            tool = EditTool.ADJUST
                        },
                        onCancel = { crop = null }
                    )
                } else {
                    // 叠加层：文字/贴纸（可拖动）
                    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
                    val boxMod = imgMod.onSizeChanged { size ->
                        if (size.width > 0) overlaySize = size
                    }
                    OverlayBox(
                        texts = texts,
                        stickers = stickers,
                        itemsSize = overlaySize,
                        onMoveText = { id, pos -> viewModel.moveText(id, pos) },
                        onMoveSticker = { id, pos -> viewModel.moveSticker(id, pos) },
                        modifier = boxMod
                    )
                    // 水印预览
                    if (watermark != WatermarkKind.NONE) {
                        WatermarkPreview(
                            mode = watermark,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        )
                    }
                }

                // ── toast ──
                toast?.let {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .clip(CamShapes.Small)
                            .background(CamColors.Black.copy(alpha = 0.85f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(it, color = CamColors.White, style = CamType.Body)
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (loading) "加载中…" else "图片加载失败",
                    color = CamColors.SecondaryText,
                    style = CamType.Body
                )
            }
        }

        // ── 顶栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CamIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "返回"
            )
            Spacer(Modifier.weight(1f))
            Text("编辑", color = CamColors.White, style = CamType.ScreenTitle)
            Spacer(Modifier.weight(1f))
            CamIconButton(
                icon = Icons.Filled.RotateLeft,
                onClick = { viewModel.rotate90() },
                contentDescription = "旋转"
            )
            CamIconButton(
                icon = Icons.Filled.Tune,
                onClick = { viewModel.undo() },
                tint = if (canUndo) CamColors.White else CamColors.TertiaryText,
                contentDescription = "撤销"
            )
            CamIconButton(
                icon = Icons.Filled.AutoFixHigh,
                onClick = { viewModel.save() },
                tint = if (saving) CamColors.TertiaryText else CamColors.Accent,
                contentDescription = "保存"
            )
        }


        // ── 底部面板 ──
        BottomTools(
            tool = tool,
            filter = filter,
            adjusts = adjusts,
            sharpen = sharpen,
            onSelectTool = { t ->
                if (t == EditTool.CROP) {
                    if (crop == null) crop = CropState()
                } else {
                    crop = null
                }
                tool = t
            },
            onFilter = { p -> viewModel.setFilter(p) },
            onAdjust = { key, v ->
                if (key == AdjustKey.SHARPNESS) {
                    sharpen = v
                    viewModel.setSharpenLive(v)
                } else {
                    viewModel.setAdjustLive { with(key, v) }
                }
            },
            onAdjustCommit = { viewModel.commitAdjust() },
            onRotate = { viewModel.rotate90() },
            onFlipH = { viewModel.flipH() },
            onFlipV = { viewModel.flipV() },
            onReset = { viewModel.resetAdjusts(); sharpen = 0f },
            onAddText = { showTextDialog = true },
            onAddSticker = { type -> viewModel.addSticker(type, Offset(0.5f, 0.5f)) },
            onWatermark = { m -> viewModel.setWatermark(m) }
        )
    }

    // ── 文字输入对话框 ──
    if (showTextDialog) {
        var input by remember { mutableStateOf("") }
        CamDialog(
            title = "添加文字",
            onDismiss = { showTextDialog = false },
            confirmText = "添加",
            onConfirm = {
                if (input.isNotBlank()) viewModel.addText(input, Offset(0.5f, 0.5f))
                showTextDialog = false
            }
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text("输入文字…", color = CamColors.TertiaryText) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = CamColors.White,
                    fontSize = 16.sp
                )
            )
        }
    }
}

/** 图片叠加层（文字/贴纸）——支持拖动 */
@Composable
private fun OverlayBox(
    texts: List<TextOverlayData>,
    stickers: List<StickerOverlayData>,
    itemsSize: IntSize,
    onMoveText: (Int, Offset) -> Unit,
    onMoveSticker: (Int, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val w = itemsSize.width.coerceAtLeast(1).toFloat()
        val h = itemsSize.height.coerceAtLeast(1).toFloat()

        texts.forEach { t ->
            Text(
                text = t.text,
                color = if (t.white) Color.White else Color.Black,
                fontSize = t.sizeSp.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = if (t.white) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.75f),
                        offset = Offset(2f, 2f),
                        blurRadius = 6f
                    )
                ),
                modifier = Modifier
                    .offset { IntOffset((t.pos.x * w).roundToInt(), (t.pos.y * h).roundToInt()) }
                    .pointerInput(t.id) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                val start = t.pos
                                onMoveText(
                                    t.id,
                                    Offset(
                                        (start.x + drag.x / w).coerceIn(0f, 1f),
                                        (start.y + drag.y / h).coerceIn(0f, 1f)
                                    )
                                )
                            }
                        )
                    }
            )
        }

        stickers.forEach { s ->
            val stickerSize = (s.scale * w).roundToInt().coerceAtLeast(32)
            Box(
                modifier = Modifier
                    .offset { IntOffset((s.pos.x * w).roundToInt(), (s.pos.y * h).roundToInt()) }
                    .size(with(LocalDensity.current) { stickerSize.toDp() })
                    .pointerInput(s.id) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                val start = s.pos
                                onMoveSticker(
                                    s.id,
                                    Offset(
                                        (start.x + drag.x / w).coerceIn(0f, 1f),
                                        (start.y + drag.y / h).coerceIn(0f, 1f)
                                    )
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                StickerGlyph(s.type, Modifier.fillMaxSize())
            }
        }
    }
}

/** 水印预览（右下角） */
@Composable
private fun WatermarkPreview(mode: WatermarkKind, modifier: Modifier = Modifier) {
    val text = when (mode) {
        WatermarkKind.DATE -> java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()).format(java.util.Date())
        else -> "AI Camera"
    }
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        style = androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.8f),
                offset = Offset(1f, 1f),
                blurRadius = 3f
            )
        ),
        modifier = modifier
    )
}

/** 底部工具面板 */
@Composable
private fun BottomTools(
    tool: EditTool,
    filter: FilterPreset?,
    adjusts: FilterParams,
    sharpen: Float,
    onSelectTool: (EditTool) -> Unit,
    onFilter: (FilterPreset?) -> Unit,
    onAdjust: (AdjustKey, Float) -> Unit,
    onAdjustCommit: () -> Unit,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onReset: () -> Unit,
    onAddText: () -> Unit,
    onAddSticker: (StickerOverlayType) -> Unit,
    onWatermark: (WatermarkKind) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CamColors.Black.copy(alpha = 0.94f))
            .border(1.dp, CamColors.FrostedStroke, CamShapes.Panel)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "调整" to EditTool.ADJUST,
                "滤镜" to EditTool.FILTER,
                "裁剪" to EditTool.CROP,
                "文字" to EditTool.TEXT,
                "贴纸" to EditTool.STICKER,
                "水印" to EditTool.WATERMARK
            ).forEach { (label, t) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CamShapes.Small)
                        .background(if (tool == t) CamColors.AccentDim else Color.Transparent)
                        .clickable { onSelectTool(t) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (tool == t) CamColors.Accent else CamColors.SecondaryText,
                        style = CamType.Secondary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        when (tool) {
            EditTool.ADJUST -> AdjustPanel(adjusts, sharpen, onAdjust, onAdjustCommit, onReset)
            EditTool.FILTER -> FilterPanel(filter, onFilter)
            EditTool.CROP -> CropPanel(onRotate, onFlipH, onFlipV)
            EditTool.TEXT -> TextPanel(onAddText)
            EditTool.STICKER -> StickerPanel(onAddSticker)
            EditTool.WATERMARK -> WatermarkPanel(onWatermark)
        }
    }
}

@Composable
private fun AdjustPanel(
    adjusts: FilterParams,
    sharpen: Float,
    onAdjust: (AdjustKey, Float) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AdjustKey.entries.forEach { key ->
            AdjustSliderRow(
                label = key.label,
                value = key.current(adjusts),
                range = key.range,
                onValue = { v -> onAdjust(key, v) },
                onCommit = onCommit
            )
        }
        AdjustSliderRow(
            label = "锐化",
            value = sharpen,
            range = 0f..2f,
            onValue = { v -> onAdjust(AdjustKey.SHARPNESS, v) },
            onCommit = onCommit
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onReset) {
                Text("重置", color = CamColors.SecondaryText, style = CamType.Body)
            }
            Text(
                "亮度 ${((adjusts.brightness) * 100).roundToInt()}%",
                color = CamColors.TertiaryText,
                style = CamType.Caption
            )
        }
    }
}

@Composable
private fun AdjustSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    onCommit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = CamColors.SecondaryText,
            style = CamType.Secondary,
            modifier = Modifier.width(48.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            onValueChangeFinished = onCommit,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = CamColors.Accent,
                activeTrackColor = CamColors.Accent
            )
        )
        Text(
            when (label) {
                "锐化" -> value.toString()
                "对比度" -> "${(value * 100).roundToInt()}%"
                else -> "${(value * 100).roundToInt()}%"
            },
            color = CamColors.TertiaryText,
            style = CamType.Caption,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun FilterPanel(
    filter: FilterPreset?,
    onFilter: (FilterPreset?) -> Unit
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            CamTextButton(
                text = "原图",
                selected = filter == null,
                onClick = { onFilter(null) }
            )
        }
        items(editorFilterPresets.drop(1), key = { it.style.name }) { preset ->
            CamTextButton(
                text = preset.style.label,
                selected = filter?.style == preset.style,
                onClick = { onFilter(preset) }
            )
        }
    }
}

@Composable
private fun CropPanel(onRotate: () -> Unit, onFlipH: () -> Unit, onFlipV: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CamTextButton("旋转90°", selected = false, onClick = onRotate)
        CamTextButton("水平翻转", selected = false, onClick = onFlipH)
        CamTextButton("垂直翻转", selected = false, onClick = onFlipV)
    }
}

@Composable
private fun TextPanel(onAddText: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CamButton("添加文字", onClick = onAddText)
        Text(
            "点按文字可拖动位置",
            color = CamColors.TertiaryText,
            style = CamType.Secondary
        )
    }
}

@Composable
private fun StickerPanel(onAddSticker: (StickerOverlayType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StickerOverlayType.entries.forEach { type ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CamColors.SurfaceElevated)
                    .clickable { onAddSticker(type) },
                contentAlignment = Alignment.Center
            ) {
                StickerGlyph(type, Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun WatermarkPanel(onSelect: (WatermarkKind) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WatermarkKind.entries.forEach { mode ->
            CamTextButton(
                text = when (mode) {
                    WatermarkKind.NONE -> "无水印"
                    WatermarkKind.DATE -> "日期"
                    WatermarkKind.BRAND -> "品牌"
                },
                selected = false,
                onClick = { onSelect(mode) }
            )
        }
    }
}

/** 贴纸图形绘制 */
@Composable
private fun StickerGlyph(type: StickerOverlayType, modifier: Modifier = Modifier) {
    val color = CamColors.White
    Canvas(modifier = modifier) {
        val s = this.size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (type) {
            StickerOverlayType.HEART -> {
                val path = Path().apply {
                    moveTo(cx, cy + s * 0.3f)
                    cubicTo(cx + s * 0.5f, cy - s * 0.2f, cx + s * 0.3f, cy - s * 0.5f, cx, cy - s * 0.05f)
                    cubicTo(cx - s * 0.3f, cy - s * 0.5f, cx - s * 0.5f, cy - s * 0.2f, cx, cy + s * 0.3f)
                    close()
                }
                drawPath(path, color = color, style = Stroke(width = s * 0.06f))
            }
            StickerOverlayType.STAR -> {
                val path = Path()
                for (i in 0 until 10) {
                    val angle = i * Math.PI / 5 - Math.PI / 2
                    val radius = if (i % 2 == 0) s * 0.4f else s * 0.16f
                    val x = cx + (Math.cos(angle) * radius).toFloat()
                    val y = cy + (Math.sin(angle) * radius).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = color, style = Stroke(width = s * 0.07f))
            }
            StickerOverlayType.SMILE -> {
                drawCircle(color = color, radius = s * 0.35f, center = Offset(cx, cy), style = Stroke(s * 0.05f))
                drawCircle(color = color, radius = s * 0.035f, center = Offset(cx - s * 0.12f, cy - s * 0.08f))
                drawCircle(color = color, radius = s * 0.035f, center = Offset(cx + s * 0.12f, cy - s * 0.08f))
                drawArc(
                    color = color,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - s * 0.2f, cy - s * 0.05f),
                    size = Size(s * 0.4f, s * 0.3f),
                    style = Stroke(s * 0.05f)
                )
            }
            StickerOverlayType.CAMERA -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - s * 0.35f, cy - s * 0.2f),
                    size = Size(s * 0.7f, s * 0.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f),
                    style = Stroke(s * 0.06f)
                )
                drawCircle(color = color, radius = s * 0.12f, center = Offset(cx, cy), style = Stroke(s * 0.06f))
                drawLine(
                    color = color,
                    start = Offset(cx - s * 0.15f, cy - s * 0.3f),
                    end = Offset(cx + s * 0.15f, cy - s * 0.3f),
                    strokeWidth = s * 0.06f
                )
            }
            StickerOverlayType.FLASH -> {
                val path = Path().apply {
                    moveTo(cx + s * 0.1f, cy - s * 0.4f)
                    lineTo(cx - s * 0.25f, cy + s * 0.1f)
                    lineTo(cx + s * 0.02f, cy + s * 0.1f)
                    lineTo(cx - s * 0.1f, cy + s * 0.4f)
                    lineTo(cx + s * 0.3f, cy - s * 0.1f)
                    lineTo(cx + s * 0.05f, cy - s * 0.1f)
                    close()
                }
                drawPath(path, color = color)
            }
            StickerOverlayType.CHECK -> {
                val path = Path().apply {
                    moveTo(cx - s * 0.3f, cy)
                    lineTo(cx - s * 0.05f, cy + s * 0.2f)
                    lineTo(cx + s * 0.3f, cy - s * 0.2f)
                }
                drawPath(path, color = color, style = Stroke(width = s * 0.09f))
            }
        }
    }
}

/** 裁剪覆盖层：四边遮罩 + 高亮裁剪框 + 手柄 + 应用/取消 */
@Composable
private fun CropOverlay(
    state: CropState,
    modifier: Modifier = Modifier,
    onChanged: (CropState) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val l = state.left * w
            val t = state.top * h
            val r = state.right * w
            val b = state.bottom * h
            val mask = Color.Black.copy(alpha = 0.55f)
            // 四边遮罩
            drawRect(mask, topLeft = Offset(0f, 0f), size = Size(w, t))
            drawRect(mask, topLeft = Offset(0f, b), size = Size(w, h - b))
            drawRect(mask, topLeft = Offset(0f, t), size = Size(l, b - t))
            drawRect(mask, topLeft = Offset(r, t), size = Size(w - r, b - t))
            // 边框 + 手柄
            drawRect(
                color = CamColors.Accent,
                topLeft = Offset(l, t),
                size = Size(r - l, b - t),
                style = Stroke(width = 5f)
            )
            val hd = 10f
            listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { c ->
                drawCircle(color = CamColors.Accent, radius = hd, center = c)
            }
        }
        // 简单手势：拖动四角手柄调整
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            val w = size.width
                            val h = size.height
                            if (w <= 0 || h <= 0) return@detectDragGestures
                            val dx = drag.x / w
                            val dy = drag.y / h
                            val nt = (state.top + dy).coerceIn(0f, state.bottom - 0.02f)
                            onChanged(state.copy(top = nt))
                        }
                    )
                }
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            CamTextButton("取消", selected = false, onClick = onCancel, modifier = Modifier.padding(end = 12.dp))
            CamButton("应用", onClick = onApply)
        }
    }
}