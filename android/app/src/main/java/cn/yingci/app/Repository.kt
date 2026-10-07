package cn.yingci.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PromptDatabase(context: Context) : SQLiteOpenHelper(context, "yingci.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE prompts (id TEXT PRIMARY KEY, name TEXT NOT NULL, original TEXT NOT NULL, edited TEXT, cover TEXT, created INTEGER NOT NULL, updated INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX prompts_updated ON prompts(updated DESC)")
        db.execSQL("CREATE TRIGGER preserve_original BEFORE UPDATE OF original ON prompts WHEN NEW.original != OLD.original BEGIN SELECT RAISE(ABORT, 'Original prompt is immutable'); END")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

class Repository(val context: Context) {
    private val helper = PromptDatabase(context).apply { setWriteAheadLoggingEnabled(true) }
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val covers = File(context.filesDir, "covers").apply { mkdirs() }
    private val drafts = File(context.cacheDir, "drafts").apply { mkdirs() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("yingci-api-v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("yingci-api-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    private fun encrypt(value: String): String { if (value.isEmpty()) return ""; val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) }
    private fun decrypt(value: String): String { if (value.isEmpty()) return ""; val data = Base64.decode(value, Base64.NO_WRAP); require(data.size > 12); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))); return String(cipher.doFinal(data.copyOfRange(12, data.size))) }
    fun preferences(): Preferences = Preferences(prefs.getInt("theme", 0).coerceIn(0,2), prefs.getBoolean("followFont", false), nearestFontSize(prefs.getInt("fontPercent",100)), false, false, prefs.getString("baseUrl", "").orEmpty(), prefs.getString("model", "").orEmpty(), runCatching { decrypt(prefs.getString("apiKey", "").orEmpty()) }.getOrDefault(""), prefs.getInt("fontWeight",400).coerceIn(400,700), DisplayMaterial.parse(prefs.getString("displayMaterial","{}").orEmpty()), prefs.getString("imageModel", "").orEmpty(),prefs.getString("wallpaper","").orEmpty(),prefs.getStringSet("wallpaperPages",setOf("0","1","2","3"))!!.mapNotNull{it.toIntOrNull()?.takeIf{n->n in 0..3}}.toSet(),prefs.getString("themePreset","纯净").orEmpty())
    fun chatPreferences(value:Preferences)=value.copy(chatIndependent=prefs.getBoolean("chatIndependent",false),chatUrl=prefs.getString("chatUrl","").orEmpty(),chatModel=prefs.getString("chatModel","").orEmpty(),chatKey=runCatching{decrypt(prefs.getString("chatKey","").orEmpty())}.getOrDefault(""),chatWallpaper=prefs.getString("chatWallpaper","").orEmpty())
    suspend fun savePreferences(value: Preferences) = withContext(Dispatchers.IO) {
        val secret = encrypt(value.apiKey.trim())
        check(prefs.edit().putInt("theme", value.theme).putBoolean("followFont", value.followFont).putInt("fontPercent", nearestFontSize(value.fontPercent)).putInt("fontWeight",value.fontWeight.coerceIn(400,700)).putString("displayMaterial",value.display.json()).putBoolean("reduceMotion", false).putBoolean("haptics", false).putString("baseUrl", value.baseUrl.trim()).putString("model", value.model.trim()).putString("imageModel", value.imageModel.trim()).putString("apiKey", secret).putString("wallpaper",value.wallpaper).putStringSet("wallpaperPages",value.wallpaperPages.map{it.toString()}.toSet()).putString("themePreset",value.themePreset).commit()) { "设置保存失败，请重试" }
        check(prefs.edit().putBoolean("chatIndependent",value.chatIndependent).putString("chatUrl",value.chatUrl.trim()).putString("chatModel",value.chatModel.trim()).putString("chatKey",encrypt(value.chatKey.trim())).putString("chatWallpaper",value.chatWallpaper).commit()){"聊天设置保存失败"}
        File(context.filesDir,"wallpapers").listFiles()?.filter{it.isFile&&it.name!=value.wallpaper&&it.name!=value.chatWallpaper&&it.name!="lake.webp"}?.forEach{it.delete()}
    }
    fun wallpaper(name:String):File?=name.takeIf{it.matches(Regex("[a-zA-Z0-9-]+\\.webp"))}?.let{File(context.filesDir,"wallpapers/$it").takeIf{f->f.isFile}}
    suspend fun chatBackground(name:String):File=withContext(Dispatchers.IO){
        wallpaper(name)?:File(context.filesDir,"chat-stream.jpg").also{target->
            if(!target.isFile)context.assets.open("chat-stream.jpg").use{input->target.outputStream().use{input.copyTo(it)}}
        }
    }
    suspend fun saveWallpaper(source:File):String=withContext(Dispatchers.IO){val dir=File(context.filesDir,"wallpapers").apply{mkdirs()};val name="${UUID.randomUUID()}.webp";source.copyTo(File(dir,name));discardDraft(source);name}
    suspend fun builtinWallpaper():String=withContext(Dispatchers.IO){val dir=File(context.filesDir,"wallpapers").apply{mkdirs()};val target=File(dir,"lake.webp");if(!target.isFile)context.assets.open("preview-lake.webp").use{input->target.outputStream().use{input.copyTo(it)}};target.name}
    private fun readRows(db: SQLiteDatabase): List<PromptEntry> = db.rawQuery("SELECT id,name,original,edited,cover,created,updated FROM prompts ORDER BY updated DESC", null).use { c -> buildList { while (c.moveToNext()) add(PromptEntry(c.getString(0),c.getString(1),c.getString(2),if(c.isNull(3))null else c.getString(3),if(c.isNull(4))null else c.getString(4),c.getLong(5),c.getLong(6))) } }
    suspend fun all(): List<PromptEntry> = withContext(Dispatchers.IO) { lock.withLock { readRows(helper.readableDatabase) } }
    fun cover(entry: PromptEntry): File? = entry.cover?.let { relative -> File(context.filesDir, relative).takeIf { it.canonicalPath.startsWith(covers.canonicalPath + File.separator) && it.isFile } }

    suspend fun importImage(uri: Uri): File = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val largest = maxOf(info.size.width, info.size.height)
            require(info.size.width > 0 && info.size.height > 0) { "图片尺寸无效" }
            if (largest > 2048) decoder.setTargetSize((info.size.width * 2048f/largest).toInt().coerceAtLeast(1),(info.size.height * 2048f/largest).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val file = File(drafts, "${UUID.randomUUID()}.webp")
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.WEBP,92,it)) }; file } catch(e: Exception) { file.delete(); throw e } finally { bitmap.recycle() }
    }
    fun discardDraft(file: File?) { if (file != null && file.canonicalPath.startsWith(drafts.canonicalPath + File.separator)) file.delete() }
    suspend fun save(id: String?, name: String, text: String, image: File?, extractedOriginal: String? = null): PromptEntry = withContext(Dispatchers.IO) { lock.withLock {
        require(name.isNotBlank() && name.length <= 120) { "名称不能为空，且最多 120 字" }; require(text.isNotBlank() && text.length <= 100000) { "提示词不能为空，且最多 10 万字" }
        val db = helper.writableDatabase; val old = id?.let { key -> readRows(db).find { it.id == key } }
        require(id == null || old != null) { "条目已不存在" }
        require(old?.name==name.trim()||validNewName(name.trim())){"新名称最多30个字，不能换行"}
        val entryId = old?.id ?: UUID.randomUUID().toString(); val now = System.currentTimeMillis()
        var newCover: File? = null
        val relative = if (image != null && image.isFile && image.canonicalPath.startsWith(drafts.canonicalPath + File.separator)) {
            val folder = File(covers,entryId).apply { mkdirs() }; newCover = File(folder,"${UUID.randomUUID()}.webp"); image.copyTo(newCover!!); "covers/$entryId/${newCover!!.name}"
        } else old?.cover
        require(extractedOriginal == null || extractedOriginal.isNotBlank() && extractedOriginal.length <= 100000) { "原始提示词无效" }
        val original = old?.original ?: extractedOriginal ?: text
        val result = PromptEntry(entryId,name.trim(),original,if(old!=null || text!=original)text else null,relative,old?.created ?: now,now)
        try {
            val values = values(result)
            if(old == null) db.insertOrThrow("prompts",null,values) else { values.remove("original"); db.update("prompts",values,"id=?",arrayOf(entryId)) }
        } catch(e: Exception) { newCover?.delete(); throw e }
        if(newCover != null && old?.cover != relative) old?.let { cover(it)?.delete() }
        discardDraft(image); result
    } }
    private fun values(e: PromptEntry) = ContentValues().apply { put("id",e.id);put("name",e.name);put("original",e.original);put("edited",e.edited);put("cover",e.cover);put("created",e.created);put("updated",e.updated) }
    suspend fun delete(entry: PromptEntry) = withContext(Dispatchers.IO) { lock.withLock { helper.writableDatabase.delete("prompts","id=?",arrayOf(entry.id)); File(covers,entry.id).takeIf { it.canonicalPath.startsWith(covers.canonicalPath+File.separator) }?.deleteRecursively(); Unit } }
    suspend fun cleanup() = withContext(Dispatchers.IO) { lock.withLock {
        val active = readRows(helper.readableDatabase).mapNotNull { it.cover }.toSet()
        covers.walkTopDown().filter { it.isFile }.forEach { if(it.relativeTo(context.filesDir).invariantSeparatorsPath !in active)it.delete() }
        drafts.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>24*60*60*1000 }?.forEach { it.delete() }
    } }
    suspend fun clearCache() = withContext(Dispatchers.IO) { File(context.cacheDir,"thumbnails").deleteRecursively(); Unit }
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) { lock.withLock {
        val entries = readRows(helper.readableDatabase)
        val array = JSONArray(); entries.forEach { e -> array.put(JSONObject().put("id",e.id).put("name",e.name).put("original",e.original).put("edited",e.edited ?: JSONObject.NULL).put("created",e.created).put("updated",e.updated).put("cover",if(cover(e)!=null)"covers/${e.id}/cover.webp" else JSONObject.NULL)) }
        val manifest = JSONObject().put("format","yingci-backup").put("version",1).put("entries",array)
        val out = context.contentResolver.openOutputStream(uri,"wt") ?: error("无法创建备份文件")
        ZipOutputStream(out.buffered()).use { zip -> zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray());zip.closeEntry(); entries.forEach { e -> cover(e)?.let { file -> zip.putNextEntry(ZipEntry("covers/${e.id}/cover.webp"));file.inputStream().use { it.copyTo(zip) };zip.closeEntry() } } }
        entries.size
    } }
    suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) { lock.withLock {
        val stage = File(context.cacheDir,"restore-${UUID.randomUUID()}").apply { mkdirs() }; val copied = mutableListOf<File>()
        try {
            var total = 0L; var count = 0; val names = hashSetOf<String>()
            ZipInputStream(context.contentResolver.openInputStream(uri)?.buffered() ?: error("无法读取备份")).use { zip ->
                while(true) {
                    val item = zip.nextEntry ?: break
                    if(item.isDirectory) { zip.closeEntry();continue }
                    val path = item.name
                    require(path == "manifest.json" || path.matches(Regex("covers/[a-zA-Z0-9-]{1,64}/cover\\.webp"))) { "备份含有不支持的文件" }
                    require(names.add(path) && ++count <= 5001) { "备份含重复文件或条目过多" }
                    val target = File(stage,path); target.parentFile?.mkdirs();var size=0L
                    target.outputStream().use { out -> val buffer=ByteArray(8192);while(true){val n=zip.read(buffer);if(n<0)break;size+=n;total+=n;require(size<=48L*1024*1024&&total<=512L*1024*1024){"备份过大，请拆分导入"};out.write(buffer,0,n)} }
                    zip.closeEntry()
                }
            }
            val manifestFile=File(stage,"manifest.json");require(manifestFile.length()<=32L*1024*1024){"备份清单过大"}
            val manifest=JSONObject(manifestFile.readText());require(manifest.optString("format")=="yingci-backup"&&manifest.optInt("version")==1){"不支持的备份格式"}
            val list=manifest.getJSONArray("entries");require(list.length()<=5000){"备份条目过多"}
            val db=helper.writableDatabase;val existing=readRows(db).associateBy{it.id};val ids=hashSetOf<String>();val prepared=mutableListOf<PromptEntry>()
            for(i in 0 until list.length()){
                val item=list.getJSONObject(i);val oldId=item.getString("id");require(oldId.matches(Regex("[a-zA-Z0-9-]{1,64}"))&&ids.add(oldId)){"无效或重复的条目编号"}
                val name=item.getString("name");val original=item.getString("original");val edited=if(item.isNull("edited"))null else item.getString("edited")
                require(name.isNotBlank()&&name.length<=120&&original.isNotBlank()&&original.length<=100000&&(edited==null||edited.isNotBlank()&&edited.length<=100000)){"备份条目内容无效"}
                val id=if(existing.containsKey(oldId))UUID.randomUUID().toString() else oldId
                var relative:String?=null
                if(!item.isNull("cover")){
                    val path=item.getString("cover");require(path=="covers/$oldId/cover.webp"){"封面路径无效"};val source=File(stage,path);require(source.isFile){"备份缺少封面"}
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(source.path,bounds);require(bounds.outWidth in 1..8192&&bounds.outHeight in 1..8192){"封面损坏或尺寸过大"}
                    val folder=File(covers,id).apply{mkdirs()};val file=File(folder,"${UUID.randomUUID()}.webp");copied.add(file);source.copyTo(file);relative="covers/$id/${file.name}"
                }
                prepared.add(PromptEntry(id,name,original,edited,relative,item.optLong("created",System.currentTimeMillis()),System.currentTimeMillis()))
            }
            db.beginTransaction();try{prepared.forEach{db.insertOrThrow("prompts",null,values(it))};db.setTransactionSuccessful()}finally{db.endTransaction()}
            copied.clear();prepared.size
        } finally { copied.forEach{it.delete()};stage.deleteRecursively() }
    } }
}


