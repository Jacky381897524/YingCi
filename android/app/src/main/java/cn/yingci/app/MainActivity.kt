package cn.yingci.app

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import java.io.File

internal val Blue = Color(0xFF007AFF)
internal val LocalPrefs = staticCompositionLocalOf { Preferences() }
internal val LocalDark = staticCompositionLocalOf { false }
internal data class PageRoute(val page:String,val tab:Int,val entry:String?,val chat:String?) {
    val key get()="$page/$tab/$entry/$chat"
}

// Each moving page owns its complete backdrop, images and glass. No shared GL planes
// can escape the page's clipping or remain over the incoming page during navigation.
@Composable internal fun PageGlass(render:Boolean,content:@Composable ()->Unit){
    val scene=remember{GlassScene().apply{enabled=render}}
    val p=LocalPrefs.current;val dark=LocalDark.current
    SideEffect{scene.environment(dark,p.reduceMotion,p.display)}
    CompositionLocalProvider(LocalGlass provides scene){
        Box(Modifier.fillMaxSize().testTag("page-surface").clip(RoundedCornerShape(0.dp)).background(MaterialTheme.colorScheme.background)){
            if(render)GlassBackground(scene,Modifier.fillMaxSize())
            content()
        }
    }
}

class MainActivity : ComponentActivity() {
    private var systemNight by mutableStateOf(false)
    private var generationLaunch by mutableIntStateOf(0)
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration){super.onConfigurationChanged(newConfig);systemNight=newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES}
    override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);if(intent.getBooleanExtra("generation",false))generationLaunch++}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        systemNight=resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
        if(intent.getBooleanExtra("generation",false))generationLaunch++
        val activeDisplay=windowManager.defaultDisplay
        activeDisplay.supportedModes.filter { it.physicalWidth==activeDisplay.mode.physicalWidth && it.physicalHeight==activeDisplay.mode.physicalHeight && it.refreshRate<=120.5f }.maxByOrNull { it.refreshRate }?.let { mode ->
            window.attributes=window.attributes.apply { preferredDisplayModeId=mode.modeId }
        }
        setContent { YingciApp(systemBars = { dark ->
            WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark }
        },systemNightOverride=systemNight,generationLaunch=generationLaunch) }
    }
}

object ImageMemory {
    private val cache=object:LruCache<String,Bitmap>(32*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    fun clear()=cache.evictAll()
    fun load(file:File,size:Int):Bitmap? {
        val key="${file.absolutePath}:${file.lastModified()}:$size"
        cache.get(key)?.let{return it}
        val info=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,info)
        var sample=1;while(maxOf(info.outWidth,info.outHeight)/sample>size*2)sample*=2
        return BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})?.also{cache.put(key,it)}
    }
}

