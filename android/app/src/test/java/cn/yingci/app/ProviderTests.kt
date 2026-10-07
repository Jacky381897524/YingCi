package cn.yingci.app

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ProviderTests {
    private val p=Preferences(baseUrl="https://openrouter.ai/api",apiKey="fake-only",model="vendor/model")
    private fun client(handler:(Request)->Pair<Int,String>)=VisionClient(OkHttpClient.Builder().addInterceptor{chain->
        val (code,body)=handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody("application/json".toMediaType())).build()
    }.build())
    private fun body(request:Request):JSONObject {val b=Buffer();request.body!!.writeTo(b);return JSONObject(b.readUtf8())}
    private fun reply(text:String) = JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content",text)))).toString()
    @Test fun openRouterMissingVersionAndFullEndpointsResolveConsistently(){
        listOf("", "/", "/api", "/api/", "/v1", "/api/v1", "/api/chat/completions/", "/api/v1/chat/completions", "/api/v1/models").forEach{
            val url="https://openrouter.ai$it"
            assertEquals("/api/v1/chat/completions",ApiEndpoint.chat(url).encodedPath)
            assertEquals("/api/v1/models",ApiEndpoint.models(url).encodedPath)
        }
    }
    @Test fun officialProvidersResolveTheirOwnPrefixes(){
        mapOf("api.openai.com" to "/v1", "api.deepseek.com" to "/v1", "api.moonshot.cn" to "/v1",
            "api.siliconflow.cn" to "/v1", "api.x.ai" to "/v1", "api.mistral.ai" to "/v1",
            "openrouter.ai" to "/api/v1", "generativelanguage.googleapis.com" to "/v1beta/openai",
            "dashscope.aliyuncs.com" to "/compatible-mode/v1", "dashscope-intl.aliyuncs.com" to "/compatible-mode/v1",
            "open.bigmodel.cn" to "/api/paas/v4", "api.z.ai" to "/api/paas/v4", "ark.cn-beijing.volces.com" to "/api/v3",
            "qianfan.baidubce.com" to "/v2", "api.groq.com" to "/openai/v1").forEach{(host,path)->
            assertEquals("https://$host$path/chat/completions",ApiEndpoint.chat("https://$host").toString())
            assertEquals("https://$host$path/models",ApiEndpoint.models("https://$host$path/").toString())
        }
        assertEquals("/v1/messages",ApiEndpoint.chat("https://api.anthropic.com").encodedPath)
    }
    @Test fun aliasesDoNotRewriteCustomPathsOrLookalikeHosts(){
        listOf("https://openrouter.ai.evil.test/api", "https://proxy.test/api", "https://openrouter.ai/custom/v2", "https://open.bigmodel.cn/api/coding/paas/v4").forEach{
            assertEquals("$it/chat/completions",ApiEndpoint.chat(it).toString())
        }
        assertFalse(ApiEndpoint.anthropic("https://api.anthropic.com.evil.test/v1"))
        listOf("http://openrouter.ai/api","https://key@openrouter.ai/api","https://openrouter.ai/api?key=secret","https://openrouter.ai/api#x").forEach{
            assertTrue(runCatching{ApiEndpoint.chat(it)}.isFailure)
        }
    }
    @Test fun independentOpenRouterModelsAndTextTestUseCorrectCredentials()=runBlocking{
        val independent=Preferences(baseUrl="https://old.test/v1",apiKey="old-key",model="old",chatIndependent=true,chatUrl=p.baseUrl,chatKey=p.apiKey,chatModel=p.model).chatConnection()
        var calls=0
        val api=client{request->
            calls++;assertEquals("openrouter.ai",request.url.host);assertEquals("Bearer fake-only",request.header("Authorization"))
            if(request.method=="GET") { assertEquals("/api/v1/models",request.url.encodedPath);200 to """{"data":[{"id":"vendor/model"}]}""" }
            else {assertEquals("/api/v1/chat/completions",request.url.encodedPath);val json=body(request)
                assertEquals("vendor/model",json.getString("model"));assertTrue(json.getJSONArray("messages").getJSONObject(0).get("content") is String)
                assertFalse(json.toString().contains("image_url"));200 to reply("连接成功")}
        }
        assertEquals(listOf("vendor/model"),api.models(independent));api.testChat(independent);assertEquals(2,calls)
    }
    @Test fun geminiOfficialCompatibilitySupportsModelsAndConversation()=runBlocking{
        val gemini=p.copy(baseUrl="https://generativelanguage.googleapis.com/v1beta",model="gemini-example")
        val api=client{request->
            assertEquals("Bearer fake-only",request.header("Authorization"));assertNull(request.url.queryParameter("key"))
            if(request.method=="GET"){assertEquals("/v1beta/openai/models",request.url.encodedPath);200 to """{"data":[{"id":"gemini-example"}]}"""}
            else{assertEquals("/v1beta/openai/chat/completions",request.url.encodedPath);200 to reply("正常回复")}
        }
        assertEquals(listOf("gemini-example"),api.models(gemini));api.testChat(gemini)
    }
    @Test fun claudeConvertsSystemHistoryAndImagesAndIgnoresThinking()=runBlocking{
        val claude=p.copy(baseUrl="https://api.anthropic.com/v1",model="claude-example")
        val messages=JSONArray().put(JSONObject().put("role","system").put("content","system rules"))
            .put(JSONObject().put("role","user").put("content",JSONArray().put(JSONObject().put("type","text").put("text","identify"))
                .put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/png;base64,AAAA")))))
            .put(JSONObject().put("role","assistant").put("content","previous answer"))
            .put(JSONObject().put("role","user").put("content","continue"))
        val answer=client{request->
            assertEquals("/v1/messages",request.url.encodedPath);assertEquals("fake-only",request.header("x-api-key"));assertNull(request.header("Authorization"));assertEquals("2023-06-01",request.header("anthropic-version"))
            val json=body(request);assertEquals("system rules",json.getString("system"));assertEquals(4096,json.getInt("max_tokens"));assertEquals(3,json.getJSONArray("messages").length())
            val image=json.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1)
            assertEquals("image",image.getString("type"));assertEquals("image/png",image.getJSONObject("source").getString("media_type"));assertEquals("AAAA",image.getJSONObject("source").getString("data"))
            200 to """{"content":[{"type":"thinking","thinking":"private"},{"type":"text","text":"answer"}],"stop_reason":"end_turn"}"""
        }.request(claude,messages)
        assertEquals("answer",answer)
    }
    @Test fun claudeModelPaginationStaysOnConfiguredHost()=runBlocking{
        var calls=0
        val ids=client{request->
            calls++;assertEquals("api.anthropic.com",request.url.host);assertEquals("/v1/models",request.url.encodedPath);assertEquals("fake-only",request.header("x-api-key"))
            if(calls==1)200 to """{"data":[{"id":"first"}],"has_more":true,"last_id":"first"}"""
            else {assertEquals("first",request.url.queryParameter("after_id"));200 to """{"data":[{"id":"second"}],"has_more":false}"""}
        }.models(p.copy(baseUrl="https://api.anthropic.com"))
        assertEquals(listOf("first","second"),ids);assertEquals(2,calls)
    }
    @Test fun malformedPaginationStopsInsteadOfLooping()=runBlocking{
        var calls=0
        val result=runCatching{client{calls++;200 to """{"data":[],"has_more":true,"last_id":"same"}"""}.models(p.copy(baseUrl="https://api.anthropic.com"))}
        assertTrue(result.isFailure);assertEquals(2,calls)
    }
    @Test fun textOnlyPartsBecomeStringsWithoutMutatingHistory(){
        val messages=JSONArray("""[{"role":"assistant","content":[{"type":"text","text":"one"},{"type":"text","text":"two"}]}]""")
        assertEquals("one\ntwo",ChatProtocol.body(p,messages).getJSONArray("messages").getJSONObject(0).getString("content"))
        assertTrue(messages.getJSONObject(0).get("content") is JSONArray)
    }
    @Test fun errorsEmptyRepliesAndTruncationNeverPassTextTest()=runBlocking{
        listOf(401 to "{}",404 to "{}",503 to "{}",200 to reply(""),200 to "{}").forEach{response->
            var calls=0;assertTrue(runCatching{client{calls++;response}.testChat(p)}.isFailure);assertEquals(1,calls)
        }
        assertTrue(runCatching{client{200 to """{"content":[{"type":"text","text":"partial"}],"stop_reason":"max_tokens"}"""}.testChat(p.copy(baseUrl="https://api.anthropic.com"))}.isFailure)
    }
    @Test fun explicitProxyMessagesEndpointUsesClaudeWithoutChangingHost(){
        val url="https://proxy.test/custom/v1/messages/"
        assertTrue(ApiEndpoint.anthropic(url));assertEquals("https://proxy.test/custom/v1/messages",ApiEndpoint.chat(url).toString())
        assertEquals("https://proxy.test/custom/v1/models",ApiEndpoint.models(url).toString())
    }
}
