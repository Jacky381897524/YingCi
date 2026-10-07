package cn.yingci.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable internal fun DiscardDialog(onDiscard:()->Unit,onContinue:()->Unit){
    AlertDialog(onDismissRequest=onContinue,title={AppText("放弃未保存的修改？")},confirmButton={TextButton(onClick=onDiscard){AppText("放弃修改")}},dismissButton={TextButton(onClick=onContinue){AppText("继续编辑")}})
}

@Composable internal fun Appearance(vm:AppViewModel,back:()->Unit){
    val saved=LocalPrefs.current
    var theme by rememberSaveable { mutableIntStateOf(saved.theme) }
    var font by rememberSaveable { mutableIntStateOf(saved.fontPercent) }
    var leave by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val dirty=theme!=saved.theme||font!=saved.fontPercent
    fun close(){if(dirty)leave=true else back()}
    BackHandler { close() }
    Column(Modifier.fillMaxSize()){
        Header("外观与文字",back={close()})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
            AppText("外观",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            GlassSelector(listOf("跟随系统","浅色","深色"),theme,LocalDark.current,false,Modifier.fillMaxWidth().height(48.dp)){theme=it}
            HorizontalDivider(Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.08f))
            AppText("文字大小",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            Column {
                GlassSlider(FontSizes.indexOf(nearestFontSize(font)).toFloat(),{font=FontSizes[it.toInt().coerceIn(0,4)]},steps=3,valueRange=0f..4f)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf("小","标准","中","大","特大").forEach{AppText(it,fontSize=12.sp)}}
            }
            HorizontalDivider(Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.08f))
            AppText("文字预览",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            val density=LocalDensity.current
            val previewDark=when(theme){1->false;2->true;else->isSystemInDarkTheme()}
            CompositionLocalProvider(LocalDensity provides Density(density.density,font/100f)){
                Column(Modifier.fillMaxWidth().background(if(previewDark)Color(0xFF151618)else Color.White).padding(18.dp)){
                    AppText("夏日手绘拼贴",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=if(previewDark)Color.White else Color(0xFF17191C))
                    Spacer(Modifier.height(10.dp))
                    AppText("保留照片的主体与情绪，让画面呈现细腻的手绘质感。",fontSize=16.sp,lineHeight=26.sp,color=if(previewDark)Color(0xFFBFC2C9)else Color(0xFF626772))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Primary("保存",Icons.Rounded.Check,enabled=busy==null,modifier=Modifier.fillMaxWidth().padding(22.dp)){
            vm.savePreferences(saved.copy(theme=theme,fontPercent=font,followFont=false,reduceMotion=false,haptics=false)){back()}
        }
    }
    if(leave)DiscardDialog(back){leave=false}
}

@Composable internal fun ApiSettings(vm:AppViewModel,back:()->Unit){
    val p=LocalPrefs.current
    var url by rememberSaveable { mutableStateOf(p.baseUrl) }
    var model by rememberSaveable { mutableStateOf(p.model) }
    var imageModel by rememberSaveable { mutableStateOf(p.imageModel) }
    var key by remember { mutableStateOf(p.apiKey) }
    var show by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var generationConfirm by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf<Boolean?>(null) }
    val testing by vm.connectionTesting.collectAsStateWithLifecycle()
    val visionStatus by vm.visionStatus.collectAsStateWithLifecycle()
    val generationStatus by vm.generationStatus.collectAsStateWithLifecycle()
    val availableModels by vm.availableModels.collectAsStateWithLifecycle()
    val modelsLoading by vm.modelsLoading.collectAsStateWithLifecycle()
    val modelsStatus by vm.modelsStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val dirty=url!=p.baseUrl||model!=p.model||imageModel!=p.imageModel||key!=p.apiKey
    fun close(){if(dirty)leave=true else back()}
    fun configuration(requireModel:Boolean=true):Preferences?=try{
        ApiEndpoint.chat(url)
        require(key.isNotBlank()){"请填写 API Key"}
        require(!requireModel||model.isNotBlank()||imageModel.isNotBlank()){"请至少填写一个模型名称"}
        p.copy(baseUrl=url.trim(),model=model.trim(),imageModel=imageModel.trim(),apiKey=key.trim())
    }catch(e:Exception){vm.message(e.message?:"配置无效");null}
    fun forTest(image:Boolean):Preferences?{
        val config=configuration(false)?:return null
        if((if(image)config.imageModel else config.model).isBlank()){
            vm.message(if(image)"请填写生图模型 ID"else"请填写能读图并返回文字的识图模型 ID")
            return null
        }
        return config
    }
    LaunchedEffect(url,model,imageModel,key){vm.resetConnectionResults()}
    LaunchedEffect(url,key){vm.resetModels();modelPicker=null}
    BackHandler { close() }
    DisposableEffect(Unit){onDispose{vm.cancelConnectionTest();vm.cancelModels()}}
    modelPicker?.let{image->
        val bigModelImage=image&&BigModelImage.isHost(url)
        val choices=if(bigModelImage)BigModelImage.candidates(availableModels)else availableModels
        ModelPicker(choices,if(image)"选择生图模型"else"选择识图模型",onDismiss={modelPicker=null},note=if(bigModelImage)"含智谱文档列出的生图模型，以及服务返回的图像相关模型。当前密钥权限需通过生图测试确认。"else ""){chosen->
            if(image)imageModel=chosen else model=chosen
            modelPicker=null
        }
        return
    }
    Column(Modifier.fillMaxSize().imePadding()){
        Header("模型连接",back={close()})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){AppText("接口类型");AppText("OpenAI 兼容",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Field(url,{url=it},"服务地址",single=true)
            OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={AppText("API Key")},textStyle=MaterialTheme.typography.bodyLarge,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),visualTransformation=if(show)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={show=!show}){Icon(if(show)Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,if(show)"隐藏密钥"else"显示密钥")}})
            TextButton(enabled=!modelsLoading,onClick={configuration(false)?.let(vm::loadModels)}){
                Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(8.dp));AppText("读取模型列表")
            }
            if(modelsLoading){LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(modelsStatus.isNotBlank())androidx.compose.foundation.text.selection.SelectionContainer{Hint(modelsStatus)}
            ModelField(model,{model=it},"识图模型",availableModels.isNotEmpty()){modelPicker=false}
            ModelField(imageModel,{imageModel=it},"生图模型",availableModels.isNotEmpty()||BigModelImage.isHost(url)){modelPicker=true}
            if(BigModelImage.isHost(url)&&BigModelImage.isTextModel(imageModel.trim()))Hint("当前填写的是对话/识图模型，请在生图模型中选择 glm-image 或 CogView。")
            Hint("服务地址示例：https://api.example.com/v1")
            HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.08f))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){AppText("识图连接 · 反推");androidx.compose.foundation.text.selection.SelectionContainer{Hint(visionStatus)}};RoundGlassButton(Icons.Rounded.PlayArrow,"测试识图连接",{forTest(false)?.let(vm::testConnection)},enabled=!testing)}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){AppText("生图连接 · 生成图片");androidx.compose.foundation.text.selection.SelectionContainer{Hint(generationStatus)}};RoundGlassButton(Icons.Rounded.PlayArrow,"测试生图连接",{if(forTest(true)!=null)generationConfirm=true},enabled=!testing)}
            if(testing){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=vm::cancelConnectionTest){AppText("取消测试")}}
            Hint("识图测试发送一张本机生成的测试图；生图测试会请求生成一张图片。服务商可能收取费用。")
            Hint("密钥加密保存在本机。支持 Chat Completions 图片输入及 Images Generations 接口。")
            Spacer(Modifier.height(4.dp))
        }
        Primary("保存",Icons.Rounded.Check,enabled=busy==null&&!testing,modifier=Modifier.fillMaxWidth().padding(22.dp)){configuration()?.let{vm.savePreferences(it){back()}}}
    }
    if(leave)DiscardDialog(back){leave=false}
    if(generationConfirm)AlertDialog(onDismissRequest={generationConfirm=false},title={AppText("测试生图连接？")},text={AppText("将使用填写的生图模型生成一张测试图片，可能按一张图片收费。测试结果不会添加到收藏。")},confirmButton={TextButton(onClick={generationConfirm=false;forTest(true)?.let(vm::testImageConnection)}){AppText("开始测试")}},dismissButton={TextButton(onClick={generationConfirm=false}){AppText("取消")}})
}

