package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V215ScreenTests {
    @get:Rule val compose=createComposeRule()
    private lateinit var root:android.view.View
    private fun start(dark:Boolean=false):AppViewModel {
        val app:Application=ApplicationProvider.getApplicationContext()
        runBlocking{Repository(app).savePreferences(Preferences(theme=if(dark)2 else 1,fontPercent=100,baseUrl="https://apihub.agnes-ai.com/v1",model="vision",imageModel="agnes-image-2.5-flash"))}
        val vm=AppViewModel(app)
        compose.setContent{root=androidx.compose.ui.platform.LocalView.current.rootView;YingciApp({},vm,renderGlass=false)}
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!vm.loading.value}
        return vm
    }
    private fun shot(name:String)=compose.runOnIdle{
        val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
        val file=File(System.getProperty("user.home"),"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun chatPreview(dark:Boolean){
        val vm=start(dark);val app:Application=ApplicationProvider.getApplicationContext()
        val source=File(app.cacheDir,"preview-chat.webp");app.assets.open("preview-lake.webp").use{src->source.outputStream().use{src.copyTo(it)}}
        val c=runBlocking{
            val c=vm.chatStore.create()
            vm.chatStore.append(c.id,ChatMessage(role="user",text="今天想做一张安静的旅行海报。"))
            vm.chatStore.append(c.id,ChatMessage(role="user",text="用这张参考图，保留山和湖。"),listOf(source))
            vm.chatStore.append(c.id,ChatMessage(role="assistant",text="可以。保留远山与湖面的层次，让雾气和留白成为画面的重点。"))
            vm.chatStore.append(c.id,ChatMessage(role="assistant",text="你更喜欢自然摄影，还是带纸张质感的手绘？"))
            vm.chatStore.append(c.id,ChatMessage(role="user",text="手绘吧，颜色柔和一点。"))
            vm.chatStore.append(c.id,ChatMessage(role="assistant",text="好的，我会保留构图，使用低饱和水彩与细腻纸纹。"))
            c
        }
        compose.onNodeWithContentDescription("生图").performClick()
        shot(if(dark)"v215-recent-dark"else"v215-recent-light")
        compose.runOnIdle{vm.activeConversation.value=c.id}
        compose.onNodeWithText("手绘吧，颜色柔和一点。").assertExists()
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithContentDescription("效果封面",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
        shot(if(dark)"v215-conversation-dark"else"v215-conversation-light")
        compose.onNodeWithContentDescription("对话菜单").performClick()
        compose.onNodeWithText("查找聊天记录").performClick()
        compose.onNodeWithText("查找当前对话").performTextInput("水彩")
        compose.onNodeWithText("好的，我会保留构图，使用低饱和水彩与细腻纸纹。").assertExists()
        compose.onNodeWithText("手绘吧，颜色柔和一点。").assertDoesNotExist()
    }
    @Test fun lightConversationBubblesAndSearch(){chatPreview(false)}
    @Test fun darkConversationBubblesAndSearch(){chatPreview(true)}
    @Test fun proposalRequiresConfirmationAndDoesNotEnqueueWhenCancelled(){
        val vm=start();val c=runBlocking{val c=vm.chatStore.create();vm.chatStore.append(c.id,ChatMessage(role="assistant",text="为湖边生成一张水彩海报",kind="proposal"));c}
        compose.onNodeWithContentDescription("生图").performClick();compose.runOnIdle{vm.activeConversation.value=c.id}
        compose.onNodeWithText("确认生图").performClick()
        compose.onNodeWithText("画面比例").assertExists();compose.onNodeWithText("清晰度").assertExists()
        shot("v215-generation-confirmation")
        compose.onNodeWithText("取消").performClick();assertTrue(vm.generations.value.isEmpty())
    }
    @Test fun generationConfirmationCanEditPrompt(){
        compose.setContent{androidx.compose.material3.MaterialTheme{
            GenerationParametersPanel(GenerationDraft(prompt="湖边海报"),Preferences(baseUrl="https://apihub.agnes-ai.com/v1",imageModel="agnes-image-2.5-flash"),true,{},{})
        }}
        compose.onNodeWithContentDescription("生图提示词").performTextReplacement("水彩海报")
        compose.onNodeWithText("水彩海报").assertExists()
    }
    @Test fun longPressOffersOnlyIconsAndDeletionNeedsConfirmation(){
        val vm=start();val entry=runBlocking{vm.repository.save(null,"封面长按测试","不会误删除",null)}
        compose.runOnIdle{vm.entries.value=listOf(entry)}
        compose.waitUntil(10000){compose.onAllNodesWithText(entry.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(entry.name).performTouchInput{longClick()}
        compose.onNodeWithContentDescription("编辑提示词").assertExists();compose.onNodeWithText("编辑").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除提示词").performClick()
        assertEquals(1,vm.entries.value.size)
        compose.onNodeWithText("取消").performClick();assertEquals(1,vm.entries.value.size)
    }
    @Test fun reverseSwitchRestoresOriginalImageAndEditedPrompt(){
        val vm=start();val app:Application=ApplicationProvider.getApplicationContext();val source=File(app.cacheDir,"drafts/reverse-preview.webp")
        app.assets.open("preview-lake.webp").use{src->source.outputStream().use{src.copyTo(it)}}
        compose.runOnIdle{vm.setReference(source);vm.analysis.value=StyleResult("湖面手绘","风格提示词");vm.reverseWorkspace.name.value="湖面手绘";vm.reverseWorkspace.prompt.value="编辑过的风格提示词"}
        compose.onNodeWithContentDescription("反推").performClick();compose.onNodeWithText("风格反推").assertExists()
        compose.onNodeWithContentDescription("切换反推模式").performClick()
        compose.onNodeWithText("正常反推").assertExists();compose.onNodeWithText("选择一张参考图").assertExists()
        compose.onNodeWithContentDescription("切换反推模式").performClick()
        compose.onNodeWithText("风格反推").assertExists();assertEquals(source,vm.reference.value);assertEquals("编辑过的风格提示词",vm.reverseWorkspace.prompt.value)
        shot("v215-reverse-switch")
    }
}
