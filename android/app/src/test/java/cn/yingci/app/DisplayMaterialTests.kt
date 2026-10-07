package cn.yingci.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class DisplayMaterialTests {
    @Test fun allPublicV2ParametersSurviveSaveAndRestart()=runBlocking{
        val context:Context=ApplicationProvider.getApplicationContext();val repo=Repository(context)
        var material=DisplayMaterial();DisplayParameters.forEach{material=material.with(it.key,it.max)}
        assertEquals(17,DisplayParameters.size)
        repo.savePreferences(repo.preferences().copy(display=material,fontWeight=700))
        val restored=Repository(context).preferences()
        DisplayParameters.forEach{assertEquals(it.key,it.max,restored.display[it.key],.0001f)}
        assertEquals(700,restored.fontWeight)
    }
    @Test fun badPersistedValuesDoNotBreakRendering(){
        val parsed=DisplayMaterial.parse("{\"refraction\":9999,\"dispersion\":-20,\"frost\":\"NaN\"}")
        assertEquals(110f,parsed["refraction"],0f);assertEquals(0f,parsed["dispersion"],0f);assertEquals(0f,parsed["frost"],0f)
        assertEquals(84f,DisplayMaterial.parse("broken")["refraction"],0f)
    }
    @Test fun resetMatchesReferenceAndQuantizesToPublicStep(){
        assertEquals(.14f,DisplayMaterial()["edgeReach"],0f)
        assertEquals(2.1f,DisplayMaterial().with("dispersion",2.14f)["dispersion"],.0001f)
        assertEquals(0f,DisplayMaterial().with("frost",Float.NaN)["frost"],0f)
    }
}
