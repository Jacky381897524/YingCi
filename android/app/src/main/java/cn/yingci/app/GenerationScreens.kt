package cn.yingci.app

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable internal fun Choice(label:String,value:String,options:List<String>,modifier:Modifier=Modifier,onChange:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Column(modifier){
        Hint(label)
        Box{TextButton(onClick={expanded=true},enabled=options.isNotEmpty(),contentPadding=PaddingValues(horizontal=4.dp)){
            AppText(value.ifBlank{"默认"},maxLines=1,fontSize=14.sp);Icon(Icons.Rounded.ExpandMore,null,Modifier.size(18.dp))
        };DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{option->DropdownMenuItem(text={AppText(option)},onClick={expanded=false;onChange(option)})}}}
    }
}

@Composable internal fun GenerationScreen(vm:AppViewModel,configure:()->Unit,historyInitially:Boolean=false,historyRequest:Int=0){
    val draft by vm.generationDraft.collectAsStateWithLifecycle()
    val records by vm.generations.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val running by GenerationService.running.collectAsStateWithLifecycle()
    val p=LocalPrefs.current;val cap=remember(p.baseUrl,p.imageModel){imageCapability(p)}
    var history by rememberSaveable{mutableStateOf(historyInitially)}
    LaunchedEffect(historyInitially,historyRequest){if(historyInitially)history=true}
    var recordId by rememberSaveable{mutableStateOf<String?>(null)}
    var choosePrompt by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    var advanced by rememberSaveable{mutableStateOf(false)}
    val entries by vm.entries.collectAsStateWithLifecycle()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)){vm.importGenerationImages(it)}
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(cap){vm.generationDraft.value=draft.copy(ratio=draft.ratio.takeIf{it in cap.ratios}?:cap.ratios.firstOrNull().orEmpty(),quality=draft.quality.takeIf{it in cap.qualities}?:cap.qualities.firstOrNull().orEmpty(),format=draft.format.takeIf{it in cap.formats}?:cap.formats.firstOrNull().orEmpty())}
    BackHandler(recordId!=null||history){if(recordId!=null)recordId=null else history=false}
    val selected=records.firstOrNull{it.id==recordId}
    if(recordId!=null&&selected!=null){GenerationDetail(selected,vm,{recordId=null},{vm.reuseGeneration(selected){recordId=null;history=false}});return}
    if(history){GenerationHistory(vm,{history=false},{recordId=it.id});return}
    Column(Modifier.fillMaxSize().imePadding()){
        Header("生图"){RoundGlassButton(Icons.Rounded.History,"生成记录",{history=true})}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            // Reference images intentionally precede the prompt; mode is inferred from their presence.
            if(draft.references.isEmpty()){
                OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=busy==null,modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Rounded.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));AppText("添加图片");Spacer(Modifier.weight(1f));Hint("可选")
                }
            }else{
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    draft.references.forEach{file->Box(Modifier.size(88.dp)){
                        Photo(file,Modifier.fillMaxSize())
                        AttachmentRemove({vm.removeGenerationImage(file)},Modifier.align(Alignment.TopEnd))
                    }}
                    if(draft.references.size<6)IconButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=busy==null,modifier=Modifier.size(88.dp).liquidGlass(0,8f)){Icon(Icons.Rounded.AddPhotoAlternate,"添加图片")}
                }
                if(draft.references.size>1)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){AppText("分别生成",Modifier.weight(1f));Switch(draft.separate,{vm.generationDraft.value=draft.copy(separate=it)})}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){AppText("提示词",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);TextButton(onClick={choosePrompt=true}){AppText("从收藏选择",fontSize=13.sp)}}
            Field(draft.prompt,{if(it.length<=32000)vm.generationDraft.value=draft.copy(prompt=it)},"描述想要的画面",Modifier.heightIn(min=140.dp,max=280.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Hint("模型",Modifier.weight(1f));TextButton(onClick=configure,modifier=Modifier.weight(3f)){AppText(p.imageModel.ifBlank{"配置生图模型"},maxLines=2,fontSize=13.sp)}}
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(cap.ratios.isNotEmpty())Choice("画面比例",draft.ratio,cap.ratios,Modifier.weight(1f)){vm.generationDraft.value=draft.copy(ratio=it)}
                if(cap.qualities.isNotEmpty())Choice("清晰度",draft.quality,cap.qualities,Modifier.weight(1f)){vm.generationDraft.value=draft.copy(quality=it)}
                Choice(if(draft.separate&&draft.references.size>1)"每张输出"else"生成张数",draft.count.toString(),listOf("1","2","3","4"),Modifier.weight(1f)){vm.generationDraft.value=draft.copy(count=it.toInt())}
            }
            TextButton(onClick={advanced=!advanced},modifier=Modifier.fillMaxWidth()){
                AppText("更多参数",Modifier.weight(1f),fontSize=13.sp);Icon(if(advanced)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null)
            }
            if(advanced){if(cap.formats.isNotEmpty())Choice("返回格式",draft.format,cap.formats){vm.generationDraft.value=draft.copy(format=it)}else Hint("当前模型未开放更多已适配参数。")}
            if(draft.references.isNotEmpty()&&!cap.references)Hint("当前模型的参考图接口尚未适配，请更换 Agnes Image 2.1/2.5 Flash 或 gpt-image-1。")
            val pending=records.count{it.pending}
            Primary(if(pending>0)"正在生成 · $pending 项"else"生成图片 · ${draft.batches().size} 张",Icons.Rounded.AutoAwesome,enabled=draft.prompt.isNotBlank()&&busy==null&&pending==0&&!running&&(draft.references.isEmpty()||cap.references),modifier=Modifier.fillMaxWidth()){confirm=true}
            if(pending>0){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=vm::stopGeneration){AppText("停止剩余任务")}}
            Hint("图片与提示词将发送至你配置的模型服务，可能产生费用。结果保存在本机。")
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){AppText("最近生成",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);TextButton(onClick={history=true}){AppText("全部")}}
            records.take(4).chunked(2).forEach{pair->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){pair.forEach{record->GenerationTile(record,vm,Modifier.weight(1f)){recordId=record.id}};if(pair.size==1)Spacer(Modifier.weight(1f))}}
            Spacer(Modifier.height(120.dp))
        }
    }
    if(choosePrompt)AlertDialog(onDismissRequest={choosePrompt=false},title={AppText("选择收藏提示词")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){if(entries.isEmpty())AppText("暂无收藏");entries.forEach{entry->TextButton(onClick={vm.generationPrompt(entry.current);choosePrompt=false},modifier=Modifier.fillMaxWidth()){AppText(entry.name,maxLines=2)}}}},confirmButton={TextButton(onClick={choosePrompt=false}){AppText("关闭")}})
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={AppText("生成 ${draft.batches().size} 张图片？")},text={AppText("将向 ${runCatching{ApiEndpoint.images(p.baseUrl).host}.getOrDefault("配置的模型服务")} 发送提示词和 ${draft.references.size} 张参考图。服务商可能收费；失败不会自动重试。")},confirmButton={TextButton(onClick={confirm=false;if(Build.VERSION.SDK_INT>=33)notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS);vm.generate()}){AppText("开始生成")}},dismissButton={TextButton(onClick={confirm=false}){AppText("取消")}})
}

