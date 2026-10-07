package cn.yingci.app

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun RoundGlassButton(icon:ImageVector,label:String,onClick:()->Unit,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.onSurface,enabled:Boolean=true,frost:Float?=null,glassKind:Int=0,glassTint:Float?=null){
    IconButton(onClick=onClick,enabled=enabled,modifier=modifier.size(48.dp).clip(CircleShape).liquidGlass(glassKind,24f,circle=true,frost=frost,tint=glassTint)){
        Icon(icon,label,Modifier.size(24.dp),tint=if(enabled)tint else tint.copy(alpha=.4f))
    }
}
@Composable internal fun AttachmentRemove(onClick:()->Unit,modifier:Modifier=Modifier){
    IconButton(onClick=onClick,modifier=modifier.size(44.dp)){
        Box(Modifier.size(23.dp).clip(CircleShape).liquidGlass(0,12f,circle=true),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Close,"移除参考图片",Modifier.size(15.dp))}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ConversationList(vm:AppViewModel){
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val records by vm.generations.collectAsStateWithLifecycle()
    var query by rememberSaveable{mutableStateOf("")}
    var search by rememberSaveable{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<Conversation?>(null)}
    val list=rememberLazyListState()
    val filtered=remember(conversations,query){conversations.filter{it.matches(query.trim())}}
    HeaderOverlay(header={Column{
        Header("最近对话"){
            RoundGlassButton(Icons.Rounded.Search,"查找聊天记录",{search=!search;query=""});Spacer(Modifier.width(8.dp))
            RoundGlassButton(Icons.Rounded.Add,"新建对话",{vm.newConversation()},enabled=!loading)
        }
        if(search)Field(query,{query=it},"查找对话或消息",Modifier.padding(horizontal=22.dp,vertical=8.dp),single=true)
    }}){headerHeight->
        FadingContent(Modifier.fillMaxSize(),top=headerHeight){
            if(loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            else if(filtered.isEmpty())Box(Modifier.fillMaxSize().padding(bottom=100.dp),contentAlignment=Alignment.Center){Hint(if(query.isBlank())"暂无对话"else"没有找到相关记录")}
            else LazyColumn(state=list,contentPadding=PaddingValues(start=22.dp,end=22.dp,top=headerHeight,bottom=120.dp)){
                itemsIndexed(filtered,key={_,c->c.id}){_,c->
                    val record=c.messages.asReversed().flatMap{it.generations}.firstNotNullOfOrNull{id->records.find{it.id==id&&it.image!=null}}
                    Row(Modifier.fillMaxWidth().combinedClickable(onClick={vm.activeConversation.value=c.id},onLongClick={deleting=c}).padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically){
                        if(record!=null)Photo(vm.generationStore.image(record),Modifier.size(52.dp))else Box(Modifier.size(52.dp).liquidGlass(0,26f,circle=true),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ChatBubbleOutline,null,tint=MaterialTheme.colorScheme.primary)}
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)){
                            AppText(c.title,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp));AppText(c.messages.lastOrNull()?.text.orEmpty().replace('\n',' '),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp));Hint(SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(Date(c.updated)))
                        }
                        IconButton(onClick={deleting=c}){Icon(Icons.Rounded.DeleteOutline,"删除对话",Modifier.size(19.dp),tint=Color(0xFFFF3B30))}
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.07f))
                }
            }
        }
    }
    deleting?.let{c->ConversationDeleteDialog({deleting=null}){vm.deleteConversation(c.id,true){deleting=null}}}
}

@Composable private fun ConversationDeleteDialog(dismiss:()->Unit,confirm:()->Unit){
    AlertDialog(onDismissRequest=dismiss,title={AppText("清理这段对话？")},text={AppText("对话、对话记忆、参考图副本和生成图片将一并删除，无法撤销。已保存到相册的图片、收藏提示词和封面不受影响。正在进行的任务需先停止。")},confirmButton={TextButton(onClick=confirm){AppText("确认删除",color=Color(0xFFFF3B30))}},dismissButton={TextButton(onClick=dismiss){AppText("取消")}})
}

