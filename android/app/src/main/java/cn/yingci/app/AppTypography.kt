package cn.yingci.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.LocalContentColor

internal val LocalGlassForeground=staticCompositionLocalOf{false}
internal val LocalNativeSurface=staticCompositionLocalOf{false}

@Composable private fun Modifier.behindNavigation(active:Boolean):Modifier {
    val scene=LocalGlass.current?:return this
    var origin by remember{mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)}
    return onGloballyPositioned{origin=it.positionInWindow()}.drawWithContent{
        scene.drawVersion
        if(active&&scene.ready){
            val mask=Path()
            scene.snapshot().filter{it.kind==2}.forEach{item->mask.addRoundRect(RoundRect(item.rect.translate(-origin.x,-origin.y),CornerRadius(item.radius)))}
            clipPath(mask,ClipOp.Difference){this@drawWithContent.drawContent()}
        }else drawContent()
    }
}

@Composable internal fun AppText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,fontSize:TextUnit=TextUnit.Unspecified,fontWeight:FontWeight?=null,lineHeight:TextUnit=TextUnit.Unspecified,maxLines:Int=Int.MAX_VALUE,overflow:TextOverflow=TextOverflow.Clip,style:TextStyle=MaterialTheme.typography.bodyLarge,refractionKind:Int=5){
    val delta=LocalPrefs.current.fontWeight-400
    val weight=(fontWeight?.weight?.plus(delta)?:style.fontWeight?.weight?:LocalPrefs.current.fontWeight).coerceIn(100,900)
    val scene=LocalGlass.current
    val foreground=LocalGlassForeground.current
    val root=LocalView.current.rootView
    val density=LocalDensity.current
    val actualColor=if(color==Color.Unspecified)LocalContentColor.current else color
    var bitmap by remember(text,actualColor,fontSize,weight,style,lineHeight,maxLines,density){mutableStateOf<android.graphics.Bitmap?>(null)}
    var rasterizedSize by remember(text,actualColor,fontSize,weight,style,lineHeight,maxLines,density){mutableStateOf<androidx.compose.ui.unit.IntSize?>(null)}
    val capture=scene!=null&&scene.enabled&&!LocalNativeSurface.current&&(!foreground||refractionKind==6)&&(scene.hostRoot==null||scene.hostRoot===root)
    val paintModifier=if(capture)Modifier.liquidGlass(refractionKind,0f,bitmap).behindNavigation(bitmap!=null)else Modifier
    androidx.compose.material3.Text(text,modifier.then(paintModifier),color=actualColor,fontSize=fontSize,fontWeight=FontWeight(weight),lineHeight=lineHeight,maxLines=maxLines,overflow=overflow,style=style,onTextLayout={layout->
        if(capture&&layout.size!=rasterizedSize&&layout.size.width>0&&layout.size.height>0){
            val scale=minOf(1f,kotlin.math.sqrt(2_000_000f/(layout.size.width.toFloat()*layout.size.height)),8192f/layout.size.height)
            val image=android.graphics.Bitmap.createBitmap((layout.size.width*scale).toInt().coerceAtLeast(1),(layout.size.height*scale).toInt().coerceAtLeast(1),android.graphics.Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image.asImageBitmap());canvas.scale(scale,scale);layout.multiParagraph.paint(canvas,actualColor,shadow=style.shadow);bitmap=image;rasterizedSize=layout.size
        }
    })
}

@Composable internal fun CollectionTitle(text:String,modifier:Modifier=Modifier){
    val measure=rememberTextMeasurer();val density=LocalDensity.current
    val label=text.replace(Regex("[\\r\\n\\u0085\\u2028\\u2029]")," ")
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min=24.dp),contentAlignment=Alignment.Center){
        val width=with(density){maxWidth.roundToPx()}.coerceIn(1,10000)
        var size=13f
        var style=MaterialTheme.typography.bodySmall.copy(fontSize=size.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center,shadow=androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha=.75f),androidx.compose.ui.geometry.Offset(0f,with(density){.4.dp.toPx()}),with(density){.8.dp.toPx()}))
        AppText(label,Modifier.fillMaxWidth(),color=Color.White,style=style,maxLines=1,overflow=TextOverflow.Ellipsis,refractionKind=6)
    }
}

@Composable internal fun GlassText(text:String,modifier:Modifier=Modifier,fontSize:TextUnit=15.sp,maxLines:Int=2,action:Boolean=false){
    val dark=LocalDark.current;val measure=rememberTextMeasurer();val density=LocalDensity.current
    val scene=LocalGlass.current;val root=LocalView.current.rootView
    val clipNavigation=scene!=null&&scene.enabled&&(scene.hostRoot==null||scene.hostRoot===root)
    BoxWithConstraints(modifier.behindNavigation(clipNavigation),contentAlignment=Alignment.Center){
        val width=with(density){maxWidth.roundToPx()}.coerceIn(1,10000)
        val height=with(density){maxHeight.roundToPx()}.coerceIn(1,10000)
        var size=fontSize.value
        var style=MaterialTheme.typography.bodyMedium.copy(fontSize=size.sp,lineHeight=(size*1.35f).sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
        while(size>10f&&measure.measure(AnnotatedString(text),style,constraints=Constraints(maxWidth=width,maxHeight=height),maxLines=maxLines).hasVisualOverflow){size-=.5f;style=style.copy(fontSize=size.sp,lineHeight=(size*1.35f).sp)}
        CompositionLocalProvider(LocalGlassForeground provides true){
            AppText(text,color=if(action)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,style=style,maxLines=maxLines,overflow=TextOverflow.Ellipsis,refractionKind=6)
        }
    }
}

internal fun appTypography(weight:Int):Typography {
    val base=Typography()
    fun TextStyle.adjust()=copy(fontFamily=FontFamily.SansSerif,letterSpacing=0.sp,fontWeight=FontWeight(((fontWeight?.weight?:400)+weight-400).coerceIn(100,900)))
    return base.copy(displayLarge=base.displayLarge.adjust(),displayMedium=base.displayMedium.adjust(),displaySmall=base.displaySmall.adjust(),headlineLarge=base.headlineLarge.adjust(),headlineMedium=base.headlineMedium.adjust(),headlineSmall=base.headlineSmall.adjust(),titleLarge=base.titleLarge.adjust(),titleMedium=base.titleMedium.adjust(),titleSmall=base.titleSmall.adjust(),bodyLarge=base.bodyLarge.adjust(),bodyMedium=base.bodyMedium.adjust(),bodySmall=base.bodySmall.adjust(),labelLarge=base.labelLarge.adjust(),labelMedium=base.labelMedium.adjust(),labelSmall=base.labelSmall.adjust())
}