@Composable
internal fun Photo(file:File?,modifier:Modifier=Modifier,large:Boolean=false,adaptive:Boolean=false,crop:Boolean=false) {
    val bitmap by produceState<Bitmap?>(null,file?.path,large){value=withContext(Dispatchers.IO){file?.let{runCatching{ImageMemory.load(it,if(large)1600 else 600)}.getOrNull()}}}
    val ratio=bitmap?.let{it.width.toFloat()/it.height}?:1f
    val scene=LocalGlass.current;val root=LocalView.current.rootView
    val native=LocalNativeSurface.current||scene==null||!scene.enabled||(scene.hostRoot!=null&&scene.hostRoot!==root)
    Box((if(adaptive)modifier.aspectRatio(ratio) else modifier).clip(RoundedCornerShape(8.dp)).then(if(native)Modifier else Modifier.liquidGlass(3,8f,bitmap)),contentAlignment=Alignment.Center){
        if(bitmap!=null){if(native||scene?.ready!=true)Image(bitmap!!.asImageBitmap(),contentDescription="效果封面",contentScale=if(large&&!crop)ContentScale.Fit else ContentScale.Crop,modifier=Modifier.fillMaxSize())}
        else Icon(Icons.Rounded.Image,null,Modifier.size(38.dp),tint=MaterialTheme.colorScheme.primary.copy(alpha=.55f))
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun YingciApp(systemBars:(Boolean)->Unit,vm:AppViewModel=viewModel(),renderGlass:Boolean=true,systemNightOverride:Boolean?=null,generationLaunch:Int=0) {
    val prefs by vm.preferences.collectAsStateWithLifecycle();val entries by vm.entries.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle();val notice by vm.notice.collectAsStateWithLifecycle();val loading by vm.loading.collectAsStateWithLifecycle()
    val dark=when(prefs.theme){1->false;2->true;else->systemNightOverride?:isSystemInDarkTheme()}
    val density=LocalDensity.current
    val colors=if(dark)darkColorScheme(primary=Color(0xFFFFD44D),onPrimary=Color(0xFF222018),secondary=Color(0xFFFFD44D),onSecondary=Color(0xFF222018),background=Color(0xFF151618),surface=Color(0xFF222326),onSurface=Color(0xFFF2F3F5),surfaceVariant=Color(0xFF303135))else lightColorScheme(primary=Blue,onPrimary=Color.White,secondary=Blue,onSecondary=Color.White,background=Color(0xFFF7F8FA),surface=Color.White,onSurface=Color(0xFF17191C),surfaceVariant=Color(0xFFEBEDF0))
    CompositionLocalProvider(LocalOverscrollFactory provides null,LocalPrefs provides prefs,LocalDark provides dark,LocalContentColor provides colors.onSurface,LocalDensity provides Density(density.density,if(prefs.followFont)density.fontScale else prefs.fontPercent/100f)){
        MaterialTheme(colorScheme=colors,typography=remember(prefs.fontWeight){appTypography(prefs.fontWeight)}){
            var tab by rememberSaveable{mutableIntStateOf(0)};var page by rememberSaveable{mutableStateOf("home")};var entryId by rememberSaveable{mutableStateOf<String?>(null)}
            var showHistory by rememberSaveable{mutableStateOf(false)}
            val activeChat by vm.activeConversation.collectAsStateWithLifecycle()
            SideEffect{systemBars(dark&&!(page=="home"&&tab==2&&activeChat!=null&&prefs.chatWallpaper==WhiteChatBackground))}
            LaunchedEffect(generationLaunch){if(generationLaunch>0){tab=2;page="home";vm.activeConversation.value=null}}
            fun generate(text:String){vm.newConversation("请根据以下提示词生成图片：\n$text");tab=2;page="home"}
            val current=entries.find{it.id==entryId}
            val snackbar=remember{SnackbarHostState()}
            LaunchedEffect(notice){notice?.let{snackbar.showSnackbar(it);vm.notice.value=null}}
            fun back(){page=if(page=="edit"&&current!=null)"detail" else "home"}
            BackHandler(page!="home"&&page!="edit"){back()}
            val savedPages=rememberSaveableStateHolder()
            val route=PageRoute(page,tab,if(page in setOf("detail","edit"))entryId else null,if(page=="home"&&tab==2)activeChat else null)
            Box(Modifier.fillMaxSize().background(colors.background)){
            AnimatedContent(targetState=route,modifier=Modifier.fillMaxSize(),transitionSpec={
                if(prefs.reduceMotion)EnterTransition.None togetherWith ExitTransition.None
                else {
                    val returning=(targetState.page=="home"&&initialState.page!="home") || (initialState.chat!=null&&targetState.chat==null) || (initialState.page=="edit"&&targetState.page=="detail")
                    val direction=if(returning)-1 else 1
                    (slideInHorizontally(tween(320,easing=FastOutSlowInEasing)){it*direction} togetherWith
                        slideOutHorizontally(tween(320,easing=FastOutSlowInEasing)){-it*direction}).using(SizeTransform(clip=true,sizeAnimationSpec={_,_->tween(0)}))
                }
            },label="页面切换"){shown->
            savedPages.SaveableStateProvider(shown.key){ChatBackgroundTheme(shown.chat!=null&&prefs.chatWallpaper==WhiteChatBackground){PageGlass(renderGlass){
            val wallpaper by produceState<File?>(null,prefs.chatWallpaper,shown.chat){value=if(shown.chat!=null&&prefs.chatWallpaper!=WhiteChatBackground)vm.repository.chatBackground(prefs.chatWallpaper)else null}
            if(shown.chat!=null){if(prefs.chatWallpaper==WhiteChatBackground)WhiteChatBackdrop()else Photo(wallpaper,Modifier.fillMaxSize(),large=true,crop=true)}
            Scaffold(containerColor=Color.Transparent,bottomBar={if(shown.page=="home"&&shown.chat==null){
                    GlassSelector(listOf("收藏","反推","生图","设置"),shown.tab,dark,prefs.reduceMotion,Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).height(68.dp)){showHistory=false;tab=it}
            }}){padding->
                Box(Modifier.fillMaxSize().padding(top=if(shown.chat!=null)0.dp else padding.calculateTopPadding(),bottom=if(shown.page=="home")0.dp else padding.calculateBottomPadding())){
                        val shownEntry=entries.find{it.id==shown.entry}
                        when(shown.page){
                            "home"->when(shown.tab){
                                0->Library(entries,loading,vm,{entryId=it.id;page="detail"},{entryId=null;page="edit"},{entryId=it.id;page="edit"})
                                1->Reverse(vm,{page="api"},{entryId=it.id;page="detail"}){text,images->vm.newConversation("请根据以下提示词生成图片：\n$text",images);tab=2;page="home"}
                                2->if(shown.chat==null)ConversationList(vm) else ChatScreen(vm,shown.chat,{vm.activeConversation.value=null},{page="chat-api"})
                                else->Settings(vm,{if(it=="history"){vm.activeConversation.value=null;tab=2}else page=it})
                            }
                            "detail"->shownEntry?.let{Detail(it,vm,{back()},{page="edit"},::generate)} ?: Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){AppText("条目已移除")}
                            "edit"->Editor(shownEntry,vm,{back()},{entryId=it.id;page="detail"})
                            "appearance"->Appearance(vm){back()}
                            "display"->DisplaySettings(vm){back()}
                            "api"->ApiSettings(vm){back()}
                            "chat-api"->ChatSettings(vm){back()}
                        }
                    if(busy!=null)Surface(Modifier.align(Alignment.TopCenter).padding(12.dp),shape=RoundedCornerShape(24.dp),shadowElevation=6.dp){Row(Modifier.padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp));AppText(busy!!,style=MaterialTheme.typography.bodySmall)}}
                }
            }
            }}}}
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        }
    }
}

