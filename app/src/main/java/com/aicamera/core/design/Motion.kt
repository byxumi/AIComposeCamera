package com.aicamera.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * v4 设计 token — 动效 (动机化, 仅 4 类):
 * 1. 按压反馈: 140ms scale 回弹 (spring)
 * 2. 面板滑入: 260ms slide up + fade
 * 3. 目标圈脉动: 1200ms 存在感
 * 4. 页面切换: fade + slide
 *
 * 全部尊重系统"减弱动效"。
 */
object CamMotion {
    const val PressMillis = 140
    const val SlideMillis = 260
    const val FadeMillis = 180
    const val PulseMillis = 1200
}

/** 系统是否开启"减弱动效" (动画时长缩放 = 0) */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/** 目标圈脉动: 返回 1 时静止 (减弱动效) */
@Composable
fun rememberPulse(
    scaleRange: ClosedFloatingPointRange<Float> = 0.92f..1.12f
): Float {
    if (rememberReduceMotion()) return 1f
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        initialValue = scaleRange.start,
        targetValue = scaleRange.endInclusive,
        animationSpec = infiniteRepeatable(
            animation = tween(CamMotion.PulseMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    return value
}

/** 按压缩放: 按住所触发的动画完成信号在 [onTap] 调用后应自行 [Animatable.animateTo] 回弹 */
@Composable
fun rememberPressScale(): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    return remember { Animatable(1f) }
}

/** 常用回弹规格 */
fun pressSpring() = spring<Float>(
    dampingRatio = 0.55f,
    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
)