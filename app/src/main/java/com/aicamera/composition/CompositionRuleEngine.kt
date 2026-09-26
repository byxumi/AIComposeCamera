package com.aicamera.composition

import com.aicamera.composition.CompositionModels.Guidance
import com.aicamera.composition.CompositionModels.RecommendedBox
import com.aicamera.composition.CompositionModels.Result
import com.aicamera.composition.CompositionModels.Style
import com.aicamera.composition.CompositionModels.Subject
import kotlin.math.abs

/**
 * 本地构图规则引擎
 *
 * 参考 ai-composition-assistant 的优先级体系：
 *   11 错误预防 > 10 全局基础 > 9 核心构图 > 8 进阶 > 7 场景特定
 *
 * 全部离线、零延迟。输入 ML Kit 检测结果，输出构图引导、推荐取景框与评分。
 */
object CompositionRuleEngine {

    // 三分法交叉点（归一化）
    val CROSS_POINTS = listOf(
        Pair(0.33f, 0.33f),
        Pair(0.67f, 0.33f),
        Pair(0.33f, 0.67f),
        Pair(0.67f, 0.67f)
    )

    private const val MIN_SUBJECT_RATIO = 0.05f
    private const val MAX_SUBJECT_RATIO = 0.90f
    private const val TILT_TOLERANCE = 5f // 度

    // 主体优先级：人脸/人体 > 宠物 > 食物 > 商品 > 建筑 > 其他
    private val SUBJECT_PRIORITY = listOf("人物", "宠物", "食物", "商品", "建筑", "其他")

    /**
     * 分析一帧的检测结果
     */
    fun analyze(
        subjects: List<Subject>,
        imageWidth: Int,
        imageHeight: Int
    ): Result {
        if (subjects.isEmpty()) {
            return Result(
                guidances = listOf(
                    Guidance("未检测到主体，请对准拍摄对象", "NONE_001", 0, Style.INFO)
                ),
                score = 50,
                sceneType = "auto"
            )
        }

        val guidances = mutableListOf<Guidance>()
        val mainSubject = selectMainSubject(subjects)

        // ─── 优先级 11：错误预防 ───
        // 头/脚被裁切
        val top = mainSubject.top
        val bottom = mainSubject.bottom
        if (top < 0.02f) {
            guidances += Guidance(
                "头部被裁切，请向下移动手机", "ERROR_002", 11, Style.ERROR, arrowDy = 1f
            )
        }
        if (bottom > 0.98f) {
            guidances += Guidance(
                "脚部被裁切，请向上移动手机", "ERROR_002", 11, Style.ERROR, arrowDy = -1f
            )
        }

        // 主体过小/过大
        if (mainSubject.areaRatio < MIN_SUBJECT_RATIO) {
            guidances += Guidance(
                "主体太小，请靠近或放大", "ERROR_004", 11, Style.SECONDARY
            )
        } else if (mainSubject.areaRatio > MAX_SUBJECT_RATIO) {
            guidances += Guidance(
                "主体太大，请后退或缩小", "ERROR_004", 11, Style.SECONDARY
            )
        }

        // ─── 优先级 10：全局基础（头顶留白） ───
        if (mainSubject.category in listOf("人物", "宠物") && top < 1f / 6f) {
            guidances += Guidance(
                "头顶留白不足，请向下移动", "GLOBAL_003", 10, Style.SECONDARY, arrowDy = 1f
            )
        }

        // ─── 优先级 9：核心构图（三分法） ───
        var recommendedBox: RecommendedBox? = null
        var arrowX = 0f
        var arrowY = 0f

        val (closestX, closestY) = findClosestCrossPoint(mainSubject.centerX, mainSubject.centerY)
        val dx = closestX - mainSubject.centerX
        val dy = closestY - mainSubject.centerY

        if (abs(dx) > 0.05f || abs(dy) > 0.05f) {
            val msg = buildList {
                if (abs(dx) > 0.05f) add(if (dx > 0) "请向右移动" else "请向左移动")
                if (abs(dy) > 0.05f) add(if (dy > 0) "请向下移动" else "请向上移动")
            }.joinToString("，")

            guidances += Guidance(
                "$msg，将主体对准取景框", "CORE_001", 9, Style.PRIMARY,
                arrowDx = dx.coerceIn(-1f, 1f),
                arrowDy = dy.coerceIn(-1f, 1f)
            )
        }

        // 计算推荐取景框：以最近交叉点为中心，主体尺寸 1.5 倍
        recommendedBox = buildRecommendedBox(closestX, closestY, mainSubject)

        // ─── 综合评分 ───
        val score = computeScore(guidances, mainSubject, arrowX, arrowY)

        val isPerfect = guidances.none { it.style == Style.ERROR || it.style == Style.PRIMARY } &&
            mainSubject.areaRatio in MIN_SUBJECT_RATIO..MAX_SUBJECT_RATIO

        return Result(
            guidances = guidances,
            recommendedBox = recommendedBox,
            mainSubject = mainSubject,
            score = score,
            isPerfect = isPerfect,
            sceneType = detectSceneType(mainSubject)
        )
    }

