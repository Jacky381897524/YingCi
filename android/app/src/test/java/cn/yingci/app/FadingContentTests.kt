package cn.yingci.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w300dp-h300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FadingContentTests {
    @get:Rule val compose=createComposeRule()
    @Test fun scrollLayerFadesAtBothEdgesWithoutFadingFixedControls(){
        lateinit var root:android.view.View
        compose.setContent{
            root=LocalView.current.rootView
            Column(Modifier.fillMaxSize().background(Color.White)){
                Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Blue))
                FadingContent(Modifier.fillMaxWidth().height(200.dp),top=40.dp,bottom=40.dp){
                    Box(Modifier.fillMaxSize().background(Color.Red))
                }
            }
        }
        compose.runOnIdle{
            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            fun green(y:Int)=android.graphics.Color.green(bitmap.getPixel(100,y))
            assertEquals(android.graphics.Color.BLUE,bitmap.getPixel(100,20))
            assertTrue(green(42)>green(60));assertTrue(green(60)>green(100))
            assertEquals(0,green(140));assertTrue(green(235)>green(215))
            bitmap.recycle()
        }
    }
}
