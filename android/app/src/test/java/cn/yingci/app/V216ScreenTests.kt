package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
class V216ScreenTests {
    @get:Rule val compose=createComposeRule()
    private lateinit var root:android.view.View
    private fun start(dark:Boolean=false):AppViewModel {
        val app:Application=ApplicationProvider.getApplicationContext()
        runBlocking{Repository(app).savePreferences(Preferences(theme=if(dark)2 else 1))}
        val vm=AppViewModel(app)
        compose.setContent{root=LocalView.current.rootView;YingciApp({},vm,renderGlass=false)}
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!vm.loading.value}
        return vm
    }
    private fun shot(name:String)=compose.runOnIdle{
        val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(b))
        val file=File(System.getProperty("user.home"),"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun roundButtonRegistersEqualSidesAndExplicitCircle(){
        val scene=GlassScene()
        compose.setContent{MaterialTheme{CompositionLocalProvider(LocalGlass provides scene){RoundGlassButton(Icons.Rounded.Add,"新增",{})}}}
        compose.runOnIdle{val item=scene.snapshot().single();assertEquals(item.rect.width,item.rect.height,.01f);assertTrue(item.circle);assertEquals(2,glassShapeType(item))}
    }
    private fun chat(dark:Boolean){
        val vm=start(dark);val app:Application=ApplicationProvider.getApplicationContext()
        val source=File(app.cacheDir,"chat-wallpaper.webp");app.assets.open("preview-lake.webp").use{input->source.outputStream().use{input.copyTo(it)}}
        val wallpaper=runBlocking{vm.repository.saveWallpaper(source)}
        val c=runBlocking{val c=vm.chatStore.create();vm.chatStore.append(c.id,ChatMessage(role="user",text="今天适合出门走走。"));vm.chatStore.append(c.id,ChatMessage(role="assistant",text="可以去湖边散散步，让自己放松一下。"));vm.chatStore.append(c.id,ChatMessage(role="assistant",text="想聊聊今天发生的事吗？"));c}
        compose.onNodeWithContentDescription("生图").performClick()
        compose.runOnIdle{vm.updatePreferences(vm.preferences.value.copy(chatWallpaper=wallpaper));vm.activeConversation.value=c.id}
        compose.onNodeWithText("想聊聊今天发生的事吗？").assertExists()
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithContentDescription("效果封面",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val plus=compose.onNodeWithContentDescription("添加图片").fetchSemanticsNode().boundsInRoot
        val send=compose.onNodeWithContentDescription("发送").fetchSemanticsNode().boundsInRoot
        val field=compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(plus.center.y,send.center.y,1f);assertEquals(plus.center.y,field.center.y,1f)
        shot(if(dark)"v216-chat-dark"else"v216-chat-light")
        compose.onNodeWithContentDescription("对话菜单").performClick();compose.onNodeWithText("狗头军师").performClick()
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();vm.conversations.value.first{it.id==c.id}.skill==GoutouSkill.ID}
        compose.onNodeWithText("狗头军师").assertExists()
        compose.onNodeWithContentDescription("对话菜单").performClick();compose.onNodeWithText("退出狗头军师").performClick()
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();vm.conversations.value.first{it.id==c.id}.skill.isEmpty()}
    }
    @Test fun lightChatComposerAlignsAndSkillMenuWorks(){chat(false)}
    @Test fun darkChatComposerAlignsAndSkillMenuWorks(){chat(true)}
    @Test fun appearanceHasNoThemeWallpaperEntry(){
        start();compose.onNodeWithContentDescription("设置").performClick();compose.onNodeWithText("外观与文字").performClick()
        compose.onNodeWithText("文字预览").assertExists();compose.onNodeWithText("主题与壁纸").assertDoesNotExist()
        shot("v216-appearance")
    }
    @Test fun headerOverlayFadesBehindControlsNotBelowThem(){
        compose.setContent{root=LocalView.current.rootView;Box(Modifier.fillMaxSize().background(Color.White)){
            HeaderOverlay(header={Box(Modifier.height(82.dp).fillMaxWidth()){Box(Modifier.size(24.dp).background(Color.Blue))}}){height->FadingContent(Modifier.fillMaxSize(),top=height,bottom=0.dp){Box(Modifier.fillMaxSize().background(Color.Red))}}
        }}
        compose.runOnIdle{
            val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(b));val d=appDensity()
            fun green(y:Int)=android.graphics.Color.green(b.getPixel((100*d).toInt(),(y*d).toInt()))
            assertTrue(green(2)>245);assertTrue(green(20)>green(50));assertTrue(green(50)>green(80));assertEquals(0,green(84))
            assertEquals(android.graphics.Color.BLUE,b.getPixel((10*d).toInt(),(10*d).toInt()));b.recycle()
        }
    }
    private fun appDensity()=ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
}