@Composable internal fun Header(title:String,subtitle:String?=null,back:(()->Unit)?=null,action:@Composable ()->Unit={}){
    if(back!=null){
        Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal=18.dp)){
            RoundGlassButton(Icons.AutoMirrored.Rounded.ArrowBack,"返回",back,Modifier.align(Alignment.CenterStart))
            AppText(title,Modifier.align(Alignment.Center),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
            Row(Modifier.align(Alignment.CenterEnd)){action()}
        };return
    }
    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=16.dp,top=14.dp,bottom=18.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){AppText(title,fontSize=30.sp,fontWeight=FontWeight.SemiBold);if(subtitle!=null){Spacer(Modifier.height(5.dp));AppText(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        action()
    }
}

@Composable internal fun Primary(text:String,icon:ImageVector?=null,enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit){
    val source=remember{MutableInteractionSource()};val pressed by source.collectIsPressedAsState();val p=LocalPrefs.current;val feedback=LocalHapticFeedback.current
    val scale by animateFloatAsState(if(pressed&&!p.reduceMotion).965f else 1f,spring(dampingRatio=.7f,stiffness=550f),label="按钮回弹")
    val pressure by animateFloatAsState(if(pressed&&!p.reduceMotion)1f else 0f,tween(if(p.reduceMotion)0 else 160),label="玻璃按压")
    Button(onClick={if(p.haptics)feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove);onClick()},enabled=enabled,interactionSource=source,colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=MaterialTheme.colorScheme.primary,disabledContainerColor=Color.Transparent),shape=RoundedCornerShape(30.dp),contentPadding=PaddingValues(horizontal=22.dp,vertical=16.dp),modifier=modifier.graphicsLayer{scaleX=scale;scaleY=scale;alpha=if(enabled)1f else .5f}.liquidGlass(4,30f,pressure=pressure)){
        if(icon!=null){Icon(icon,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))};GlassText(text,action=true)
    }
}
@Composable internal fun Hint(text:String,modifier:Modifier=Modifier){AppText(text,modifier,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,lineHeight=20.sp)}
@Composable private fun Block(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.fillMaxWidth().padding(vertical=8.dp),content=content)}
@Composable internal fun Field(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,single:Boolean=false){OutlinedTextField(value,onValueChange,modifier.fillMaxWidth(),textStyle=MaterialTheme.typography.bodyLarge,colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.onSurface.copy(alpha=.14f),focusedBorderColor=MaterialTheme.colorScheme.primary),label={AppText(label)},singleLine=single,shape=RoundedCornerShape(8.dp))}
@Composable internal fun CollectionNameField(value:String,onValueChange:(String)->Unit,label:String,reject:()->Unit){
    Column{
        Field(value,{if(acceptNameInput(it,value))onValueChange(it)else reject()},label,single=true)
        Hint("${value.codePointCount(0,value.length)} / $CollectionNameLimit",Modifier.align(Alignment.End).padding(top=4.dp))
    }
}

