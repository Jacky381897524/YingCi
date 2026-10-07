package cn.yingci.app

import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

internal object ServiceError {
    fun from(response: Response, apiKey: String = ""): IOException {
        val fallback = when (response.code) {
            401, 403 -> "身份验证失败，请检查 API Key 和模型权限"
            402 -> "服务商拒绝了本次计费请求，请检查账户余额、密钥消费限额与模型费用"
            404 -> "接口或模型不存在，请检查服务地址与模型名称"
            429 -> "服务限流或额度不足，请查看服务商提示"
            400, 422 -> "服务未接受请求，请检查模型和接口格式"
            503 -> "服务暂不可用，需结合下方服务商提示确认原因"
            in 300..399 -> "服务返回重定向，请填写最终 HTTPS 接口地址"
            else -> "模型服务返回错误"
        }
        // Only display bounded, structured error fields, never an HTML gateway page.
        val root = runCatching { JSONObject(response.peekBody(16 * 1024L).string()) }.getOrNull()
        val error = root?.optJSONObject("error")
        val detail = (error?.opt("message") as? String)
            ?: (root?.opt("message") as? String)
            ?: (root?.opt("error") as? String)
        val code = error?.opt("code")?.takeIf { it is String || it is Number }?.toString()
        val summary = when {
            response.code == 402 && detail?.contains("max_tokens", ignoreCase = true) == true ->
                "本次请求的预估费用超过可用额度，涉及输出长度上限。请检查账户余额或密钥消费限额，也可选择费用更低的模型。"
            detail?.contains("is an image model", ignoreCase = true) == true &&
                response.request.url.encodedPath.endsWith("/chat/completions") ->
                "识图模型类型不匹配：当前是生图模型。请更换为支持读图并返回文字的模型；该生图模型只填在生图模型一栏。"
            detail?.contains("No available channel", ignoreCase = true) == true ->
                "服务商没有为当前密钥分组提供此模型的可用渠道。请核对模型 ID，或联系服务商检查密钥分组、模型权限和渠道状态。"
            code == "model_not_found" -> "服务商找不到此模型或当前密钥无权使用，请核对模型 ID 和权限。"
            else -> fallback
        }
        fun clean(value: String?, limit: Int): String {
            var text = value.orEmpty()
            if (apiKey.isNotBlank()) text = text.replace(apiKey.trim(), "[已隐藏密钥]")
            text = text.replace(Regex("(?i)Bearer\\s+[^\\s\"<>]+"), "Bearer [已隐藏]")
                .replace(Regex("\\bsk-[A-Za-z0-9_-]+"), "[已隐藏密钥]")
                .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ").trim()
            return text.take(limit)
        }
        val message = buildString {
            append("$summary（HTTP ${response.code}）")
            val description = clean(detail, 500)
            if (description.isNotBlank()) append("\n服务商：$description")
            else append("\n服务未返回可读的错误原因，请稍后重试或联系服务商。")
            val errorCode = clean(code, 80)
            if (errorCode.isNotBlank()) append("\n错误代码：$errorCode")
            val requestId = clean(response.header("x-request-id") ?: response.header("x-trace-id"), 100)
            if (requestId.isNotBlank()) append("\n请求编号：$requestId")
        }
        return IOException(message)
    }
}
