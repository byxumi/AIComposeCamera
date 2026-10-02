package com.aicamera.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 云端 AI 提供方（预留接口）。
 *
 * 设计：把本地规则引擎（LocalAiRules）与云端大模型（AiProvider）解耦。
 * - 默认走本地规则（离线可用、零成本）
 * - 配置 API key 后可切换云端（如 Qwen/阶跃/豆包 视觉模型），上传当前帧 → 返回拍摄建议文案
 */
object AiProvider {

    /** 云端 AI 配置（由设置页填写） */
    var apiBaseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    var apiKey: String = ""
    var modelName: String = "qwen-vl-plus"
    var enabled: Boolean = false

    /**
     * 请求云端视觉模型分析画面。
     * @param imageBase64 当前帧 JPEG base64（无前缀）
     * @param prompt 用户拍摄意图
     * @return AI 建议文案；失败返回 null
     */
    suspend fun analyzeImage(context: Context, imageBase64: String, prompt: String): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || imageBase64.isBlank()) return@withContext null
            try {
                val payload = JSONObject().apply {
                    put("model", modelName)
                    put("messages", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "你是一名专业摄影师助手。根据用户拍摄意图与当前画面，用简体中文给出简洁的构图/光线/拍摄建议，不超过60字。")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64"))
                                })
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", prompt.ifBlank { "帮我看一下这个画面怎么拍更好" })
                                })
                            })
                        })
                    })
                    put("max_tokens", 200)
                }

                val conn = (URL(apiBaseUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                }
                conn.outputStream.use { os: OutputStream ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: return@withContext null
                conn.disconnect()
                if (code !in 200..299) return@withContext null
                val json = JSONObject(text)
                val content = json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                content.trim()
            } catch (e: Exception) {
                null
            }
        }
}