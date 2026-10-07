package cn.yingci.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerationTests {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private val p=Preferences(baseUrl="https://apihub.agnes-ai.com/v1",apiKey="unit-test-not-real",imageModel="agnes-image-2.5-flash")
    private fun picture():ByteArray {val bitmap=Bitmap.createBitmap(12,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.GREEN);val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return out.toByteArray()}
    private fun input():File {val folder=File(context.cacheDir,"drafts").apply{mkdirs()};return File(folder,"${System.nanoTime()}.webp").apply{writeBytes(picture())}}
    private fun body(r:Request):JSONObject{val buffer=Buffer();r.body!!.writeTo(buffer);return JSONObject(buffer.readUtf8())}
    @Test fun modeIsInferredAndSeparateControlsBatchCount(){
        val files=listOf(File("a"),File("b"))
        assertEquals(2,GenerationDraft(count=2).batches().size)
        assertEquals(2,GenerationDraft(references=files,count=2).batches().size)
        assertEquals(4,GenerationDraft(references=files,separate=true,count=2).batches().size)
        assertTrue(GenerationDraft(references=files,separate=true,count=2).batches().all{it.size==1})
        assertTrue(runCatching{GenerationDraft(count=0).batches()}.isFailure)
    }
    @Test fun agnesUsesDocumentedExtraBodyAndDataUriWithoutUploadingElsewhere(){
        val client=GenerationClient();val file=input();val request=client.buildRequest(p,GenerationDraft(prompt="test",quality="2K",ratio="3:4"),listOf(file))
        val json=body(request);assertEquals("/v1/images/generations",request.url.encodedPath)
        assertEquals("2K",json.getString("size"));assertEquals("3:4",json.getString("ratio"));assertEquals(1,json.getInt("n"));assertFalse(json.has("response_format"));assertTrue(request.body!!.isOneShot())
        assertTrue(json.getJSONObject("extra_body").getJSONArray("image").getString(0).startsWith("data:image/webp;base64,"))
    }
    @Test fun agnesTextUsesNoImageAndRequestsBase64OnlyWhenSelected(){
        val request=GenerationClient().buildRequest(p,GenerationDraft(prompt="test",format="b64_json"),emptyList())
        val json=body(request);assertTrue(json.getBoolean("return_base64"));assertFalse(json.getJSONObject("extra_body").has("image"))
    }
    @Test fun unknownCapabilitiesDoNotDropReferenceImages(){
        assertTrue(runCatching{GenerationClient().buildRequest(p.copy(imageModel="unknown-image"),GenerationDraft(prompt="test"),listOf(input()))}.isFailure)
        val json=body(GenerationClient().buildRequest(p.copy(imageModel="unknown-image"),GenerationDraft(prompt="test"),emptyList()))
        assertFalse(json.has("size"));assertFalse(json.has("quality"));assertEquals(1,json.getInt("n"))
    }
    @Test fun gptImageUsesMultipartWithAllReferences(){
        val request=GenerationClient().buildRequest(p.copy(imageModel="gpt-image-1"),GenerationDraft(prompt="test",quality="auto",format="png"),listOf(input(),input()))
        val buffer=Buffer();request.body!!.writeTo(buffer);val encoded=buffer.readUtf8()
        assertEquals("/v1/images/edits",request.url.encodedPath);assertEquals(2,Regex("name=\"image\\[\\]\"").findAll(encoded).count())
    }
    @Test fun invalidProviderOptionsAreRejectedBeforeSending(){
        val client=GenerationClient()
        assertTrue(runCatching{client.buildRequest(p,GenerationDraft(prompt="test",quality="99K"),emptyList())}.isFailure)
        assertTrue(runCatching{client.buildRequest(p,GenerationDraft(prompt="test",ratio="99:1"),emptyList())}.isFailure)
        assertTrue(runCatching{client.buildRequest(p,GenerationDraft(prompt="test",format="unknown"),emptyList())}.isFailure)
    }
    @Test fun bigModelPickerOffersDocumentedImageModelsWithoutMislabelingChatModels(){
        assertTrue(BigModelImage.isHost("https://open.bigmodel.cn/api/paas/v4"))
        val choices=BigModelImage.candidates(listOf("glm-5.3-flashx","glm-5.3-image-understanding","cogview-next"))
        assertTrue(choices.contains("glm-image"));assertTrue(choices.contains("cogview-4"))
        assertTrue(choices.contains("cogview-next"));assertFalse(choices.contains("glm-5.3-flashx"));assertFalse(choices.contains("glm-5.3-image-understanding"))
    }
    @Test fun bigModelUsesItsOwnImageParametersAndRejectsTextModels(){
        val config=p.copy(baseUrl="https://open.bigmodel.cn/api/paas/v4",imageModel="glm-image")
        val client=GenerationClient()
        val request=client.buildRequest(config,GenerationDraft(prompt="test",quality="hd",ratio="3:2"),emptyList())
        val json=body(request)
        assertEquals("/api/paas/v4/images/generations",request.url.encodedPath)
        assertEquals("1568x1056",json.getString("size"));assertEquals("hd",json.getString("quality"));assertFalse(json.has("n"))
        val cogview=body(client.buildRequest(config.copy(imageModel="cogview-4"),GenerationDraft(prompt="test",quality="standard",ratio="1:1"),emptyList()))
        assertEquals("1024x1024",cogview.getString("size"))
        assertTrue(runCatching{client.buildRequest(config.copy(imageModel="glm-5.3-flashx"),GenerationDraft(prompt="test"),emptyList())}.exceptionOrNull()?.message.orEmpty().contains("对话/识图"))
    }
    @Test fun responseImageIsDecodedAndAssetDownloadHasNoCredentials()=runBlocking{
        var calls=0;val bytes=picture()
        val client=GenerationClient(OkHttpClient.Builder().addInterceptor{chain->calls++;val req=chain.request()
            val response=if(calls==1){assertEquals("Bearer unit-test-not-real",req.header("Authorization"));"""{"data":[{"url":"https://assets.example.test/output.png"}]}""".toResponseBody("application/json".toMediaType())}else{assertNull(req.header("Authorization"));bytes.toResponseBody("image/png".toMediaType())}
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response).build()
        }.build())
        assertArrayEquals(bytes,client.generate(p,GenerationDraft(prompt="test"),emptyList()));assertEquals(2,calls)
    }
    @Test fun failureDoesNotRetryAndSecretsAreRedacted()=runBlocking{
        var calls=0
        val client=GenerationClient(OkHttpClient.Builder().addInterceptor{chain->calls++;Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("fail").body("""{"error":{"message":"upstream rejected unit-test-not-real"}}""".toResponseBody("application/json".toMediaType())).build()}.build())
        val failure=runCatching{client.generate(p,GenerationDraft(prompt="test"),emptyList())}.exceptionOrNull()
        assertNotNull(failure);assertEquals(1,calls);assertFalse(failure!!.message.orEmpty().contains("unit-test-not-real"))
    }
    @Test fun invalidImageIsNeverAccepted(){assertTrue(runCatching{GenerationClient.validateImage("not image".toByteArray())}.isFailure);assertEquals("png",GenerationClient.validateImage(picture()))}
    @Test fun immutableCollectionSurvivesGenerationDeletion()=runBlocking{
        val store=GenerationStore.get(context);store.initialize()
        val records=store.enqueue(p,GenerationDraft(prompt="original",references=listOf(input())))
        val running=store.next()!!;store.finish(running,picture());val ready=store.records.value.first{it.id==running.id}
        assertFalse(GenerationStore.json(ready).toString().contains(p.apiKey))
        val repo=Repository(context);val owned=File(context.cacheDir,"drafts/${System.nanoTime()}.webp");store.image(ready)!!.copyTo(owned)
        val entry=repo.save(null,"生成收藏",ready.prompt,owned);store.delete(records.map{it.id}.toSet())
        assertFalse(store.folder(ready.id).exists());assertTrue(repo.cover(entry)!!.isFile);assertEquals("original",repo.all().first{it.id==entry.id}.original)
    }
    @Test fun queuedAndRunningTasksCannotBeDeletedAndInterruptIsManual()=runBlocking{
        val store=GenerationStore.get(context);store.initialize();val queued=store.enqueue(p,GenerationDraft(prompt="test",count=2))
        assertTrue(runCatching{store.delete(queued.map{it.id}.toSet())}.isFailure)
        val running=store.next()!!;store.interrupt()
        assertEquals("unknown",store.records.value.first{it.id==running.id}.status)
        assertEquals(1,store.records.value.count{it.status=="stopped"});assertNull(store.next())
        store.delete(queued.map{it.id}.toSet())
    }
    @Test fun storagePathsCannotEscapeGenerationRoot(){val store=GenerationStore.get(context);assertTrue(runCatching{store.folder("../covers")}.isFailure);assertTrue(runCatching{store.file("abc","../other")}.isFailure)}
    @Test fun coldStartPreservesCompletedImagesAndNeverResubmitsInterruptedRequests()=runBlocking{
        val first=GenerationStore(context);first.initialize();first.enqueue(p,GenerationDraft(prompt="test",count=3))
        val completed=first.next()!!;first.finish(completed,picture());val running=first.next()!!
        val reopened=GenerationStore(context);reopened.initialize()
        assertEquals("unknown",reopened.records.value.first{it.id==running.id}.status)
        assertEquals(1,reopened.records.value.count{it.status=="stopped"})
        val saved=reopened.records.value.first{it.id==completed.id}
        assertEquals("success",saved.status);assertTrue(reopened.image(saved)!!.isFile);assertNull(reopened.next())
    }
    @Test fun newNamesAreLimitedButExistingLongNamesRemainEditable()=runBlocking{
        assertTrue(validNewName("图".repeat(30)));assertFalse(validNewName("图".repeat(31)))
        assertTrue(validNewName("\uD83D\uDE00".repeat(30)));assertFalse(validNewName("图\n图"));assertFalse(validNewName("图\u2028图"))
        assertFalse(acceptNameInput("图".repeat(31),"图".repeat(30)))
        assertTrue(acceptNameInput("旧".repeat(29),"旧".repeat(30)));assertFalse(acceptNameInput("旧".repeat(31),"旧".repeat(30)))
        val repo=Repository(context);assertTrue(runCatching{repo.save(null,"图".repeat(31),"prompt",null)}.isFailure)
        val old=repo.save(null,"旧条目","原始内容",null)
        PromptDatabase(context).writableDatabase.execSQL("UPDATE prompts SET name=? WHERE id=?",arrayOf("旧".repeat(60),old.id))
        val updated=repo.save(old.id,"旧".repeat(60),"新内容",null);assertEquals("旧".repeat(60),updated.name);assertEquals("原始内容",updated.original)
    }
    @Test fun glassEnvironmentChangeWakesRendererWithoutTouch(){val scene=GlassScene();var wakes=0;scene.wake={wakes++};scene.environment(true,false,DisplayMaterial());assertEquals(1,wakes);scene.environment(true,false,DisplayMaterial());assertEquals(1,wakes);scene.environment(false,false,DisplayMaterial());assertEquals(2,wakes)}
}
