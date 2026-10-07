package cn.yingci.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GlassSlider(value:Float,onValueChange:(Float)->Unit,valueRange:ClosedFloatingPointRange<Float>,steps:Int=0){
    val trackColor=MaterialTheme.colorScheme.onSurface.copy(alpha=.12f)
    val activeColor=MaterialTheme.colorScheme.primary
    Slider(value,onValueChange,Modifier.fillMaxWidth(),steps=steps,valueRange=valueRange,
        thumb={Box(Modifier.size(26.dp).liquidGlass(0,13f,circle=true))},
        track={
            Canvas(Modifier.fillMaxWidth().height(3.dp)){
                val start=androidx.compose.ui.geometry.Offset(0f,size.height/2)
                val end=androidx.compose.ui.geometry.Offset(size.width,size.height/2)
                val fraction=((value-valueRange.start)/(valueRange.endInclusive-valueRange.start)).coerceIn(0f,1f)
                drawLine(trackColor,start,end,strokeWidth=size.height,cap=androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(activeColor,start,end.copy(x=size.width*fraction),strokeWidth=size.height,cap=androidx.compose.ui.graphics.StrokeCap.Round)
                if(steps>0)for(i in 0..steps+1)drawCircle(if(i.toFloat()/(steps+1)<=fraction)activeColor else trackColor,radius=4.dp.toPx(),center=end.copy(x=size.width*i/(steps+1)))
            }
        })
}

@Composable internal fun GlassSwitch(value:Boolean,onChange:(Boolean)->Unit){
    val reduced=LocalPrefs.current.reduceMotion
    val offset by animateDpAsState(if(value)20.dp else 3.dp,if(reduced)tween(0)else spring(dampingRatio=.8f,stiffness=500f),label="开关滑动")
    Box(Modifier.size(48.dp,44.dp).toggleable(value=value,role=Role.Switch,onValueChange=onChange),contentAlignment=Alignment.Center){
        Box(Modifier.size(46.dp,28.dp).liquidGlass(if(value)1 else 0,16f)){
            Box(Modifier.offset{IntOffset(offset.roundToPx(),3.dp.roundToPx())}.size(22.dp).liquidGlass(0,12f,circle=true))
        }
    }
}

@Composable fun GlassSelector(labels:List<String>,selected:Int,dark:Boolean,reduced:Boolean,modifier:Modifier=Modifier,onSelect:(Int)->Unit){
    val navigation=labels==listOf("收藏","反推","生图","设置")
    val height=if(navigation)68.dp else 48.dp
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center){
        val width=maxWidth;val cell=(width-12.dp)/labels.size
        val cellPx=with(LocalDensity.current){cell.toPx()}
        var drag by remember{mutableStateOf<Float?>(null)}
        val sources=remember(labels){labels.map{MutableInteractionSource()}}
        val pressed=sources.map{it.collectIsPressedAsState().value}.any{it}
        val pressure by animateFloatAsState(if(!reduced&&(pressed||drag!=null))1f else 0f,if(reduced)tween(0)else spring(dampingRatio=.65f,stiffness=520f),label="玻璃按压膨胀")
        val target=drag?:selected.toFloat()
        val position by animateFloatAsState(target,if(reduced)tween(0)else spring(dampingRatio=.82f,stiffness=420f),label="玻璃滑块")
        Box(Modifier.fillMaxWidth().height(height).liquidGlass(if(navigation)2 else 4,34f).pointerInput(labels,selected,cellPx){
            detectHorizontalDragGestures(onDragStart={drag=selected.toFloat()},onDragEnd={val index=(drag?:selected.toFloat()).roundToInt().coerceIn(labels.indices);drag=null;onSelect(index)},onDragCancel={drag=null}){change,delta->change.consume();drag=((drag?:selected.toFloat())+delta/cellPx).coerceIn(0f,(labels.size-1).toFloat())}
        }){
            Box(Modifier.offset{IntOffset((6.dp+cell*position-cell*.065f*pressure).roundToPx(),(5.dp-2.dp*pressure).roundToPx())}.width(cell*(1f+.13f*pressure)).height(height-10.dp+4.dp*pressure).liquidGlass(0,30f,pressure=pressure))
            Row(Modifier.fillMaxSize().padding(horizontal=6.dp)){
                labels.forEachIndexed{i,label->
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(30.dp)).semantics{this.selected=i==selected;role=Role.Tab}.clickable(interactionSource=sources[i],indication=null){onSelect(i)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                        val color=if(i==selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        if(navigation)Icon(listOf(Icons.Rounded.Bookmark,Icons.Rounded.DocumentScanner,Icons.Rounded.AddPhotoAlternate,Icons.Rounded.Settings)[i],label,Modifier.size(30.dp),tint=color)
                        else GlassText(label,fontSize=13.sp,maxLines=1)
                    }
                }
            }
        }
    }
}