    /** 选择核心主体（按优先级，同类取面积最大） */
    fun selectMainSubject(subjects: List<Subject>): Subject {
        return subjects.maxWithOrNull(
            compareBy<Subject>(
                { SUBJECT_PRIORITY.indexOf(it.category).let { idx -> if (idx < 0) 99 else idx } },
                { it.areaRatio }
            )
        ) ?: subjects.first()
    }

    /** 找到最近的交叉点（返回交叉点坐标） */
    fun findClosestCrossPoint(cx: Float, cy: Float): Pair<Float, Float> {
        return CROSS_POINTS.minByOrNull { (x, y) -> abs(x - cx) + abs(y - cy) }
            ?: CROSS_POINTS.first()
    }

    /** 推荐取景框：以交叉点为中心，主体 1.5 倍大小，约束在画面内 */
    private fun buildRecommendedBox(
        cx: Float, cy: Float, subject: Subject
    ): RecommendedBox {
        val w = (subject.width * 1.5f).coerceIn(0.2f, 0.9f)
        val h = (subject.height * 1.5f).coerceIn(0.2f, 0.9f)
        var left = (cx - w / 2).coerceIn(0.02f, 1f - w - 0.02f)
        var top = (cy - h / 2).coerceIn(0.02f, 1f - h - 0.02f)
        // 防溢出
        left = left.coerceIn(0f, 1f - w)
        top = top.coerceIn(0f, 1f - h)
        return RecommendedBox(left, top, left + w, top + h, "thirds", Style.PRIMARY)
    }

    /** 场景识别（简化：按主体类别） */
    private fun detectSceneType(subject: Subject): String = when (subject.category) {
        "人物" -> "portrait"
        "宠物" -> "pet"
        "食物" -> "food"
        "商品" -> "product"
        "建筑" -> "architecture"
        else -> "auto"
    }

    /**
     * 评分：0-100
     * 满分 100，按活跃引导扣除：
     *   ERROR 级每条 -18，PRIMARY 级每条 -8，SECONDARY 级每条 -5，
     *   主体过小/过大额外 -10；置信度低再按需微调。
     */
    fun computeScore(guidances: List<Guidance>, subject: Subject? = null, arrowX: Float = 0f, arrowY: Float = 0f): Int {
        var score = 100
        for (g in guidances) {
            score -= when (g.style) {
                Style.ERROR -> 18
                Style.PRIMARY -> 8
                Style.SECONDARY -> 5
                Style.INFO -> 2
            }
        }
        if (subject != null) {
            if (subject.areaRatio < MIN_SUBJECT_RATIO || subject.areaRatio > MAX_SUBJECT_RATIO) score -= 10
        }
        if (arrowX != 0f || arrowY != 0f) score -= 0 // 方向提示已含在 PRIMARY 扣分
        return score.coerceIn(0, 100)
    }
}