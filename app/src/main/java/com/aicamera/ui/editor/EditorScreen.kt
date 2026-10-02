package com.aicamera.ui.editor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.aicamera.core.design.CamColors
import com.aicamera.core.design.CamShapes
import com.aicamera.core.design.CamType
import com.aicamera.core.util.BitmapFilters
import com.aicamera.data.PhotoStore
import com.aicamera.domain.model.FilterPreset
import com.aicamera.domain.model.FilterStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 编辑器 ViewModel: 采样解码 + 滤镜应用（后台线程） */
class EditorViewModel(private val context: android.content.Context, private val uriString: String) : ViewModel() {

    private val _bitmap = MutableStateFlow<ImageBitmap?>(null)
    val bitmap: StateFlow<ImageBitmap?> = _bitmap.asStateFlow()

    private val _filter = MutableStateFlow(FilterPreset(FilterStyle.NONE))
    val filter: StateFlow<FilterPreset> = _filter.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

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

    private var source: android.graphics.Bitmap? = null

    init {
        loadSource()
    }

    private fun loadSource() {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeSampled(uriString) }
            source = bmp
            _bitmap.value = bmp?.asImageBitmap()
        }
    }

    private fun decodeSampled(uriString: String): android.graphics.Bitmap? {
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

    fun selectFilter(preset: FilterPreset) {
        _filter.value = preset
        viewModelScope.launch {
            val src = source ?: return@launch
            val out = withContext(Dispatchers.IO) {
                BitmapFilters.applyFilter(
                    src,
                    saturation = preset.saturation,
                    contrast = preset.contrast,
                    brightness = preset.brightness,
                    warmth = preset.warmth,
                    vignette = preset.vignette
                )
            }
            _bitmap.value = out.asImageBitmap()
        }
    }

    fun save() {
        viewModelScope.launch {
            _saving.value = true
            val bmp = source?.let { src ->
                withContext(Dispatchers.IO) {
                    BitmapFilters.applyFilter(
                        src,
                        saturation = _filter.value.saturation,
                        contrast = _filter.value.contrast,
                        brightness = _filter.value.brightness,
                        warmth = _filter.value.warmth,
                        vignette = _filter.value.vignette
                    )
                }
            }
            if (bmp != null) {
                val file = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
                var ok = false
                withContext(Dispatchers.IO) {
                    ok = try {
                        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                        true
                    } catch (_: Exception) { false }
                }
                if (ok) {
                    ok = PhotoStore.saveImage(context, file)
                    file.delete()
                }
                _saved.value = ok
                _toast.value = if (ok) "已保存到相册" else "保存失败"
            } else {
                _toast.value = "图片加载失败"
            }
            _saving.value = false
        }
    }

    fun onToastShown() { _toast.value = null }
}

/** 编辑器页 — 全屏图片 + 底部滤镜横条 + 保存 */
@Composable
fun EditorScreen(
    imageUri: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                EditorViewModel(
                    context = app,
                    uriString = imageUri
                )
            }
        }
    )
) {
    val bitmap by viewModel.bitmap.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val toast by viewModel.toast.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(saved) { if (saved) onBack() }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1800)
            viewModel.onToastShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CamColors.Black)
            .statusBarsPadding()
    ) {
        // 图片
        val bmp = bitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = "编辑中图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (bmp.width > bmp.height) 16f / 9f else 3f / 4f),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = CamColors.SecondaryText, style = CamType.Body)
            }
        }

        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = CamColors.Accent, style = CamType.ScreenTitle)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CamShapes.Control)
                    .background(if (saving) CamColors.SurfaceElevated else CamColors.Accent)
                    .clickable(enabled = !saving, onClick = viewModel::save)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    if (saving) "保存中…" else "保存",
                    color = if (saving) CamColors.TertiaryText else CamColors.Black,
                    style = CamType.BodyMedium
                )
            }
        }

        // Toast
        toast?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clip(CamShapes.Control)
                    .background(CamColors.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(it, color = CamColors.White, style = CamType.Body)
            }
        }

        // 底部滤镜条
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CamColors.Black.copy(alpha = 0.9f))
                .padding(bottom = 16.dp)
        ) {
            Text(
                "滤镜",
                color = CamColors.SecondaryText,
                style = CamType.Secondary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    FilterDot(
                        label = "原图",
                        color = Color.Transparent,
                        selected = filter.style == FilterStyle.NONE
                    ) { viewModel.selectFilter(viewModel.filters[0]) }
                }
                items(viewModel.filters.drop(1)) { preset ->
                    FilterDot(
                        label = preset.style.label,
                        color = Color(preset.style.accent),
                        selected = filter.style == preset.style
                    ) { viewModel.selectFilter(preset) }
                }
            }
        }
    }
}

/** 滤镜圆点选择器 */
@Composable
private fun FilterDot(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (color == Color.Transparent) CamColors.SurfaceElevated else color)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = CamColors.Accent,
                    shape = CircleShape
                )
                .padding(if (selected) 2.dp else 0.dp)
        )
        Text(
            label,
            color = if (selected) CamColors.Accent else CamColors.SecondaryText,
            style = CamType.Caption,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
