package com.aicamera.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * v4 组件 — 受控玻璃 + 纯黑相机 UI。
 *
 * 仅 1 处毛玻璃: [CamFrostedPanel] (底部控制面板, 有真实层级理由)。
 * 其余全部: 纯黑底 + 白色高对比控件 + 单一强调色 [CamColors.Accent]。
 */

/** 受控毛玻璃面板: 底部控制区 (唯一玻璃层) — 70% 黑 + 顶部 1dp 高光线 */
@Composable
fun CamFrostedPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CamColors.Frosted)
            .drawBehind {
                drawLine(
                    color = CamColors.FrostedStroke,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        content()
    }
}

/** 相机顶部工具按钮: 白色线框圆钮, 44dp, 黑 35% 底座 */
@Composable
fun CamIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CamColors.White,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, CamColors.White.copy(alpha = 0.28f), CircleShape)
            .background(CamColors.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 文本小按钮 (模式标签 / 滤镜名) — 选中 = 强调色描边 + 20% 强调底 */
@Composable
fun CamTextButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CamColors.White
) {
    Box(
        modifier = modifier
            .clip(CamShapes.Control)
            .background(if (selected) CamColors.AccentDim else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) CamColors.Accent.copy(alpha = 0.6f) else Color.Transparent,
                shape = CamShapes.Control
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) CamColors.Accent else tint,
            style = CamType.BodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 快门: 白色双环 (录制时为红方角) — 按压回弹 0.92 */
@Composable
fun CamShutter(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "shutterPress"
    )
    val outer = if (isRecording) CamColors.Error else CamColors.White
    Box(
        modifier = modifier
            .size(76.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(4.dp, outer, CircleShape)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) 34.dp else 58.dp)
                .clip(CamShapes.Small)
                .background(outer)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
        )
    }
}

/** AI 目标圆圈: 黄圈 + 双轴 + 中心点; 未达标显示黄色方向箭头, 达标转绿 */
@Composable
fun CamTargetRing(
    cx: Float,
    cy: Float,
    radiusPx: Float,
    reached: Boolean,
    moveX: Float = 0f,
    moveY: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(cx * size.width, cy * size.height)
        val r = radiusPx
        val color = if (reached) CamColors.Success else CamColors.Accent

        drawCircle(color, radius = r, center = center, style = Stroke(2.dp.toPx()))
        drawCircle(color, radius = r * 0.84f, center = center, style = Stroke(1.dp.toPx()))
        drawLine(
            color,
            Offset(center.x - r * 0.55f, center.y),
            Offset(center.x + r * 0.55f, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color,
            Offset(center.x, center.y - r * 0.55f),
            Offset(center.x, center.y + r * 0.55f),
            strokeWidth = 1.dp.toPx()
        )
        drawCircle(color, radius = 2.dp.toPx(), center = center)

        if (!reached && (moveX != 0f || moveY != 0f)) {
            val len = r * 0.5f
            val ax = center.x + moveX * len
            val ay = center.y + moveY * len
            val angle = kotlin.math.atan2(moveY.toDouble(), moveX.toDouble())
            val headLen = 10.dp.toPx()
            val path = Path().apply {
                moveTo(ax, ay)
                lineTo(
                    (ax - headLen * cos(angle - 0.5)).toFloat(),
                    (ay - headLen * sin(angle - 0.5)).toFloat()
                )
                lineTo(
                    (ax - headLen * cos(angle + 0.5)).toFloat(),
                    (ay - headLen * sin(angle + 0.5)).toFloat()
                )
                close()
            }
            drawPath(path, CamColors.Accent)
        }
    }
}

/** 目标圈脉动版 (AI 辅助开启时) — 存在感动效, 减速时静止 */
@Composable
fun PulsingTarget(
    cx: Float,
    cy: Float,
    radiusPx: Float,
    reached: Boolean,
    moveX: Float = 0f,
    moveY: Float = 0f,
    modifier: Modifier = Modifier
) {
    val pulse = rememberPulse(0.92f..1.12f)
    Box(modifier = modifier) {
        CamTargetRing(cx, cy, radiusPx, reached, moveX, moveY, Modifier.fillMaxSize())
        if (pulse != 1f) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = CamColors.Accent.copy(alpha = (1f - pulse) * 0.4f),
                    radius = radiusPx * pulse,
                    center = Offset(cx * size.width, cy * size.height),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
    }
}

/** 底部面板: 滑入 + 淡入 (260ms 受控动效) */
@Composable
fun AnimatedBottomPanel(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeOut(animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)),
        modifier = modifier
    ) {
        content()
    }
}

/** 玻璃主按钮 (Accent 填充, 黑字) */
@Composable
fun CamButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CamShapes.Control,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) CamColors.Accent else Color.Transparent,
            contentColor = CamColors.Black,
            disabledContainerColor = CamColors.SurfaceElevated,
            disabledContentColor = CamColors.TertiaryText
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(text, style = CamType.BodyMedium)
    }
}

/** 对话框: 纯黑面 + 24dp 圆角 + 强调确认按钮 */
@Composable
fun CamDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = CamShapes.Panel,
        color = CamColors.Surface
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                title,
                style = CamType.Title,
                color = CamColors.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消", color = CamColors.SecondaryText, style = CamType.Body)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = CamShapes.Control,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CamColors.Accent,
                        contentColor = CamColors.Black
                    )
                ) {
                    Text(confirmText, style = CamType.BodyMedium)
                }
            }
        }
    }
}

/** 评分角标: 灰底白字圆角 (不作装饰色) */
@Composable
fun ScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CamShapes.Small)
            .background(CamColors.Black.copy(alpha = 0.55f))
            .border(1.dp, CamColors.White.copy(alpha = 0.2f), CamShapes.Small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            "构图 $score",
            color = CamColors.White,
            style = CamType.Caption
        )
    }
}