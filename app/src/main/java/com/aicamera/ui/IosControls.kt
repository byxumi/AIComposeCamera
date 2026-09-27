package com.aicamera.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.ui.theme.IosBlue
import com.aicamera.ui.theme.IosFrostedDark
import com.aicamera.ui.theme.IosSystemLabel
import com.aicamera.ui.theme.IosSystemSecondaryLabel

/**
 * iOS 风格开关（UISwitch）：白底 + 绿/蓝色滑块圆钮。
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackColor: Color = IosBlue
) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val knobSize = 27.dp
    val knobOffset by animateDpAsState(
        targetValue = if (checked) (trackWidth - knobSize - 2.dp) else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "iosSwitch"
    )
    val bg by animateColorAsState(
        targetValue = if (checked) trackColor else Color(0xFFE9E9EA),
        label = "iosSwitchBg"
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(knobSize)
                .clip(CircleShape)
                .background(Color.White)
                .border(0.5.dp, Color(0x33000000), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // 高光
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }
    }
}

/**
 * iOS 分段控件（UISegmentedControl）：胶囊底 + 选中块滑块。
 */
@Composable
fun <T> IosSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = IosFrostedDark.copy(alpha = 0.35f),
    selectedColor: Color = Color.White,
    textColor: Color = IosSystemLabel,
    selectedTextColor: Color = Color.Black
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) selectedColor else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(value) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) selectedTextColor else textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * iOS 风格滑杆（UISlider）：蓝色轨道 + 白色圆钮。
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeColor: Color = IosBlue,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f)
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor
        )
    )
}

/** iOS 毛玻璃胶囊按钮（顶部工具栏） */
@Composable
fun IosToolButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tintColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(IosFrostedDark.copy(alpha = 0.85f))
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}