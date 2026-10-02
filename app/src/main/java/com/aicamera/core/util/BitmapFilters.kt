package com.aicamera.core.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset

/**
 * 滤镜参数：与 [com.aicamera.domain.model.FilterPreset] 对应，
 * 供 ColorMatrix 滤镜引擎使用。
 */
data class FilterParams(
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val brightness: Float = 0f,
    val warmth: Float = 0f,   // -1..1 冷→暖
    val vignette: Float = 0f  // 0..1 暗角
)


/** 编辑器：文字叠加数据（坐标归一化 0..1） */
data class TextOverlayData(
    val id: Int,
    val text: String,
    val pos: Offset = Offset(0.5f, 0.5f),
    val sizeSp: Float = 28f,
    val white: Boolean = true
)

/** 贴纸类型 */
enum class StickerOverlayType { HEART, STAR, SMILE, CAMERA, FLASH, CHECK }

/** 贴纸叠加数据 */
data class StickerOverlayData(
    val id: Int,
    val type: StickerOverlayType,
    val pos: Offset = Offset(0.5f, 0.5f),
    val scale: Float = 0.15f
)

/** 水印模式 */
enum class WatermarkKind { NONE, DATE, BRAND }

/** 基于 ColorMatrix 的轻量滤镜引擎 */
object BitmapFilters {

    private const val MAX_DIMENSION_FOR_VIGNETTE = 1920

