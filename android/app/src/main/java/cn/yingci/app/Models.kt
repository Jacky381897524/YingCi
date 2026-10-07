package cn.yingci.app

import java.text.Normalizer
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class PromptEntry(val id: String, val name: String, val original: String, val edited: String?, val cover: String?, val created: Long, val updated: Long) {
    val current: String get() = edited ?: original
}
data class Preferences(val theme: Int = 0, val followFont: Boolean = false, val fontPercent: Int = 100, val reduceMotion: Boolean = false, val haptics: Boolean = true, val baseUrl: String = "", val model: String = "", val apiKey: String = "", val fontWeight:Int=400, val display:DisplayMaterial=DisplayMaterial(), val imageModel:String="",val wallpaper:String="",val wallpaperPages:Set<Int> = setOf(0,1,2,3),val themePreset:String="纯净",val chatIndependent:Boolean=false,val chatUrl:String="",val chatModel:String="",val chatKey:String="",val chatWallpaper:String="") {
    fun chatConnection()=if(chatIndependent)copy(baseUrl=chatUrl,model=chatModel,apiKey=chatKey)else this
}

val FontSizes=listOf(85,100,115,130,145)
fun nearestFontSize(value:Int)=FontSizes.minBy{kotlin.math.abs(it-value)}

object PromptSearch {
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
    fun matches(entry: PromptEntry, query: String): Boolean {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val name = normalize(entry.name)
        val all = "$name\n${normalize(entry.original)}\n${normalize(entry.edited.orEmpty())}"
        return terms.all { term -> all.contains(term) || (term.length >= 2 && term.length <= 24 && subsequence(term, name)) || typo(term, name) }
    }
    private fun subsequence(term: String, name: String): Boolean { var i = 0; for (c in name) if (i < term.length && c == term[i]) i++; return i == term.length }
    private fun typo(term: String, name: String): Boolean {
        if (term.length !in 4..32 || name.length > 64) return false
        val limit = if (term.length >= 8) 2 else 1
        return name.split(Regex("[\\s_—-]+")).any { word ->
            if (kotlin.math.abs(word.length - term.length) > limit) false else {
                var row = IntArray(word.length + 1) { it }
                term.forEachIndexed { i, c -> val next = IntArray(word.length + 1); next[0] = i + 1; for (j in word.indices) next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (c == word[j]) 0 else 1); row = next }
                row[word.length] <= limit
            }
        }
    }
}

object ApiEndpoint {
    fun models(raw: String): HttpUrl {
        val base = base(raw)
        return base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/models").build()
    }
    fun images(raw: String): HttpUrl {
        val base = base(raw)
        return base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/images/generations").build()
    }
    fun chat(raw: String): HttpUrl {
        val base = base(raw)
        val suffix = if (anthropic(raw)) "/messages" else "/chat/completions"
        return base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + suffix).build()
    }
    fun anthropic(raw: String): Boolean {
        val url = validated(raw)
        return url.host == "api.anthropic.com" || url.encodedPath.trimEnd('/').endsWith("/messages")
    }
    private fun validated(raw: String): HttpUrl {
        val url = raw.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入完整的 HTTPS 服务地址")
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "服务地址须使用 HTTPS，且不能含账号、参数或片段" }
        return url
    }
    fun base(raw: String): HttpUrl {
        val url = validated(raw)
        var path = url.encodedPath.trimEnd('/')
        for (suffix in listOf("/chat/completions", "/messages", "/models", "/images/generations")) {
            if (path.endsWith(suffix)) { path = path.removeSuffix(suffix); break }
        }
        // Only known official hosts get aliases; custom proxy prefixes stay untouched.
        val defaultPath = when (url.host) {
            "openrouter.ai" -> "/api/v1"
            "generativelanguage.googleapis.com" -> "/v1beta/openai"
            "dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com", "dashscope-us.aliyuncs.com" -> "/compatible-mode/v1"
            "open.bigmodel.cn", "api.z.ai" -> "/api/paas/v4"
            "ark.cn-beijing.volces.com" -> "/api/v3"
            "qianfan.baidubce.com" -> "/v2"
            "api.groq.com" -> "/openai/v1"
            else -> "/v1"
        }
        val aliases = when (url.host) {
            "openrouter.ai" -> setOf("", "/api", "/v1")
            "generativelanguage.googleapis.com" -> setOf("", "/v1", "/v1beta")
            "dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com", "dashscope-us.aliyuncs.com" -> setOf("", "/compatible-mode")
            "open.bigmodel.cn", "api.z.ai" -> setOf("", "/api", "/api/paas")
            "ark.cn-beijing.volces.com" -> setOf("", "/api")
            "api.groq.com" -> setOf("", "/openai")
            else -> setOf("")
        }
        if (path in aliases) path = defaultPath
        return url.newBuilder().encodedPath(path).build()
    }
}

