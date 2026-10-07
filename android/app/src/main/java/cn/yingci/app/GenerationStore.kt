package cn.yingci.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

internal class GenerationStore internal constructor(private val context:Context) {
    private val db=object:SQLiteOpenHelper(context,"generations.db",null,1){
        override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE images(id TEXT PRIMARY KEY,created INTEGER NOT NULL,json TEXT NOT NULL)")}
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
    }
    private val root=File(context.filesDir,"generations").apply{mkdirs()}
    private val mutex=Mutex()
    private var initialized=false
    val records=MutableStateFlow<List<GenerationRecord>>(emptyList())
    private fun read()=db.readableDatabase.rawQuery("SELECT json FROM images ORDER BY created DESC,rowid DESC",null).use{c->buildList{while(c.moveToNext())add(parse(JSONObject(c.getString(0))))}}
    private fun put(r:GenerationRecord){db.writableDatabase.insertWithOnConflict("images",null,ContentValues().apply{put("id",r.id);put("created",r.created);put("json",json(r).toString())},SQLiteDatabase.CONFLICT_REPLACE)}
    suspend fun initialize()=withContext(Dispatchers.IO){mutex.withLock{
        if(!initialized){read().filter{it.pending}.forEach{put(it.copy(status=if(it.status=="running")"unknown"else"stopped",error="上次任务已中断，未自动重新提交。请核对服务商记录后手动重试。"))};initialized=true}
        records.value=read()
    }}
    fun folder(id:String):File {require(id.matches(Regex("[a-zA-Z0-9-]{1,64}")));return File(root,id)}
    fun image(r:GenerationRecord):File?=r.image?.let{file(r.id,it)}?.takeIf{it.isFile}
    fun file(id:String,name:String):File {require(name.matches(Regex("[a-zA-Z0-9.-]+"))&&name!="."&&name!="..");return File(folder(id),name)}
    suspend fun enqueue(p:Preferences,d:GenerationDraft,conversationId:String?=null):List<GenerationRecord> = withContext(Dispatchers.IO){mutex.withLock{
        require(records.value.none{it.pending}){"已有生成任务，请等待完成"}
        require(d.prompt.isNotBlank()&&d.prompt.length<=32000){"提示词不能为空，且最多32000字"}
        val cap=imageCapability(p);require(d.references.isEmpty()||cap.references){"当前模型的参考图接口尚未适配，请更换已适配模型"}
        require(cap.provider!="bigmodel"||!BigModelImage.isTextModel(p.imageModel.trim())){"${p.imageModel.trim()} 是对话/识图模型，请选择 glm-image 或 CogView 生图模型"}
        ApiEndpoint.images(p.baseUrl);require(p.apiKey.isNotBlank()&&p.imageModel.isNotBlank()){"请先配置生图模型及 API Key"}
        val prepared=mutableListOf<GenerationRecord>()
        try{
            d.batches().forEach{inputs->
                val id=UUID.randomUUID().toString();val dir=folder(id).apply{mkdirs()}
                val r=GenerationRecord(id,d.prompt,p.imageModel,p.baseUrl,d.ratio,d.quality,d.format,inputs.indices.map{"input-$it.webp"},System.currentTimeMillis(),conversationId=conversationId)
                prepared.add(r)
                inputs.forEachIndexed{i,f->require(f.isFile){"参考图已失效，请重新选择"};f.copyTo(File(dir,r.inputs[i]))}
            }
            val database=db.writableDatabase;database.beginTransaction();try{prepared.forEach(::put);database.setTransactionSuccessful()}finally{database.endTransaction()}
            records.value=read();prepared.toList()
        }catch(e:Exception){prepared.forEach{folder(it.id).deleteRecursively()};throw e}
    }}
    suspend fun next():GenerationRecord?=withContext(Dispatchers.IO){mutex.withLock{
        val item=read().lastOrNull{it.status=="queued"}?:return@withLock null
        val running=item.copy(status="running");put(running);records.value=read();running
    }}
    suspend fun finish(r:GenerationRecord,bytes:ByteArray)=withContext(Dispatchers.IO){mutex.withLock{
        val ext=GenerationClient.validateImage(bytes);val dir=folder(r.id);val temp=File(dir,"result.tmp");val target=File(dir,"result.$ext")
        try{temp.outputStream().use{it.write(bytes)};check(temp.renameTo(target)){"图片保存失败"};put(r.copy(status="success",image=target.name,error=""));records.value=read()}finally{temp.delete()}
    }}
    suspend fun fail(r:GenerationRecord,message:String)=withContext(Dispatchers.IO){mutex.withLock{put(r.copy(status="failed",error=message.take(1200)));records.value=read()}}
    suspend fun interrupt()=withContext(Dispatchers.IO){mutex.withLock{read().filter{it.pending}.forEach{put(it.copy(status=if(it.status=="running")"unknown"else"stopped",error="任务已停止。已提交的请求可能仍在服务端处理，未自动重试。"))};records.value=read()}}
    suspend fun delete(ids:Set<String>)=withContext(Dispatchers.IO){mutex.withLock{
        val selected=read().filter{it.id in ids};require(selected.none{it.pending}){"请先停止正在生成的任务"}
        selected.forEach{r->require(folder(r.id).deleteRecursively()||!folder(r.id).exists()){"图片清理失败，请重试"};db.writableDatabase.delete("images","id=?",arrayOf(r.id))};records.value=read()
    }}
    suspend fun export(r:GenerationRecord)=withContext(Dispatchers.IO){
        val image=image(r)?:error("图片已不存在")
        val mime=when(image.extension){"png"->"image/png";"jpg"->"image/jpeg";else->"image/webp"}
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"映词-${r.id}.${image.extension}");put(MediaStore.Images.Media.MIME_TYPE,mime);put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/映词");put(MediaStore.Images.Media.IS_PENDING,1)}
        val resolver=context.contentResolver;val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("无法保存到相册")
        try{resolver.openOutputStream(uri)?.use{out->image.inputStream().use{it.copyTo(out)}}?:error("无法写入相册");resolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)}catch(e:Exception){resolver.delete(uri,null,null);throw e}
    }
    companion object {
        @Volatile private var instance:GenerationStore?=null
        fun get(context:Context):GenerationStore {
            val application=context.applicationContext
            return instance?.takeIf{it.context===application}?:synchronized(this){
                instance?.takeIf{it.context===application}?:GenerationStore(application).also{instance=it}
            }
        }
        fun json(r:GenerationRecord)=JSONObject().put("id",r.id).put("prompt",r.prompt).put("model",r.model).put("baseUrl",r.baseUrl).put("ratio",r.ratio).put("quality",r.quality).put("format",r.format).put("inputs",JSONArray(r.inputs)).put("created",r.created).put("status",r.status).put("image",r.image?:JSONObject.NULL).put("error",r.error).put("conversationId",r.conversationId?:JSONObject.NULL)
        fun parse(j:JSONObject)=GenerationRecord(j.getString("id"),j.getString("prompt"),j.getString("model"),j.getString("baseUrl"),j.getString("ratio"),j.getString("quality"),j.getString("format"),j.getJSONArray("inputs").let{a->List(a.length()){a.getString(it)}},j.getLong("created"),j.getString("status"),if(j.isNull("image"))null else j.getString("image"),j.optString("error"),if(j.isNull("conversationId"))null else j.optString("conversationId").takeIf{it.isNotBlank()})
    }
}