@Composable internal fun ChatScreen(vm:AppViewModel,id:String,back:()->Unit,configure:()->Unit){
    MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(primary=Blue,secondary=Blue,onPrimary=Color.White,onSecondary=Color.White)){
        ChatContent(vm,id,back)
    }
}

@Composable private fun ChatContent(vm:AppViewModel,id:String,back:()->Unit){
    val all by vm.conversations.collectAsStateWithLifecycle()
    val records by vm.generations.collectAsStateWithLifecycle()
    val c=all.find{it.id==id}
    if(c==null){LaunchedEffect(id){back()};return}
    val input by vm.chatInput(id).collectAsStateWithLifecycle()
    val chatting by vm.chatting.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val generationDraft by vm.generationDraft.collectAsStateWithLifecycle()
    val settings=LocalPrefs.current
    var menu by remember(id){mutableStateOf(false)}
    var backgroundMenu by remember(id){mutableStateOf(false)}
    var customize by remember(id){mutableStateOf(false)}
    var search by remember(id){mutableStateOf(false)}
    var query by remember(id){mutableStateOf("")}
    var clear by remember(id){mutableStateOf(false)}
    var choosePrompt by remember(id){mutableStateOf(false)}
    var parameters by remember(id){mutableStateOf(false)}
    var proposal by remember(id){mutableStateOf<ChatMessage?>(null)}
    var detailId by remember(id){mutableStateOf<String?>(null)}
    val keyboard=LocalSoftwareKeyboardController.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)){uris->vm.importChatImages(id,uris)}
    val wallpaper=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let(vm::setChatWallpaper)}
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val list=rememberLazyListState()
    val owner=LocalLifecycleOwner.current
    val showingChat=!customize&&!parameters&&proposal==null&&detailId==null
    DisposableEffect(owner,id,showingChat){
        vm.chatVisible(id,showingChat&&owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_START)vm.chatVisible(id,showingChat)
            if(event==Lifecycle.Event.ON_STOP)vm.chatVisible(id,false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer);vm.chatVisible(id,false)}
    }
    var first by remember(id){mutableStateOf(true)}
    val messages=remember(c.messages,query){if(query.isBlank())c.messages else c.messages.filter{it.text.contains(query,true)}}
    LaunchedEffect(messages.size){
        if(messages.isNotEmpty()&&(first||!list.canScrollForward||(list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:0)>=messages.size-4)){list.animateScrollToItem(messages.lastIndex)}
        first=false
    }
    BackHandler{when{menu||backgroundMenu->{menu=false;backgroundMenu=false};customize->customize=false;detailId!=null->detailId=null;else->back()}}
    if(customize){PageGlass(LocalGlass.current?.enabled==true){ConversationOptions(c,{customize=false}){options,memory->vm.configureChat(id,options,memory){customize=false}}};return}
    val detail=records.find{it.id==detailId}
    if(detail!=null){Box(Modifier.fillMaxSize().statusBarsPadding()){GenerationDetail(detail,vm,{detailId=null}){
        vm.importChatImages(id,listOfNotNull(vm.generationStore.image(detail)).map{android.net.Uri.fromFile(it)})
        detailId=null
    }};return}
    if(parameters){
        BackHandler{parameters=false}
        GenerationParametersPanel(generationDraft.copy(references=input.images),settings,false,{parameters=false}){d->vm.generationDraft.value=d.copy(references=emptyList());parameters=false}
        return
    }
    proposal?.let{m->
        BackHandler{proposal=null}
        GenerationParametersPanel(remember(c,m,records,settings,generationDraft){vm.proposalDraft(c,m)},settings,true,{proposal=null}){draft->
            if(Build.VERSION.SDK_INT>=33)notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            vm.confirmChatGeneration(id,m,draft);proposal=null
        }
        return
    }
    val density=LocalDensity.current
    var composerHeight by remember{mutableStateOf(100.dp)}
    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()){
        HeaderOverlay(Modifier.fillMaxSize().behindGlassMenu(includeComposer=true),header={Column{
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
            RoundGlassButton(Icons.AutoMirrored.Rounded.ArrowBack,"返回最近对话",back)
            AppText(if(c.skill==GoutouSkill.ID)"狗头军师"else c.title,Modifier.weight(1f).padding(horizontal=12.dp),fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            RoundGlassButton(Icons.Rounded.MoreHoriz,"对话菜单",{menu=true})
        }
        if(search)Row(Modifier.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){Field(query,{query=it},"查找当前对话",Modifier.weight(1f),single=true);IconButton(onClick={search=false;query=""}){Icon(Icons.Rounded.Close,"关闭查找")}}
        }}){headerHeight->
        FadingContent(Modifier.fillMaxSize().testTag("chat-scroll-surface"),top=headerHeight,bottom=96.dp){
            LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=headerHeight+12.dp,bottom=composerHeight+16.dp)){
                itemsIndexed(messages,key={_,m->m.id}){index,m->
                    val tail=messages.getOrNull(index+1)?.role!=m.role
                    val grouped=messages.getOrNull(index-1)?.role==m.role
                    ChatBubble(m,tail,grouped){
                        val images=vm.messageImages(c,m)
                        images.forEach{file->Photo(file,Modifier.fillMaxWidth().clickable{m.generations.firstOrNull{id->records.find{it.id==id}?.let(vm.generationStore::image)==file}?.let{detailId=it}},large=true,adaptive=true);Spacer(Modifier.height(6.dp))}
                        if(m.kind=="generation"){
                            m.generations.forEach{gid->records.find{it.id==gid}?.let{r->
                                if(r.status!="success"){
                                    AppText(r.label,fontSize=13.sp);if(r.pending)LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=8.dp))
                                    if(r.error.isNotBlank())AppText(r.error,fontSize=12.sp,maxLines=6,overflow=TextOverflow.Ellipsis)
                                }
                                TextButton(onClick={detailId=r.id}){AppText(if(r.status=="success")"查看图片与操作"else"查看任务",fontSize=12.sp)}
                                if(r.pending)TextButton(onClick=vm::stopGeneration){AppText("停止剩余生成",fontSize=12.sp)}
                            }?:AppText("图片记录已删除",fontSize=12.sp)}
                        }else{
                            SelectionContainer{AppText(m.text,color=if(m.role=="user")MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,fontSize=15.sp,lineHeight=23.sp,maxLines=if(m.kind=="proposal")5 else Int.MAX_VALUE,overflow=TextOverflow.Ellipsis)}
                            if(m.kind=="proposal")TextButton(onClick={proposal=m}){Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));AppText("确认生图",fontSize=13.sp)}
                            if(m.kind=="error")TextButton(onClick={vm.retryChat(id)},enabled=chatting==null){AppText("重试回复",fontSize=13.sp)}
                        }
                    }
                }
                if(chatting==id)item{Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp));Hint("正在回复")}}
            }
        }
        }
        Column(Modifier.align(Alignment.BottomCenter).behindGlassMenu().onSizeChanged{composerHeight=with(density){it.height.toDp()}}.navigationBarsPadding()){
        if(input.images.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            input.images.forEach{file->Box(Modifier.size(66.dp)){Photo(file,Modifier.fillMaxSize());AttachmentRemove({vm.removeChatImage(id,file)},Modifier.align(Alignment.TopEnd))}}
            RoundGlassButton(Icons.Rounded.Tune,"生图参数",{parameters=true},Modifier.align(Alignment.CenterVertically))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            RoundGlassButton(Icons.Rounded.Add,"添加图片",{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=busy==null&&input.images.size<6,glassKind=9)
            TextField(input.text,{if(it.length<=32000){vm.stopContinuation(id);vm.chatInput(id).value=input.copy(text=it)}},modifier=Modifier.weight(1f).heightIn(min=56.dp,max=150.dp).clip(RoundedCornerShape(28.dp)).liquidGlass(9,28f,frost=1f,tint=if(LocalDark.current)1.15f else .9f),placeholder={AppText("发送消息",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=15.sp)},maxLines=5,textStyle=MaterialTheme.typography.bodyLarge,shape=RoundedCornerShape(28.dp),colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
            RoundGlassButton(if(chatting==id)Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,if(chatting==id)"停止回复"else"发送",{if(chatting==id)vm.stopChat()else{keyboard?.hide();vm.sendChat(id)}},tint=MaterialTheme.colorScheme.primary,enabled=chatting==id||(chatting==null&&busy==null&&(input.text.isNotBlank()||input.images.isNotEmpty())),glassKind=9)
        }
        }
        if(menu||backgroundMenu)ChatGlassMenu({menu=false;backgroundMenu=false}){
            if(backgroundMenu){
                ChatMenuItem("选择背景图片"){backgroundMenu=false;wallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
                ChatMenuItem("恢复默认背景"){backgroundMenu=false;vm.updatePreferences(vm.preferences.value.copy(chatWallpaper=""))}
                ChatMenuItem("恢复白底背景"){backgroundMenu=false;vm.updatePreferences(vm.preferences.value.copy(chatWallpaper=WhiteChatBackground))}
            }else{
                ChatMenuItem("新建对话"){menu=false;vm.stopContinuation(id);vm.newConversation()}
                ChatMenuItem(if(c.skill==GoutouSkill.ID)"退出狗头军师"else"狗头军师",enabled=chatting==null){menu=false;vm.setChatSkill(id,if(c.skill==GoutouSkill.ID)""else GoutouSkill.ID)}
                ChatMenuItem("查找聊天记录"){menu=false;search=!search;query=""}
                ChatMenuItem("从收藏选择提示词"){menu=false;choosePrompt=true}
                ChatMenuItem("设置聊天背景"){menu=false;backgroundMenu=true}
                ChatMenuItem("对话模型自定义设定"){menu=false;vm.stopContinuation(id);customize=true}
                ChatMenuItem("清空聊天记录",danger=true){menu=false;clear=true}
            }
        }
    }
    if(clear)ConversationDeleteDialog({clear=false}){vm.deleteConversation(id,false){clear=false}}
    if(choosePrompt)AlertDialog(onDismissRequest={choosePrompt=false},title={AppText("选择收藏提示词")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){entries.forEach{e->TextButton(onClick={vm.chatInput(id).value=input.copy(text="请根据以下提示词生成图片：\n${e.current}");choosePrompt=false}){AppText(e.name)}}}},confirmButton={TextButton(onClick={choosePrompt=false}){AppText("关闭")}})
}

