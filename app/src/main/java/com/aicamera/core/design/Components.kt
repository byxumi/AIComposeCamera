package com.aicamera.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
    tint: Color = CamColors.White,
    compact: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(CamShapes.Control)
            .then(
                if (selected) Modifier.background(
                    Brush.horizontalGradient(
                        listOf(CamColors.AccentLight, CamColors.AccentStrong)
                    )
                ) else Modifier
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) CamColors.Accent.copy(alpha = 0.6f) else Color.Transparent,
                shape = CamShapes.Control
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 6.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) CamColors.Black else tint,
            style = CamType.BodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 快门 — mola 精确复刻 (rj0.java b0/e0): 80dp 外环深金 22% + 64dp 内环深金 12% + 金色渐变描边; 视频模式内圆红色 0xFFE5484D, 录制中 28dp 红色停止方块 */
@Composable
fun CamShutter(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdMode: Boolean = false,
    holding: Boolean = false,
    onHoldStart: () -> Unit = {},
    onHoldEnd: () -> Unit = {},
    video: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        // mola rj0: shutterScale=spring(0.5,400)+0.92 按压; holdShutterScale=spring(0.5,1500)+0.82 按住流光
        targetValue = if (holding) 0.82f else if (pressed) 0.92f else 1f,
        animationSpec = if (holding) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium) else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "shutterScale"
    )
    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(CamColors.AccentStrong.copy(alpha = 0.22f))
            .border(2.dp, CamColors.AccentLight.copy(alpha = 0.55f), CircleShape)
            .then(
                if (holdMode) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onHoldStart()
                                try {
                                    awaitRelease()
                                } finally {
                                    onHoldEnd()
                                }
                            }
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) 64.dp else 64.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isRecording -> CamColors.Error
                        video -> Color(0xFFE5484D)
                        else -> CamColors.AccentStrong.copy(alpha = 0.12f)
                    }
                )
                .clickable(
                    enabled = enabled && !holdMode,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
        )
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CamShapes.Small)
                    .background(CamColors.Error)
            )
        }
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

/**
 * mola 风格 AI 目标圈 — 逆向 si.java case 0:
 * 金色双圈 + 5 段扫弧 + 中心呼吸环 + 到达后旋转扫光弧 + 4 条正交刻度。
 */
@Composable
fun MolaTargetRing(
    cx: Float,
    cy: Float,
    radiusPx: Float,
    reached: Boolean,
    modifier: Modifier = Modifier
) {
    val sweep by rememberInfiniteTransition(label = "molaSweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CamMotion.PulseMillis * 2, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "molaSweep"
    )
    val breath = rememberPulse(0.94f..1.06f)
    val gold = CamColors.Accent
    val deepGold = CamColors.AccentStrong
    val brightGold = CamColors.AccentLight
    Canvas(modifier = modifier) {
        val center = Offset(cx * size.width, cy * size.height)
        val r = radiusPx
        val thin = 1.dp.toPx()
        val thick = 2.dp.toPx()

        // 外圈 (金色)
        drawCircle(gold.copy(alpha = 0.9f), radius = r, center = center, style = Stroke(thick))
        // 内圈 (深金)
        drawCircle(deepGold.copy(alpha = 0.7f), radius = r * 0.84f, center = center, style = Stroke(thin))

        // 5 段扫弧: 随 sweep 依次亮起 (与 si.java 5 段渐变更一致)
        for (i in 0 until 5) {
            val segStart = (i / 5f) * 360f
            val segEnd = segStart + 60f
            val localAlpha = ((sweep * 5f - i).coerceIn(0f, 1f))
            if (localAlpha > 0.01f) {
                drawArc(
                    color = brightGold.copy(alpha = localAlpha * 0.85f),
                    startAngle = segStart - 90f,
                    sweepAngle = segEnd - segStart,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = Stroke(thick, cap = StrokeCap.Round)
                )
            }
        }

        // 中心呼吸环
        drawCircle(
            color = gold.copy(alpha = 0.55f * breath),
            radius = 2.2.dp.toPx() * breath,
            center = center,
            style = Stroke(1.5.dp.toPx())
        )
        drawCircle(Color.Black.copy(alpha = 0.28f), radius = 1.5.dp.toPx(), center = center)

        // 到达后: 旋转扫光弧 (si.java E0: 起扫 -90°-(f*180+180)/2, 扫 180°)
        if (reached) {
            val rot = sweep * 360f
            rotate(rot, center) {
                drawArc(
                    color = brightGold.copy(alpha = 0.5f),
                    startAngle = -90f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = Stroke(thick, cap = StrokeCap.Round)
                )
            }
            // 4 条正交刻度线
            for (i in 0 until 4) {
                val angle = i * (Math.PI / 2)
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                drawLine(
                    color = gold.copy(alpha = 0.5f),
                    start = Offset(center.x + cosA * r * 0.7f, center.y + sinA * r * 0.7f),
                    end = Offset(center.x + cosA * r * 0.95f, center.y + sinA * r * 0.95f),
                    strokeWidth = thick
                )
            }
        } else {
            // 未到达: 移动方向箭头提示
            val arrowLen = r * 0.35f
            drawLine(
                color = brightGold.copy(alpha = 0.8f),
                start = Offset(center.x - arrowLen, center.y),
                end = Offset(center.x + arrowLen, center.y),
                strokeWidth = thick,
                cap = StrokeCap.Round
            )
            drawLine(
                color = brightGold.copy(alpha = 0.8f),
                start = Offset(center.x, center.y - arrowLen),
                end = Offset(center.x, center.y + arrowLen),
                strokeWidth = thick,
                cap = StrokeCap.Round
            )
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