@Composable internal fun CollectionTitleBand(name:String,modifier:Modifier=Modifier){
    val shape=RoundedCornerShape(bottomStart=8.dp,bottomEnd=8.dp)
    // The GL layer needs its own corner mask; the parent Compose clip is not shared.
    Box(modifier.clip(shape).liquidGlass(7,0f,shape=shape,frost=.5f,tint=.65f,darkSurface=true),contentAlignment=Alignment.Center){
        CollectionTitle(name,Modifier.padding(horizontal=10.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Library(entries:List<PromptEntry>,loading:Boolean,vm:AppViewModel,open:(PromptEntry)->Unit,add:()->Unit,edit:(PromptEntry)->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    var search by rememberSaveable{mutableStateOf(false)}
    var menu by remember{mutableStateOf<PromptEntry?>(null)}
    var deleting by remember{mutableStateOf<PromptEntry?>(null)}
    val scroll=rememberLazyGridState()
    val filtered by produceState(entries,entries,query){if(query.isNotEmpty())delay(100);value=withContext(Dispatchers.Default){entries.filter{PromptSearch.matches(it,query)}}}
    HeaderOverlay(header={
        Row(Modifier.fillMaxWidth().height(82.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(search)OutlinedTextField(query,{query=it},Modifier.weight(1f).height(56.dp).liquidGlass(0,28f),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=Color.Transparent),textStyle=MaterialTheme.typography.bodyMedium,placeholder={GlassText("搜索名称或提示词",fontSize=13.sp,maxLines=1)},trailingIcon={IconButton(onClick={query="";search=false}){Icon(Icons.Rounded.Close,"关闭搜索")}},singleLine=true,shape=RoundedCornerShape(28.dp))
            else{AppText("映词",Modifier.weight(1f),fontSize=30.sp,fontWeight=FontWeight.SemiBold);RoundGlassButton(Icons.Rounded.Search,"搜索",{search=true})}
            RoundGlassButton(Icons.Rounded.Add,"新建提示词",add)
        }
    }){headerHeight->
        if(loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        else if(filtered.isEmpty())Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            Icon(if(query.isEmpty())Icons.Rounded.CollectionsBookmark else Icons.Rounded.Search,null,Modifier.size(54.dp),tint=MaterialTheme.colorScheme.primary.copy(alpha=.65f));Spacer(Modifier.height(20.dp));AppText(if(query.isEmpty())"收藏你的第一份灵感"else"没有找到相关提示词",fontSize=20.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(10.dp));Hint(if(query.isEmpty())"上传一张效果图，配上你的提示词。"else"试试更短的词，或搜索提示词中的内容。");Spacer(Modifier.height(24.dp));if(query.isEmpty())Primary("新建提示词",Icons.Rounded.Add,onClick=add)
        }else FadingContent(Modifier.fillMaxSize(),top=headerHeight){LazyVerticalGrid(state=scroll,columns=GridCells.Fixed(2),contentPadding=PaddingValues(start=22.dp,end=22.dp,top=headerHeight,bottom=120.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            items(filtered,key={it.id}){item->
                Box(Modifier.fillMaxWidth().aspectRatio(.75f).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick={if(menu!=null)menu=null else open(item)},onLongClick={menu=item})){
                    Photo(vm.repository.cover(item),Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.17f).background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.15f),Color.Transparent))))
                    CollectionTitleBand(item.name,Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.125f))
                    if(menu?.id==item.id)Row(Modifier.align(Alignment.Center),horizontalArrangement=Arrangement.spacedBy(14.dp)){
                        RoundGlassButton(Icons.Rounded.Edit,"编辑提示词",{menu=null;edit(item)},frost=.5f,glassTint=.35f)
                        RoundGlassButton(Icons.Rounded.DeleteOutline,"删除提示词",{menu=null;deleting=item},tint=Color(0xFFFF3B30),frost=.5f,glassTint=.35f)
                    }
                }
            }
        }}
    }
    BackHandler(menu!=null){menu=null}
    deleting?.let{item->AlertDialog(onDismissRequest={deleting=null},title={AppText("删除这条提示词？")},text={AppText("将删除封面、原始版和修改版。此操作不可撤销。")},confirmButton={TextButton(onClick={vm.delete(item){};deleting=null}){AppText("删除",color=Color(0xFFFF3B30))}},dismissButton={TextButton(onClick={deleting=null}){AppText("取消")}})}
}

