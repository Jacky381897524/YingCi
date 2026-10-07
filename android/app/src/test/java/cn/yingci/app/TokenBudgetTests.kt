package cn.yingci.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TokenBudgetTests {
    private val p=Preferences(baseUrl="https://openrouter.ai/api/v1",model="typesafe/jev-router",apiKey="fake-budget-key")
    private val screenshotError="""{"error":{"code":402,"message":"This request requires more credits, or fewer max_tokens. You requested up to 131072 tokens, but can only afford 59922."}}"""
    private fun client(handler:(Request)->Pair<Int,String>)=VisionClient(OkHttpClient.Builder().addInterceptor{chain->
        val (code,json)=handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").body(json.toResponseBody("application/json".toMediaType())).build()
    }.build())
    private fun body(request:Request):JSONObject{val buffer=Buffer();request.body!!.writeTo(buffer);return JSONObject(buffer.readUtf8())}
    private fun reply(text:String,finish:String="stop")=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",finish).put("message",JSONObject().put("content",text)))).toString()
    private fun messages()=JSONArray().put(JSONObject().put("role","user").put("content","hello"))
    @Test fun screenshotCreditRejectionIsAvoidedByExplicitProbeBudget()=runBlocking{
        var calls=0;var sent=0
        client{request->
            calls++;val json=body(request);sent=json.optInt("max_tokens",131072)
            if(sent>59922)402 to screenshotError else 200 to reply("连接成功")
        }.testChat(p)
        assertEquals(512,sent);assertEquals(1,calls)
    }
    @Test fun actualReplyUsesBoundedLargerBudgetAndKeepsSelectedModel()=runBlocking{
        var captured:JSONObject?=null
        val answer=client{request->captured=body(request);200 to reply("完整回复")}.request(p,messages())
        assertEquals("完整回复",answer);assertEquals(4096,captured!!.getInt("max_tokens"))
        assertEquals("typesafe/jev-router",captured!!.getString("model"))
        assertFalse(captured!!.has("max_completion_tokens"))
    }
    @Test fun visionProbeAlsoUsesSmallBudgetWithoutSkippingImageValidation()=runBlocking{
        var sent=0
        client{request->
            val json=body(request);sent=json.getInt("max_tokens")
            val url=json.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url")
            val bytes=Base64.decode(url.substringAfter(','),Base64.DEFAULT)
            val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            val color=bitmap.getPixel(0,0);bitmap.recycle()
            200 to reply(when(color){Color.RED->"RED";Color.GREEN->"GREEN";Color.BLUE->"BLUE";else->"UNKNOWN"})
        }.test(p)
        assertEquals(512,sent)
    }
    @Test fun emptyAndTruncatedProbeRepliesStillFail()=runBlocking{
        listOf(reply(""),reply("部分内容","length")).forEach{response->
            var calls=0;assertTrue(runCatching{client{calls++;200 to response}.testChat(p)}.isFailure);assertEquals(1,calls)
        }
    }
    @Test fun real402IsExplainedAndNotRetried()=runBlocking{
        var calls=0
        val error=runCatching{client{calls++;402 to screenshotError}.testChat(p)}.exceptionOrNull()
        assertEquals(1,calls);assertTrue(error?.message.orEmpty().contains("预估费用超过可用额度"))
        assertTrue(error?.message.orEmpty().contains("HTTP 402"));assertTrue(error?.message.orEmpty().contains("59922"))
    }
    @Test fun generic402ExplainsCreditNotConnectivity()=runBlocking{
        val error=runCatching{client{402 to "{}"}.testChat(p)}.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("密钥消费限额"));assertFalse(error?.message.orEmpty().contains("连接失败"))
    }
    @Test fun budgetsDoNotAddUnsupportedFieldsToOtherCompatibleProviders(){
        listOf("https://api.openai.com/v1","https://generativelanguage.googleapis.com/v1beta/openai","https://custom.test/v1","https://openrouter.ai.other.test/api/v1").forEach{url->
            val json=ChatProtocol.body(p.copy(baseUrl=url),messages())
            assertFalse(json.has("max_tokens"));assertFalse(json.has("max_completion_tokens"))
        }
        val native=p.copy(baseUrl="https://api.anthropic.com/v1")
        assertEquals(4096,ChatProtocol.body(native,messages()).getInt("max_tokens"))
        assertEquals(512,ChatProtocol.body(native,messages(),512).getInt("max_tokens"))
        assertEquals(4096,ChatProtocol.body(p.copy(baseUrl="https://openrouter.ai/api"),messages()).getInt("max_tokens"))
    }
    @Test fun invalidBudgetsCannotProduceUnboundedRequests(){
        listOf(0,-1,131072).forEach{assertTrue(runCatching{ChatProtocol.body(p,messages(),it)}.isFailure)}
    }
}
