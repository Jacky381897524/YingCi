package cn.yingci.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AppViewModel(application: Application): AndroidViewModel(application) {
    val repository=Repository(application)
    private val vision=VisionClient()
    val entries=MutableStateFlow<List<PromptEntry>>(emptyList())
    val preferences=MutableStateFlow(Preferences())
    val loading=MutableStateFlow(true)
    val busy=MutableStateFlow<String?>(null)
    val notice=MutableStateFlow<String?>(null)
    internal val reverseMode=MutableStateFlow(false)
    private val reverseStates=mapOf(false to ReverseWorkspace(),true to ReverseWorkspace())
    val analyzing get()=reverseStates.getValue(reverseMode.value).analyzing
    val analysis get()=reverseStates.getValue(reverseMode.value).analysis
    val reference get()=reverseStates.getValue(reverseMode.value).reference
    internal val reverseWorkspace get()=reverseStates.getValue(reverseMode.value)
    internal val generationStore=GenerationStore.get(application)
    internal val generations=generationStore.records
    internal val generationDraft=MutableStateFlow(GenerationDraft())
    internal val chatStore=ChatStore(application)
    internal val conversations=chatStore.conversations
    internal val activeConversation=MutableStateFlow<String?>(null)
    internal val chatting=MutableStateFlow<String?>(null)
    internal val chatInputs=mutableMapOf<String,MutableStateFlow<ChatInput>>()
    private val chatClient=ChatClient(vision,GoutouSkill(application.assets))
    private var chatJob:Job?=null
    internal val continuing=MutableStateFlow<String?>(null)
    private var visibleChat:String?=null
    private var analysisJob:Job?=null
    private var connectionJob:Job?=null
    private var connectionRevision=0L
    private var analysisRevision=0L
    val connectionTesting=MutableStateFlow(false)
    val visionStatus=MutableStateFlow("未测试")
    val chatStatus=MutableStateFlow("未测试")
    val generationStatus=MutableStateFlow("未测试")
    val availableModels=MutableStateFlow<List<String>>(emptyList())
    val modelsLoading=MutableStateFlow(false)
    val modelsStatus=MutableStateFlow("")
    private var modelsJob:Job?=null
    private var modelsRevision=0L
    private val prefsLock=Mutex()
    init { viewModelScope.launch { try{preferences.value=withContext(Dispatchers.IO){repository.chatPreferences(repository.preferences())};entries.value=repository.all();generationStore.initialize();chatStore.initialize(generations.value);repository.cleanup()}catch(e:Exception){notice.value="读取本机数据失败，请重试"}finally{loading.value=false} } }
    fun message(value:String){notice.value=value}
    private fun action(label:String,block:suspend()->Unit){if(busy.value!=null)return;busy.value=label;viewModelScope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){notice.value=e.message?.take(180) ?: "操作未完成，请重试"}finally{busy.value=null}}}
    fun importImage(uri:Uri,onReady:(File)->Unit){action("正在整理图片"){onReady(repository.importImage(uri))}}
    fun updatePreferences(value:Preferences){preferences.value=value;viewModelScope.launch{prefsLock.withLock{try{repository.savePreferences(preferences.value)}catch(e:Exception){notice.value="设置未能保存，请重试"}}}}
    fun savePreferences(value:Preferences,onDone:()->Unit){action("正在保存设置"){prefsLock.withLock{repository.savePreferences(value);preferences.value=value};notice.value="设置已保存";onDone()}}
    fun saveExtracted(name:String,text:String,image:File?,original:String,onDone:(PromptEntry)->Unit){action("正在保存"){val item=repository.save(null,name,text,image,original);entries.value=repository.all();onDone(item);notice.value="已保存到本机"}}
    internal fun saveReverse(name:String,text:String,image:File?,original:String,onDone:(PromptEntry)->Unit){action("正在保存"){
        val copy=image?.let{repository.importImage(Uri.fromFile(it))}
        try{val item=repository.save(null,name,text,copy,original);entries.value=repository.all();onDone(item);notice.value="已保存到本机"}finally{repository.discardDraft(copy)}
    }}
    fun save(id:String?,name:String,text:String,image:File?,onDone:(PromptEntry)->Unit){action("正在保存"){val item=repository.save(id,name,text,image);entries.value=repository.all();onDone(item);notice.value="已保存到本机"}}
    fun delete(entry:PromptEntry,onDone:()->Unit){action("正在删除"){repository.delete(entry);entries.value=repository.all();onDone();notice.value="已删除"}}
    fun backup(uri:Uri){action("正在导出备份"){val n=repository.export(uri);notice.value="已导出 $n 条提示词及封面"}}
    fun restore(uri:Uri){action("正在恢复备份"){val n=repository.restore(uri);entries.value=repository.all();notice.value="已恢复 $n 条，不覆盖本机现有条目"}}
    fun clearCache(){action("正在清理缓存"){repository.clearCache();ImageMemory.clear();notice.value="缓存已清理，提示词与封面仍保留"}}
    internal fun generationPrompt(text:String){generationDraft.value=generationDraft.value.copy(prompt=text)}
    internal fun reuseGeneration(record:GenerationRecord,onReady:()->Unit){action("正在准备参考图"){
        val copied=mutableListOf<File>()
        try{
            for(name in record.inputs)copied.add(repository.importImage(Uri.fromFile(generationStore.file(record.id,name))))
            val cap=imageCapability(preferences.value)
            generationDraft.value.references.forEach(repository::discardDraft)
            generationDraft.value=GenerationDraft(record.prompt,copied.toList(),ratio=record.ratio.takeIf{it in cap.ratios}?:cap.ratios.firstOrNull().orEmpty(),quality=record.quality.takeIf{it in cap.qualities}?:cap.qualities.firstOrNull().orEmpty(),format=record.format.takeIf{it in cap.formats}?:cap.formats.firstOrNull().orEmpty())
            onReady()
        }catch(e:Exception){copied.forEach(repository::discardDraft);throw e}
    }}
    internal fun importGenerationImages(uris:List<Uri>){action("正在整理参考图"){
        val files=generationDraft.value.references.toMutableList()
        for(uri in uris.take(6-files.size)){files.add(repository.importImage(uri));generationDraft.value=generationDraft.value.copy(references=files.toList())}
    }}
    internal fun removeGenerationImage(file:File){repository.discardDraft(file);val files=generationDraft.value.references-file;generationDraft.value=generationDraft.value.copy(references=files,separate=files.size>1&&generationDraft.value.separate)}
    internal fun generate(draft:GenerationDraft=generationDraft.value){action("正在提交生成任务"){
        require(!GenerationService.running.value){"请等待当前任务结束"}
        generationStore.initialize();generationStore.enqueue(preferences.value,draft)
        try{androidx.core.content.ContextCompat.startForegroundService(getApplication(),android.content.Intent(getApplication(),GenerationService::class.java));notice.value="已提交；结果将自动保存在生成记录"}
        catch(e:Exception){generationStore.interrupt();throw IllegalStateException("无法启动后台生成，请返回应用前台后重试")}
    }}
    internal fun retryGeneration(record:GenerationRecord){action("正在重新提交"){
        require(!GenerationService.running.value){"请等待当前任务结束"}
        val p=preferences.value;require(ApiEndpoint.images(p.baseUrl)==ApiEndpoint.images(record.baseUrl)){"服务地址已改变，请从生图页重新生成"}
        val draft=GenerationDraft(record.prompt,record.inputs.map{generationStore.file(record.id,it)},ratio=record.ratio,quality=record.quality,format=record.format)
        val conversation=record.conversationId?:conversations.value.firstOrNull{c->c.messages.any{record.id in it.generations}}?.id
        val created=generationStore.enqueue(p.copy(imageModel=record.model),draft,conversation)
        if(conversation!=null)chatStore.append(conversation,ChatMessage(role="assistant",text=draft.prompt,generations=created.map{it.id},kind="generation"))
        try{androidx.core.content.ContextCompat.startForegroundService(getApplication(),android.content.Intent(getApplication(),GenerationService::class.java))}catch(e:Exception){generationStore.interrupt();throw e}
    }}
    internal fun stopGeneration(){getApplication<Application>().stopService(android.content.Intent(getApplication(),GenerationService::class.java));viewModelScope.launch{generationStore.interrupt()}}
    internal fun deleteGenerations(ids:Set<String>){action("正在清理生成记录"){generationStore.delete(ids);ImageMemory.clear();notice.value="记录及对应图片已删除，收藏封面仍保留"}}
    internal fun exportGeneration(record:GenerationRecord){action("正在保存到相册"){generationStore.export(record);notice.value="已保存至相册 · 映词"}}
    internal fun collectGeneration(record:GenerationRecord,name:String,entry:PromptEntry?=null){action("正在保存收藏"){
        require(entry!=null||validNewName(name)){"收藏名称需为1至30个字，不能换行"}
        val image=generationStore.image(record)?:error("图片已不存在")
        val draft=repository.importImage(Uri.fromFile(image))
        try{repository.save(entry?.id,entry?.name?:name,entry?.current?:record.prompt,draft);entries.value=repository.all();notice.value=if(entry==null)"图片与提示词已收藏"else"封面已更换，提示词保持不变"}finally{repository.discardDraft(draft)}
    }}
    fun cancelModels(){modelsRevision++;modelsJob?.cancel();modelsLoading.value=false}
    fun resetModels(){cancelModels();availableModels.value=emptyList();modelsStatus.value=""}
    fun loadModels(value:Preferences){
        cancelModels()
        val revision=modelsRevision
        availableModels.value=emptyList();modelsLoading.value=true;modelsStatus.value="正在读取模型列表"
        modelsJob=viewModelScope.launch{
            try{
                val models=vision.models(value)
                if(revision==modelsRevision){
                    availableModels.value=models
                    modelsStatus.value=if(models.isEmpty())"服务端返回空列表，请检查密钥权限或手动填写模型 ID"else"已读取 ${models.size} 个模型，能力尚未验证"
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(revision==modelsRevision)modelsStatus.value=(e.message?.take(1000)?:"模型列表读取失败")+"\n可手动填写服务商提供的模型 ID；列表读取失败不代表聊天不可用。"}
            finally{if(revision==modelsRevision)modelsLoading.value=false}
        }
    }
    fun resetConnectionResults(){cancelConnectionTest();visionStatus.value="未测试";generationStatus.value="未测试";chatStatus.value="未测试"}
    fun cancelConnectionTest(){connectionRevision++;connectionJob?.cancel();connectionTesting.value=false;listOf(visionStatus,generationStatus,chatStatus).forEach{if(it.value=="正在测试")it.value="已取消"}}
    fun testConnection(value:Preferences)=testModel(visionStatus,"图片识别验证通过"){vision.test(value)}
    fun testChatConnection(value:Preferences)=testModel(chatStatus,"聊天连接通过，已收到文字回复"){vision.testChat(value)}
    fun testImageConnection(value:Preferences)=testModel(generationStatus,"已收到有效图片"){vision.testGeneration(value)}
    private fun testModel(status:MutableStateFlow<String>,success:String,block:suspend()->Unit){
        if(connectionTesting.value)return
        val revision=++connectionRevision
        status.value="正在测试";connectionTesting.value=true
        connectionJob=viewModelScope.launch{try{block();if(revision==connectionRevision)status.value=success}catch(e:CancellationException){throw e}catch(e:Exception){if(revision==connectionRevision)status.value=e.message?.take(1000)?:"连接失败"}finally{if(revision==connectionRevision)connectionTesting.value=false}}
    }
    fun setReference(file:File){if(analyzing.value)return;repository.discardDraft(reference.value);reference.value=file;analysis.value=null;reverseWorkspace.name.value="";reverseWorkspace.prompt.value=""}
    internal fun switchReverse(){reverseMode.value=!reverseMode.value}
    fun analyze(){if(analyzing.value)return;val file=reference.value ?: return message("请先选择参考图");val p=preferences.value
        if(p.apiKey.isBlank()||p.baseUrl.isBlank()||p.model.isBlank())return message("请先在设置中配置服务地址、API Key 和视觉模型")
        val state=reverseWorkspace;val faithful=reverseMode.value
        state.analyzing.value=true;state.analysis.value=null
        val revision=++analysisRevision
        state.job=viewModelScope.launch{try{val result=vision.analyze(p,file,faithful);state.analysis.value=result;state.name.value=result.name;state.prompt.value=result.prompt}catch(e:CancellationException){throw e}catch(e:Exception){notice.value=e.message?.take(180) ?: "反推未完成，请重试"}finally{state.analyzing.value=false}}
    }
    fun cancelAnalysis(){reverseWorkspace.job?.cancel();analyzing.value=false;notice.value="已取消反推"}

    internal fun chatInput(id:String)=chatInputs.getOrPut(id){MutableStateFlow(ChatInput())}
    internal fun chatVisible(id:String,visible:Boolean){
        if(visible)visibleChat=id else if(visibleChat==id){visibleChat=null;stopContinuation(id)}
    }
    internal fun stopContinuation(id:String){if(continuing.value==id)stopChat()}
    internal fun configureChat(id:String,options:ChatOptions,memory:String,done:()->Unit){
        stopChat()
        action("正在保存对话设定"){
            chatJob?.join()
            chatStore.configure(id,options,memory);done()
        }
    }
    internal fun setChatSkill(id:String,skill:String){
        if(chatting.value!=null)return message("请等待当前回复完成")
        action("正在切换对话模式"){
            chatStore.setSkill(id,skill)
            if(skill==GoutouSkill.ID&&conversations.value.first{it.id==id}.messages.isEmpty())chatStore.append(id,ChatMessage(role="assistant",text=GoutouSkill.WELCOME))
        }
    }
    internal fun newConversation(prompt:String="",images:List<File> = emptyList()){action("正在新建对话"){
        val c=chatStore.create();val copied=images.map{repository.importImage(Uri.fromFile(it))}
        chatInput(c.id).value=ChatInput(prompt,copied);activeConversation.value=c.id
    }}
    internal fun importChatImages(id:String,uris:List<Uri>){action("正在整理图片"){
        val state=chatInput(id);val files=state.value.images.toMutableList()
        for(uri in uris.take(6-files.size)){files.add(repository.importImage(uri));state.value=state.value.copy(images=files.toList())}
    }}
    internal fun removeChatImage(id:String,file:File){repository.discardDraft(file);val state=chatInput(id);state.value=state.value.copy(images=state.value.images-file)}
    internal fun messageImages(c:Conversation,m:ChatMessage):List<File> = (m.images.map{chatStore.image(c.id,it)}+m.generations.mapNotNull{id->generations.value.find{it.id==id}?.let(generationStore::image)}).filter{it.isFile}
    internal fun sendChat(id:String){
        if(chatting.value!=null)return message("请等待当前回复完成")
        val input=chatInput(id).value
        if(input.text.isBlank()&&input.images.isEmpty())return
        val p=preferences.value.chatConnection()
        if(p.apiKey.isBlank()||p.model.isBlank()||p.baseUrl.isBlank())return message("请先配置识图模型或独立聊天模型")
        chatting.value=id
        chatJob=viewModelScope.launch{
            try{
                chatStore.append(id,ChatMessage(role="user",text=input.text.ifBlank{"请描述这张图片"}),input.images)
                input.images.forEach(repository::discardDraft);chatInput(id).value=ChatInput()
                replyToConversation(id)
            }catch(e:CancellationException){withContext(NonCancellable){chatStore.append(id,ChatMessage(role="assistant",text="已停止回复",kind="cancelled"))};throw e}
            catch(e:Exception){chatStore.append(id,ChatMessage(role="assistant",text=e.message?.take(1200)?:"回复失败，请重试",kind="error"))}
            finally{chatting.value=null;continuing.value=null}
        }
    }
    private suspend fun replyToConversation(id:String){
        chatTurns(allowed={conversations.value.firstOrNull{it.id==id}?.let{canContinue(it,id)}==true},waiting={continuing.value=id}){extra->
            val c=conversations.value.first{it.id==id}
            val reply=chatClient.reply(preferences.value,c,continuation=extra){messageImages(c,it)}
            currentCoroutineContext().ensureActive()
            if(extra&&!canContinue(c,id))return@chatTurns false
            if(!extra&&reply.text.isBlank()&&reply.imagePrompt==null)error("模型没有返回文字，请重试")
            if(reply.text.isNotBlank())chatStore.append(id,ChatMessage(role="assistant",text=reply.text))
            if(!extra)reply.imagePrompt?.let{chatStore.append(id,ChatMessage(role="assistant",text=it,kind="proposal",referenceMessage=reply.referenceMessage))}
            reply.memory?.let{chatStore.remember(id,it)}
            reply.more&&reply.imagePrompt==null&&reply.text.isNotBlank()&&canContinue(c,id)
        }
        continuing.value=null
    }
    private fun canContinue(c:Conversation,id:String)=c.options.continuous&&visibleChat==id&&activeConversation.value==id
    internal fun retryChat(id:String){
        if(chatting.value!=null)return
        chatting.value=id
        chatJob=viewModelScope.launch{try{replyToConversation(id)}catch(e:CancellationException){throw e}catch(e:Exception){message(e.message?:"回复失败")}finally{chatting.value=null;continuing.value=null}}
    }
    internal fun stopChat(){chatJob?.cancel()}
    internal fun proposalDraft(c:Conversation,m:ChatMessage):GenerationDraft {
        val ref=c.messages.find{it.id==m.referenceMessage}?.let{messageImages(c,it)}.orEmpty().take(6)
        val cap=imageCapability(preferences.value)
        val previous=generationDraft.value
        return previous.copy(prompt=m.text,references=ref,ratio=previous.ratio.takeIf{it in cap.ratios}?:cap.ratios.firstOrNull().orEmpty(),quality=previous.quality.takeIf{it in cap.qualities}?:cap.qualities.firstOrNull().orEmpty(),format=previous.format.takeIf{it in cap.formats}?:cap.formats.firstOrNull().orEmpty(),separate=previous.separate&&ref.size>1)
    }
    internal fun confirmChatGeneration(id:String,m:ChatMessage,draft:GenerationDraft){action("正在提交生成任务"){
        require(conversations.value.any{it.id==id}){"对话已删除"}
        require(conversations.value.first{it.id==id}.messages.any{it.id==m.id&&it.kind=="proposal"}){"该生图方案已提交"}
        require(!GenerationService.running.value){"请等待当前生成任务结束"}
        val records=generationStore.enqueue(preferences.value,draft,id)
        chatStore.replace(id,m.copy(kind="generation",text=draft.prompt,generations=records.map{it.id}))
        generationDraft.value=draft.copy(references=emptyList(),prompt="")
        try{androidx.core.content.ContextCompat.startForegroundService(getApplication(),android.content.Intent(getApplication(),GenerationService::class.java))}
        catch(e:Exception){generationStore.interrupt();throw IllegalStateException("后台生成未能启动，请手动重试")}
    }}
    internal fun deleteConversation(id:String,remove:Boolean,onDone:()->Unit={}){action("正在清理对话"){
        require(chatting.value!=id){"请先停止当前回复"}
        val c=conversations.value.firstOrNull{it.id==id}?:return@action
        val ids=c.messages.flatMap{it.generations}.toSet()+generations.value.filter{it.conversationId==id}.map{it.id}
        generationStore.delete(ids)
        chatStore.clear(id,remove)
        chatInputs.remove(id)?.value?.images?.forEach(repository::discardDraft)
        if(remove&&activeConversation.value==id)activeConversation.value=null
        ImageMemory.clear();onDone()
    }}
    internal fun setChatWallpaper(uri:Uri){action("正在保存聊天背景"){
        val draft=repository.importImage(uri)
        prefsLock.withLock{val name=repository.saveWallpaper(draft);val p=preferences.value.copy(chatWallpaper=name);repository.savePreferences(p);preferences.value=p}
    }}
}

internal class ReverseWorkspace {
    val reference=MutableStateFlow<File?>(null)
    val analysis=MutableStateFlow<StyleResult?>(null)
    val analyzing=MutableStateFlow(false)
    val name=MutableStateFlow("")
    val prompt=MutableStateFlow("")
    var job:Job?=null
}
internal data class ChatInput(val text:String="",val images:List<File> = emptyList())
