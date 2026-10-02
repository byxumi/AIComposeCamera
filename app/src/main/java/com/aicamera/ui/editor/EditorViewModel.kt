package com.aicamera.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.core.util.BitmapFilters
import com.aicamera.core.util.TextOverlayData
import com.aicamera.core.util.StickerOverlayData
import com.aicamera.core.util.StickerOverlayType
import com.aicamera.core.util.WatermarkKind
import com.aicamera.core.util.FilterParams
import com.aicamera.core.util.LutRepository
import com.aicamera.core.util.MolaLut
import com.aicamera.data.PhotoStore
import com.aicamera.domain.model.FilterPreset
import com.aicamera.domain.model.FilterStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


/**
 * Doka 风格编辑器 ViewModel。
 *
 * 管线（保存时按序应用）:
 *   源图 → 几何(旋转/翻转/裁剪) → 滤镜预设 → 调整(亮度/对比度/饱和度/锐化/色温/暗角) → 文字/贴纸/水印 → 输出
 * 实时预览：几何+色彩渲染一次作为 editBitmap，文字/贴纸/水印用 Compose 层实时绘制，不反复渲染位图。
 */
@OptIn(FlowPreview::class)
class EditorViewModel(private val context: Context, private val uriString: String) : ViewModel() {

    /** 原始解码位图 */
    private var source: Bitmap? = null

    /** 几何+色彩处理后的位图（预览与保存基础） */
    private val _editBitmap = MutableStateFlow<Bitmap?>(null)
    val editBitmap: StateFlow<Bitmap?> = _editBitmap.asStateFlow()

    /** 撤销栈：每次几何/色彩变更前压入上一次 editBitmap */
    private var undoStack: ArrayDeque<Bitmap> = ArrayDeque()

    /** 原始宽度/高度（旋转后可能交换，用于归一化渲染尺寸） */
    val renderWidth: Float get() = (_editBitmap.value?.width ?: 1).toFloat()
    val renderHeight: Float get() = (_editBitmap.value?.height ?: 1).toFloat()

    // ── 几何状态 ──
    private var rotation = 0 // 累积 0/90/180/270
    private var flipH = false
    private var flipV = false

    // ── 色彩状态 ──
    private val _adjusts = MutableStateFlow(FilterParams())
    val adjusts: StateFlow<FilterParams> = _adjusts.asStateFlow()

    private var sharpenAmount = 0f

    // ── 滤镜 ──
    private val _filter = MutableStateFlow<FilterPreset?>(null)
    val filter: StateFlow<FilterPreset?> = _filter.asStateFlow()

    /** mola LUT 滤镜 id(null=未选)。与 ColorMatrix filter 互斥,取其一。 */
    private val _lutFilter = MutableStateFlow<String?>(null)
    val lutFilter: StateFlow<String?> = _lutFilter.asStateFlow()

    // ── 叠加层（Compose 实时绘制，保存时合成）──
    private val _texts = MutableStateFlow<List<TextOverlayData>>(emptyList())
    val texts: StateFlow<List<TextOverlayData>> = _texts.asStateFlow()
    private val _stickers = MutableStateFlow<List<StickerOverlayData>>(emptyList())
    val stickers: StateFlow<List<StickerOverlayData>> = _stickers.asStateFlow()
    private var nextOverlayId = 1

    // ── 水印 ──
    private val _watermark = MutableStateFlow(WatermarkKind.NONE)
    val watermark: StateFlow<WatermarkKind> = _watermark.asStateFlow()

