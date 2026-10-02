package com.aicamera.core.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** mola 滤镜清单条目(来自 assets/luts/manifest.json)。 */
data class MolaFilter(
    val id: String,
    val category: String,
    val lutFile: String,
    val coverFile: String,
) {
    /** 显示名(与 id 相同, mola 即用中文 id)。 */
    val label: String get() = id
}

/**
 * mola LUT 资源仓库: 解析 manifest.json,按需解码 .lut,内存缓存。
 * 与 mola 的 151 款滤镜完全一致(13 分类)。
 */
object LutRepository {

    @Volatile private var filters: List<MolaFilter> = emptyList()
    @Volatile private var categories: List<String> = emptyList()
    private val cache = HashMap<String, MolaLut.LutData>()

    /** 加载 manifest(幂等)。IO 线程调用,之后可用主线程读 filters/categories。 */
    suspend fun ensureLoaded(context: Context) {
        if (filters.isNotEmpty()) return
        withContext(Dispatchers.IO) {
            val text = context.assets.open("luts/manifest.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            val catArr = obj.getJSONArray("categories")
            val cats = mutableListOf<String>()
            for (i in 0 until catArr.length()) cats.add(catArr.getString(i))
            val fArr = obj.getJSONArray("filters")
            val fs = mutableListOf<MolaFilter>()
            for (i in 0 until fArr.length()) {
                val f = fArr.getJSONObject(i)
                fs.add(MolaFilter(f.getString("id"), f.getString("category"), f.getString("lut"), f.getString("cover")))
            }
            synchronized(this) {
                categories = cats
                filters = fs
            }
        }
    }

    fun allFilters(): List<MolaFilter> = filters

    fun categories(): List<String> = categories

    fun filtersIn(category: String): List<MolaFilter> = filters.filter { it.category == category }

    /** 按 id 取滤镜;id=null 或不存在返回 null。 */
    fun find(id: String?): MolaFilter? = id?.let { i -> filters.firstOrNull { it.id == i } }

    /** 解码并缓存 LUT(IO 线程),主线程调用会抛异常? 不——由调用方负责 IO。 */
    suspend fun lutFor(context: Context, filter: MolaFilter): MolaLut.LutData? = withContext(Dispatchers.IO) {
        val cached = synchronized(cache) { cache[filter.lutFile] }
        if (cached != null) {
            cached
        } else {
            val bytes = context.assets.open("luts/${filter.lutFile}").readBytes()
            val lut = MolaLut.decode(bytes)
            if (lut != null) synchronized(cache) { cache[filter.lutFile] = lut }
            lut
        }
    }

    /** 清除 LUT 缓存(可选项画内存)。 */
    fun clearCache() { synchronized(cache) { cache.clear() } }
}