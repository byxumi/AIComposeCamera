package com.aicamera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aicamera.core.design.CamColors
import com.aicamera.core.design.CamShapes
import com.aicamera.core.design.CamType
import com.aicamera.data.SettingsRepository
import com.aicamera.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch { repository.settings.collect { _settings.value = it } }
    }

    fun updateGridMode(v: Int) = viewModelScope.launch { repository.updateGridMode(v) }
    fun updateShowSubjects(v: Boolean) = viewModelScope.launch { repository.updateShowSubjects(v) }
    fun updateShowScore(v: Boolean) = viewModelScope.launch { repository.updateShowScore(v) }
    fun updateAutoShutter(v: Boolean) = viewModelScope.launch { repository.updateAutoShutter(v) }
    fun updateShutterSensitivity(v: Int) = viewModelScope.launch { repository.updateShutterSensitivity(v) }
    fun updatePoseGuidance(v: Boolean) = viewModelScope.launch { repository.updatePoseGuidance(v) }
    fun updateHaptic(v: Boolean) = viewModelScope.launch { repository.updateHaptic(v) }
    fun updateShutterSound(v: Boolean) = viewModelScope.launch { repository.updateShutterSound(v) }
    fun updateSaveLocation(v: Boolean) = viewModelScope.launch { repository.updateSaveLocation(v) }
    fun updateWatermarkMode(v: String) = viewModelScope.launch { repository.updateWatermarkMode(v) }
}

/**
 * 设置页 — iOS 风格分组列表: 纯黑底, 圆角分组卡片, 右侧开关。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                SettingsViewModel(SettingsRepository(app))
            }
        }
    )
) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CamColors.Black)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
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
            Text(
                "设置",
                color = CamColors.White,
                style = CamType.ScreenTitle,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SettingsSection("构图") {
                ChoiceRow(
                    options = listOf("九宫格", "中心"),
                    selectedIndex = settings.gridMode.takeIf { it in 0..1 } ?: 1,
                    onSelect = { viewModel.updateGridMode(it) }
                )
                ToggleRow("显示主体框", settings.showSubjects, viewModel::updateShowSubjects)
                ToggleRow("显示构图评分", settings.showScore, viewModel::updateShowScore)
                ToggleRow("姿势引导", settings.poseGuidance, viewModel::updatePoseGuidance)
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection("拍摄") {
                ToggleRow("自动快门", settings.autoShutter, viewModel::updateAutoShutter)
                if (settings.autoShutter) {
                    SensitivityRow(settings.shutterSensitivity, viewModel::updateShutterSensitivity)
                }
                ToggleRow("快门音", settings.shutterSound, viewModel::updateShutterSound)
                ToggleRow("触感反馈", settings.hapticFeedback, viewModel::updateHaptic)
                ToggleRow("保存到独立相册", settings.saveLocation, viewModel::updateSaveLocation)
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection("高级") {
                ChoiceRow(
                    options = listOf("无水印", "日期", "品牌"),
                    selectedIndex = when (settings.watermarkMode) {
                        "date" -> 1
                        "brand" -> 2
                        else -> 0
                    },
                    onSelect = { i ->
                        viewModel.updateWatermarkMode(arrayOf("none", "date", "brand")[i])
                    }
                )
            }

            Spacer(Modifier.height(40.dp))
            Text(
                "AI 构图相机 v4.0.0",
                color = CamColors.TertiaryText,
                style = CamType.Caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 分组卡片容器 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        title,
        color = CamColors.SecondaryText,
        style = CamType.Secondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CamShapes.Panel)
            .background(CamColors.Surface)
    ) {
        content()
    }
}

/** 开关行 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = CamColors.White, style = CamType.Body, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CamColors.White,
                checkedTrackColor = CamColors.Accent,
                uncheckedThumbColor = CamColors.White,
                uncheckedTrackColor = CamColors.SurfaceElevated,
                uncheckedBorderColor = CamColors.Separator
            )
        )
    }
}

/** 标签行 (网格样式/水印) */
@Composable
private fun ChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("网格样式", color = CamColors.White, style = CamType.Body, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .clip(CamShapes.Control)
                        .background(
                            if (index == selectedIndex) CamColors.AccentDim
                            else CamColors.SurfaceElevated
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (index == selectedIndex) CamColors.Accent else CamColors.SecondaryText,
                        style = CamType.Secondary
                    )
                }
            }
        }
    }
}

/** 灵敏度滑杆行 */
@Composable
private fun SensitivityRow(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            "自动快门灵敏度 ($value 分)",
            color = CamColors.SecondaryText,
            style = CamType.Secondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 40f..90f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = CamColors.White,
                activeTrackColor = CamColors.Accent,
                inactiveTrackColor = CamColors.SurfaceElevated
            )
        )
    }
}