@Composable private fun Detail(entry:PromptEntry,vm:AppViewModel,back:()->Unit,edit:()->Unit,generate:(String)->Unit){
    var original by rememberSaveable(entry.id){mutableStateOf(false)};var confirm by remember{mutableStateOf(false)}
    val clipboard=LocalClipboardManager.current;val p=LocalPrefs.current
    Column(Modifier.fillMaxSize()){
        Header("提示词",back=back){RoundGlassButton(Icons.Rounded.Edit,"编辑提示词",edit);Spacer(Modifier.width(6.dp));RoundGlassButton(Icons.Rounded.DeleteOutline,"删除提示词",{confirm=true},tint=Color(0xFFFF3B30))}
        FadingContent(Modifier.weight(1f),bottom=24.dp){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp)){
            Photo(vm.repository.cover(entry),Modifier.fillMaxWidth(),large=true,adaptive=true)
            AppText(entry.name,Modifier.padding(top=24.dp,bottom=2.dp),fontSize=25.sp,fontWeight=FontWeight.SemiBold)
            GlassSelector(listOf("我的版本","原始版本"),if(original)1 else 0,LocalDark.current,p.reduceMotion,Modifier.fillMaxWidth().padding(top=14.dp,bottom=8.dp).height(48.dp)){original=it==1}
            Spacer(Modifier.height(22.dp));SelectionContainer{AppText(if(original)entry.original else entry.current,Modifier.padding(horizontal=4.dp),fontSize=15.sp,lineHeight=27.sp)};Spacer(Modifier.height(24.dp))
            Primary("用这份提示词生图",Icons.Rounded.AddPhotoAlternate,modifier=Modifier.fillMaxWidth()){generate(if(original)entry.original else entry.current)}
        }}
        Primary("复制提示词",Icons.Rounded.ContentCopy,modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=12.dp)){clipboard.setText(AnnotatedString(if(original)entry.original else entry.current));vm.message("已复制提示词")}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={AppText("删除这条提示词？")},text={AppText("封面、原始版和修改版都会从本机删除。删除后可从你导出的备份中恢复。")},confirmButton={TextButton(onClick={confirm=false;vm.delete(entry,back)}){AppText("删除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={confirm=false}){AppText("保留")}})
}

@Composable private fun Editor(entry:PromptEntry?,vm:AppViewModel,back:()->Unit,saved:(PromptEntry)->Unit){
    var name by rememberSaveable(entry?.id){mutableStateOf(entry?.name.orEmpty())};var text by rememberSaveable(entry?.id){mutableStateOf(entry?.current.orEmpty())};var imagePath by rememberSaveable(entry?.id){mutableStateOf<String?>(null)};var leave by remember{mutableStateOf(false)}
    val image=imagePath?.let{File(it)};val busy by vm.busy.collectAsStateWithLifecycle()
    val dirty=name!=entry?.name.orEmpty()||text!=entry?.current.orEmpty()||image!=null
    fun close(){if(dirty)leave=true else back()}
    BackHandler{close()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let{vm.importImage(it){file->vm.repository.discardDraft(image);imagePath=file.path}}}
    Column(Modifier.fillMaxSize().imePadding()){
        Header(if(entry==null)"新建灵感"else"编辑提示词",back={close()})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable{picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},contentAlignment=Alignment.BottomCenter){
                Photo(image?:entry?.let{vm.repository.cover(it)},Modifier.fillMaxWidth(),large=true,adaptive=true)
                Row(Modifier.padding(14.dp).liquidGlass(4,22f).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.AddPhotoAlternate,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));GlassText("选择或更换封面",fontSize=13.sp,action=true)}
            }
            CollectionNameField(name,{name=it},"提示词名称"){vm.message("名称最多30个字，不能换行")}
            Field(text,{if(it.length<=100000)text=it},"完整提示词",Modifier.heightIn(min=250.dp))
            Hint(if(entry==null)"首次保存的提示词将作为原始版保留。"else"本次保存会更新修改版。最初保存的原始版始终保留。");Spacer(Modifier.height(10.dp))
        }
        Primary("保存到提示词库",Icons.Rounded.Check,enabled=(name==entry?.name||validNewName(name))&&text.isNotBlank()&&busy==null,modifier=Modifier.fillMaxWidth().padding(22.dp)){vm.save(entry?.id,name,text,image,saved)}
    }
    if(leave)AlertDialog(onDismissRequest={leave=false},title={AppText("放弃未保存的修改？")},confirmButton={TextButton(onClick={vm.repository.discardDraft(image);leave=false;back()}){AppText("放弃修改")}},dismissButton={TextButton(onClick={leave=false}){AppText("继续编辑")}})
}

