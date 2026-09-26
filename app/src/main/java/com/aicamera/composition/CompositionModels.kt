package com.aicamera.composition

/**
 * 构图检测与引导的共用数据模型
 *
 * 所有坐标均为归一化 [0,1]，左上角为原点。
 */
object CompositionModels {

    /**
     * 单个检测到的主体（像素框由 ML Kit 提供，这里归一化）
     */
    data class Subject(
        val centerX: Float,      // 中心 x [0,1]
        val centerY: Float,      // 中心 y [0,1]
        val left: Float,         // 左 [0,1]
        val top: Float,          // 上 [0,1]
        val right: Float,        // 右 [0,1]
        val bottom: Float,       // 下 [0,1]
        val width: Float,        // 宽 [0,1]
        val height: Float,       // 高 [0,1]
        val confidence: Float,   // 置信度 [0,1]
        val category: String,    // 中文类别：人物/宠物/食物/商品/建筑/其他
        val label: String = ""   // 原始类别标签（如 person/cat）
    ) {
        val areaRatio: Float get() = width * height
        val aspectRatio: Float get() = if (height > 0f) width / height else 1f
    }

    /**
     * 一条构图引导
     */
    data class Guidance(
        val message: String,     // 用户可见提示
        val ruleId: String,      // 规则号，如 CORE_001
        val priority: Int,       // 11 错误预防 > 10 基础 > 9 核心 > 8 进阶 > 7 场景
        val style: Style,        // 引导样式（绿色框/黄色框/错误红/文字）
        val arrowDx: Float = 0f, // 箭头方向：-1 左移 / +1 右移 / 0 不动
        val arrowDy: Float = 0f  // 箭头方向：-1 上移 / +1 下移 / 0 不动
    )

    enum class Style { PRIMARY, SECONDARY, ERROR, INFO }

    /**
     * 整帧构图分析结果
     */
    data class Result(
        val guidances: List<Guidance> = emptyList(),
        val recommendedBox: RecommendedBox? = null,  // 推荐取景框（归一化）
        val mainSubject: Subject? = null,
        val tiltAngle: Float = 0f,   // 倾斜角（度），0=水平
        val score: Int = 100,        // 0-100 构图分
        val isPerfect: Boolean = false,
        val sceneType: String = "auto"
    )

    /**
     * 推荐取景框（归一化坐标）
     */
    data class RecommendedBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val type: String,
        val color: Style
    )
}