package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
class ScreenTests {
    @get:Rule val compose=createComposeRule()
    private lateinit var screenshotView:android.view.View
    private fun screenshot(name:String){
        val folder=File(System.getProperty("user.home"),"qa").apply{mkdirs()}
        compose.runOnIdle{
            val view=screenshotView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(folder,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            bitmap.recycle()
        }
    }
    @Test fun collectionDetailAndSettingsFlow(){
        val app:Application=ApplicationProvider.getApplicationContext()
        val repo=Repository(app)
        val draft=File(app.cacheDir,"drafts/test.webp");app.assets.open("preview-lake.webp").use{src->draft.outputStream().use{src.copyTo(it)}}
        val first=runBlocking{repo.save(null,"夏日手绘拼贴","原始提示词：保留新照片内容，迁移手绘语言。",draft)}
        runBlocking{repo.save(first.id,first.name,"修改后的提示词：保留主体，使用摄影与手绘拼贴。",null)}
        val vm=AppViewModel(app)
        compose.setContent{screenshotView=androidx.compose.ui.platform.LocalView.current.rootView;YingciApp({},vm,renderGlass=false)}
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!vm.loading.value}
        compose.mainClock.advanceTimeBy(300)
        compose.waitUntil(5000){compose.onAllNodesWithText("夏日手绘拼贴").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(5000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithContentDescription("效果封面",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        screenshot("library")
        compose.onNodeWithText("夏日手绘拼贴").assertIsDisplayed()
        compose.onNodeWithText("夏日手绘拼贴").performClick()
        compose.onNodeWithText("我的版本").assertExists()
        compose.onNodeWithText("原始版本").performClick()
        compose.onNodeWithText("原始提示词：保留新照片内容，迁移手绘语言。").performScrollTo().assertIsDisplayed()
        screenshot("detail")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("外观与文字").performClick()
        compose.onNodeWithText("文字大小").assertIsDisplayed()
        screenshot("appearance")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("显示").performClick()
        screenshot("display")
    }
    @Test fun darkLargestFontApiScreenFits(){
        val app:Application=ApplicationProvider.getApplicationContext()
        val repo=Repository(app)
        runBlocking{repo.savePreferences(Preferences(theme=2,fontPercent=145))}
        val vm=AppViewModel(app)
        compose.setContent{screenshotView=androidx.compose.ui.platform.LocalView.current.rootView;YingciApp({},vm,renderGlass=false)}
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!vm.loading.value}
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("模型连接").performClick()
        compose.onNodeWithText("API Key").assertIsDisplayed()
        compose.onNodeWithText("生图模型").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsDisplayed()
        screenshot("api-dark-large")
        val diagnostic="服务暂不可用（HTTP 503）\n服务商：No available channel for model vision-test\n请求编号：trace-test-123"
        compose.runOnIdle{vm.visionStatus.value=diagnostic}
        compose.onNodeWithText(diagnostic).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsDisplayed()
        screenshot("api-error-dark-large")
    }
    @Test fun modelSelectionKeepsRolesSeparateUntilSave(){
        val app:Application=ApplicationProvider.getApplicationContext()
        val repo=Repository(app)
        runBlocking{repo.savePreferences(Preferences(baseUrl="https://apihub.agnes-ai.com/v1",model="old-vision",imageModel="old-image"))}
        val vm=AppViewModel(app)
        compose.setContent{screenshotView=androidx.compose.ui.platform.LocalView.current.rootView;YingciApp({},vm,renderGlass=false)}
        compose.waitUntil(5000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!vm.loading.value}
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("模型连接").performClick()
        compose.runOnIdle{vm.availableModels.value=listOf("Agnes-Image-2.5 Flash","vision-exact-id")}
        compose.onNodeWithContentDescription("选择识图模型").performScrollTo().performClick()
        compose.onNodeWithText("筛选模型").performTextInput("vision")
        compose.onNodeWithText("vision-exact-id").performClick()
        compose.onNodeWithText("old-image").assertExists()
        compose.onNodeWithContentDescription("选择生图模型").performScrollTo().performClick()
        screenshot("model-picker")
        compose.onNodeWithText("Agnes-Image-2.5 Flash").performClick()
        compose.onNodeWithText("vision-exact-id").assertExists()
        compose.onNodeWithText("测试连接").assertDoesNotExist()
        compose.onNodeWithText("Agnes-Image-2.5 Flash").assertExists()
        compose.onNodeWithText("保存").assertIsDisplayed()
        val saved=runBlocking{repo.preferences()}
        assertEquals("old-vision",saved.model)
        assertEquals("old-image",saved.imageModel)
    }
}
