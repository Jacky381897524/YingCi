package cn.yingci.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ConversationRevisionTests {
    private val app:Application get()=ApplicationProvider.getApplicationContext()
    @Test fun preferencesAndMemoryPersistIndependentlyAndClearWithHistory()=runBlocking{
        val store=ChatStore(app);val a=store.create();val b=store.create()
        val options=ChatOptions("少话 口语化 不要标点",true,true)
        store.configure(a.id,options,"喜欢海边")
        store.remember(a.id,"喜欢海边和绿色")
        val reopened=ChatStore(app);reopened.initialize(emptyList())
        assertEquals(options,reopened.conversations.value.first{it.id==a.id}.options)
        assertEquals("喜欢海边和绿色",reopened.conversations.value.first{it.id==a.id}.memory)
        assertEquals("",reopened.conversations.value.first{it.id==b.id}.memory)
        reopened.clear(a.id,false)
        val cleared=reopened.conversations.value.first{it.id==a.id}
        assertEquals("",cleared.memory);assertEquals(options,cleared.options)
    }
    @Test fun oldChatJsonLoadsAndDisabledMemoryIsNeitherSentNorUpdated()=runBlocking{
        val old=JSONObject("""{"id":"old","title":"旧对话","updated":1,"messages":[]}""")
        assertEquals(ChatOptions(),ChatStore.parse(old).options)
        val store=ChatStore(app);val c=store.create()
        store.configure(c.id,ChatOptions(remember=false),"不要发送的旧记忆")
        store.remember(c.id,"新记忆")
        val saved=store.conversations.value.single()
        assertEquals("不要发送的旧记忆",saved.memory)
        assertFalse(ChatClient.conversationInstructions(saved).contains("旧记忆"))
    }
    @Test fun instructionsMemoryAndContinuationReachConfiguredEndpoint()=runBlocking{
        val client=VisionClient(OkHttpClient.Builder().addInterceptor{chain->
            val request=chain.request();val buffer=Buffer();request.body!!.writeTo(buffer)
            val json=JSONObject(buffer.readUtf8());val messages=json.getJSONArray("messages").toString()
            assertTrue(messages.contains("少话口语化"));assertTrue(messages.contains("喜欢绿色"))
            assertTrue(messages.contains("没有新的用户消息"));assertFalse(messages.contains("[消息ID:"))
            assertEquals("configured.test",request.url.host)
            val response="""{"choices":[{"message":{"content":"{\"type\":\"chat\",\"text\":\"再说一句\",\"memory\":\"喜欢绿色和海边\",\"more\":false}"}}]}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("ok").body(response.toResponseBody("application/json".toMediaType())).build()
        }.build())
        val c=Conversation(options=ChatOptions("少话口语化",true,true),memory="喜欢绿色",messages=listOf(ChatMessage(role="user",text="今天很开心")))
        val reply=ChatClient(client).reply(Preferences(baseUrl="https://configured.test/v1",model="chat",apiKey="fake"),c,true){emptyList()}
        assertEquals("喜欢绿色和海边",reply.memory);assertFalse(reply.more)
    }
    @Test fun metadataNeverAppearsInVisibleTextAndEmptyContinuationIsAllowed(){
        assertEquals("你好",ChatClient.parse("[消息 ID:123-abc]\n你好",emptySet()).text)
        val reply=ChatClient.parse("""{"type":"chat","text":"","more":false}""",emptySet())
        assertEquals("",reply.text);assertFalse(reply.more)
    }
    @Test fun automaticRepliesAreBoundedToThreeExtras()=runBlocking{
        val turns=mutableListOf<Boolean>();var pauses=0
        chatTurns({true},{},pause={pauses++}){extra->turns+=extra;true}
        assertEquals(listOf(false,true,true,true),turns);assertEquals(3,pauses)
    }
    @Test fun leavingPageDuringWaitStopsBeforeAnotherPaidRequest()=runBlocking{
        var visible=true;var requests=0
        chatTurns({visible},{},pause={visible=false}){requests++;true}
        assertEquals(1,requests)
    }
    @Test fun cancellationStopsPendingContinuation()=runBlocking{
        var calls=0;val waiting=CompletableDeferred<Unit>()
        val job=launch{chatTurns({true},{},pause={waiting.complete(Unit);awaitCancellation()}){calls++;true}}
        waiting.await();job.cancelAndJoin();assertEquals(1,calls)
    }
    @Test fun defaultWallpaperIsExactProvidedAssetAndCustomWallpaperWins()=runBlocking{
        val repo=Repository(app);val original=app.assets.open("chat-stream.jpg").use{it.readBytes()}
        fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).toList()
        assertEquals(hash(original),hash(repo.chatBackground("").readBytes()))
        assertEquals(repo.chatBackground(""),repo.chatBackground("missing.webp"))
        val folder=java.io.File(app.filesDir,"wallpapers").apply{mkdirs()}
        val custom=java.io.File(folder,"custom.webp").apply{writeBytes(byteArrayOf(1,2,3))}
        assertEquals(custom,repo.chatBackground("custom.webp"))
    }
}