@Composable private fun Reverse(vm:AppViewModel,configure:()->Unit,saved:(PromptEntry)->Unit,generate:(String,List<File>)->Unit){
    val faithful=vm.reverseMode.collectAsStateWithLifecycle().value
    val workspace=vm.reverseWorkspace
    val reference by vm.reference.collectAsStateWithLifecycle();val analyzing by vm.analyzing.collectAsStateWithLifecycle();val result by vm.analysis.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val p=LocalPrefs.current
    val clipboard=LocalClipboardManager.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let{vm.importImage(it,vm::setReference)}}
    val name by workspace.name.collectAsStateWithLifecycle();val prompt by workspace.prompt.collectAsStateWithLifecycle()
    val scroll=rememberScrollState()
    HeaderOverlay(header={
        Header(if(faithful)"正常反推"else"风格反推"){RoundGlassButton(Icons.Rounded.SwapHoriz,"切换反推模式",vm::switchReverse)}
    }){headerHeight->
        FadingContent(Modifier.fillMaxSize(),top=headerHeight){Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start=22.dp,end=22.dp,top=headerHeight),verticalArrangement=Arrangement.spacedBy(18.dp)){
            Box(Modifier.fillMaxWidth().then(if(reference==null)Modifier.height(260.dp).liquidGlass(4,20f)else Modifier).clip(RoundedCornerShape(20.dp)).clickable(enabled=!analyzing&&busy==null){picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},contentAlignment=Alignment.Center){
                if(reference!=null){Photo(reference,Modifier.fillMaxWidth(),large=true,adaptive=true);GlassText("更换参考图",Modifier.align(Alignment.BottomCenter).padding(14.dp).liquidGlass(4,22f).padding(horizontal=16.dp,vertical=10.dp),fontSize=13.sp,action=true)}
                else Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Rounded.AddPhotoAlternate,null,Modifier.size(45.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(18.dp));GlassText(if(faithful)"选择一张参考图"else"选择一张风格参考图",action=true);Spacer(Modifier.height(9.dp));GlassText(if(faithful)"还原主体、构图、光线与画面细节"else"提取画风、质感、光线与设计方式",fontSize=12.sp)}
            }
            if(p.apiKey.isBlank()||p.baseUrl.isBlank()||p.model.isBlank())Block{AppText("先连接你的视觉模型",fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(8.dp));Hint("使用你自己的 API Key，无需自建服务器。");TextButton(onClick=configure){AppText("前往配置 →")}}
            Block{Hint("所选图片将发送至你配置的模型服务。模型可能按调用收费。")}
            Primary(if(analyzing)"正在分析图片…"else if(result!=null)"重新反推"else if(faithful)"反推画面提示词"else"提取可迁移的风格",Icons.Rounded.AutoAwesome,enabled=reference!=null&&!analyzing&&busy==null,modifier=Modifier.fillMaxWidth(),onClick=vm::analyze)
            if(analyzing){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=vm::cancelAnalysis,modifier=Modifier.align(Alignment.CenterHorizontally)){AppText("取消反推")}}
            if(result!=null){
                Spacer(Modifier.height(4.dp));AppText(if(faithful)"画面提示词"else"可迁移提示词",fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                CollectionNameField(name,{workspace.name.value=it},"收藏名称"){vm.message("名称最多30个字，不能换行")}
                Field(prompt,{if(it.length<=100000)workspace.prompt.value=it},"可编辑的提示词",Modifier.heightIn(min=240.dp))
                OutlinedButton(onClick={clipboard.setText(AnnotatedString(prompt));vm.message("已复制提示词")},modifier=Modifier.fillMaxWidth().liquidGlass(4,22f),shape=RoundedCornerShape(22.dp),contentPadding=PaddingValues(16.dp)){Icon(Icons.Rounded.ContentCopy,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));GlassText("复制提示词",action=true)}
                Primary(if(faithful)"收藏提示词"else"收藏这份风格",Icons.Rounded.BookmarkAdd,enabled=validNewName(name)&&prompt.isNotBlank()&&busy==null,modifier=Modifier.fillMaxWidth()){
                    vm.saveReverse(name,prompt,reference,result!!.prompt,saved)
                }
                Primary("用这份提示词生图",Icons.Rounded.AddPhotoAlternate,modifier=Modifier.fillMaxWidth()){generate(prompt,if(faithful)listOfNotNull(reference)else emptyList())}
            }
            Spacer(Modifier.height(120.dp))
        }}
    }
}