@Composable private fun ModelField(value:String,onChange:(String)->Unit,label:String,canChoose:Boolean,onChoose:()->Unit){
    OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={AppText(label)},textStyle=MaterialTheme.typography.bodyLarge,
        singleLine=true,keyboardOptions=KeyboardOptions(autoCorrectEnabled=false),trailingIcon={
            if(canChoose)IconButton(onClick=onChoose){Icon(Icons.Rounded.ArrowDropDown,"选择$label")}
        })
}

@Composable internal fun ModelPicker(models:List<String>,title:String,onDismiss:()->Unit,note:String="",onChoose:(String)->Unit){
    var query by remember { mutableStateOf("") }
    val filtered=remember(models,query){models.filter{it.contains(query.trim(),ignoreCase=true)}}
    BackHandler(onBack=onDismiss)
    Column(Modifier.fillMaxSize().imePadding()){
        Header(title,back=onDismiss)
        Column(Modifier.weight(1f).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Field(query,{query=it},"筛选模型",single=true)
            if(note.isNotBlank())Hint(note)
            if(filtered.isEmpty())Hint("没有匹配的模型")
            LazyColumn(Modifier.fillMaxWidth().weight(1f)){
                items(filtered,key={it}){id->TextButton(onClick={onChoose(id)},modifier=Modifier.fillMaxWidth()){
                    AppText(id,Modifier.fillMaxWidth())
                }}
            }
        }
    }
}
