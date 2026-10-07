package cn.yingci.app

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.util.Locale

@Composable internal fun DisplaySettings(vm:AppViewModel,back:()->Unit){
    val prefs=LocalPrefs.current
    var draft by remember { mutableStateOf(prefs.display) }
    val scene=LocalGlass.current
    var advanced by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val specimen=remember { context.assets.open("preview-lake.webp").use { BitmapFactory.decodeStream(it) } }
    fun update(value:DisplayMaterial){draft=value;scene?.material=value;scene?.revision?.incrementAndGet();scene?.wake?.invoke()}
    fun close(){if(draft!=prefs.display)leave=true else back()}
    BackHandler { close() }
    DisposableEffect(Unit){onDispose{scene?.material=vm.preferences.value.display;scene?.revision?.incrementAndGet();scene?.wake?.invoke()}}
    Column(Modifier.fillMaxSize()){
        Header("显示",back={close()})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(8.dp)).liquidGlass(3,8f,specimen),contentAlignment=Alignment.Center){
                if(scene?.ready!=true)Image(specimen.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                val labelColor=if(scene?.ready==true)Color.White else MaterialTheme.colorScheme.onSurface
                Row(Modifier.width(200.dp).height(64.dp).liquidGlass(0,32f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Icon(Icons.Rounded.BookmarkBorder,null,tint=labelColor);Spacer(Modifier.width(10.dp));AppText("玻璃预览",color=labelColor,fontSize=17.sp,fontWeight=FontWeight.Medium)}
            }
            AppText("液态玻璃",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            val common=listOf("refraction","dispersion","frost","tint","highlight")
            val parameters=if(advanced)DisplayParameters.filter{it.key !in common}else common.map{key->DisplayParameters.first{it.key==key}}
            parameters.forEach{p->
                Column{
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){AppText(p.label,Modifier.weight(1f),fontSize=15.sp);AppText(String.format(Locale.ROOT,if(p.step>=1)"%.0f"else"%.2f",draft[p.key]),fontSize=14.sp)}
                    GlassSlider(draft[p.key],{update(draft.with(p.key,it))},valueRange=p.min..p.max)
                }
            }
            TextButton(onClick={advanced=!advanced}){AppText(if(advanced)"常用参数"else"更多参数");Icon(if(advanced)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null)}
        }
        Row(Modifier.fillMaxWidth().padding(22.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={update(DisplayMaterial())},modifier=Modifier.weight(1f)){AppText("恢复默认")}
            Primary("保存",modifier=Modifier.weight(1f)){vm.savePreferences(prefs.copy(display=draft)){back()}}
        }
    }
    if(leave)DiscardDialog(back){leave=false}
}
