package cn.yingci.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SearchAndEndpointTests {
    private val entry=PromptEntry("1","拼贴艺术海报 Poster","柔和自然光线，保留主体身份","蓝色水彩质感",null,1,1)
    @Test fun searchCoversBothVersions(){assertTrue(PromptSearch.matches(entry,"自然光"));assertTrue(PromptSearch.matches(entry,"水彩"))}
    @Test fun fuzzyChineseAndEnglish(){assertTrue(PromptSearch.matches(entry,"拼海报"));assertTrue(PromptSearch.matches(entry,"PＯＳＴＥＲ"));assertTrue(PromptSearch.matches(entry,"postr"))}
    @Test fun searchAllTermsRequired(){assertTrue(PromptSearch.matches(entry,"海报 水彩"));assertFalse(PromptSearch.matches(entry,"海报 太空"));assertTrue(PromptSearch.matches(entry,"  "))}
    @Test fun endpoints(){assertEquals("https://example.com/v1/chat/completions",ApiEndpoint.chat("https://example.com").toString());assertEquals("https://example.com/v1/chat/completions",ApiEndpoint.chat("https://example.com/v1/").toString());assertEquals("https://example.com/custom/chat/completions",ApiEndpoint.chat("https://example.com/custom/chat/completions").toString())}
    @Test fun invalidEndpointsRejected(){listOf("http://example.com","https://key@example.com","https://example.com?api_key=x","https://example.com/#x","no-url").forEach{assertTrue(runCatching{ApiEndpoint.chat(it)}.isFailure)}}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RepositoryTests {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private fun temporary(name:String)=File(context.cacheDir,"${System.nanoTime()}-$name")
    @Test fun editedExtractionPreservesModelOriginal()=runBlocking{
        val repo=Repository(context)
        val entry=repo.save(null,"风格","用户修改后的结果",null,"模型的原始输出")
        assertEquals("模型的原始输出",entry.original)
        assertEquals("用户修改后的结果",entry.current)
        repo.save(entry.id,"风格","再次修改",null)
        val restored=Repository(context).all().first{it.id==entry.id}
        assertEquals("模型的原始输出",restored.original)
        assertEquals("再次修改",restored.current)
    }
    @Test fun repeatedEditsPreserveExactlyOneOriginal()=runBlocking{
        val repo=Repository(context)
        val first=repo.save(null,"原名","最初提示词",null)
        repo.save(first.id,"第二个名字","第一次修改",null)
        repo.save(first.id,"第三个名字","最终修改",null)
        val final=repo.all().single{it.id==first.id}
        assertEquals("最初提示词",final.original);assertEquals("最终修改",final.edited);assertEquals("第三个名字",final.name)
        val db=PromptDatabase(context).writableDatabase
        assertTrue(runCatching{db.update("prompts",ContentValues().apply{put("original","覆盖尝试")},"id=?",arrayOf(first.id))}.isFailure)
        assertEquals("最初提示词",repo.all().first{it.id==first.id}.original)
    }
    @Test fun backupRestoresVersionsWithoutOverwriting()=runBlocking{
        val repo=Repository(context);val first=repo.save(null,"备份测试","原始内容",null);repo.save(first.id,"备份测试","编辑内容",null)
        val before=repo.all();val zip=temporary("backup.zip");val count=repo.export(Uri.fromFile(zip))
        assertEquals(before.size,count)
        val manifest=ZipInputStream(zip.inputStream()).use{it.nextEntry;it.readBytes().toString(Charsets.UTF_8)}
        assertFalse(manifest.contains("apiKey"));assertFalse(manifest.contains("baseUrl"))
        assertEquals(count,repo.restore(Uri.fromFile(zip)))
        val after=repo.all();assertEquals(before.size*2,after.size);assertEquals(before.first{it.id==first.id},after.first{it.id==first.id})
        assertTrue(after.any{it.id!=first.id&&it.name=="备份测试"&&it.original=="原始内容"&&it.edited=="编辑内容"})
    }
    @Test fun untrustedZipCannotEscapeOrAlterLibrary()=runBlocking{
        val repo=Repository(context);val before=repo.all();val zip=temporary("invalid.zip")
        ZipOutputStream(zip.outputStream()).use{it.putNextEntry(ZipEntry("../escaped.txt"));it.write("bad".toByteArray());it.closeEntry()}
        assertTrue(runCatching{repo.restore(Uri.fromFile(zip))}.isFailure);assertEquals(before,repo.all());assertFalse(File(context.cacheDir,"escaped.txt").exists())
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun imageCoverIsManagedAndBackedUp()=runBlocking{
        val repo=Repository(context);val bitmap=android.graphics.Bitmap.createBitmap(10,20,android.graphics.Bitmap.Config.ARGB_8888)
        // ContentResolver-backed ImageDecoder is Android-only in the Windows native runtime.
        // Seed a real encoded draft to verify the application's archive/restore ownership logic.
        val draft=File(context.cacheDir,"drafts/${System.nanoTime()}.webp")
        draft.outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP,92,it))};bitmap.recycle()
        val saved=repo.save(null,"封面测试","测试提示词",draft)
        assertFalse(draft.exists());assertTrue(repo.cover(saved)!!.isFile)
        val backup=temporary("cover.zip");repo.export(Uri.fromFile(backup));repo.restore(Uri.fromFile(backup))
        val restored=repo.all().first{it.id!=saved.id&&it.name==saved.name};assertTrue(repo.cover(restored)!!.isFile)
        repo.delete(saved);assertTrue(repo.cover(restored)!!.isFile)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ActivitySmokeTests {
    @Test fun activityCanStartAndStop(){
        org.robolectric.Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup().visible()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(controller.get().findViewById<android.view.View>(android.R.id.content))
        }
    }
}
