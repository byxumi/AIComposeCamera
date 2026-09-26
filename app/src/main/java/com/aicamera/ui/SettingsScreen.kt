package com.aicamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicamera.settings.GridMode
import com.aicamera.settings.ShutterSensitivity
import com.aicamera.ui.theme.NeoAccent
import com.aicamera.ui.theme.NeoBackground
import com.aicamera.ui.theme.NeoBackgroundCard
import com.aicamera.ui.theme.NeoTextPrimary
import com.aicamera.ui.theme.NeoTextSecondary
import com.aicamera.viewmodel.CompositionViewModel

/**
 * 设置界面：网格模式、主体框、评分、自动快门、灵敏度、姿势引导、震动反馈。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: CompositionViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeoBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = NeoTextPrimary
                )
            }
            Text(
                "相机设置",
                color = NeoTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // 网格
        SettingSection(title = "构图网格") {
            GridMode.entries.forEach { mode ->
                SettingRadioRow(
                    label = when (mode) {
                        GridMode.NONE -> "无网格"
                        GridMode.THIRDS -> "三分法"
                        GridMode.GOLDEN -> "黄金分割"
                    },
                    selected = settings.gridMode == mode,
                    onSelect = { viewModel.updateGridMode(mode) }
                )
            }
        }

        // 检测显示
        SettingSection(title = "画面检测") {
            SettingSwitchRow(
                label = "显示主体检测框",
                checked = settings.showSubjects,
                onChecked = { viewModel.updateShowSubjects(it) }
            )
            SettingSwitchRow(
                label = "显示构图评分",
                checked = settings.showScore,
                onChecked = { viewModel.updateShowScore(it) }
            )
        }

        // 自动快门
        SettingSection(title = "自动快门") {
            SettingSwitchRow(
                label = "开启自动快门",
                checked = settings.autoShutter,
                onChecked = { viewModel.updateAutoShutter(it) }
            )
            if (settings.autoShutter) {
                Text(
                    "灵敏度",
                    color = NeoTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp)
                )
                ShutterSensitivity.entries.forEach { s ->
                    SettingRadioRow(
                        label = when (s) {
                            ShutterSensitivity.HIGH -> "高（80 分以上）"
                            ShutterSensitivity.MEDIUM -> "中（70 分以上）"
                            ShutterSensitivity.LOW -> "低（60 分以上）"
                        },
                        selected = settings.shutterSensitivity == s,
                        onSelect = { viewModel.updateSensitivity(s) }
                    )
                }
            }
        }

        // 高级
        SettingSection(title = "高级") {
            SettingSwitchRow(
                label = "姿态引导（需重启生效）",
                checked = settings.poseGuidance,
                onChecked = { viewModel.updatePoseGuidance(it) }
            )
            SettingSwitchRow(
                label = "震动反馈",
                checked = settings.hapticFeedback,
                onChecked = { viewModel.updateHaptic(it) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            color = NeoAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        Surface(
            color = NeoBackgroundCard,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = NeoTextPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier.width(52.dp)
        )
    }
}

@Composable
private fun SettingRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = label,
            color = NeoTextPrimary,
            fontSize = 15.sp
        )
    }
}