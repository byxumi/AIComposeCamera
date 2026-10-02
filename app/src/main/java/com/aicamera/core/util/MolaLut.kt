package com.aicamera.core.util

import android.graphics.Bitmap
import kotlin.math.pow

/**
 * mola 相机的 LUT 滤镜引擎(CPU 端)。
 *
 * 从 mola APK 逆向出的 3D LUT 格式:
 *   首字节 = 立方体边长 N(16 或 32), 其后 N³ × 3 字节 = RGB 三元组,
 *   排列顺序: 外层 b → 中层 g → 内层 r(即纹理布局 x = b*N + r, y = g)。
 *
 * 色调曲线 boost() 与 LUT 采样公式 1:1 对齐 mola GPU shader(aa1.java):
 *   黑点 → gamma → 对比 → 高光肩部(>0.784 软压到 0.98) → 饱和,
 *   LUT 按 B 通道分层线性插值, r/g 取最近格(贴齐 shader 的 +0.5 采样)。
 */
object MolaLut {

    /** 解析 .lut 文件字节 → 3D 查找数据。非法返回 null。 */
    fun decode(bytes: ByteArray): LutData? {
        if (bytes.size < 2) return null
        val n = bytes[0].toInt() and 0xFF
        if (n != 16 && n != 32) return null
        val expected = 1 + n * n * n * 3
        if (bytes.size != expected) return null
        val data = FloatArray(n * n * n * 3)
        var i = 1
        var j = 0
        while (j < data.size) {
            data[j] = (bytes[i].toInt() and 0xFF) / 255f
            data[j + 1] = (bytes[i + 1].toInt() and 0xFF) / 255f
            data[j + 2] = (bytes[i + 2].toInt() and 0xFF) / 255f
            i += 3
            j += 3
        }
        return LutData(n, data)
    }

    /** 3D LUT 数据: n=n³ 立方体, data 长度 n³*3, 值 0..1, [b][g][r] 排列。 */
    class LutData(val n: Int, val data: FloatArray) {
        private val m = (n - 1).toFloat()

        /** 三线性插值采样(r/g/b 均 0..1), 返回 FloatArray(3) 输出 0..1。 */
        fun sample(r: Float, g: Float, b: Float): FloatArray {
            val br = r * m
            val gr = g * m
            val bl = b * m
            val r0 = br.toInt().coerceIn(0, n - 1)
            val r1 = (r0 + 1).coerceAtMost(n - 1)
            val g0 = gr.toInt().coerceIn(0, n - 1)
            val g1 = (g0 + 1).coerceAtMost(n - 1)
            val b0 = bl.toInt().coerceIn(0, n - 1)
            val b1 = (b0 + 1).coerceAtMost(n - 1)
            val fr = br - r0
            val fg = gr - g0
            val fb = bl - b0
            val w000 = (1f - fr) * (1f - fg) * (1f - fb)
            val w100 = fr * (1f - fg) * (1f - fb)
            val w010 = (1f - fr) * fg * (1f - fb)
            val w110 = fr * fg * (1f - fb)
            val w001 = (1f - fr) * (1f - fg) * fb
            val w101 = fr * (1f - fg) * fb
            val w011 = (1f - fr) * fg * fb
            val w111 = fr * fg * fb
            var out = 0f; var utg = 0f; var utb = 0f
            // b1 层
            val i001 = idx(b0, g0, r0)
            out = data[i001] * w000
            utg = data[i001 + 1] * w000
            utb = data[i001 + 2] * w000
            var o = idx(b0, g0, r1)
            out += data[o] * w100; utg += data[o + 1] * w100; utb += data[o + 2] * w100
            o = idx(b0, g1, r0)
            out += data[o] * w010; utg += data[o + 1] * w010; utb += data[o + 2] * w010
            o = idx(b0, g1, r1)
            out += data[o] * w110; utg += data[o + 1] * w110; utb += data[o + 2] * w110
            // b1 层
            val i101 = idx(b1, g0, r0)
            out += data[i101] * w001; utg += data[i101 + 1] * w001; utb += data[i101 + 2] * w001
            o = idx(b1, g0, r1)
            out += data[o] * w101; utg += data[o + 1] * w101; utb += data[o + 2] * w101
            o = idx(b1, g1, r0)
            out += data[o] * w011; utg += data[o + 1] * w011; utb += data[o + 2] * w011
            o = idx(b1, g1, r1)
            out += data[o] * w111; utg += data[o + 1] * w111; utb += data[o + 2] * w111
            return floatArrayOf(out, utg, utb)
        }

        private inline fun idx(b: Int, g: Int, r: Int): Int = (b * n + g) * n * 3 + r * 3
    }

