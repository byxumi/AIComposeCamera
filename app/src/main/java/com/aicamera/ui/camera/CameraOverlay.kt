package com.aicamera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aicamera.core.design.CamColors
import com.aicamera.core.design.CamType
import com.aicamera.core.design.PulsingTarget
import com.aicamera.core.design.ScoreBadge
import com.aicamera.domain.model.GuidanceType
import com.aicamera.domain.model.OverlayState
import com.aicamera.domain.model.Severity

/**
 * 取景器覆盖层 — 绘网格 / 水平仪 / 主体框 / 推荐框 / AI 目标圈 / 锁定高亮 / 引导文案。
 * 纯黑取景器上只有四种颜色: 白 (中性框) / 黄 (强调=关注) / 绿 (达成) / 红 (错误)。
 */
@Composable
fun CameraOverlay(
    state: OverlayState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // ── Canvas: 网格 / 水平仪 / 主体框 / 推荐框 ──
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val thin = 1.dp.toPx()
            val gridColor = Color.White.copy(alpha = 0.25f)

            // 网格
            if (state.showGridLines) {
                when (state.gridMode) {
                    1 -> {
                        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), thin)
                        drawLine(gridColor, Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), thin)
                        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), thin)
                        drawLine(gridColor, Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), thin)
                    }
                    2 -> {
                        drawLine(gridColor, Offset(w / 2f, 0f), Offset(w / 2f, h), thin)
                        drawLine(gridColor, Offset(0f, h / 2f), Offset(w, h / 2f), thin)
                        drawRoundRect(
                            color = gridColor,
                            topLeft = Offset(w * 0.1f, h * 0.1f),
                            size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.8f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(thin)
                        )
                    }
                }
            }

            // 水平仪: 倾斜 > 2.5° 显示警示线
            if (state.showHorizonLine && kotlin.math.abs(state.horizonDegrees) > 2.5f) {
                val lineY = h / 2f
                val slope = state.horizonDegrees / 45f
                val dx = h * 0.25f
                drawLine(
                    color = CamColors.Warning,
                    start = Offset(w / 2f - dx, lineY - dx * slope),
                    end = Offset(w / 2f + dx, lineY + dx * slope),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // 主体检测框 (白细线), 锁定/选中主体黄色高亮
            if (state.subjectsEnabled) {
                for (s in state.subjects) {
                    val left = s.box.left * w
                    val top = s.box.top * h
                    val right = s.box.right * w
                    val bottom = s.box.bottom * h
                    val isLocked = s.id == state.lockedSubjectId
                    val isSelected = s.id == state.selectedSubjectId
                    val color = when {
                        isLocked -> CamColors.Accent
                        isSelected -> CamColors.Accent.copy(alpha = 0.7f)
                        else -> Color.White.copy(alpha = 0.35f)
                    }
                    val strokeW = if (isLocked || isSelected) 2.dp.toPx() else thin
                    drawRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW)
                    )
                }
            }

            // 推荐取景框
            state.recommendation?.let { r ->
                val left = r.frame.left * w
                val top = r.frame.top * h
                val right = r.frame.right * w
                val bottom = r.frame.bottom * h
                drawRect(
                    color = CamColors.Success.copy(alpha = 0.8f),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
                )
            }
        }

        // ── AI 目标圈 (脉动) ──
        state.aiTarget?.let { t ->
            PulsingTarget(
                cx = t.cx,
                cy = t.cy,
                radiusPx = t.radiusNormalized * 1080f.coerceAtLeast(1f),
                reached = t.reached,
                moveX = t.moveX,
                moveY = t.moveY,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 构图评分 (右上角) ──
        if (state.scoreEnabled && state.score > 0) {
            ScoreBadge(
                score = state.score,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 16.dp)
            )
        }

        // ── 引导文案 (顶部工具栏下方) ──
        val guidance = state.guidance
        if (guidance.type != GuidanceType.NONE && guidance.message.isNotBlank()) {
            val color = when (guidance.severity) {
                Severity.ERROR -> CamColors.Error
                Severity.WARN -> CamColors.Warning
                Severity.GOOD -> CamColors.Success
                Severity.INFO -> Color.White
            }
            Text(
                text = guidance.message,
                color = color,
                style = CamType.BodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 32.dp, end = 32.dp)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
