package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V226ScreenTests {
    @get:Rule val compose=createComposeRule()
    private lateinit var root:android.view.View
    private fun settings(dark:Boolean):AppViewModel {
        val app:Application=ApplicationProvider.getApplicationContext()
        runBlocking{Repository(app).savePreferences(Preferences(theme=if(dark)2 else 1,chatIndependent=true,chatUrl="https://openrouter.ai/api",chatModel="vendor/text-model"))}
        val vm=AppViewModel(app)
        compose.setContent{root=androidx.compose.ui.platform.LocalView.current.rootView;YingciApp({},vm,renderGlass=false)}
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!vm.loading.value}
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("聊天模型").performClick()
        return vm
    }
    private fun shot(name:String)=compose.runOnIdle{
        val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
        val file=File(System.getProperty("user.home"),"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun verify(dark:Boolean){
        val vm=settings(dark)
        compose.onNodeWithText("聊天接口：https://openrouter.ai/api/v1/chat/completions").assertExists()
        shot(if(dark)"v226-settings-dark"else"v226-settings-light")
        compose.runOnIdle{vm.chatStatus.value="聊天连接通过，已收到文字回复";vm.visionStatus.value="接口已响应，但模型不支持图片"}
        compose.onNodeWithText("测试聊天连接").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("测试图片识别").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("聊天连接通过，已收到文字回复").assertExists()
        compose.onNodeWithText("接口已响应，但模型不支持图片").assertExists()
        shot(if(dark)"v226-tests-dark"else"v226-tests-light")
        compose.onNodeWithText("https://openrouter.ai/api").performScrollTo().performTextReplacement("https://api.anthropic.com/v1")
        compose.onNodeWithText("聊天接口：https://api.anthropic.com/v1/messages").assertExists()
        compose.runOnIdle{assertEquals("未测试",vm.chatStatus.value);assertEquals("未测试",vm.visionStatus.value)}
    }
    @Test fun lightSettingsShowResolvedEndpointAndSeparateTests(){verify(false)}
    @Test fun darkSettingsShowResolvedEndpointAndSeparateTests(){verify(true)}
}
