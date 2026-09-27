package com.aicamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicamera.settings.GridMode
import com.aicamera.settings.ShutterSensitivity
import com.aicamera.ui.theme.IosBlue
import com.aicamera.ui.theme.IosGray
import com.aicamera.ui.theme.IosRed
import com.aicamera.ui.theme.IosSystemBackground
import com.aicamera.ui.theme.IosSystemGroupedBackground
import com.aicamera.ui.theme.IosSystemLabel
import com.aicamera.ui.theme.IosSystemSecondaryGroupedBackground
import com.aicamera.ui.theme.IosSystemSecondaryLabel
import com.aicamera.ui.theme.IosSystemSeparator
import com.aicamera.viewmodel.CompositionViewModel

/**
 * iOS 风格设置页：分组圆角列表（UIGroupedList）。
 * 每组一个圆角卡片，行间细分隔线，支持开关 / 分段控件 / 弹跳选项。
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
            .background(IosSystemBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 顶栏（iOS 大标题）──
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
                    tint = IosBlue
                )
            }
            Text(
                "相机设置",
                color = IosSystemLabel,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── 组 1：构图网格 ──
        IosSettingsSection(title = "构图网格") {
            IosSettingsOption(
                label = "无网格",
                icon = null,
                iconColor = IosGray,
                selected = settings.gridMode == GridMode.NONE,
                onClick = { viewModel.updateGridMode(GridMode.NONE) },
                showDivider = true
            )
            IosSettingsOption(
                label = "三分法",
                icon = null,
                iconColor = IosGray,
                selected = settings.gridMode == GridMode.THIRDS,
                onClick = { viewModel.updateGridMode(GridMode.THIRDS) },
                showDivider = true
            )
            IosSettingsOption(
                label = "黄金分割",
                icon = null,
                iconColor = IosGray,
                selected = settings.gridMode == GridMode.GOLDEN,
                onClick = { viewModel.updateGridMode(GridMode.GOLDEN) },
                showDivider = false
            )
        }

        // ── 组 2：画面检测 ──
        IosSettingsSection(title = "画面检测") {
            IosSettingsSwitch(
                label = "显示主体检测框",
                checked = settings.showSubjects,
                onChecked = { viewModel.updateShowSubjects(it) },
                showDivider = true
            )
            IosSettingsSwitch(
                label = "显示构图评分",
                checked = settings.showScore,
                onChecked = { viewModel.updateShowScore(it) },
                showDivider = false
            )
        }

        // ── 组 3：自动快门 ──
        IosSettingsSection(title = "自动快门") {
            IosSettingsSwitch(
                label = "开启自动快门",
                checked = settings.autoShutter,
                onChecked = { viewModel.updateAutoShutter(it) },
                showDivider = settings.autoShutter
            )
            if (settings.autoShutter) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        "灵敏度",
                        color = IosSystemSecondaryLabel,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    IosSegmentedControl(
                        options = listOf(
                            ShutterSensitivity.HIGH to "高",
                            ShutterSensitivity.MEDIUM to "中",
                            ShutterSensitivity.LOW to "低"
                        ),
                        selected = settings.shutterSensitivity,
                        onSelect = { viewModel.updateSensitivity(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── 组 4：高级 ──
        IosSettingsSection(title = "高级") {
            IosSettingsSwitch(
                label = "姿态引导",
                subLabel = "需重启生效",
                checked = settings.poseGuidance,
                onChecked = { viewModel.updatePoseGuidance(it) },
                showDivider = true
            )
            IosSettingsSwitch(
                label = "震动反馈",
                checked = settings.hapticFeedback,
                onChecked = { viewModel.updateHaptic(it) },
                showDivider = false
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

/** iOS 分组标题（小号大写灰字） */
@Composable
private fun IosSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        color = IosGray,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 18.dp, top = 22.dp, bottom = 6.dp)
    )
}

/** iOS 分组圆角卡片 */
@Composable
private fun IosGroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable Column.() -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(IosSystemSecondaryGroupedBackground)
    ) {
        content()
    }
}

/** 设置分组容器 */
@Composable
private fun IosSettingsSection(
    title: String,
    content: @Composable Column.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        IosSectionTitle(title)
        IosGroupedCard {
            content()
        }
    }
}

/** iOS 设置行（带分隔线） */
@Composable
private fun IosDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 14.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(IosSystemSeparator)
    )
}

/** 单选行（iOS 打勾样式） */
@Composable
private fun IosSettingsOption(
    label: String,
    icon: ImageVector?,
    iconColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = IosSystemLabel,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text(
                    text = "✓",
                    color = IosBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showDivider) IosDivider()
    }
}

/** 开关行（iOS UISwitch） */
@Composable
private fun IosSettingsSwitch(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    showDivider: Boolean,
    subLabel: String? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onChecked(!checked) }
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = IosSystemLabel,
                    fontSize = 16.sp
                )
                subLabel?.let {
                    Text(
                        text = it,
                        color = IosSystemSecondaryLabel,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            IosSwitch(
                checked = checked,
                onCheckedChange = onChecked
            )
        }
        if (showDivider) IosDivider()
    }
}