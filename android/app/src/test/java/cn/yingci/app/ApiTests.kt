package cn.yingci.app

import android.graphics.Bitmap
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
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApiTests {
    private val prefs=Preferences(baseUrl="https://example.test/v1",apiKey="test-only-key",model="vision-test",imageModel="image-test")
    private fun client(handler:(Request)->Pair<Int,String>)=VisionClient(OkHttpClient.Builder().addInterceptor{chain->
        val (code,json)=handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").body(json.toResponseBody("application/json".toMediaType())).build()
    }.build())
    private fun body(request:Request):JSONObject {val buffer=Buffer();request.body!!.writeTo(buffer);return JSONObject(buffer.readUtf8())}
    private fun reply(text:String)=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","stop").put("message",JSONObject().put("content",text)))).toString()
    @Test fun visionTestActuallySendsAndChecksImage()=runBlocking{
        client { request->
            assertEquals("/v1/chat/completions",request.url.encodedPath)
            assertEquals("Bearer test-only-key",request.header("Authorization"))
            val json=body(request);assertEquals("vision-test",json.getString("model"))
            val parts=json.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
            val url=parts.getJSONObject(1).getJSONObject("image_url").getString("url")
            val bytes=Base64.decode(url.substringAfter(','),Base64.DEFAULT)
            val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            val color=image.getPixel(0,0);image.recycle()
            val answer=when(color){Color.RED->"RED";Color.GREEN->"GREEN";Color.BLUE->"BLUE";else->error("unexpected color")}
            200 to reply(answer)
        }.test(prefs)
    }
    @Test fun genericTextSuccessDoesNotPassVisionTest()=runBlocking{
        assertTrue(runCatching{client{200 to reply("连接成功")}.test(prefs)}.isFailure)
    }
    @Test fun authFailureIsActionable()=runBlocking{
        val failure=runCatching{client{401 to "{}"}.test(prefs)}.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("验证失败"))
    }
    @Test fun generationSendsSelectedModelAndValidatesImage()=runBlocking{
        val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);image.eraseColor(Color.GREEN)
        val buffer=ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,buffer);image.recycle()
        val encoded=Base64.encodeToString(buffer.toByteArray(),Base64.NO_WRAP)
        client{request->
            assertEquals("/v1/images/generations",request.url.encodedPath)
            assertEquals("image-test",body(request).getString("model"))
            200 to JSONObject().put("data",JSONArray().put(JSONObject().put("b64_json",encoded))).toString()
        }.testGeneration(prefs)
    }
    @Test fun invalidGenerationDataCannotPass()=runBlocking{
        assertTrue(runCatching{client{200 to "{\"data\":[{\"b64_json\":\"bm90IGFuIGltYWdl\"}]}"}.testGeneration(prefs)}.isFailure)
    }
    @Test fun bigModelGenerationTestMatchesActualRequest()=runBlocking{
        val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);image.eraseColor(Color.GREEN)
        val buffer=ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,buffer);image.recycle()
        val encoded=Base64.encodeToString(buffer.toByteArray(),Base64.NO_WRAP)
        client{request->
            assertEquals("/api/paas/v4/images/generations",request.url.encodedPath)
            val json=body(request);assertEquals("glm-image",json.getString("model"))
            assertEquals("1280x1280",json.getString("size"));assertEquals("hd",json.getString("quality"));assertFalse(json.has("n"))
            200 to JSONObject().put("data",JSONArray().put(JSONObject().put("b64_json",encoded))).toString()
        }.testGeneration(prefs.copy(baseUrl="https://open.bigmodel.cn/api/paas/v4",imageModel="glm-image"))
    }
    @Test fun fullChatUrlDerivesSiblingImageEndpoint(){
        assertEquals("https://example.test/v1/images/generations",ApiEndpoint.images("https://example.test/v1/chat/completions").toString())
    }
    @Test fun agnesBaseUrlUsesSingleVersionPrefix(){
        assertEquals("https://apihub.agnes-ai.com/v1/chat/completions",ApiEndpoint.chat("https://apihub.agnes-ai.com/v1").toString())
        assertEquals("https://apihub.agnes-ai.com/v1/images/generations",ApiEndpoint.images("https://apihub.agnes-ai.com/v1/").toString())
    }
    @Test fun unavailableVisionPreservesProviderReasonWithoutRetry()=runBlocking{
        var calls=0
        val failure=runCatching{client{calls++;503 to """{"error":{"message":"No available channel for model vision-test","code":"no_available_channel"}}"""}.test(prefs)}.exceptionOrNull()
        assertEquals(1,calls)
        assertTrue(failure?.message.orEmpty().contains("HTTP 503"))
        assertTrue(failure?.message.orEmpty().contains("No available channel for model vision-test"))
        assertTrue(failure?.message.orEmpty().contains("no_available_channel"))
    }
    @Test fun generationAlsoPreservesProviderReason()=runBlocking{
        val failure=runCatching{client{503 to """{"message":"Upstream image service unavailable"}"""}.testGeneration(prefs)}.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("Upstream image service unavailable"))
    }
    private fun errorResponse(body:String,type:String="application/json")=Response.Builder()
        .request(Request.Builder().url("https://example.test/v1/chat/completions").build())
        .protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
        .header("x-request-id","trace-test-123").body(body.toResponseBody(type.toMediaType())).build()
    @Test fun diagnosticRedactsCredentialsAndRetainsRequestId(){
        val json=JSONObject().put("error",JSONObject().put("message","Rejected test-only-key and Bearer abc-secret and sk-upstream-secret"))
        errorResponse(json.toString()).use{response->
            val message=ServiceError.from(response,prefs.apiKey).message.orEmpty()
            assertFalse(message.contains(prefs.apiKey))
            assertFalse(message.contains("abc-secret"))
            assertFalse(message.contains("sk-upstream-secret"))
            assertTrue(message.contains("trace-test-123"))
        }
    }
    @Test fun diagnosticBoundsLongJsonAndHidesHtml(){
        errorResponse("<html>SECRET GATEWAY PAGE</html>","text/html").use{
            val message=ServiceError.from(it).message.orEmpty()
            assertTrue(message.contains("HTTP 503"))
            assertFalse(message.contains("SECRET GATEWAY PAGE"))
        }
        errorResponse(JSONObject().put("message","x".repeat(10000)).toString()).use{
            assertTrue(ServiceError.from(it).message.orEmpty().length<1000)
        }
        errorResponse(JSONObject().put("message","x".repeat(40000)).toString()).use{
            assertTrue(ServiceError.from(it).message.orEmpty().contains("未返回可读"))
        }
    }
    @Test fun modelsUseAuthenticatedGetWithoutGeneration()=runBlocking{
        val ids=client{request->
            assertEquals("GET",request.method)
            assertNull(request.body)
            assertEquals("/v1/models",request.url.encodedPath)
            assertEquals("Bearer test-only-key",request.header("Authorization"))
            200 to """{"data":[{"id":"vision-exact-id"},{"id":"Agnes-Image-2.5 Flash"},{"id":"vision-exact-id"},{"id":null},{}]}"""
        }.models(prefs.copy(model="",imageModel=""))
        assertEquals(listOf("Agnes-Image-2.5 Flash","vision-exact-id"),ids)
    }
    @Test fun modelsReturnEmptyWithoutInventingDefaults()=runBlocking{
        assertTrue(client{200 to """{"data":[]}"""}.models(prefs).isEmpty())
    }
    @Test fun modelsErrorsRemainActionable()=runBlocking{
        val denied=runCatching{client{401 to "{}"}.models(prefs)}.exceptionOrNull()
        assertTrue(denied?.message.orEmpty().contains("验证失败"))
        val malformed=runCatching{client{200 to "<html>login</html>"}.models(prefs)}.exceptionOrNull()
        assertTrue(malformed?.message.orEmpty().contains("手动填写"))
        assertTrue(runCatching{client{200 to "{}"}.models(prefs)}.isFailure)
    }
    @Test fun screenshotImageModelErrorExplainsWrongPurpose()=runBlocking{
        val failure=runCatching{client{400 to """{"error":{"message":"Model Agnes-Image-2.5 Flash is an image model. Use /v1/images/generations.","code":"invalid_request"}}"""}.test(prefs)}.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().startsWith("识图模型类型不匹配"))
        assertTrue(failure?.message.orEmpty().contains("读图并返回文字"))
    }
    @Test fun screenshotChannelErrorExplainsProviderConfiguration()=runBlocking{
        val failure=runCatching{client{503 to """{"error":{"message":"No available channel for model Agnes-Image-2.5 Flash under group default (distributor)","code":"model_not_found"}}"""}.testGeneration(prefs)}.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().startsWith("服务商没有为当前密钥分组"))
        assertTrue(failure?.message.orEmpty().contains("default (distributor)"))
        assertTrue(failure?.message.orEmpty().contains("HTTP 503"))
    }
    @Test fun trailingChatSlashNormalizesEveryEndpoint(){
        val url="https://apihub.agnes-ai.com/v1/chat/completions/"
        assertEquals("/v1/chat/completions",ApiEndpoint.chat(url).encodedPath)
        assertEquals("/v1/models",ApiEndpoint.models(url).encodedPath)
        assertEquals("/v1/images/generations",ApiEndpoint.images(url).encodedPath)
    }
}
