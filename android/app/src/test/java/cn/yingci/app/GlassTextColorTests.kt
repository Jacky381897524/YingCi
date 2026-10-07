package cn.yingci.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h300dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassTextColorTests {
    @get:Rule val compose=createComposeRule()
    @Test fun actionLabelsUseAccentWhileOrdinaryGlassKeepsBlackWhiteAcrossThemeChanges(){
        var dark by mutableStateOf(false)
        lateinit var root:android.view.View
        compose.setContent{
            root=androidx.compose.ui.platform.LocalView.current.rootView
            val scheme=if(dark)darkColorScheme(primary=Color(0xFFFFD44D))else lightColorScheme(primary=Blue)
            MaterialTheme(colorScheme=scheme){
                CompositionLocalProvider(LocalDark provides dark,LocalGlass provides remember(dark){GlassScene().also{it.enabled=false;it.dark=dark}}){
                    Surface(Modifier.fillMaxWidth()){
                        Column{
                            Primary("重新反推",modifier=Modifier.fillMaxWidth()){}
                            OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth().liquidGlass()){
                                GlassText("复制提示词",action=true)
                            }
                            GlassText("封面标题",Modifier.fillMaxWidth().liquidGlass())
                        }
                    }
                }
            }
        }
        fun check(label:String,color:Color){
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){assertTrue(it(layouts))}
            assertEquals(color,layouts.single().layoutInput.style.color)
        }
        fun screenshot(name:String)=compose.runOnIdle{
            val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(image))
            val file=File(System.getProperty("user.home"),"qa/$name.png");file.parentFile!!.mkdirs()
            file.outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
        }
        check("重新反推",Blue);check("复制提示词",Blue);check("封面标题",lightColorScheme().onSurface)
        screenshot("v102-action-colors-light")
        compose.runOnIdle{dark=true}
        check("重新反推",Color(0xFFFFD44D));check("复制提示词",Color(0xFFFFD44D));check("封面标题",darkColorScheme().onSurface)
        screenshot("v102-action-colors-dark")
    }
}