@Composable private fun ChatBubble(message:ChatMessage,tail:Boolean,grouped:Boolean,content:@Composable ColumnScope.()->Unit){
    val mine=message.role=="user"
    val density=LocalDensity.current
    val radius=with(density){19.dp.toPx()};val tip=with(density){7.dp.toPx()}
    val shape=remember(mine,tail,radius,tip){GenericShape{size,_->
        val left=if(mine)0f else tip;val right=if(mine)size.width-tip else size.width
        val r=minOf(radius,size.height/2,(right-left)/2)
        moveTo(left+r,0f);lineTo(right-r,0f);quadraticTo(right,0f,right,r);lineTo(right,size.height-r)
        if(mine&&tail){quadraticTo(right,size.height-tip,right+tip,size.height);quadraticTo(right-r*.4f,size.height,right-r,size.height)}else quadraticTo(right,size.height,right-r,size.height)
        lineTo(left+r,size.height)
        if(!mine&&tail){quadraticTo(left+r*.4f,size.height,left-tip,size.height);quadraticTo(left,size.height-tip,left,size.height-r)}else quadraticTo(left,size.height,left,size.height-r)
        lineTo(left,r);quadraticTo(left,0f,left+r,0f);close()
    }}
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val maxBubble=maxWidth*.86f
        Row(Modifier.fillMaxWidth().padding(top=if(grouped)4.dp else 15.dp),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){
            Column(Modifier.widthIn(max=maxBubble).clip(shape).then(if(mine)Modifier.background(MaterialTheme.colorScheme.primary,shape)else Modifier.liquidGlass(7,20f,shape=shape)).border(.7.dp,MaterialTheme.colorScheme.onSurface.copy(alpha=.12f),shape).padding(start=if(mine)14.dp else 21.dp,end=if(mine)21.dp else 14.dp,top=11.dp,bottom=11.dp)){
                CompositionLocalProvider(LocalNativeSurface provides true){content()}
            }
        }
    }
}

