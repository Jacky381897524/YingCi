package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
class ConversationRevisionScreenTests {
    @get:Rule val compose=createComposeRule()
    private lateinit var root:android.view.View
    private lateinit var composeHost:android.view.View
    private val app:Application get()=ApplicationProvider.getApplicationContext()
    private fun idleUntil(condition:()->Boolean)=compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();condition()}
    private fun start(dark:Boolean=true):AppViewModel {
        runBlocking{Repository(app).savePreferences(Preferences(theme=if(dark)2 else 1))}
        val vm=AppViewModel(app)
        compose.setContent{composeHost=LocalView.current;root=composeHost.rootView;YingciApp({},vm,renderGlass=false)}
        idleUntil{!vm.loading.value};return vm
    }
    private fun shot(name:String)=compose.runOnIdle{
        val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(b))
        val f=File(System.getProperty("user.home"),"qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    private fun chat(dark:Boolean){
        val vm=start(dark)
        val c=runBlocking{val c=vm.chatStore.create();vm.chatStore.append(c.id,ChatMessage(role="user",text="今天想去海边"));vm.chatStore.append(c.id,ChatMessage(role="assistant",text="好呀 慢慢走 不着急"));c}
        compose.onNodeWithContentDescription("生图").performClick()
        compose.runOnIdle{vm.activeConversation.value=c.id}
        idleUntil{compose.onAllNodesWithContentDescription("效果封面",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val viewport=compose.onNodeWithTag("chat-scroll-surface").fetchSemanticsNode().boundsInRoot
        val composer=compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(viewport.bottom>=composer.bottom)
        compose.onNodeWithContentDescription("对话菜单").performClick()
        compose.onNodeWithText("对话记录").assertDoesNotExist();compose.onNodeWithText("聊天模型").assertDoesNotExist();compose.onNodeWithText("恢复默认背景").assertDoesNotExist()
        compose.onNodeWithText("对话模型自定义设定").assertIsDisplayed()
        shot(if(dark)"revision12-menu-dark"else"revision12-menu-light")
        compose.onNodeWithText("设置聊天背景").performClick()
        compose.onNodeWithText("恢复默认背景").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("对话菜单").performClick()
        compose.onNodeWithText("对话模型自定义设定").performClick()
        compose.onNodeWithText("角色与回复风格").performTextInput("少话 口语化 不用标点")
        compose.onNodeWithText("保存").performClick()
        idleUntil{vm.conversations.value.first{it.id==c.id}.options.instructions=="少话 口语化 不用标点"}
        compose.onNodeWithContentDescription("对话菜单").assertExists()
        shot(if(dark)"revision12-chat-dark"else"revision12-chat-light")
    }
    @Test fun darkChatMenuAndPerConversationSettings(){chat(true)}
    @Test fun lightChatMenuAndPerConversationSettings(){chat(false)}
    @Test fun whiteBackgroundCanBeSavedAndRestoredWithoutChangingAppTheme(){
        val vm=start()
        val conversation=runBlocking{vm.chatStore.create()}
        compose.onNodeWithContentDescription("生图").performClick()
        compose.runOnIdle{vm.activeConversation.value=conversation.id}
        compose.onNodeWithContentDescription("对话菜单").performClick()
        compose.onNodeWithText("设置聊天背景").performClick()
        compose.onNodeWithText("恢复白底背景").performClick()
        idleUntil{vm.preferences.value.chatWallpaper==WhiteChatBackground}
        compose.onAllNodesWithContentDescription("效果封面",useUnmergedTree=true).assertCountEquals(0)
        compose.runOnIdle{
            val repository=Repository(app)
            val saved=repository.chatPreferences(repository.preferences())
            assertEquals(WhiteChatBackground,saved.chatWallpaper)
            assertEquals(2,saved.theme)
            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(root.width/2,root.height/2))
            bitmap.recycle()
        }
        shot("v311-chat-white")
        compose.onNodeWithContentDescription("对话菜单").performClick()
        compose.onNodeWithText("设置聊天背景").performClick()
        compose.onNodeWithText("恢复默认背景").performClick()
        idleUntil{vm.preferences.value.chatWallpaper.isEmpty()&&compose.onAllNodesWithContentDescription("效果封面",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun conversationSettingsCoverTheStatusBarInset(){
        val vm=start()
        val conversation=runBlocking{vm.chatStore.create()}
        compose.onNodeWithContentDescription("生图").performClick()
        compose.runOnIdle{vm.activeConversation.value=conversation.id}
        compose.waitForIdle()
        compose.runOnIdle{
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(composeHost,
                androidx.core.view.WindowInsetsCompat.Builder().setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(),androidx.core.graphics.Insets.of(0,72,0,0)).build())
        }
        compose.onNodeWithContentDescription("对话菜单").performClick()
        compose.onNodeWithText("对话模型自定义设定").performClick()
        compose.waitForIdle()
        val surfaces=compose.onAllNodesWithTag("page-surface",useUnmergedTree=true).fetchSemanticsNodes().map{it.boundsInRoot}
        assertEquals(2,surfaces.size)
        assertEquals(surfaces[0].top,surfaces[1].top,1f)
        assertEquals(surfaces[0].height,surfaces[1].height,1f)
        compose.runOnIdle{
            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            assertEquals(android.graphics.Color.rgb(21,22,24),bitmap.getPixel(root.width/2,24))
            bitmap.recycle()
        }
        shot("v311-conversation-settings-inset")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("对话菜单").assertIsDisplayed()
    }
    @Test fun menuAndCoverControlsRegisterRequestedFrost(){
        val scene=GlassScene()
        compose.setContent{MaterialTheme{CompositionLocalProvider(LocalGlass provides scene){Box(Modifier.fillMaxSize()){
            RoundGlassButton(Icons.Rounded.Edit,"编辑",{},frost=.5f,glassTint=.35f)
            ChatGlassMenu({}){ChatMenuItem("新建对话") {}}
        }}}}
        compose.runOnIdle{
            assertEquals(.5f,scene.snapshot().first{it.circle}.frost!!,0f)
            assertEquals(.35f,scene.snapshot().first{it.circle}.tint!!,0f)
            assertEquals(.7f,scene.snapshot().first{it.kind==8}.frost!!,0f)
        }
    }
    @Test fun coverTitleGlassIsTranslucentAndMasksBothBottomCorners(){
        val scene=GlassScene()
        compose.setContent{root=LocalView.current.rootView;MaterialTheme{CompositionLocalProvider(LocalGlass provides scene){
            Box(Modifier.fillMaxSize()){
                CollectionTitleBand("半透明标题",Modifier.width(180.dp).height(32.dp))
            }
        }}}
        compose.runOnIdle{
            val band=scene.snapshot().single{it.kind==7}
            assertEquals(.5f,band.frost!!,0f)
            assertEquals(.65f,band.tint!!,0f)
            assertEquals(true,band.darkSurface)
            val mask=band.mask!!
            assertEquals(0,android.graphics.Color.alpha(mask.getPixel(0,mask.height-1)))
            assertEquals(0,android.graphics.Color.alpha(mask.getPixel(mask.width-1,mask.height-1)))
            assertEquals(255,android.graphics.Color.alpha(mask.getPixel(mask.width/2,mask.height-1)))
            assertEquals(255,android.graphics.Color.alpha(mask.getPixel(0,0)))
        }
    }
    @Test fun explicitGlassTintKeepsFallbackCoverVisible(){
        val scene=GlassScene().apply{enabled=false}
        compose.setContent{root=LocalView.current.rootView;MaterialTheme{CompositionLocalProvider(LocalGlass provides scene,LocalDark provides true){
            Box(Modifier.fillMaxSize().background(Color.Red)){
                CollectionTitleBand("标题",Modifier.width(180.dp).height(32.dp))
                RoundGlassButton(Icons.Rounded.Edit,"编辑",{},Modifier.padding(top=60.dp),frost=.5f,glassTint=.35f)
            }
        }}}
        compose.runOnIdle{
            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            val density=app.resources.displayMetrics.density
            for((x,y) in listOf(12 to 14,24 to 68)){
                val pixel=bitmap.getPixel((x*density).toInt(),(y*density).toInt())
                assertTrue("The red reference must remain visible through the gray glass",android.graphics.Color.red(pixel)-android.graphics.Color.green(pixel)>100)
            }
            assertEquals(android.graphics.Color.RED,bitmap.getPixel(0,(32*density).toInt()-1))
            bitmap.recycle()
        }
    }
    @Test fun messagesCannotPaintOverFixedComposerGlass(){
        val scene=GlassScene().apply{enabled=false}
        compose.setContent{root=LocalView.current.rootView;MaterialTheme{CompositionLocalProvider(LocalGlass provides scene,LocalDark provides true){
            Box(Modifier.fillMaxSize().background(Color.White)){
                Box(Modifier.fillMaxSize().behindGlassMenu(includeComposer=true).background(Color.Red))
                Box(Modifier.size(100.dp).liquidGlass(9,20f,frost=1f))
            }
        }}}
        compose.runOnIdle{
            val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(b))
            val d=app.resources.displayMetrics.density
            val middle=b.getPixel((50*d).toInt(),(50*d).toInt())
            assertTrue(kotlin.math.abs(android.graphics.Color.red(middle)-android.graphics.Color.green(middle))<5)
            assertEquals(android.graphics.Color.RED,b.getPixel((150*d).toInt(),(50*d).toInt()));b.recycle()
        }
    }
    @Test fun coverActionsAreCenteredAndTitleStaysInsideCover(){
        val vm=start()
        val source=File(app.cacheDir,"drafts/cover-preview.webp");source.parentFile!!.mkdirs()
        app.assets.open("preview-lake.webp").use{input->source.outputStream().use{input.copyTo(it)}}
        val entry=runBlocking{vm.repository.save(null,"封面毛玻璃标题","提示词",source)}
        compose.runOnIdle{vm.entries.value=listOf(entry)}
        idleUntil{compose.onAllNodesWithText(entry.name).fetchSemanticsNodes().isNotEmpty()}
        val cover=compose.onNode(hasAnyDescendant(hasText(entry.name)) and hasClickAction(),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(entry.name).performTouchInput{longClick()}
        val edit=compose.onNodeWithContentDescription("编辑提示词").fetchSemanticsNode().boundsInRoot
        val delete=compose.onNodeWithContentDescription("删除提示词").fetchSemanticsNode().boundsInRoot
        assertEquals(cover.center.y,edit.center.y,1f);assertEquals(cover.center.y,delete.center.y,1f)
        assertEquals(cover.center.x,(edit.center.x+delete.center.x)/2,1f)
        val title=compose.onNodeWithText(entry.name,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(title.top>=cover.bottom-cover.height*.125f);assertTrue(title.bottom<=cover.bottom)
        shot("revision12-cover-actions")
    }
    @Test fun navigationFramesKeepPageSurfacesAdjacentInsteadOfBlending(){
        start();compose.onNodeWithContentDescription("设置").performClick();compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("外观与文字").performClick()
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val surfaces=compose.onAllNodesWithTag("page-surface",useUnmergedTree=true).fetchSemanticsNodes().map{it.boundsInRoot}.filter{it.width>0}
        shot("revision12-navigation-mid")
        assertEquals(surfaces.toString(),2,surfaces.size)
        assertTrue(surfaces.toString(),surfaces[0].intersect(surfaces[1]).width<=1f)
        compose.mainClock.advanceTimeBy(400);compose.mainClock.autoAdvance=true
        compose.onNodeWithText("文字预览").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("聊天模型").assertExists()
    }
}