@Composable private fun SettingRow(icon:ImageVector,title:String,subtitle:String?=null,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=15.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){AppText(title,fontWeight=FontWeight.Medium);if(subtitle!=null){Spacer(Modifier.height(4.dp));Hint(subtitle)}};Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Settings(vm:AppViewModel,open:(String)->Unit){
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){it?.let(vm::backup)}
    var restoreUri by remember{mutableStateOf<android.net.Uri?>(null)}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){restoreUri=it}
    val scroll=rememberScrollState()
    HeaderOverlay(header={Header("设置")}){headerHeight->
        FadingContent(Modifier.fillMaxSize(),top=headerHeight){Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start=22.dp,end=22.dp,top=headerHeight),verticalArrangement=Arrangement.spacedBy(18.dp)){
            Block{SettingRow(Icons.Rounded.BlurOn,"显示","液态玻璃 · 17 项材质调节"){open("display")};HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.07f));SettingRow(Icons.Rounded.Palette,"外观与文字","主题、字号与字体粗细"){open("appearance")};HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.07f));SettingRow(Icons.Rounded.Key,"模型连接","连接你的视觉模型"){open("api")}}
            SettingRow(Icons.Rounded.ChatBubbleOutline,"聊天模型","默认共用识图模型，可单独配置"){open("chat-api")}
            SettingRow(Icons.Rounded.History,"对话与出图记录","本机记录与清理"){open("history")}
            Block{AppText("本机数据",fontWeight=FontWeight.SemiBold);SettingRow(Icons.Rounded.FileUpload,"导出完整备份","提示词、原始版、修改版及封面"){export.launch("映词备份-${java.text.SimpleDateFormat("yyyyMMdd-HHmm",java.util.Locale.ROOT).format(java.util.Date())}.zip")};HorizontalDivider();SettingRow(Icons.Rounded.FileDownload,"从备份恢复","合并导入，不覆盖已有收藏"){restore.launch(arrayOf("application/zip","application/octet-stream"))};HorizontalDivider();SettingRow(Icons.Rounded.CleaningServices,"清理图片缓存","保留你的收藏和原始封面",vm::clearCache)}
            Block{AppText("映词",fontSize=23.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(5.dp));Hint("V${BuildConfig.VERSION_NAME}");Spacer(Modifier.height(14.dp));Hint("提示词与封面保存在本机。备份不含 API Key。卸载前请先导出备份。");Spacer(Modifier.height(10.dp));Hint("读取模型列表、测试连接与反推时访问你配置的模型服务。");Spacer(Modifier.height(10.dp));Hint("Liquid Glass · Oliver Nemo · MIT")}
            AuthorCredit()
            Spacer(Modifier.height(120.dp))
        }}
    }
    if(restoreUri!=null)AlertDialog(onDismissRequest={restoreUri=null},title={AppText("恢复这份备份？")},text={AppText("将备份中的提示词及封面添加到本机。即使名称或编号相同，也会保留为独立条目，现有数据不会被替换。")},confirmButton={TextButton(onClick={restoreUri?.let(vm::restore);restoreUri=null}){AppText("开始恢复")}},dismissButton={TextButton(onClick={restoreUri=null}){AppText("取消")}})
}

@Composable internal fun AuthorCredit(){
    val color=MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
        AppText("作者：",color=color,fontSize=12.sp,maxLines=1)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(16.dp).testTag("author-avatar").background(Color(0xFFFFFF00),androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(6.dp))
        AppText("海街寺庙",color=color,fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1)
    }
}





