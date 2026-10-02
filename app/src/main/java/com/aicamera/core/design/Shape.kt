package com.aicamera.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * v4 设计 token — 圆角系统 (一致性锁定)。
 *
 * 唯三取值:
 * - 面板 / 大容器: 24dp
 * - 控件 / 列表行 / 图片: 14dp
 * - 圆形按钮 (快门 / 图标钮): 全圆
 */
object CamShapes {
    val Panel = RoundedCornerShape(24.dp)
    val Control = RoundedCornerShape(14.dp)
    val Small = RoundedCornerShape(8.dp)
}