    /**
     * 应用滤镜：饱和度 / 对比度 / 亮度 / 色温（R、B 通道偏移）/ 暗角。
     * 返回新的 Bitmap，不修改原图。
     */
    fun applyFilter(
        source: Bitmap,
        saturation: Float = 1f,
        contrast: Float = 1f,
        brightness: Float = 0f,
        warmth: Float = 0f,
        vignette: Float = 0f
    ): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)

        // 1) 色彩矩阵：饱和度 → 对比度 → 亮度 → 色温
        val colorMatrix = ColorMatrix()

        // 饱和度
        if (saturation != 1f) {
            val sat = ColorMatrix().apply { setSaturation(saturation.coerceIn(0f, 3f)) }
            colorMatrix.postConcat(sat)
        }

        // 对比度 + 亮度（对比度围绕 128 缩放，亮度为 -255..255 偏移）
        if (contrast != 1f || brightness != 0f) {
            val c = contrast.coerceIn(0f, 3f)
            val b = brightness.coerceIn(-255f, 255f)
            val offset = 128f * (1f - c) + b
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, offset,
                    0f, c, 0f, 0f, offset,
                    0f, 0f, c, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            colorMatrix.postConcat(contrastMatrix)
        }

        // 色温：warmth > 0 偏暖（R 增 B 减），< 0 偏冷
        if (warmth != 0f) {
            val w = warmth.coerceIn(-1f, 1f) * 30f // 最大 ±30 通道偏移
            val warmMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, w,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, -w,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            colorMatrix.postConcat(warmMatrix)
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
            isAntiAlias = true
            isFilterBitmap = true
        }
        val canvas = android.graphics.Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, paint)

        // 2) 暗角：手动像素扫描，大图（宽或高 > 1920）跳过以免卡顿
        if (vignette > 0f && width <= MAX_DIMENSION_FOR_VIGNETTE && height <= MAX_DIMENSION_FOR_VIGNETTE) {
            applyVignette(output, width, height, vignette.coerceIn(0f, 1f))
        }

        return output
    }

    /**
     * 手动像素级暗角：边缘按到中心距离线性压暗，最大压暗
     * [MAX_DIMENSION_FOR_VIGNETTE] 上限内图片的内存（约 1920²×4 字节）。
     */
    private fun applyVignette(bitmap: Bitmap, width: Int, height: Int, strength: Float) {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val centerX = width / 2f
        val centerY = height / 2f
        // 用宽高中的较大值归一化距离，保证角上压暗强度接近 strength
        val maxDim = kotlin.math.max(width, height)

        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = (x - centerX) / maxDim
                val dy = (y - centerY) / maxDim
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                // 距离 0..1，靠近边缘才显著压暗（pow 2 让暗角更柔和）
                val factor = 1f - (dist * dist * strength)
                if (factor < 1f) {
                    val pixel = pixels[i]
                    val r = (((pixel shr 16) and 0xFF) * factor).toInt()
                    val g = (((pixel shr 8) and 0xFF) * factor).toInt()
                    val b = ((pixel and 0xFF) * factor).toInt()
                    pixels[i] = (pixel and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
                i++
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    // ─────────────────────── v5: 几何变换 ───────────────────────

    /** 旋转 90° 的倍数。返回新 Bitmap。 */
    fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        val out = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        return out
    }

    /** 翻转。flipH=true 水平镜像, flipV=true 垂直镜像。返回新 Bitmap。 */
    fun flip(source: Bitmap, flipH: Boolean = false, flipV: Boolean = false): Bitmap {
        if (!flipH && !flipV) return source
        val matrix = Matrix().apply {
            postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * 裁剪。rect 为源图上的相对矩形 (0..1)。
     * 返回新 Bitmap，最大化保留分辨率。
     */
    fun crop(source: Bitmap, rect: RectF): Bitmap {
        val x = (rect.left * source.width).toInt().coerceIn(0, source.width - 1)
        val y = (rect.top * source.height).toInt().coerceIn(0, source.height - 1)
        val w = (rect.width() * source.width).toInt().coerceIn(1, source.width - x)
        val h = (rect.height() * source.height).toInt().coerceIn(1, source.height - y)
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    /** 3x3 卷积锐化。amount 0..2，返回新 Bitmap。 */
    fun sharpen(source: Bitmap, amount: Float): Bitmap {
        val a = amount.coerceIn(0f, 2f)
        if (a <= 0.01f) return source
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        val src = IntArray(width * height)
        val dst = IntArray(width * height)
        source.getPixels(src, 0, width, 0, 0, width, height)

        // 卷积核: [0,-1,0; -1,5,-1; 0,-1,0] (中心权重随 amount 增强)
        val center = 1f + 4f * a
        val edge = -a
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val c = src[i]
                val l = src.getOrElse(y * width + (x - 1)) { c }
                val r = src.getOrElse(y * width + (x + 1)) { c }
                val u = src.getOrElse((y - 1) * width + x) { c }
                val d = src.getOrElse((y + 1) * width + x) { c }

                fun channel(shift: Int): Int {
                    val cv = (c shr shift) and 0xFF
                    var v = center * cv + edge * (((l shr shift) and 0xFF) +
                        ((r shr shift) and 0xFF) + ((u shr shift) and 0xFF) + ((d shr shift) and 0xFF))
                    v = v.coerceIn(0f, 255f)
                    return v.toInt()
                }
                val nr = channel(16)
                val ng = channel(8)
                val nb = channel(0)
                dst[i] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        output.setPixels(dst, 0, width, 0, 0, width, height)
        return output
    }
    /**
     * 合成叠加层（文字 / 贴纸 / 水印）到位图上，返回新的 Bitmap。
     * 文字用系统字体白色/黑色描边；贴纸用 Canvas 自绘简单图形；水印在右下角。
     */
    fun composeOverlays(
        source: Bitmap,
        texts: List<TextOverlayData> = emptyList(),
        stickers: List<StickerOverlayData> = emptyList(),
        watermark: WatermarkKind = WatermarkKind.NONE
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        // 文字
        for (t in texts) {
            val sizePx = t.sizeSp * out.density * 2f // 相对 sp → px
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (t.white) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                textSize = sizePx.coerceAtLeast(12f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val x = t.pos.x * w
            val y = t.pos.y * h
            // 简单描边：画 4 个方向偏移
            val stroke = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = if (t.white) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            for ((dx, dy) in listOf(-2f to 0f, 2f to 0f, 0f to -2f, 0f to 2f)) {
                canvas.drawText(t.text, x + dx, y + dy, stroke)
            }
            canvas.drawText(t.text, x, y, paint)
        }

        // 贴纸
        for (s in stickers) {
            val r = s.scale * w
            val cx = s.pos.x * w
            val cy = s.pos.y * h
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
            when (s.type) {
                StickerOverlayType.HEART -> drawHeart(canvas, cx, cy, r, p)
                StickerOverlayType.STAR -> drawStar(canvas, cx, cy, r, p)
                StickerOverlayType.SMILE -> drawSmile(canvas, cx, cy, r, p)
                StickerOverlayType.CAMERA -> drawCameraIcon(canvas, cx, cy, r, p)
                StickerOverlayType.FLASH -> drawFlashIcon(canvas, cx, cy, r, p)
                StickerOverlayType.CHECK -> drawCheckIcon(canvas, cx, cy, r, p)
            }
        }

        // 水印
        if (watermark != WatermarkKind.NONE) {
            val text = when (watermark) {
                WatermarkKind.DATE -> java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()).format(java.util.Date())
                WatermarkKind.BRAND -> "AI Camera"
                else -> ""
            }
            if (text.isNotEmpty()) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 14f * out.density
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }
                canvas.drawText(text, w - p.measureText(text) - 12f * out.density, h - 10f * out.density, p)
            }
        }
        return out
    }

    private fun drawHeart(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        // 简单心形：两个圆 + 三角形
        val s = r / 2f
        c.drawCircle(cx - s / 2f, cy - s / 2f, s, p)
        c.drawCircle(cx + s / 2f, cy - s / 2f, s, p)
        val path = android.graphics.Path().apply {
            moveTo(cx - s, cy - s / 2f)
            lineTo(cx + s, cy - s / 2f)
            lineTo(cx, cy + s * 1.2f)
            close()
        }
        c.drawPath(path, p)
    }

    private fun drawStar(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val path = android.graphics.Path()
        for (i in 0..9) {
            val angle = Math.PI / 5 * i - Math.PI / 2
            val rad = if (i % 2 == 0) r else r * 0.4f
            val x = cx + (Math.cos(angle) * rad).toFloat()
            val y = cy + (Math.sin(angle) * rad).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
    }

    private fun drawSmile(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        c.drawCircle(cx, cy, r, p)
        val eye = Paint(p).apply { color = android.graphics.Color.BLACK; strokeWidth = r * 0.1f; style = Paint.Style.STROKE }
        c.drawCircle(cx - r * 0.35f, cy - r * 0.2f, r * 0.08f, eye)
        c.drawCircle(cx + r * 0.35f, cy - r * 0.2f, r * 0.08f, eye)
        val mouth = Paint(eye).apply { strokeWidth = r * 0.12f }
        c.drawArc(cx - r * 0.5f, cy - r * 0.3f, cx + r * 0.5f, cy + r * 0.7f, 20f, 140f, false, mouth)
    }

    private fun drawCameraIcon(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val body = android.graphics.RectF(cx - r, cy - r * 0.6f, cx + r, cy + r * 0.6f)
        c.drawRoundRect(body, r * 0.2f, r * 0.2f, p)
        val lens = Paint(p).apply { color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = r * 0.25f }
        c.drawCircle(cx, cy, r * 0.35f, lens)
        val top = Paint(p).apply { color = android.graphics.Color.WHITE }
        c.drawRect(cx - r * 0.4f, cy - r * 0.9f, cx + r * 0.4f, cy - r * 0.5f, top)
        val lensDot = Paint(p).apply { color = android.graphics.Color.BLACK }
        c.drawCircle(cx, cy, r * 0.12f, lensDot)
    }

    private fun drawFlashIcon(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val path = android.graphics.Path().apply {
            moveTo(cx + r * 0.2f, cy - r)
            lineTo(cx - r * 0.4f, cy + r * 0.2f)
            lineTo(cx + r * 0.05f, cy + r * 0.2f)
            lineTo(cx - r * 0.2f, cy + r)
            lineTo(cx + r * 0.5f, cy - r * 0.2f)
            lineTo(cx + r * 0.05f, cy - r * 0.2f)
            close()
        }
        c.drawPath(path, p)
    }

    private fun drawCheckIcon(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val stroke = Paint(p).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = r * 0.25f
            strokeCap = Paint.Cap.ROUND
        }
        val path = android.graphics.Path().apply {
            moveTo(cx - r * 0.5f, cy)
            lineTo(cx - r * 0.1f, cy + r * 0.4f)
            lineTo(cx + r * 0.6f, cy - r * 0.4f)
        }
        c.drawPath(path, stroke)
    }
}
