package cn.yingci.app

import android.app.Application
import androidx.compose.ui.geometry.Rect
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class V216Tests {
    private val app:Application get()=ApplicationProvider.getApplicationContext()
    @Test fun circlesUseAnalyticCircleRegardlessOfCornerRoundness(){
        val r=Rect(0f,0f,144f,144f)
        assertEquals(2,glassShapeType(GlassItem(r,r,2f,0,circle=true)))
        assertEquals(0,glassShapeType(GlassItem(r,r,22f,0)))
        assertEquals(1,glassShapeType(GlassItem(Rect(0f,0f,300f,100f),r,50f,0)))
    }
    @Test fun fadeIsSmoothAtBothEnds(){
        assertEquals(0f,smoothFade(-1f),0f);assertEquals(1f,smoothFade(2f),0f)
        assertTrue(smoothFade(.01f)<.001f);assertTrue(smoothFade(.99f)>.999f)
        assertEquals(.5f,smoothFade(.5f),.001f)
    }
    @Test fun shaderAdapterAddsPerPixelFadeAndBubbleMaskWithoutChangingOptics(){
        val upstream=app.assets.open("glass/reference-v2.frag").bufferedReader().use{it.readText()}
        val adapted=glassOverlayShader(upstream)
        assertTrue(adapted.contains("uniform sampler2D uAppMask;"))
        assertTrue(adapted.contains("uniform vec4 uAppFade;"))
        assertTrue(adapted.contains("texture(uAppMask"));assertTrue(adapted.contains("* appAlpha;"))
        assertFalse(upstream.contains("uAppMask"))
    }
    @Test fun skillModePersistsAndOldConversationsRemainNormal()=runBlocking{
        val store=ChatStore(app);val c=store.create();store.setSkill(c.id,GoutouSkill.ID)
        val reopened=ChatStore(app);reopened.initialize(emptyList())
        assertEquals(GoutouSkill.ID,reopened.conversations.value.single().skill)
        assertTrue(reopened.conversations.value.single().matches("狗头军师"))
        reopened.setSkill(c.id,"");assertEquals("",reopened.conversations.value.single().skill)
        val old=ChatStore.json(c);old.remove("skill");assertEquals("",ChatStore.parse(old).skill)
        assertTrue(runCatching{store.setSkill(c.id,"../../secret")}.isFailure)
    }
    @Test fun bundledSkillIsPinnedBoundedAndPrioritizesSafety(){
        val skill=GoutouSkill(app.assets)
        val refs=skill.references("遇到家暴威胁，焦虑分手冲突，怎么回复截图")
        assertEquals(3,refs.size);assertTrue(refs.first().contains("17-"))
        assertNull(skill.prompt(Conversation()))
        val prompt=skill.prompt(Conversation(skill=GoutouSkill.ID,messages=listOf(ChatMessage(role="user",text="怎么回"))))!!
        assertTrue(prompt.contains("先接住情绪"));assertTrue(prompt.contains("实战话术"));assertTrue(prompt.contains("没有文件"));assertTrue(prompt.length<23000)
        assertTrue(app.assets.open("skills/goutoujunshi/SOURCE.txt").bufferedReader().use{it.readText()}.contains(GoutouSkill.REVISION))
        assertTrue(app.assets.open("skills/goutoujunshi/LICENSE").bufferedReader().use{it.readText()}.contains("MIT"))
        assertFalse(app.assets.list("skills/goutoujunshi").orEmpty().contains("scripts"))
    }
    @Test fun skillActuallyReachesChatRequestAndCanBeDisabled()=runBlocking{
        var requests=0
        val client=VisionClient(OkHttpClient.Builder().addInterceptor{chain->
            val req=chain.request();val buffer=Buffer();req.body!!.writeTo(buffer)
            val body=JSONObject(buffer.readUtf8());val history=body.getJSONArray("messages")
            assertEquals("vision",body.getString("model"));assertEquals("example.test",req.url.host)
            if(requests++==0){assertEquals("system",history.getJSONObject(1).getString("role"));assertTrue(history.getJSONObject(1).getString("content").contains("狗头军师"))}
            else assertFalse((0 until history.length()).any{history.getJSONObject(it).optString("content").contains("当前启用狗头军师")})
            val data=JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content","{\"type\":\"chat\",\"text\":\"说说发生了什么\"}"))))
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("ok").body(data.toString().toResponseBody("application/json".toMediaType())).build()
        }.build())
        val chat=ChatClient(client,GoutouSkill(app.assets));val c=Conversation(skill=GoutouSkill.ID,messages=listOf(ChatMessage(role="user",text="怎么回？")))
        val p=Preferences(baseUrl="https://example.test/v1",model="vision",apiKey="test-key")
        assertEquals("说说发生了什么",chat.reply(p,c){emptyList()}.text)
        chat.reply(p,c.copy(skill="")){emptyList()};assertEquals(2,requests)
    }
}
