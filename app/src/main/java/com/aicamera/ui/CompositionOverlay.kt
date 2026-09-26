package com.aicamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.composition.DetectedSubject
import com.aicamera.composition.Guidance
import com.aicamera.composition.GuidanceType
import com.aicamera.composition.OverlayState
import com.aicamera.composition.Severity
import com.aicamera.settings.GridMode
import com.aicamera.ui.theme.ArrowColor
import com.aicamera.ui.theme.FrameColor
import com.aicamera.ui.theme.GuideError
import com.aicamera.ui.theme.GuideGood
import com.aicamera.ui.theme.GuideWarn
import com.aicamera.ui.theme.NeoAccent
import com.aicamera.ui.theme.NeoTextPrimary
import com.aicamera.ui.theme.NeoTextSecondary
import kotlin.math.abs

/**
 * 构图覆盖层：在相机预览之上绘制网格、主体框、推荐取景框、方向箭头、水平仪、评分。
 */
@Composable
fun CompositionOverlay(
    state: OverlayState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. 网格
            drawGrid(state.gridMode)

            // 2. 水平仪
            drawHorizon(state.horizonDegrees)

            // 3. 主体框
            if (state.subjectsEnabled) {
                state.subjects.forEach { drawSubjectBox(it) }
            }

            // 4. 姿态骨架
            state.pose?.let { drawPoseScale(it) }

            // 5. 推荐取景框 + 箭头
            state.recommendation?.let { rec ->
                drawRecommendation(rec)
            }
        }
    }
}

/** 网格绘制：三分法 / 黄金分割 */
private fun DrawScope.drawGrid(mode: Int) {
    val gridColor = Color.White.copy(alpha = 0.22f)
    val gridStroke = Stroke(width = 1.dp.toPx())
    when (GridMode.from(mode)) {
        GridMode.NONE -> {}
        GridMode.THIRDS -> {
            // 横竖各两条三分线
            for (i in 1..2) {
                val fx = size.width * i / 3f
                drawLine(gridColor, Offset(fx, 0f), Offset(fx, size.height), strokeWidth = gridStroke.width)
            }
            for (i in 1..2) {
                val fy = size.height * i / 3f
                drawLine(gridColor, Offset(0f, fy), Offset(size.width, fy), strokeWidth = gridStroke.width)
            }
        }
        GridMode.GOLDEN -> {
            // 黄金分割近似线（0.382 / 0.618）
            for (fx in listOf(0.382f, 0.618f)) {
                drawLine(gridColor, Offset(size.width * fx, 0f), Offset(size.width * fx, size.height), strokeWidth = gridStroke.width)
            }
            for (fy in listOf(0.382f, 0.618f)) {
                drawLine(gridColor, Offset(0f, size.height * fy), Offset(size.width, size.height * fy), strokeWidth = gridStroke.width)
            }
        }
    }
}

/** 水平仪：中间一条随倾斜角度翻转的线 + 中心十字 */
private fun DrawScope.drawHorizon(degrees: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val len = size.width * 0.5f
    val level = abs(degrees) <= 1.5f
    val color = if (level) GuideGood else GuideWarn

    // 中心参考十字
    val crossColor = Color.White.copy(alpha = 0.25f)
    drawLine(crossColor, Offset(cx - 8.dp.toPx(), cy), Offset(cx + 8.dp.toPx(), cy), strokeWidth = 1.dp.toPx())
    drawLine(crossColor, Offset(cx, cy - 8.dp.toPx()), Offset(cx, cy + 8.dp.toPx()), strokeWidth = 1.dp.toPx())

    // 倾斜线（roll 角度）
    val rad = Math.toRadians(degrees.toDouble())
    val dx = (len / 2f) * kotlin.math.cos(rad).toFloat()
    val dy = (len / 2f) * kotlin.math.sin(rad).toFloat()
    drawLine(
        color = color,
        start = Offset(cx - dx, cy - dy),
        end = Offset(cx + dx, cy + dy),
        strokeWidth = 2.dp.toPx()
    )
}

/** 主体检测框 */
private fun DrawScope.drawSubjectBox(subject: DetectedSubject) {
    val rect = normalizedRect(subject.box)
    val color = when (subject.kind) {
        com.aicamera.composition.SubjectKind.FACE -> GuideGood
        com.aicamera.composition.SubjectKind.POSE -> NeoAccent
        else -> FrameColor
    }
    drawRoundRect(
        color = color.copy(alpha = 0.9f),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )
    // 标签角标
    drawContext.canvas.nativeCanvas.drawText(
        subject.label,
        rect.left + 4.dp.toPx(),
        rect.top - 4.dp.toPx(),
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 11.dp.toPx()
            isAntiAlias = true
        }
    )
}

