package cn.yingci.app

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal object ChatProtocol {
    const val REPLY_TOKENS = 4096
    const val TEST_TOKENS = 512
    fun authorize(builder: Request.Builder, p: Preferences): Request.Builder {
        builder.header("Accept", "application/json")
        return if (ApiEndpoint.anthropic(p.baseUrl)) {
            builder.header("x-api-key", p.apiKey.trim()).header("anthropic-version", "2023-06-01")
        } else builder.header("Authorization", "Bearer ${p.apiKey.trim()}")
    }

    fun body(p: Preferences, messages: JSONArray, maxOutputTokens: Int = REPLY_TOKENS): JSONObject {
        require(maxOutputTokens in 1..REPLY_TOKENS) { "输出长度上限无效" }
        val native = ApiEndpoint.anthropic(p.baseUrl)
        val turns = JSONArray()
        val system = mutableListOf<String>()
        for (i in 0 until messages.length()) {
            val message = messages.getJSONObject(i)
            val role = message.getString("role")
            val value = message.get("content")
            val parts = if (value is JSONArray) value else JSONArray().put(JSONObject().put("type", "text").put("text", value.toString()))
            if (native && role in setOf("system", "developer")) {
                system.add(text(parts)); continue
            }
            val content: Any = if (native) anthropicParts(parts) else {
                if ((0 until parts.length()).all { parts.getJSONObject(it).optString("type") == "text" }) text(parts) else parts
            }
            turns.put(JSONObject().put("role", role).put("content", content))
        }
        return JSONObject().put("model", p.model.trim()).put("messages", turns).put("stream", false).apply {
            // OpenRouter otherwise reserves credit against a potentially huge model default.
            if (native || ApiEndpoint.base(p.baseUrl).host == "openrouter.ai") put("max_tokens", maxOutputTokens)
            if (native) {
                if (system.isNotEmpty()) put("system", system.joinToString("\n\n"))
            }
        }
    }

    private fun anthropicParts(parts: JSONArray): JSONArray = JSONArray().apply {
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            when (part.getString("type")) {
                "text" -> put(JSONObject().put("type", "text").put("text", part.getString("text")))
                "image_url" -> {
                    val url = part.getJSONObject("image_url").getString("url")
                    val source = if (url.startsWith("data:")) {
                        val comma = url.indexOf(',')
                        require(comma > 5 && url.substring(0, comma).endsWith(";base64")) { "图片编码格式无效" }
                        val mime = url.substring(5, comma).removeSuffix(";base64")
                        require(mime in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) { "不支持此图片格式" }
                        JSONObject().put("type", "base64").put("media_type", mime).put("data", url.substring(comma + 1))
                    } else {
                        val parsed = java.net.URI(url)
                        require(parsed.scheme == "https" && !parsed.host.isNullOrBlank() && parsed.userInfo == null) { "图片地址须为 HTTPS" }
                        JSONObject().put("type", "url").put("url", url)
                    }
                    put(JSONObject().put("type", "image").put("source", source))
                }
                else -> error("不支持此消息内容类型")
            }
        }
    }

    private fun text(parts: JSONArray): String = buildList {
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i)
            if (part?.optString("type") == "text") add(part.optString("text"))
        }
    }.joinToString("\n")

    fun response(p: Preferences, root: JSONObject): String {
        val result = if (ApiEndpoint.anthropic(p.baseUrl)) {
            require(root.optString("stop_reason") != "max_tokens") { "模型输出被截断，请缩短问题或更换模型后重试" }
            text(root.optJSONArray("content") ?: JSONArray())
        } else {
            val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: error("服务未返回 Chat Completions 格式的文字结果")
            require(choice.optString("finish_reason") != "length") { "模型回复达到输出长度上限，未作为完整结果保存；请缩短问题或拆分请求后重试" }
            val content = choice.optJSONObject("message")?.opt("content")
            when (content) { is String -> content; is JSONArray -> text(content); else -> "" }
        }
        require(result.isNotBlank()) { "模型没有返回可用文字，请检查模型是否支持此接口" }
        return result
    }
}
