package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
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
class GenerationParameterSurfaceTests {
    @get:Rule val compose=createComposeRule()
    private lateinit var root:android.view.View
    private val app:Application get()=ApplicationProvider.getApplicationContext()
    private val scene=GlassScene()
    private var accepted:GenerationDraft?=null
    private var dismissed=false

    private fun start(dark:Boolean,confirm:Boolean=false){
        val p=Preferences(baseUrl="https://apihub.agnes-ai.com/v1",imageModel="agnes-image-2.5-flash",apiKey="test-key")
        val reference=File(app.cacheDir,"parameter-reference.webp")
        app.assets.open("preview-lake.webp").use{input->reference.outputStream().use{input.copyTo(it)}}
        val wallpaper=app.assets.open("chat-stream.jpg").use{BitmapFactory.decodeStream(it)}
        compose.setContent{
            root=LocalView.current.rootView
            val colors=if(dark)darkColorScheme(primary=Blue,onPrimary=Color.White)else lightColorScheme(primary=Blue,onPrimary=Color.White)
            MaterialTheme(colorScheme=colors){
                CompositionLocalProvider(LocalGlass provides scene,LocalDark provides dark,LocalPrefs provides p,LocalContentColor provides colors.onSurface){
                    Box(Modifier.fillMaxSize()){
                        Image(wallpaper.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        GenerationParametersPanel(GenerationDraft(prompt="画一片海",references=listOf(reference)),p,confirm,{dismissed=true},{accepted=it})
                    }
                }
            }
        }
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("效果封面",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
    }

    private fun verifySurface(){
        compose.runOnIdle{
            val items=scene.snapshot()
            val surface=items.single{it.kind==7}
            assertEquals(1f,surface.frost!!,0f)
            assertEquals(1.3f,surface.tint!!,0f)
            assertEquals(0f,surface.radius,0f)
            assertNull("The backing must not fade with the scrolling parameters",surface.fade)
            assertEquals(root.width.toFloat(),surface.rect.width,1f)
            assertEquals(root.height.toFloat(),surface.rect.height,1f)
            assertFalse("Foreground labels and references must not be blurred into the backing",items.any{it.kind in 3..6})
        }
        compose.onNodeWithText("画面比例").assertIsDisplayed()
        compose.onNodeWithText("清晰度").assertIsDisplayed()
        compose.onNodeWithText("生成张数").assertIsDisplayed()
        compose.onNodeWithText("图片格式").assertIsDisplayed()
    }

    private fun shot(name:String)=compose.runOnIdle{
        val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        val file=File(System.getProperty("user.home"),"qa/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }

    @Test fun darkParametersHaveFullPageFrostAndStillSave(){
        start(true);verifySurface();shot("revision13-parameters-dark")
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle{assertEquals(3,accepted!!.count);assertEquals(1,accepted!!.references.size);assertFalse(dismissed)}
    }

    @Test fun lightParametersHaveFullPageFrostAndCancelWithoutSaving(){
        start(false);verifySurface();shot("revision13-parameters-light")
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle{assertTrue(dismissed);assertNull(accepted)}
    }

    @Test fun confirmationSharesFrostAndKeepsPromptEditable(){
        start(true,confirm=true);verifySurface()
        compose.onNodeWithContentDescription("生图提示词").performTextReplacement("画一片蓝色的海")
        compose.onNodeWithContentDescription("移除参考图片").performClick()
        compose.onNodeWithText("确认生成").assertIsEnabled()
        shot("revision13-confirmation-dark")
        compose.onNodeWithText("确认生成").performClick()
        compose.runOnIdle{assertEquals("画一片蓝色的海",accepted!!.prompt);assertTrue(accepted!!.references.isEmpty())}
    }
}