/** 姿态骨架：简化的肩线/髋线（主体比例参考） */
private fun DrawScope.drawPoseScale(pose: com.aicamera.composition.PoseLimb) {
    if (!pose.visible) return
    val color = NeoAccent.copy(alpha = 0.8f)
    val stroke = Stroke(width = 2.dp.toPx())
    // 肩中点 → 髋中点
    val shoulder = Offset(pose.shoulderMidX * size.width, pose.shoulderMidY * size.height)
    val hip = Offset(pose.hipMidX * size.width, pose.hipMidY * size.height)
    drawLine(color, shoulder, hip, strokeWidth = stroke.width)
    // 小圆点
    drawCircle(color, radius = 4.dp.toPx(), center = shoulder)
    drawCircle(color, radius = 4.dp.toPx(), center = hip)
}

/** 推荐取景框 + 方向箭头 */
private fun DrawScope.drawRecommendation(rec: com.aicamera.composition.Recommendation) {
    val rect = normalizedRect(rec.frame)
    // 虚线框（用多个小段模拟）
    val color = GuideGood.copy(alpha = 0.9f)
    val stroke = Stroke(width = 2.dp.toPx())
    val radius = 8.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(radius),
        style = stroke
    )
    // 四角加粗
    drawCornerBrackets(rect, color)

    // 方向箭头（屏幕中央 → 移动方向）
    val cx = size.width / 2f
    val cy = size.height / 2f
    val moveX = rec.moveX
    val moveY = rec.moveY
    if (abs(moveX) > 0.1f || abs(moveY) > 0.1f) {
        val len = 36.dp.toPx()
        val dx = moveX.coerceIn(-1f, 1f) * len
        val dy = moveY.coerceIn(-1f, 1f) * len
        val start = Offset(cx, cy)
        val end = Offset(cx + dx, cy + dy)
        drawArrow(start, end, ArrowColor)
    }

    // 缩放提示文字
    if (rec.zoomHint != 1f) {
        drawContext.canvas.nativeCanvas.drawText(
            if (rec.zoomHint > 1f) "放大 ${"%.1f".format(rec.zoomHint)}x" else "缩小",
            size.width / 2f - 30.dp.toPx(),
            size.height * 0.72f,
            android.graphics.Paint().apply {
                color = ArrowColor.toArgb()
                textSize = 14.dp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }
}

private fun DrawScope.drawCornerBrackets(rect: Rect, color: Color) {
    val bLen = 18.dp.toPx()
    val sw = 3.dp.toPx()
    val c = color
    // 左上
    drawLine(c, Offset(rect.left, rect.top + bLen), Offset(rect.left, rect.top), strokeWidth = sw)
    drawLine(c, Offset(rect.left, rect.top), Offset(rect.left + bLen, rect.top), strokeWidth = sw)
    // 右上
    drawLine(c, Offset(rect.right - bLen, rect.top), Offset(rect.right, rect.top), strokeWidth = sw)
    drawLine(c, Offset(rect.right, rect.top), Offset(rect.right, rect.top + bLen), strokeWidth = sw)
    // 左下
    drawLine(c, Offset(rect.left, rect.bottom - bLen), Offset(rect.left, rect.bottom), strokeWidth = sw)
    drawLine(c, Offset(rect.left, rect.bottom), Offset(rect.left + bLen, rect.bottom), strokeWidth = sw)
    // 右下
    drawLine(c, Offset(rect.right - bLen, rect.bottom), Offset(rect.right, rect.bottom), strokeWidth = sw)
    drawLine(c, Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - bLen), strokeWidth = sw)
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len < 1f) return
    val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
    val headLen = 14.dp.toPx()
    val headAngle = 0.5f

    // 箭杆
    drawLine(color, from, to, strokeWidth = 3.dp.toPx())

    // 箭头
    val p1 = Offset(
        to.x - headLen * kotlin.math.cos(angle - headAngle).toFloat(),
        to.y - headLen * kotlin.math.sin(angle - headAngle).toFloat()
    )
    val p2 = Offset(
        to.x - headLen * kotlin.math.cos(angle + headAngle).toFloat(),
        to.y - headLen * kotlin.math.sin(angle + headAngle).toFloat()
    )
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color)
}

/** 归一化 [0..1] → 像素 */
private fun DrawScope.normalizedRect(box: android.graphics.RectF): Rect {
    val left = box.left * size.width
    val top = box.top * size.height
    val right = box.right * size.width
    val bottom = box.bottom * size.height
    return Rect(left, top, right, bottom)
}

/** 引导提示气泡（覆盖层下方，由主界面显示） */
fun guidanceColor(g: Guidance): Color = when (g.severity) {
    Severity.GOOD -> GuideGood
    Severity.ERROR -> GuideError
    Severity.WARN -> GuideWarn
    Severity.INFO -> NeoTextPrimary
}