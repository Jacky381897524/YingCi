package cn.yingci.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionTitleTests {
    @get:Rule val compose=createComposeRule()
    @Test fun oneLineTitleFitsNarrowColumnsAndOldNamesAreOnlyEllipsized(){
        val scene=GlassScene().apply{enabled=false}
        val old="旧标题不会因为升级而被修改或截断"
        compose.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalGlass provides scene,LocalDensity provides Density(density.density,1.45f)){
                MaterialTheme{Column(Modifier.width(132.dp)){
                    CollectionTitle("春日花园手绘拼贴")
                    CollectionTitle(old)
                }}
            }
        }
        fun layout(text:String):TextLayoutResult {
            val result=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult){assertTrue(it(result))}
            return result.single()
        }
        val normal=layout("春日花园手绘拼贴")
        assertEquals(1,normal.lineCount);assertEquals(13.sp,normal.layoutInput.style.fontSize)
        val legacy=layout(old);assertEquals(1,legacy.lineCount);assertTrue(legacy.isLineEllipsized(0));assertEquals(old,legacy.layoutInput.text.text)
        compose.runOnIdle{assertTrue("Titles must not register a glass capsule",scene.snapshot().none{it.kind<3})}
    }
}
