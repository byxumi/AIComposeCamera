package com.aicamera.core.util

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

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
}