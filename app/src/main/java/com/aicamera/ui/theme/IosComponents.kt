package com.aicamera.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS 风格毛玻璃 Surface：模拟背景模糊 + 高光边缘。
 * 用半透明白 + 渐变实现轻量毛玻璃效果（无需 RenderEffect 兼容性更好）。
 */
@Composable
fun IosGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = IosFrostedDark,
    borderAlpha: Float = 0.2f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.35f))
                )
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha),
                        Color.Transparent
                    )
                )
            )
            .padding(0.dp)
    ) {
        content()
    }
}

/** iOS 圆角卡片容器 */
@Composable
fun IosCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(IosSystemSecondaryGroupedBackground)
    ) {
        content()
    }
}