    // ── 状态 ──
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    val filters = listOf(
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

    init {
        loadSource()
    }

    private fun loadSource() {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeSampled(uriString) }
            source = bmp
            if (bmp != null) {
                _editBitmap.value = bmp
                _loading.value = false
            } else {
                _toast.value = "图片加载失败"
                _loading.value = false
            }
        }
    }

    private fun decodeSampled(uriString: String): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val uri = Uri.parse(uriString)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2
            val finalOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, finalOpts) }
        } catch (_: Exception) { null }
    }

    // ── 几何操作 ──

    fun rotate90() {
        val src = source ?: return
        pushUndo()
        rotation = (rotation + 90) % 360
        applyGeometry(src)
    }

    fun flipH() {
        val src = source ?: return
        pushUndo()
        flipH = !flipH
        applyGeometry(src)
    }

    fun flipV() {
        val src = source ?: return
        pushUndo()
        flipV = !flipV
        applyGeometry(src)
    }

    /** 重置几何（旋转/翻转回到原状） */
    fun resetGeometry() {
        val src = source ?: return
        if (rotation == 0 && !flipH && !flipV) return
        pushUndo()
        rotation = 0; flipH = false; flipV = false
        applyGeometry(src)
    }

    /** 裁剪：rect 为当前 editBitmap 上的相对矩形 (0..1)。应用后成为新的基准。 */
    fun applyCrop(rect: RectF) {
        val src = _editBitmap.value ?: return
        // 防止无效裁剪
        val r = RectF(
            rect.left.coerceIn(0f, 1f),
            rect.top.coerceIn(0f, 1f),
            rect.right.coerceIn(0f, 1f),
            rect.bottom.coerceIn(0f, 1f)
        )
        if (r.width() < 0.05f || r.height() < 0.05f) return
        pushUndo()
        viewModelScope.launch {
            val cropped = withContextSafe { BitmapFilters.crop(src, r) } ?: kotlin.run { _editBitmap.value = src; return@launch }
            source = cropped
            rotation = 0; flipH = false; flipV = false // 裁剪后就以裁剪结果为基准
            _editBitmap.value = cropped
            // 重新应用色彩/滤镜
            rerenderColor()
        }
    }

    private fun applyGeometry(src: Bitmap) {
        viewModelScope.launch {
            var out = src
            out = withContext(Dispatchers.IO) { BitmapFilters.rotate(out, rotation) } ?: out
            out = withContext(Dispatchers.IO) { BitmapFilters.flip(out, flipH, flipV) } ?: out
            _editBitmap.value = out
            rerenderColor()
        }
    }

    // ── 色彩与滤镜 ──

    fun setFilter(preset: FilterPreset?) {
        // NONE 滤镜视为无滤镜
        val effective = preset?.takeIf { it.style != FilterStyle.NONE }
        if (_filter.value?.style == effective?.style && _lutFilter.value == null) return
        pushUndo()
        _filter.value = effective
        _lutFilter.value = null
        rerenderColor()
    }

    /** 选择 mola LUT 滤镜(与 ColorMatrix 滤镜互斥)。id=null 清除。 */
    fun setLutFilter(id: String?) {
        if (_lutFilter.value == id) return
        pushUndo()
        _lutFilter.value = id
        if (id != null) _filter.value = null
        rerenderColor()
    }

    /** 滑块实时调整：更新参数并重渲染，但不压撤销栈（避免拖动几卡一次撤销） */
    fun setAdjustLive(delta: FilterParams.() -> FilterParams) {
        _adjusts.value = delta(_adjusts.value)
        rerenderColor()
    }

    /** 滑块结束/点按时提交：记录撤销点（重复调用会 push 多次，简化处理：先清再压） */
    fun commitAdjust() {
        val current = _editBitmap.value ?: return
        if (undoStack.isNotEmpty() && undoStack.last() === current) return
        pushUndo()
    }

    /** 更新单个调整参数并立即渲染（点按/结束调用，带撤销点） */
    fun updateAdjust(delta: FilterParams.() -> FilterParams) {
        pushUndo()
        _adjusts.value = delta(_adjusts.value)
        rerenderColor()
    }

    fun setSharpen(amount: Float) {
        pushUndo()
        sharpenAmount = amount.coerceIn(0f, 2f)
        rerenderColor()
    }

    /** 锐化实时调整（不压栈） */
    fun setSharpenLive(amount: Float) {
        sharpenAmount = amount.coerceIn(0f, 2f)
        rerenderColor()
    }

    /** 重置全部色彩调整 */
    fun resetAdjusts() {
        if (_adjusts.value == FilterParams() && sharpenAmount == 0f && _filter.value == null && _lutFilter.value == null) return
        pushUndo()
        _adjusts.value = FilterParams()
        sharpenAmount = 0f
        _filter.value = null
        _lutFilter.value = null
        rerenderColor()
    }

    private fun rerenderColor() {
        val base = _editBitmap.value ?: return
        viewModelScope.launch {
            delay(80) // 防抖：滑块拖动不连续触发重渲染
            val a = _adjusts.value
            val preset = _filter.value
            val lutId = _lutFilter.value
            val sharp = sharpenAmount
            val out = withContext(Dispatchers.IO) {
                var bmp = if (lutId != null) {
                    // mola LUT 路径：先调色调整(ColorMatrix)，再套 LUT(boost+三线性)
                    val lut = findLut(lutId)
                    val adjusted = BitmapFilters.applyFilter(
                        base,
                        saturation = a.saturation,
                        contrast = a.contrast,
                        brightness = a.brightness,
                        warmth = a.warmth,
                        vignette = a.vignette
                    )
                    if (lut != null) MolaLut.apply(adjusted, lut) else adjusted
                } else {
                    BitmapFilters.applyFilter(
                        base,
                        saturation = if (preset != null) preset.saturation * a.saturation else a.saturation,
                        contrast = if (preset != null) preset.contrast * a.contrast else a.contrast,
                        brightness = if (preset != null) preset.brightness + a.brightness else a.brightness,
                        warmth = if (preset != null) preset.warmth + a.warmth else a.warmth,
                        vignette = if (preset != null) preset.vignette + a.vignette else a.vignette
                    )
                }
                if (sharp > 0f) bmp = BitmapFilters.sharpen(bmp, sharp)
                bmp
            }
            _editBitmap.value = out
        }
    }

    /** 按 id 解码 LUT(IO 线程内调用)。 */
    private fun findLut(id: String): MolaLut.LutData? {
        val f = LutRepository.find(id) ?: return null
        val bytes = runCatching { context.assets.open("luts/${f.lutFile}").readBytes() }.getOrNull() ?: return null
        return MolaLut.decode(bytes)
    }

    // ── 文字叠加 ──

    fun addText(text: String, pos: Offset = Offset(0.5f, 0.5f)) {
        if (text.isBlank()) return
        _texts.value = _texts.value + TextOverlayData(id = nextOverlayId++, text = text, pos = pos)
    }

    fun moveText(id: Int, pos: Offset) {
        _texts.value = _texts.value.map { if (it.id == id) it.copy(pos = pos) else it }
    }

    fun removeText(id: Int) {
        _texts.value = _texts.value.filterNot { it.id == id }
    }

    // ── 贴纸叠加 ──

    fun addSticker(type: StickerOverlayType, pos: Offset = Offset(0.5f, 0.5f)) {
        _stickers.value = _stickers.value + StickerOverlayData(id = nextOverlayId++, type = type, pos = pos)
    }

    fun moveSticker(id: Int, pos: Offset) {
        _stickers.value = _stickers.value.map { if (it.id == id) it.copy(pos = pos) else it }
    }

    fun removeSticker(id: Int) {
        _stickers.value = _stickers.value.filterNot { it.id == id }
    }

    // ── 水印 ──

    fun setWatermark(mode: WatermarkKind) {
        if (_watermark.value == mode) return
        _watermark.value = mode
    }

    // ── 撤销 / 保存 ──

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        _editBitmap.value = prev
        _canUndo.value = undoStack.isNotEmpty()
        _toast.value = "已撤销"
    }

    private fun pushUndo() {
        val current = _editBitmap.value ?: return
        if (undoStack.size >= 8) undoStack.removeFirst()
        undoStack.addLast(current)
        _canUndo.value = true
    }

    /** 保存：几何+色彩 base 上合成文字/贴纸/水印 → JPEG → 相册 */
    fun save() {
        viewModelScope.launch {
            _saving.value = true
            val base = _editBitmap.value
            val texts = _texts.value
            val stickers = _stickers.value
            val wm = _watermark.value
            if (base == null) {
                _toast.value = "图片加载失败"
                _saving.value = false
                return@launch
            }
            val file = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
            var ok = false
            withContext(Dispatchers.IO) {
                try {
                    val finalBmp = BitmapFilters.composeOverlays(base, texts, stickers, wm)
                    file.outputStream().use { finalBmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    ok = true
                } catch (_: Exception) { ok = false }
            }
            if (ok) {
                ok = PhotoStore.saveImage(context, file)
                file.delete()
            }
            _saved.value = ok
            _toast.value = if (ok) "已保存到相册" else "保存失败"
            _saving.value = false
        }
    }

    fun onToastShown() { _toast.value = null }

    private suspend fun <T> withContextSafe(block: suspend () -> T): T? =
        try { withContext(Dispatchers.IO) { block() } } catch (_: Exception) { null }
}