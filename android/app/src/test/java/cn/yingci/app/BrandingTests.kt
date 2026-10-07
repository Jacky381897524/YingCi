package cn.yingci.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandingTests {
    @Test fun launcherUsesSuppliedLogoAndVersionSupportsUpgrade(){
        val app:Application=ApplicationProvider.getApplicationContext()
        val icon=app.getDrawable(R.drawable.ic_launcher)!!
        assertTrue(icon is BitmapDrawable)
        icon as BitmapDrawable
        assertTrue(icon.bitmap.width>=1024);assertEquals(icon.bitmap.width,icon.bitmap.height)
        assertEquals(Gravity.FILL,icon.gravity)
        assertEquals("3.1.1",BuildConfig.VERSION_NAME);assertEquals(16,BuildConfig.VERSION_CODE)
        assertEquals("cn.yingci.local",BuildConfig.APPLICATION_ID)
        val rendered=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,192,192);icon.draw(Canvas(rendered))
        val file=File(System.getProperty("user.home"),"qa/v215-launcher-icon.png");file.parentFile!!.mkdirs()
        file.outputStream().use{assertTrue(rendered.compress(Bitmap.CompressFormat.PNG,100,it))};rendered.recycle()
    }
}
