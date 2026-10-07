package cn.yingci.app

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class ChatMessage(val id:String=UUID.randomUUID().toString(),val role:String,val text:String,val created:Long=System.currentTimeMillis(),val images:List<String> = emptyList(),val generations:List<String> = emptyList(),val kind:String="text",val referenceMessage:String?=null)
internal data class ChatOptions(val instructions:String="",val remember:Boolean=true,val continuous:Boolean=false)
internal data class Conversation(val id:String=UUID.randomUUID().toString(),val title:String="新对话",val updated:Long=System.currentTimeMillis(),val messages:List<ChatMessage> = emptyList(),val skill:String="",val options:ChatOptions=ChatOptions(),val memory:String="") {
    fun matches(query:String)=query.isBlank()||title.contains(query,true)||(skill==GoutouSkill.ID&&"狗头军师".contains(query))||messages.any{it.text.contains(query,true)}
}

internal class ChatStore(context:Context) {
    private val root=File(context.filesDir,"conversations").apply{mkdirs()}
    private val db=object:SQLiteOpenHelper(context,"conversations.db",null,1){
        override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE conversations(id TEXT PRIMARY KEY,updated INTEGER NOT NULL,json TEXT NOT NULL)")}
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
    }
    private val mutex=Mutex()
    val conversations=MutableStateFlow<List<Conversation>>(emptyList())
    fun folder(id:String):File {require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")));return File(root,id)}
    fun image(id:String,name:String):File {require(name.matches(Regex("[a-zA-Z0-9-]+\\.webp")));return File(folder(id),name)}
    private fun read()=db.readableDatabase.rawQuery("SELECT json FROM conversations ORDER BY updated DESC,rowid DESC",null).use{c->buildList{while(c.moveToNext())add(parse(JSONObject(c.getString(0))))}}
    private fun put(c:Conversation){db.writableDatabase.insertWithOnConflict("conversations",null,ContentValues().apply{put("id",c.id);put("updated",c.updated);put("json",json(c).toString())},SQLiteDatabase.CONFLICT_REPLACE)}
    suspend fun initialize(records:List<GenerationRecord>)=withContext(Dispatchers.IO){mutex.withLock{
        val all=read().associateBy{it.id}.toMutableMap()
        val known=all.values.flatMap{it.messages}.flatMap{it.generations}.toSet()
        records.filter{it.id !in known}.sortedBy{it.created}.forEach{r->
            val id=r.conversationId?:"legacy-${r.id}"
            val c=all[id]?:Conversation(id,r.prompt.take(28),r.created)
            val m=ChatMessage(id="image-${r.id}",role="assistant",text=r.prompt,created=r.created,generations=listOf(r.id),kind="generation")
            all[id]=c.copy(updated=maxOf(c.updated,r.created),messages=c.messages+m)
        }
        all.values.forEach(::put);conversations.value=read()
    }}
    suspend fun create()=withContext(Dispatchers.IO){mutex.withLock{Conversation().also{put(it);conversations.value=read()}}}
    suspend fun setSkill(id:String,skill:String)=withContext(Dispatchers.IO){mutex.withLock{
        require(skill.isEmpty()||skill==GoutouSkill.ID)
        val c=read().firstOrNull{it.id==id}?:error("对话已删除")
        put(c.copy(skill=skill,updated=System.currentTimeMillis()));conversations.value=read()
    }}
    suspend fun configure(id:String,options:ChatOptions,memory:String)=withContext(Dispatchers.IO){mutex.withLock{
        require(options.instructions.length<=4000&&memory.length<=6000)
        val c=read().firstOrNull{it.id==id}?:error("对话已删除")
        put(c.copy(options=options,memory=memory,updated=System.currentTimeMillis()));conversations.value=read()
    }}
    suspend fun remember(id:String,memory:String)=withContext(Dispatchers.IO){mutex.withLock{
        val c=read().firstOrNull{it.id==id}?:return@withLock
        if(c.options.remember){put(c.copy(memory=memory.take(6000)));conversations.value=read()}
    }}
    suspend fun append(id:String,message:ChatMessage,files:List<File> = emptyList()):ChatMessage=withContext(Dispatchers.IO){mutex.withLock{
        val c=read().firstOrNull{it.id==id}?:error("对话已删除")
        val copied=mutableListOf<File>()
        try{
            require(files.size<=6){"每次最多添加6张图片"}
            folder(id).mkdirs()
            val names=files.map{source->val f=image(id,"${UUID.randomUUID()}.webp");copied.add(f);source.copyTo(f);f.name}
            val m=message.copy(images=message.images+names)
            val title=if(c.title=="新对话"&&m.role=="user")m.text.take(28).ifBlank{"图片对话"}else c.title
            put(c.copy(title=title,updated=System.currentTimeMillis(),messages=c.messages+m));conversations.value=read();m
        }catch(e:Exception){copied.forEach{it.delete()};throw e}
    }}
    suspend fun replace(id:String,message:ChatMessage)=withContext(Dispatchers.IO){mutex.withLock{
        val c=read().firstOrNull{it.id==id}?:return@withLock
        put(c.copy(updated=System.currentTimeMillis(),messages=c.messages.map{if(it.id==message.id)message else it}));conversations.value=read()
    }}
    suspend fun clear(id:String,remove:Boolean)=withContext(Dispatchers.IO){mutex.withLock{
        val c=read().firstOrNull{it.id==id}?:return@withLock
        val dir=folder(id);check(dir.deleteRecursively()||!dir.exists()){"图片清理失败，请重试"}
        if(remove)db.writableDatabase.delete("conversations","id=?",arrayOf(id))else put(c.copy(messages=emptyList(),memory="",title="新对话",updated=System.currentTimeMillis()))
        conversations.value=read()
    }}
    companion object {
        private fun strings(j:JSONObject,key:String)=j.optJSONArray(key)?.let{a->List(a.length()){a.getString(it)}}?:emptyList()
        fun json(c:Conversation)=JSONObject().put("id",c.id).put("title",c.title).put("updated",c.updated).put("skill",c.skill).put("instructions",c.options.instructions).put("remember",c.options.remember).put("continuous",c.options.continuous).put("memory",c.memory).put("messages",JSONArray(c.messages.map{m->JSONObject().put("id",m.id).put("role",m.role).put("text",m.text).put("created",m.created).put("images",JSONArray(m.images)).put("generations",JSONArray(m.generations)).put("kind",m.kind).put("referenceMessage",m.referenceMessage?:JSONObject.NULL)}))
        fun parse(j:JSONObject)=Conversation(j.getString("id"),j.getString("title"),j.getLong("updated"),j.getJSONArray("messages").let{a->List(a.length()){i->val m=a.getJSONObject(i);ChatMessage(m.getString("id"),m.getString("role"),m.getString("text"),m.getLong("created"),strings(m,"images"),strings(m,"generations"),m.optString("kind","text"),if(m.isNull("referenceMessage"))null else m.optString("referenceMessage"))}},j.optString("skill","").takeIf{it==GoutouSkill.ID}.orEmpty(),ChatOptions(j.optString("instructions","").take(4000),j.optBoolean("remember",true),j.optBoolean("continuous",false)),j.optString("memory","").take(6000))
    }
}