    /** boost 曲线参数, 默认全部不变(1.0/0.0), 与 mola 静态路径一致。 */
    data class BoostParams(
        val sat: Float = 1f,
        val black: Float = 0f,
        val gamma: Float = 1f,
        val contrast: Float = 1f,
        val shoulder: Boolean = true,
        val white: Float = 1f,
    )

    /**
     * 对位图应用 boost + LUT。
     * @param source 源位图(ARGB_8888, 只读)
     * @param lut    LUT 数据; null 时仅应用 boost
     * @param params boost 曲线参数
     * @return 新位图(与 source 同尺寸 ARGB_8888)。IO 线程调用。
     */
    fun apply(source: Bitmap, lut: LutData?, params: BoostParams = BoostParams()): Bitmap {
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val sat = params.sat
        val invDenom = 1f / maxOf(params.white - params.black, 0.004f)
        var i = 0
        while (i < src.size) {
            val px = src[i]
            val a = (px ushr 24) and 0xFF
            val r = ((px ushr 16) and 0xFF) / 255f
            val g = ((px ushr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            // —— boost: 黑点 → gamma → 对比 → 肩部 → 饱和 ——
            var cr = ((r - params.black) * invDenom).coerceIn(0f, 1f)
            var cg = ((g - params.black) * invDenom).coerceIn(0f, 1f)
            var cb = ((b - params.black) * invDenom).coerceIn(0f, 1f)
            if (kotlin.math.abs(params.gamma - 1f) > 0.001f) {
                cr = cr.pow(params.gamma); cg = cg.pow(params.gamma); cb = cb.pow(params.gamma)
            }
            if (kotlin.math.abs(params.contrast - 1f) > 0.001f) {
                cr = ((cr - 0.5f) * params.contrast + 0.5f).coerceIn(0f, 1f)
                cg = ((cg - 0.5f) * params.contrast + 0.5f).coerceIn(0f, 1f)
                cb = ((cb - 0.5f) * params.contrast + 0.5f).coerceIn(0f, 1f)
            }
            if (params.shoulder) {
                // 高光肩部: >0.784 软压, 缓出到 0.98
                if (cr >= 0.784f) { val t = ((cr - 0.784f) / 0.216f).coerceIn(0f, 1f); cr = 0.784f + 0.196f * (1f - (1f - t) * (1f - t)) }
                if (cg >= 0.784f) { val t = ((cg - 0.784f) / 0.216f).coerceIn(0f, 1f); cg = 0.784f + 0.196f * (1f - (1f - t) * (1f - t)) }
                if (cb >= 0.784f) { val t = ((cb - 0.784f) / 0.216f).coerceIn(0f, 1f); cb = 0.784f + 0.196f * (1f - (1f - t) * (1f - t)) }
            }
            val l = 0.299f * cr + 0.587f * cg + 0.114f * cb
            var or = (l + (cr - l) * sat).coerceIn(0f, 1f)
            var og = (l + (cg - l) * sat).coerceIn(0f, 1f)
            var ob = (l + (cb - l) * sat).coerceIn(0f, 1f)
            // —— LUT(boost 之后) ——
            if (lut != null) {
                val s = lut.sample(or, og, ob)
                or = s[0]; og = s[1]; ob = s[2]
            }
            dst[i] = (a shl 24) or ((or * 255f).toInt().coerceIn(0, 255) shl 16) or
                ((og * 255f).toInt().coerceIn(0, 255) shl 8) or
                (ob * 255f).toInt().coerceIn(0, 255)
            i++
        }
        out.setPixels(dst, 0, w, 0, 0, w, h)
        return out
    }

    /** 快速预览: 采样到位图 grid 尺寸(如 96×96), 供滤镜轮缩略图。 */
    fun previewBitmap(lut: LutData?, size: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val px = IntArray(size * size)
        val step = 255f / (size - 1)
        var i = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = x * step / 255f
                val g = y * step / 255f
                val b = 0.6f
                var cr = r; var cg = g; var cb = b
                val l = 0.299f * cr + 0.587f * cg + 0.114f * cb
                var or = l + (cr - l)
                var og = l + (cg - l)
                var ob = l + (cb - l)
                if (lut != null) {
                    val s = lut.sample(or, og, ob)
                    or = s[0]; og = s[1]; ob = s[2]
                }
                px[i++] = (0xFF shl 24) or ((or * 255f).toInt().coerceIn(0, 255) shl 16) or
                    ((og * 255f).toInt().coerceIn(0, 255) shl 8) or
                    (ob * 255f).toInt().coerceIn(0, 255)
            }
        }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }
}