@Composable internal fun GenerationParametersPanel(initial:GenerationDraft,p:Preferences,confirm:Boolean,dismiss:()->Unit,accept:(GenerationDraft)->Unit){
    val cap=remember(p.baseUrl,p.imageModel){imageCapability(p)}
    var draft by remember(initial){mutableStateOf(initial.copy(ratio=initial.ratio.takeIf{it in cap.ratios}?:cap.ratios.firstOrNull().orEmpty(),quality=initial.quality.takeIf{it in cap.qualities}?:cap.qualities.firstOrNull().orEmpty(),format=initial.format.takeIf{it in cap.formats}?:cap.formats.firstOrNull().orEmpty()))}
    Box(Modifier.fillMaxSize().liquidGlass(7,0f,frost=1f,tint=1.3f)){
    // Keep foreground text and thumbnails out of the full-page glass sample.
    CompositionLocalProvider(LocalNativeSurface provides true){
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()){
    Header(if(confirm)"确认生图"else"生图参数",back=dismiss)
    if(confirm)Field(draft.prompt,{if(it.length<=32000)draft=draft.copy(prompt=it)},"提示词",Modifier.padding(horizontal=22.dp).height(136.dp).semantics{contentDescription="生图提示词"})
    Spacer(Modifier.height(16.dp))
    FadingContent(Modifier.weight(1f),top=12.dp,bottom=16.dp){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(confirm)Hint(p.imageModel.ifBlank{"尚未配置生图模型"})
        if(draft.references.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){draft.references.forEach{f->Box(Modifier.size(66.dp)){Photo(f,Modifier.fillMaxSize());AttachmentRemove({draft=draft.copy(references=draft.references-f)},Modifier.align(Alignment.TopEnd))}}}
        if(cap.ratios.isNotEmpty())Choice("画面比例",draft.ratio,cap.ratios){draft=draft.copy(ratio=it)}
        if(cap.qualities.isNotEmpty())Choice("清晰度",draft.quality,cap.qualities){draft=draft.copy(quality=it)}
        Choice("生成张数",draft.count.toString(),listOf("1","2","3","4")){draft=draft.copy(count=it.toInt())}
        if(draft.references.size>1)Row(verticalAlignment=Alignment.CenterVertically){AppText("分别生成",Modifier.weight(1f));Switch(draft.separate,{draft=draft.copy(separate=it)})}
        if(cap.formats.isNotEmpty())Choice("图片格式",draft.format,cap.formats){draft=draft.copy(format=it)}
        if(draft.references.isNotEmpty()&&!cap.references)Hint("当前生图模型尚未适配参考图，请更换模型或移除参考图。")
        if(confirm)Hint("将发送提示词与 ${draft.references.size} 张参考图，共生成 ${draft.batches().size} 张。服务商可能收费，失败不会自动重试。")
    }}
    Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=12.dp),horizontalArrangement=Arrangement.End){
        TextButton(onClick=dismiss){AppText("取消")}
        TextButton(onClick={accept(draft)},enabled=!confirm||(draft.prompt.isNotBlank()&&p.imageModel.isNotBlank()&&p.apiKey.isNotBlank()&&(draft.references.isEmpty()||cap.references))){AppText(if(confirm)"确认生成"else"保存")}
    }
    }
    }
    }
}
