package com.aicamera.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.aicamera.core.design.CamColors
import com.aicamera.core.design.CamIconButton
import com.aicamera.core.design.CamType
import com.aicamera.data.GalleryRepository
import com.aicamera.domain.model.GalleryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 相册页 ViewModel：MediaStore 照片列表 */
class GalleryViewModel(
    private val repository: GalleryRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<GalleryItem>>(emptyList())
    val items: StateFlow<List<GalleryItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _failed.value = false
            try {
                _items.value = repository.loadImages()
            } catch (e: Exception) {
                _failed.value = true
            }
            _loading.value = false
        }
    }
}

/**
 * v4 相册页 — 2 列网格瀑布 + 全屏大图查看。
 * 白色缩略图栅格, 黑色大图浏览, 无装饰色。
 */
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: GalleryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                GalleryViewModel(GalleryRepository(app))
            }
        }
    )
) {
    val context = LocalContext.current
    val images by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val failed by viewModel.failed.collectAsState()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permLauncher.launch(
                if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    // 大图查看状态
    var viewerUri by remember { mutableStateOf<String?>(null) }

    if (viewerUri != null) {
        // ── 全屏大图 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CamColors.Black)
                .clickable { viewerUri = null }
        ) {
            AsyncImage(
                model = Uri.parse(viewerUri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                CamIconButton(
                    icon = Icons.Filled.ArrowBack,
                    onClick = { viewerUri = null },
                    contentDescription = "返回"
                )
            }
            Text(
                text = "点按调色",
                color = CamColors.White.copy(alpha = 0.6f),
                style = CamType.Caption,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CamColors.Black)
            .statusBarsPadding()
    ) {
        // ── 顶栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CamIconButton(
                icon = Icons.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "返回"
            )
            Text(
                text = "相册",
                color = CamColors.White,
                style = CamType.ScreenTitle,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = CamColors.SecondaryText, style = CamType.Body)
                }
            }
            failed -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("读取相册失败", color = CamColors.SecondaryText, style = CamType.Body)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "重试",
                        color = CamColors.Accent,
                        style = CamType.BodyMedium,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .clickable { viewModel.refresh() }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
            images.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = CamColors.TertiaryText,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "还没有照片\n回相机拍一张吧",
                        color = CamColors.SecondaryText,
                        style = CamType.Body,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(images, key = { it.id }) { item ->
                        AsyncImage(
                            model = Uri.parse(item.uri),
                            contentDescription = item.uri,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .clickable { viewerUri = item.uri }
                        )
                    }
                }
            }
        }
    }
}
