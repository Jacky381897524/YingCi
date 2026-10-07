package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
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
class V102ScreenTests {
    @get:Rule val compose=createComposeRule()
    private lateinit var root:android.view.View
    private fun start(dark:Boolean=false):AppViewModel {
        val app:Application=ApplicationProvider.getApplicationContext()
        runBlocking{Repository(app).savePreferences(Preferences(theme=if(dark)2 else 1,fontPercent=if(dark)145 else 100,baseUrl="https://apihub.agnes-ai.com/v1",imageModel="agnes-image-2.5-flash"))}
        val vm=AppViewModel(app)
        compose.setContent{root=androidx.compose.ui.platform.LocalView.current.rootView;YingciApp({},vm,renderGlass=false)}
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!vm.loading.value}
        assertNull("App data initialization must succeed",vm.notice.value)
        return vm
    }
    private fun screenshot(name:String){compose.runOnIdle{
        val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(image))
        val file=File(System.getProperty("user.home"),"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }}
    @Test fun recentListOpensChatAndHidesNavigationUntilBack(){
        val vm=start();compose.onNodeWithContentDescription("生图").performClick()
        compose.onNodeWithText("最近对话").assertIsDisplayed()
        compose.onNodeWithContentDescription("新建对话").performClick()
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();vm.activeConversation.value!=null&&vm.busy.value==null}
        compose.onNodeWithContentDescription("添加图片").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
        compose.onNodeWithText("发送消息").performTextInput("柔和光线下的玻璃花瓶，自然摄影质感。")
        screenshot("v215-chat-light")
        compose.onNodeWithContentDescription("返回最近对话").performClick()
        compose.onNodeWithText("最近对话").assertIsDisplayed();compose.onNodeWithContentDescription("设置").assertExists()
    }
    @Test fun multiReferenceSwitchDoesNotCreateSeparateModes(){
        val vm=start(true);val app:Application=ApplicationProvider.getApplicationContext()
        val dir=File(app.cacheDir,"drafts").apply{mkdirs()};val a=File(dir,"ref-a.webp");val b=File(dir,"ref-b.webp")
        app.assets.open("preview-lake.webp").use{source->a.outputStream().use{source.copyTo(it)}};a.copyTo(b,overwrite=true)
        val c=runBlocking{vm.chatStore.create()}
        compose.runOnIdle{vm.chatInput(c.id).value=ChatInput("保留主体，转换为细腻手绘风格。",listOf(a,b));vm.activeConversation.value=c.id}
        compose.onNodeWithContentDescription("生图").performClick()
        compose.onNodeWithText("分别生成").assertDoesNotExist()
        compose.onNodeWithContentDescription("生图参数").performClick()
        compose.onNodeWithText("分别生成").performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("保存").performClick();compose.runOnIdle{assertTrue(vm.generationDraft.value.separate)}
        screenshot("v215-chat-dark-large")
        compose.onAllNodesWithContentDescription("移除参考图片")[0].performClick()
        compose.runOnIdle{assertEquals(1,vm.chatInput(c.id).value.images.size)}
        compose.onNodeWithContentDescription("移除参考图片").performClick();compose.onNodeWithContentDescription("添加图片").assertIsDisplayed()
    }
    @Test fun searchStaysInToolbarAndBottomNavigationHasOnlyIcons(){
        val vm=start();val entry=runBlocking{vm.repository.save(null,"顶部搜索测试","测试提示词",null)}
        compose.runOnIdle{vm.entries.value=listOf(entry)}
        compose.waitUntil(10000){
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(entry.name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("设置").assertExists();compose.onNodeWithText("设置").assertDoesNotExist()
        val before=compose.onNodeWithText(entry.name).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("搜索").performClick()
        val after=compose.onNodeWithText(entry.name).fetchSemanticsNode().boundsInRoot
        assertEquals(before.top,after.top,.5f)
        screenshot("v102-search")
    }
    @Test fun collectionTitleSitsInsideCoverAndRejectsThirtyFirstCharacter(){
        val vm=start();val app:Application=ApplicationProvider.getApplicationContext()
        val image=File(app.cacheDir,"drafts/title-cover.webp")
        app.assets.open("preview-lake.webp").use{source->image.outputStream().use{source.copyTo(it)}}
        val entry=runBlocking{vm.repository.save(null,"春日花园手绘拼贴","测试提示词",image)}
        compose.runOnIdle{vm.entries.value=listOf(entry)}
        compose.waitUntil(10000){
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithContentDescription("效果封面",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText(entry.name).fetchSemanticsNodes().isNotEmpty()
        }
        val cover=compose.onNodeWithContentDescription("效果封面",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val title=compose.onNodeWithText(entry.name,useUnmergedTree=true)
        val bounds=title.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.bottom<=cover.bottom);assertTrue(bounds.top>cover.top);assertEquals(cover.center.x,bounds.center.x,.5f)
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        title.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){assertTrue(it(layouts))}
        assertEquals(1,layouts.single().lineCount);assertEquals(androidx.compose.ui.graphics.Color.White,layouts.single().layoutInput.style.color)
        assertNotNull(layouts.single().layoutInput.style.shadow)
        screenshot("v215-title-inside-cover-light")
        compose.onNodeWithContentDescription("新建提示词").performClick()
        val field=compose.onNodeWithText("提示词名称")
        field.performTextInput("春".repeat(30))
        field.performTextInput("风")
        field.assertTextContains("春".repeat(30))
        compose.onNodeWithText("春".repeat(30)+"风").assertDoesNotExist()
        field.performTextReplacement("图".repeat(31))
        field.assertTextContains("春".repeat(30))
    }
    @Test fun settingsFooterShowsAuthorYellowAvatarAndNewVersion(){
        start(true)
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("海街寺庙").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,10000f)}
        compose.waitForIdle()
        compose.onNodeWithText("作者：").assertIsDisplayed()
        compose.onNodeWithText("V3.1.1").assertExists()
        val author=compose.onNodeWithText("海街寺庙").fetchSemanticsNode().boundsInRoot
        val avatar=compose.onNodeWithTag("author-avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(avatar.width,avatar.height,.5f);assertTrue(avatar.right<author.left)
        val credit=compose.onNodeWithText("Liquid Glass · Oliver Nemo · MIT").fetchSemanticsNode().boundsInRoot
        assertTrue(credit.bottom<author.top)
        assertTrue(author.bottom<compose.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot.top)
        compose.runOnIdle{
            val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(image))
            assertEquals(android.graphics.Color.YELLOW,image.getPixel(avatar.center.x.toInt(),avatar.center.y.toInt()))
            image.recycle()
        }
        screenshot("v214-author-footer-dark-large")
    }
}
