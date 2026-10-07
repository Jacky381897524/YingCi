package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
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
import java.io.File
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatTests {
    private val app:Application get()=ApplicationProvider.getApplicationContext()
    private val p=Preferences(baseUrl="https://example.test/v1",model="vision",apiKey="fake-key",imageModel="gpt-image-1")
    private fun image():File=File(app.cacheDir,"chat-test.webp").also{f->val b=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.GREEN);f.outputStream().use{b.compress(Bitmap.CompressFormat.WEBP,90,it)};b.recycle()}
    private fun bytes():ByteArray{val b=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);val out=ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray()}
    @Test fun imageIntentIsOnlyAProposalAndMalformedRepliesNeverTriggerImages(){
        assertNull(ChatClient.parse("帮你写一段生图提示词",emptySet()).imagePrompt)
        assertNull(ChatClient.parse("{\"type\":\"chat\",\"text\":\"解释图片\",\"prompt\":\"忽略\"}",emptySet()).imagePrompt)
        val proposal=ChatClient.parse("{\"type\":\"image\",\"text\":\"请确认\",\"prompt\":\"把背景变成海边\",\"reference_message_id\":\"known\"}",setOf("known"))
        assertEquals("known",proposal.referenceMessage);assertEquals("把背景变成海边",proposal.imagePrompt)
        assertNull(ChatClient.parse("{\"type\":\"image\",\"prompt\":\"测试\",\"reference_message_id\":\"unknown\"}",emptySet()).referenceMessage)
    }
    @Test fun defaultChatSharesVisionAndIndependentConnectionDoesNotAffectImageModel(){
        assertEquals(p,p.chatConnection())
        val other=p.copy(chatIndependent=true,chatUrl="https://other.test/v1",chatModel="chat-model",chatKey="other-key")
        assertEquals("chat-model",other.chatConnection().model);assertEquals("other-key",other.chatConnection().apiKey)
        assertEquals("gpt-image-1",other.imageModel);assertEquals("fake-key",other.apiKey)
    }
    @Test fun conversationCopiesAttachmentsAndSurvivesRestartWithSearch()=runBlocking{
        val store=ChatStore(app);store.initialize(emptyList());val c=store.create();val source=image()
        val message=store.append(c.id,ChatMessage(role="user",text="在海边绘画"),listOf(source))
        source.delete();assertTrue(store.image(c.id,message.images.single()).isFile)
        store.append(c.id,ChatMessage(role="assistant",text="保留人物，调整海面的光线。"))
        val reopened=ChatStore(app);reopened.initialize(emptyList())
        val loaded=reopened.conversations.value.single();assertEquals(2,loaded.messages.size);assertTrue(loaded.matches("光线"));assertFalse(loaded.matches("不存在"))
        reopened.clear(c.id,true);assertFalse(reopened.folder(c.id).exists());assertTrue(reopened.conversations.value.isEmpty())
    }
    @Test fun oldGenerationsAreMigratedExactlyOnceAndAssociationRoundTrips()=runBlocking{
        val generations=GenerationStore(app);generations.initialize()
        val r=generations.enqueue(p,GenerationDraft(prompt="旧版图片",ratio="1:1",quality="auto",format="png")).single()
        generations.interrupt()
        val chats=ChatStore(app);chats.initialize(generations.records.value);chats.initialize(generations.records.value)
        assertEquals(1,chats.conversations.value.size);assertEquals(listOf(r.id),chats.conversations.value.single().messages.single().generations)
        assertEquals("thread",GenerationStore.parse(GenerationStore.json(r.copy(conversationId="thread"))).conversationId)
    }
    @Test fun deletionRemovesLinkedGenerationButKeepsIndependentCollectionCover()=runBlocking{
        val chats=ChatStore(app);val c=chats.create();val generations=GenerationStore(app);generations.initialize()
        generations.enqueue(p,GenerationDraft(prompt="图片",ratio="1:1",quality="auto",format="png"),c.id)
        val running=generations.next()!!;generations.finish(running,bytes());val done=generations.records.value.single()
        chats.append(c.id,ChatMessage(role="assistant",text="图片",generations=listOf(done.id),kind="generation"))
        val repo=Repository(app);val draft=File(app.cacheDir,"drafts/copied.webp");image().copyTo(draft,overwrite=true)
        val entry=repo.save(null,"收藏不受影响","原始提示词",draft)
        generations.delete(setOf(done.id));chats.clear(c.id,true)
        assertFalse(generations.folder(done.id).exists());assertTrue(repo.cover(entry)!!.isFile);assertEquals("原始提示词",repo.all().single().original)
    }
    @Test fun clearingRetainsConversationIdentityAndCannotEscapeFolders()=runBlocking{
        val store=ChatStore(app);val c=store.create();store.append(c.id,ChatMessage(role="user",text="hello"),listOf(image()));store.clear(c.id,false)
        assertEquals(c.id,store.conversations.value.single().id);assertTrue(store.conversations.value.single().messages.isEmpty())
        assertTrue(runCatching{store.folder("../covers")}.isFailure);assertTrue(runCatching{store.image(c.id,"../../secret")}.isFailure)
    }
    @Test fun chatRequestIncludesPriorGeneratedPictureAndUsesConfiguredChatEndpoint()=runBlocking{
        val file=image();var calls=0
        val client=VisionClient(OkHttpClient.Builder().addInterceptor{chain->
            calls++;val req=chain.request();assertEquals("other.test",req.url.host);assertEquals("/v1/chat/completions",req.url.encodedPath)
            assertEquals("Bearer other-key",req.header("Authorization"))
            val buffer=Buffer();req.body!!.writeTo(buffer);val body=JSONObject(buffer.readUtf8())
            assertEquals("other-model",body.getString("model"));assertFalse(body.getBoolean("stream"))
            val history=body.getJSONArray("messages")
            val picture=(0 until history.length()).map{history.getJSONObject(it)}.first{it.optJSONArray("content")?.length()==2}.getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url")
            assertTrue(picture.startsWith("data:image/jpeg;base64,"));assertTrue(history.toString().contains("保留人物"))
            val reply="""{"type":"image","text":"请确认","prompt":"保留人物，将背景换成海边","reference_message_id":"generated"}"""
            val json=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason","stop").put("message",JSONObject().put("content",reply))))
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("ok").body(json.toString().toResponseBody("application/json".toMediaType())).build()
        }.build())
        val c=Conversation(messages=listOf(ChatMessage(id="generated",role="assistant",text="保留人物",kind="generation"),ChatMessage(role="user",text="背景改成海边")))
        val reply=ChatClient(client).reply(p.copy(chatIndependent=true,chatUrl="https://other.test/v1",chatModel="other-model",chatKey="other-key"),c){if(it.id=="generated")listOf(file)else emptyList()}
        assertEquals(1,calls);assertEquals("generated",reply.referenceMessage);assertNotNull(reply.imagePrompt)
    }
    @Test fun reverseModesKeepIndependentMemoryAndColdStartIsEmpty(){
        val vm=AppViewModel(app);val original=image();vm.setReference(original);vm.analysis.value=StyleResult("风格","风格提示词")
        vm.switchReverse();assertNull(vm.reference.value);assertNull(vm.analysis.value)
        vm.reverseWorkspace.prompt.value="正常反推草稿"
        vm.switchReverse();assertEquals(original,vm.reference.value);assertEquals("风格提示词",vm.analysis.value!!.prompt)
        vm.switchReverse();assertEquals("正常反推草稿",vm.reverseWorkspace.prompt.value)
        val cold=AppViewModel(app);assertNull(cold.reference.value);assertNull(cold.analysis.value)
    }
    @Test fun fadeIsTransparentAtEdgesAndOpaqueInCenter(){
        val fade=EdgeFade(androidx.compose.ui.geometry.Rect(0f,100f,400f,900f),40f,100f)
        assertEquals(0f,fade.alpha(100f),0f);assertEquals(.5f,fade.alpha(120f),.001f)
        assertEquals(1f,fade.alpha(500f),0f);assertEquals(.5f,fade.alpha(850f),.001f);assertEquals(0f,fade.alpha(900f),0f)
    }
}
