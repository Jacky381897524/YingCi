package cn.yingci.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class EdgeFade(val rect:Rect,val top:Float,val bottom:Float) {
    fun alpha(y:Float)=minOf(if(top>0)smoothFade((y-rect.top)/top)else 1f,if(bottom>0)smoothFade((rect.bottom-y)/bottom)else 1f)
}
internal fun smoothFade(value:Float):Float {val t=value.coerceIn(0f,1f);return t*t*(3f-2f*t)}
internal val LocalEdgeFade=staticCompositionLocalOf<EdgeFade?>{null}

// The scrolling viewport includes the header; only the fixed controls sit above it.
@Composable internal fun HeaderOverlay(modifier:Modifier=Modifier,header:@Composable ()->Unit,content:@Composable (Dp)->Unit){
    val density=LocalDensity.current
    var height by remember{mutableStateOf(82.dp)}
    Box(modifier.fillMaxSize()){
        content(height)
        Box(Modifier.fillMaxWidth().onSizeChanged{height=with(density){it.height.toDp()}}){header()}
    }
}

// Native content and the GL scene use the same window-space fade bounds.
@Composable internal fun FadingContent(modifier:Modifier=Modifier,top:Dp=28.dp,bottom:Dp=96.dp,content:@Composable BoxScope.()->Unit){
    var rect by remember{mutableStateOf(Rect.Zero)}
    val density=LocalDensity.current
    val topPx=with(density){top.toPx()};val bottomPx=with(density){bottom.toPx()}
    Box(modifier.onGloballyPositioned{rect=it.boundsInWindow()}.graphicsLayer{compositingStrategy=CompositingStrategy.Offscreen}.drawWithContent{
        drawContent()
        if(topPx>0)drawRect(Brush.verticalGradient(*Array(17){i->val t=i/16f;t to Color.Black.copy(alpha=smoothFade(t))},startY=0f,endY=topPx.coerceAtMost(size.height/2)),blendMode=BlendMode.DstIn)
        if(bottomPx>0)drawRect(Brush.verticalGradient(*Array(17){i->val t=i/16f;t to Color.Black.copy(alpha=1f-smoothFade(t))},startY=(size.height-bottomPx).coerceAtLeast(size.height/2),endY=size.height),blendMode=BlendMode.DstIn)
    }){CompositionLocalProvider(LocalEdgeFade provides EdgeFade(rect,topPx.coerceAtMost(rect.height/2),bottomPx.coerceAtMost(rect.height/2))){content()}}
}
