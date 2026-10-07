package cn.yingci.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val WhiteChatBackground="plain:white"

@Composable internal fun ChatBackgroundTheme(white:Boolean,content:@Composable ()->Unit){
    if(!white){content();return}
    val colors=lightColorScheme(primary=Blue,secondary=Blue,onPrimary=Color.White,onSecondary=Color.White,background=Color.White,surface=Color.White,onSurface=Color(0xFF17191C))
    CompositionLocalProvider(LocalDark provides false,LocalContentColor provides colors.onSurface){MaterialTheme(colorScheme=colors,content=content)}
}

@Composable internal fun WhiteChatBackdrop(){
    val bitmap=remember{android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.WHITE)}}
    val scene=LocalGlass.current
    Box(Modifier.fillMaxSize().then(if(scene?.ready!=true)Modifier.background(Color.White)else Modifier).liquidGlass(3,0f,bitmap=bitmap))
}

@Composable internal fun BoxScope.ChatGlassMenu(dismiss:()->Unit,content:@Composable ColumnScope.()->Unit){
    Box(Modifier.fillMaxSize().clickable(remember{MutableInteractionSource()},indication=null,onClick=dismiss).semantics{contentDescription="关闭对话菜单"})
    BoxWithConstraints(Modifier.align(Alignment.TopEnd).fillMaxSize().padding(top=64.dp,end=18.dp,bottom=16.dp),contentAlignment=Alignment.TopEnd){
        Column(Modifier.widthIn(max=280.dp).fillMaxWidth(.76f).heightIn(max=maxHeight).clip(RoundedCornerShape(20.dp))
            .liquidGlass(8,20f,frost=.7f,tint=1.25f)
            .clickable(remember{MutableInteractionSource()},indication=null){}
            .verticalScroll(rememberScrollState()).padding(vertical=8.dp)){
            CompositionLocalProvider(LocalNativeSurface provides true){content()}
        }
    }
}

@Composable internal fun ChatMenuItem(text:String,enabled:Boolean=true,danger:Boolean=false,onClick:()->Unit){
    Box(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=20.dp,vertical=13.dp)){
        AppText(text,fontSize=15.sp,color=if(danger)Color(0xFFFF3B30)else MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .4f))
    }
}

@Composable internal fun ConversationOptions(c:Conversation,back:()->Unit,save:(ChatOptions,String)->Unit){
    var instructions by rememberSaveable(c.id){mutableStateOf(c.options.instructions)}
    var memory by rememberSaveable(c.id){mutableStateOf(c.memory)}
    var remember by rememberSaveable(c.id){mutableStateOf(c.options.remember)}
    var continuous by rememberSaveable(c.id){mutableStateOf(c.options.continuous)}
    BackHandler(onBack=back)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()){
        Header("对话设定",back=back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(instructions,{if(it.length<=4000)instructions=it},Modifier.fillMaxWidth().heightIn(min=150.dp),label={AppText("角色与回复风格")},maxLines=10)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){AppText("连续回复",Modifier.weight(1f));GlassSwitch(continuous){continuous=it}}
            Hint("仅当前聊天页在前台时生效，每轮最多追加 3 条。可随时停止；追加回复会额外调用模型，可能产生费用。")
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){AppText("对话记忆",Modifier.weight(1f));GlassSwitch(remember){remember=it}}
            Hint("记忆只用于当前对话，保存在本机，启用后随请求发送给你配置的模型。关闭后保留但不发送；清空聊天记录会同时删除记忆。")
            OutlinedTextField(memory,{if(it.length<=6000)memory=it},Modifier.fillMaxWidth().heightIn(min=160.dp),label={AppText("记忆内容")},maxLines=12)
            TextButton(onClick={memory=""},enabled=memory.isNotEmpty()){AppText("清空记忆",color=Color(0xFFFF3B30))}
            Spacer(Modifier.height(16.dp))
        }
        Primary("保存",Icons.Rounded.Check,modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=12.dp)){save(ChatOptions(instructions.trim(),remember,continuous),memory.trim())}
    }
}
