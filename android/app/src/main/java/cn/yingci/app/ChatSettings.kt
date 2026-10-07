package cn.yingci.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable internal fun ChatSettings(vm:AppViewModel,back:()->Unit){
    val p=LocalPrefs.current
    var independent by rememberSaveable{mutableStateOf(p.chatIndependent)}
    var url by rememberSaveable{mutableStateOf(p.chatUrl)}
    var model by rememberSaveable{mutableStateOf(p.chatModel)}
    var key by remember{mutableStateOf(p.chatKey)}
    var visible by remember{mutableStateOf(false)}
    var picker by remember{mutableStateOf(false)}
    var leave by remember{mutableStateOf(false)}
    val models by vm.availableModels.collectAsStateWithLifecycle()
    val loading by vm.modelsLoading.collectAsStateWithLifecycle()
    val status by vm.modelsStatus.collectAsStateWithLifecycle()
    val test by vm.chatStatus.collectAsStateWithLifecycle()
    val visionTest by vm.visionStatus.collectAsStateWithLifecycle()
    val testing by vm.connectionTesting.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    fun config()=p.copy(chatIndependent=independent,chatUrl=url.trim(),chatModel=model.trim(),chatKey=key.trim())
    fun close(){if(config()!=p)leave=true else back()}
    fun checked(requireModel:Boolean):Preferences?=try{
        val value=config();val connection=value.chatConnection();ApiEndpoint.chat(connection.baseUrl)
        require(connection.apiKey.isNotBlank()){"请填写 API Key"};require(!requireModel||connection.model.isNotBlank()){"请选择或填写聊天模型"};value
    }catch(e:Exception){vm.message(e.message?:"配置无效");null}
    LaunchedEffect(url,key,independent){vm.resetModels()}
    LaunchedEffect(url,key,model,independent){vm.resetConnectionResults()}
    DisposableEffect(Unit){onDispose{vm.cancelModels();vm.cancelConnectionTest()}}
    BackHandler{close()}
    if(picker){ModelPicker(models,"选择聊天模型",{picker=false}){model=it;picker=false};return}
    Column(Modifier.fillMaxSize().imePadding()){
        Header("聊天模型",back={close()})
        FadingContent(Modifier.weight(1f),bottom=24.dp){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){AppText("单独配置聊天服务",Modifier.weight(1f));GlassSwitch(independent,{independent=it})}
            if(independent){
                Field(url,{url=it},"服务地址",single=true)
                val resolved=remember(url){runCatching{ApiEndpoint.chat(url).toString()}.getOrNull()}
                if(resolved!=null)Hint("聊天接口：$resolved")
                OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={AppText("API Key")},singleLine=true,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={visible=!visible}){Icon(if(visible)Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,"显示或隐藏密钥")}})
                TextButton(onClick={checked(false)?.chatConnection()?.let(vm::loadModels)},enabled=!loading){Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(8.dp));AppText("读取模型列表")}
                if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(status.isNotBlank())Hint(status)
                Field(model,{model=it},"聊天模型",single=true)
                if(models.isNotEmpty())TextButton(onClick={picker=true}){AppText("从模型列表选择")}
            }else{AppText("共用识图模型");Hint(p.model.ifBlank{"尚未配置识图模型"});Hint(p.baseUrl)}
            HorizontalDivider()
            AppText("聊天连接")
            Hint(test)
            TextButton(onClick={checked(true)?.chatConnection()?.let(vm::testChatConnection)},enabled=!testing){Icon(Icons.Rounded.PlayArrow,null);Spacer(Modifier.width(8.dp));AppText("测试聊天连接")}
            AppText("图片识别（可选）")
            Hint(visionTest)
            TextButton(onClick={checked(true)?.chatConnection()?.let(vm::testConnection)},enabled=!testing){Icon(Icons.Rounded.Image,null);Spacer(Modifier.width(8.dp));AppText("测试图片识别")}
            if(testing){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=vm::cancelConnectionTest){AppText("取消测试")}}
            Hint("聊天测试仅发送文字；图片识别测试会发送本机测试图。两项测试均可能收费。仅支持文字的模型也可聊天，模型列表不代表识图能力已验证。密钥加密保存在本机。")
            Spacer(Modifier.height(24.dp))
        }}
        Primary("保存",Icons.Rounded.Check,enabled=busy==null&&!testing,modifier=Modifier.fillMaxWidth().padding(22.dp)){
            val value=if(independent)checked(true)else config()
            value?.let{vm.savePreferences(it,back)}
        }
    }
    if(leave)DiscardDialog(back){leave=false}
}