@Composable private fun GenerationTile(record:GenerationRecord,vm:AppViewModel,modifier:Modifier=Modifier,open:()->Unit){
    Column(modifier.clickable(onClick=open)){
        Photo(vm.generationStore.image(record),Modifier.fillMaxWidth().aspectRatio(.75f))
        AppText(record.label,Modifier.padding(top=8.dp),fontSize=13.sp,maxLines=1)
        if(record.pending)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=5.dp))
    }
}

@Composable internal fun GenerationHistory(vm:AppViewModel,back:()->Unit,open:(GenerationRecord)->Unit){
    val records by vm.generations.collectAsStateWithLifecycle()
    var selection by remember{mutableStateOf(false)};var selected by remember{mutableStateOf(setOf<String>())};var confirm by remember{mutableStateOf<Set<String>?>(null)}
    Column(Modifier.fillMaxSize()){
        Header("生成记录",back=back){IconButton(onClick={selection=!selection;selected=emptySet()}){Icon(if(selection)Icons.Rounded.Close else Icons.Rounded.Checklist,if(selection)"结束选择"else"选择记录")}}
        Row(Modifier.fillMaxWidth().padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically){Hint(if(selection)"已选 ${selected.size} 张"else"${records.size} 条记录",Modifier.weight(1f));if(selection){TextButton(onClick={selected=records.filterNot{it.pending}.map{it.id}.toSet()}){AppText("全选")};IconButton(onClick={if(selected.isNotEmpty())confirm=selected},enabled=selected.isNotEmpty()){Icon(Icons.Rounded.DeleteOutline,"删除所选")}}else TextButton(onClick={confirm=records.filterNot{it.pending}.map{it.id}.toSet()},enabled=records.any{!it.pending}){AppText("清空",color=MaterialTheme.colorScheme.error)}}
        if(records.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Hint("暂无生成记录")}
        else LazyVerticalGrid(GridCells.Fixed(2),contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=120.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            items(records,key={it.id}){r->Box{GenerationTile(r,vm){if(selection&&!r.pending)selected=if(r.id in selected)selected-r.id else selected+r.id else if(!selection)open(r)};if(selection)Checkbox(r.id in selected,{checked->selected=if(checked)selected+r.id else selected-r.id},enabled=!r.pending,modifier=Modifier.align(Alignment.TopEnd))}}
        }
    }
    confirm?.let{ids->AlertDialog(onDismissRequest={confirm=null},title={AppText("删除 ${ids.size} 条记录？")},text={AppText("对应生成图片和参考图副本将一并清理。已经存为收藏封面的图片不会被删除。")},confirmButton={TextButton(onClick={vm.deleteGenerations(ids);selected=emptySet();selection=false;confirm=null}){AppText("删除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={confirm=null}){AppText("取消")}})}
}

@Composable internal fun GenerationDetail(record:GenerationRecord,vm:AppViewModel,back:()->Unit,reuse:()->Unit){
    var menu by remember{mutableStateOf(false)};var action by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var target by remember{mutableStateOf<PromptEntry?>(null)}
    val clipboard=LocalClipboardManager.current;val entries by vm.entries.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()){
        Header("生成结果",back=back){Box{IconButton(onClick={menu=true}){Icon(Icons.Rounded.MoreHoriz,"更多操作")};DropdownMenu(menu,{menu=false}){
            if(record.status=="success"){
                DropdownMenuItem(text={AppText("设为已有收藏封面")},onClick={menu=false;action="cover"})
                DropdownMenuItem(text={AppText("图片与提示词存为新收藏")},onClick={menu=false;action="collect"})
                DropdownMenuItem(text={AppText("保存到相册")},onClick={menu=false;vm.exportGeneration(record)})
            }
            DropdownMenuItem(text={AppText("复制提示词")},onClick={menu=false;clipboard.setText(AnnotatedString(record.prompt));vm.message("已复制提示词")})
            if(!record.pending)DropdownMenuItem(text={AppText("删除记录",color=MaterialTheme.colorScheme.error)},onClick={menu=false;action="delete"})
        }}}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            if(record.image!=null)Photo(vm.generationStore.image(record),Modifier.fillMaxWidth(),large=true,adaptive=true)
            AppText(record.label,fontWeight=FontWeight.SemiBold)
            if(record.pending){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=vm::stopGeneration){AppText("停止剩余任务")}}
            if(record.error.isNotBlank())Hint(record.error)
            if(record.status in listOf("failed","unknown","stopped"))Primary("手动重试",Icons.Rounded.Refresh,enabled=busy==null){action="retry"}
            if(record.status=="success")Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Primary("保存相册",Icons.Rounded.Download,modifier=Modifier.weight(1f),enabled=busy==null){vm.exportGeneration(record)}
                Primary("再次使用",Icons.Rounded.Refresh,modifier=Modifier.weight(1f),onClick=reuse)
            }
            AppText("提示词",fontWeight=FontWeight.SemiBold);AppText(record.prompt,lineHeight=26.sp)
            Hint(record.model+" · "+record.ratio+" · "+record.quality)
            Spacer(Modifier.height(120.dp))
        }
    }
    if(action=="collect")AlertDialog(onDismissRequest={action=""},title={AppText("存为新收藏")},text={CollectionNameField(name,{name=it},"收藏名称"){vm.message("名称最多30个字，不能换行")}},confirmButton={TextButton(onClick={vm.collectGeneration(record,name);action=""},enabled=validNewName(name)&&busy==null){AppText("收藏")}},dismissButton={TextButton(onClick={action=""}){AppText("取消")}})
    if(action=="cover")AlertDialog(onDismissRequest={action=""},title={AppText("选择收藏")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){if(entries.isEmpty())Hint("暂无收藏");entries.forEach{e->TextButton(onClick={target=e;action="confirm-cover"}){AppText(e.name)}}}},confirmButton={TextButton(onClick={action=""}){AppText("取消")}})
    if(action=="confirm-cover")AlertDialog(onDismissRequest={action=""},title={AppText("更换收藏封面？")},text={AppText("将更换“${target?.name}”的封面，提示词不变。")},confirmButton={TextButton(onClick={target?.let{vm.collectGeneration(record,it.name,it)};action=""},enabled=busy==null){AppText("更换")}},dismissButton={TextButton(onClick={action=""}){AppText("取消")}})
    if(action=="delete")AlertDialog(onDismissRequest={action=""},title={AppText("删除这条记录？")},text={AppText("清理对应生成图片与参考图副本。收藏中的封面不受影响。")},confirmButton={TextButton(onClick={vm.deleteGenerations(setOf(record.id));action="";back()}){AppText("删除")}},dismissButton={TextButton(onClick={action=""}){AppText("取消")}})
    if(action=="retry")AlertDialog(onDismissRequest={action=""},title={AppText("重新提交生成请求？")},text={AppText("服务端可能已经处理上次请求。请先核对服务商记录；此次重试可能产生额外费用。")},confirmButton={TextButton(onClick={vm.retryGeneration(record);action=""}){AppText("确认重试")}},dismissButton={TextButton(onClick={action=""}){AppText("取消")}})
}
