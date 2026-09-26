package com.aicamera.composition

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.aicamera.composition.CompositionModels.RecommendedBox
import com.aicamera.composition.CompositionModels.Result
import com.aicamera.composition.CompositionModels.Style
import com.aicamera.composition.CompositionModels.Subject

/**
 * OverlayView — 取景构图叠加层（Canvas 自绘）
 *
 * 绘制内容：
 *  - 九宫格（可开关）
 *  - 检测主体框
 *  - 推荐取景框（绿色虚线）
 *  - 方向引导箭头
 *  - 水平倾斜参考
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ─── 状态 ───
    var showGrid: Boolean = true
        set(value) { field = value; invalidate() }
    var gridStyle: Int = 0  // 0 三分法 / 1 黄金分割
        set(value) { field = value; invalidate() }

    private var result: Result? = null
    private var subjects: List<Subject> = emptyList()

    fun updateData(result: Result?, subjects: List<Subject>) {
        this.result = result
        this.subjects = subjects
        invalidate()
    }

    // ─── 画笔（低饱和、细线、不抢画面） ───
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val goldenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2AFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val subjectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = null
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val arrowPath = Path()

    // ─── 绘制 ───
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. 网格
        if (showGrid) drawGrid(canvas, w, h)

        // 2. 推荐取景框
        result?.recommendedBox?.let { box ->
            drawBox(canvas, box, w, h)
        }

        // 3. 主体框
        for (s in subjects) {
            drawSubject(canvas, s, w, h)
        }

        // 4. 方向箭头（取优先级最高的 PRIMARY 引导）
        val guide = result?.guidances?.firstOrNull { it.style == Style.PRIMARY }
        if (guide != null && (guide.arrowDx != 0f || guide.arrowDy != 0f)) {
            drawArrow(canvas, guide.arrowDx, guide.arrowDy, w, h)
        }
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        val paint = if (gridStyle == 0) gridPaint else goldenPaint
        if (gridStyle == 0) {
            // 三分法：2 横 2 竖
            for (i in 1..2) {
                val x = w * i / 3f
                val y = h * i / 3f
                canvas.drawLine(x, 0f, x, h, paint)
                canvas.drawLine(0f, y, w, y, paint)
            }
            // 交叉点小圈
            for (px in listOf(1, 2)) for (py in listOf(1, 2)) {
                val cx = w * px / 3f
                val cy = h * py / 3f
                canvas.drawCircle(cx, cy, 6f, paint)
                canvas.drawCircle(cx, cy, 1.5f, paint)
            }
        } else {
            // 黄金分割：0.618 / 0.382 线
            val (a, b) = 0.382f to 0.618f
            val xs = listOf(a, b)
            val ys = listOf(a, b)
            for (x in xs) canvas.drawLine(x * w, 0f, x * w, h, paint)
            for (y in ys) canvas.drawLine(0f, y * h, w, y * h, paint)
            for (x in xs) for (y in ys) {
                canvas.drawCircle(x * w, y * h, 6f, paint)
                canvas.drawCircle(x * w, y * h, 1.5f, paint)
            }
        }
    }

    private fun drawBox(canvas: Canvas, box: RecommendedBox, w: Float, h: Float) {
        val left = box.left * w
        val top = box.top * h
        val right = box.right * w
        val bottom = box.bottom * h

        // 青色主框
        boxPaint.color = Color.parseColor("#CC5AC8C8")
        canvas.drawRect(left, top, right, bottom, boxPaint)

        // 四角强化
        val corner = 24f
        val cornerPaint = boxPaint
        canvas.drawLine(left, top + corner, left, top, cornerPaint)
        canvas.drawLine(left, top, left + corner, top, cornerPaint)
        canvas.drawLine(right - corner, top, right, top, cornerPaint)
        canvas.drawLine(right, top, right, top + corner, cornerPaint)
        canvas.drawLine(right, bottom - corner, right, bottom, cornerPaint)
        canvas.drawLine(right, bottom, right - corner, bottom, cornerPaint)
        canvas.drawLine(left + corner, bottom, left, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left, bottom - corner, cornerPaint)
    }

    private fun drawSubject(canvas: Canvas, s: Subject, w: Float, h: Float) {
        val left = s.left * w
        val top = s.top * h
        val right = s.right * w
        val bottom = s.bottom * h

        // 描边框（白色半透明）
        canvas.drawRect(left, top, right, bottom, subjectPaint)

        // 顶部小标签（类别）
        val label = s.category
        val tw = textPaint.measureText(label)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99000000")
            style = Paint.Style.FILL
        }
        val labelTop = (top - 22f).coerceAtLeast(0f)
        canvas.drawRoundRect(
            RectF(left, labelTop, left + tw + 16f, labelTop + 24f), 4f, 4f, bg
        )
        canvas.drawText(label, left + tw / 2f + 8f, labelTop + 17f, textPaint)
    }

    private fun drawArrow(canvas: Canvas, dx: Float, dy: Float, w: Float, h: Float) {
        if (dx == 0f && dy == 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val len = 80f
        val endX = cx + dx * len
        val endY = cy + dy * len

        arrowPath.reset()
        arrowPath.moveTo(cx, cy)
        arrowPath.lineTo(endX, endY)
        canvas.drawPath(arrowPath, arrowPaint)

        // 箭头头部
        val angle = Math.atan2((dy * len).toDouble(), (dx * len).toDouble())
        val headLen = 28f
        val headAngle = 0.6
        val p1x = endX - headLen * Math.cos(angle - headAngle).toFloat()
        val p1y = endY - headLen * Math.sin(angle - headAngle).toFloat()
        val p2x = endX - headLen * Math.cos(angle + headAngle).toFloat()
        val p2y = endY - headLen * Math.sin(angle + headAngle).toFloat()
        canvas.drawLine(endX, endY, p1x, p1y, arrowPaint)
        canvas.drawLine(endX, endY, p2x, p2y, arrowPaint)
    }
}