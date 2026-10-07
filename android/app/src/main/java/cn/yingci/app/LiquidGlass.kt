package cn.yingci.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.*
import android.opengl.GLES30.*
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

internal data class GlassItem(val rect:Rect,val clip:Rect,val radius:Float,val kind:Int,val bitmap:Bitmap?=null,val pressure:Float=0f,val fade:EdgeFade?=null,val circle:Boolean=false,val mask:Bitmap?=null,val frost:Float?=null,val tint:Float?=null,val darkSurface:Boolean?=null)
internal fun glassShapeType(item:GlassItem):Int=when {item.circle->2;item.mask!=null->0;item.radius>=min(item.rect.width,item.rect.height)/2-1->1;else->0}
internal class GlassScene {
    val items=linkedMapOf<Any,GlassItem>()
    val revision=AtomicLong()
    @Volatile var origin=Rect.Zero
    @Volatile var dark=false
    @Volatile var reduced=false
    @Volatile var material=DisplayMaterial()
    var ready by mutableStateOf(false)
    var enabled=true
    var drawVersion by mutableLongStateOf(0L)
    var hostRoot:android.view.View?=null
    @Volatile var wake:(()->Unit)?=null
    fun put(key:Any,item:GlassItem)=synchronized(this){if(items[key]!=item){items[key]=item;drawVersion++;revision.incrementAndGet();wake?.invoke()};Unit}
    fun remove(key:Any)=synchronized(this){items.remove(key);drawVersion++;revision.incrementAndGet();wake?.invoke();Unit}
    fun environment(isDark:Boolean,isReduced:Boolean,value:DisplayMaterial){if(dark!=isDark||reduced!=isReduced||material!=value){dark=isDark;reduced=isReduced;material=value;revision.incrementAndGet();wake?.invoke()}}
    fun snapshot()=synchronized(this){items.values.toList()}
}
internal val LocalGlass=staticCompositionLocalOf<GlassScene?>{null}

@Composable internal fun Modifier.behindGlassMenu(includeComposer:Boolean=false):Modifier {
    val scene=LocalGlass.current?:return this
    var origin by remember{mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)}
    return onGloballyPositioned{origin=it.positionInWindow()}.drawWithContent{
        scene.drawVersion
        val path=Path()
        scene.snapshot().filter{it.kind==8||(includeComposer&&it.kind==9)}.forEach{path.addRoundRect(RoundRect(it.rect.translate(-origin.x,-origin.y),CornerRadius(it.radius)))}
        clipPath(path,ClipOp.Difference){this@drawWithContent.drawContent()}
    }
}

// Text is sampled behind glass, never painted twice over its native foreground.
// Kind 5: page text; kind 6: glass labels, included only beneath navigation.
@Composable internal fun Modifier.liquidGlass(kind:Int=0,radius:Float=22f,bitmap:Bitmap?=null,pressure:Float=0f,circle:Boolean=false,shape:Shape?=null,frost:Float?=null,tint:Float?=null,darkSurface:Boolean?=null):Modifier {
    val scene=LocalGlass.current?:return this
    val root=LocalView.current.rootView
    val outline=shape?:if(circle)CircleShape else RoundedCornerShape(androidx.compose.ui.unit.Dp(radius))
    val isDark=darkSurface?:LocalDark.current
    val fallback=(if(isDark)Color(0xFF242528)else Color(0xFFF0F2F6)).copy(alpha=if(tint!=null).2f+.58f*(tint/1.5f).coerceIn(0f,1f) else if(frost!=null).35f+frost*.4f else if(kind==7){if(isDark).62f else .5f}else 1f)
    if((!scene.enabled&&kind!=8&&kind!=9)||(scene.hostRoot!=null&&scene.hostRoot!==root))return if(kind<3||kind==4||kind>=7)this.background(fallback,outline)else this
    val key=remember{Any()};val localDensity=LocalDensity.current;val density=localDensity.density
    val direction=LocalLayoutDirection.current
    var size by remember{mutableStateOf(IntSize.Zero)}
    val mask=remember(shape,size,localDensity,direction){if(shape!=null&&size.width>0&&size.height>0){
        Bitmap.createBitmap(size.width.coerceAtMost(1024),size.height.coerceAtMost(2048),Bitmap.Config.ARGB_8888).also{b->val canvas=Canvas(b.asImageBitmap());canvas.scale(b.width.toFloat()/size.width,b.height.toFloat()/size.height);canvas.drawOutline(shape.createOutline(Size(size.width.toFloat(),size.height.toFloat()),direction,localDensity),Paint().apply{color=Color.White})}
    }else null}
    val fade=LocalEdgeFade.current
    DisposableEffect(scene,key){onDispose{scene.remove(key)}}
    SideEffect{synchronized(scene){scene.items[key]?.let{scene.put(key,it.copy(kind=kind,radius=radius*density,bitmap=bitmap,pressure=pressure,fade=fade,circle=circle,mask=mask,frost=frost,tint=tint,darkSurface=darkSurface))}}}
    return (if(!scene.ready && (kind<3||kind==4||kind>=7))this.background(fallback,outline) else this).onSizeChanged{size=it}.onGloballyPositioned{c->
        val p=c.positionInWindow();val end=c.localToWindow(androidx.compose.ui.geometry.Offset(c.size.width.toFloat(),c.size.height.toFloat()));scene.put(key,GlassItem(Rect(p.x,p.y,end.x,end.y),c.boundsInWindow(),radius*density,kind,bitmap,pressure,fade,circle,mask,frost,tint,darkSurface))
    }
}

@Composable internal fun GlassBackground(scene:GlassScene,modifier:Modifier){
    AndroidView(factory={GlassTexture(it,scene)},modifier=modifier.graphicsLayer{alpha=if(scene.ready)1f else 0f}.onGloballyPositioned{c->synchronized(scene){scene.origin=Rect(c.positionInWindow(),Size(c.size.width.toFloat(),c.size.height.toFloat()));scene.revision.incrementAndGet()}},onRelease={it.close()},update={it.changed()})
}

private class GlassTexture(context:Context,private val scene:GlassScene):TextureView(context),TextureView.SurfaceTextureListener {
    private var worker:HandlerThread?=null
    private var handler:Handler?=null
    private var renderer:GlassRenderer?=null
    private var visible=true
    init{isOpaque=true;surfaceTextureListener=this;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    override fun onAttachedToWindow(){super.onAttachedToWindow();scene.hostRoot=rootView}
    override fun onSurfaceTextureAvailable(surface:SurfaceTexture,width:Int,height:Int){
        val thread=HandlerThread("YingciGlass");thread.start();worker=thread
        handler=Handler(thread.looper).also{h->h.post{try{renderer=GlassRenderer(context,scene,surface,width,height);scene.wake={if(visible){h.removeCallbacks(frame);h.post(frame)}};h.post(frame)}catch(e:Exception){android.util.Log.e("YingciGlass","Renderer unavailable",e);post{scene.ready=false}}}}
    }
    private val frame=object:Runnable{override fun run(){
        if(!visible)return
        try{renderer?.draw();if(renderer!=null&&!scene.ready)post{if(handler!=null)scene.ready=true}}catch(e:Exception){android.util.Log.e("YingciGlass","Frame failed",e);post{scene.ready=false};return}
        handler?.removeCallbacks(this)
        if(scene.revision.get()!=renderer?.lastRevision)handler?.post(this)
    }}
    fun changed(){scene.revision.incrementAndGet();scene.wake?.invoke()}
    override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture,width:Int,height:Int){handler?.post{renderer?.resize(width,height);handler?.post(frame)}}
    override fun onSurfaceTextureUpdated(surface:SurfaceTexture){}
    override fun onSurfaceTextureDestroyed(surface:SurfaceTexture):Boolean{shutdown(surface);return false}
    private fun shutdown(surface:SurfaceTexture?){scene.wake=null;val h=handler;val thread=worker;handler=null;worker=null;if(h!=null){h.removeCallbacksAndMessages(null);h.post{renderer?.close();renderer=null;surface?.release();thread?.quitSafely()}}else surface?.release();scene.ready=false}
    fun close(){shutdown(null)}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);visible=visibility==VISIBLE;handler?.removeCallbacks(frame);if(visible)handler?.post(frame)}
}

private class GlassRenderer(context:Context,private val scene:GlassScene,surface:SurfaceTexture,width:Int,height:Int){
    private val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var eglContext:EGLContext=EGL14.EGL_NO_CONTEXT
    private var window:EGLSurface=EGL14.EGL_NO_SURFACE
    private var w=width;private var h=height
    private var rt=0;private var baseRt=0;private var fbo=0
    private val textures=mutableMapOf<Bitmap,Int>()
    private val uniforms=mutableMapOf<Pair<Int,String>,Int>()
    private var paint=0;private var present=0;private var glass=0;private var down=0;private var background=0
    private var mipLevels=7
    var lastRevision=-1L
        private set
    private var lastDark=false
    private var lastReduced=false
    private var lastBackdropItems=emptyList<GlassItem>()
    private val density=context.resources.displayMetrics.density
    init{
        check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
        val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
        check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,count,0)&&count[0]>0)
        eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
        window=EGL14.eglCreateWindowSurface(display,configs[0],surface,intArrayOf(EGL14.EGL_NONE),0)
        check(EGL14.eglMakeCurrent(display,window,window,eglContext))
        fun source(name:String)=context.assets.open("glass/$name").bufferedReader().use{it.readText()}
        val vertex=source("fullscreen.vert")
        fun program(name:String):Int{
            fun compile(type:Int,text:String):Int{val id=glCreateShader(type);glShaderSource(id,text);glCompileShader(id);val ok=IntArray(1);glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){glGetShaderInfoLog(id)};return id}
            val v=compile(GL_VERTEX_SHADER,vertex);val f=compile(GL_FRAGMENT_SHADER,if(name=="reference-v2.frag")glassOverlayShader(source(name))else source(name));val p=glCreateProgram();glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p);val ok=IntArray(1);glGetProgramiv(p,GL_LINK_STATUS,ok,0);check(ok[0]!=0){glGetProgramInfoLog(p)};glDeleteShader(v);glDeleteShader(f);return p
        }
        paint=program("paint.frag");present=program("reference-present.frag");glass=program("reference-v2.frag");down=program("reference-down.frag")
        background=program("background.frag")
        rt=texture();val ids=IntArray(1);glGenFramebuffers(1,ids,0);fbo=ids[0];resize(width,height)
    }
    private fun texture():Int{val ids=IntArray(1);glGenTextures(1,ids,0);glBindTexture(GL_TEXTURE_2D,ids[0]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return ids[0]}
    fun resize(width:Int,height:Int){w=width;h=height;glDeleteTextures(1,intArrayOf(rt),0);rt=texture()
        mipLevels=min(7,floor(log2(max(w,h).toFloat())).toInt()+1)
        val halfFloat=glGetString(GL_EXTENSIONS).orEmpty().contains("color_buffer_float")||glGetString(GL_EXTENSIONS).orEmpty().contains("color_buffer_half_float")
        var format=if(halfFloat)GL_RGBA16F else GL_RGBA8
        glTexStorage2D(GL_TEXTURE_2D,mipLevels,format,w,h)
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,rt,0)
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE){format=GL_RGBA8;glDeleteTextures(1,intArrayOf(rt),0);rt=texture();glTexStorage2D(GL_TEXTURE_2D,mipLevels,format,w,h);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,rt,0)}
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE)
        glDeleteTextures(1,intArrayOf(baseRt),0);baseRt=texture();glTexStorage2D(GL_TEXTURE_2D,1,format,w,h)
        glBindFramebuffer(GL_FRAMEBUFFER,0);lastRevision=-1}
    private fun buildMips(){
        glBindTexture(GL_TEXTURE_2D,rt)
        mipLevels=min(7,floor(log2(max(w,h).toFloat())).toInt()+1)
        glUseProgram(down);i(down,"uTex",0)
        for(level in 1 until mipLevels){
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_BASE_LEVEL,level-1);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,level-1)
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,rt,level)
            glViewport(0,0,max(1,w shr level),max(1,h shr level));glUniform2f(u(down,"uTexel"),1f/max(1,w shr (level-1)),1f/max(1,h shr (level-1)));drawTriangle()
        }
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_BASE_LEVEL,0);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,mipLevels-1)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR)
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,rt,0);glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,w,h)
    }
    private fun u(p:Int,name:String)=uniforms.getOrPut(p to name){glGetUniformLocation(p,name)}
    private fun f(p:Int,name:String,v:Float)=glUniform1f(u(p,name),v)
    private fun i(p:Int,name:String,v:Int)=glUniform1i(u(p,name),v)
    private fun rect(p:Int,name:String,r:Rect)=glUniform4f(u(p,name),r.left,r.top,r.width,r.height)
    private fun clip(r:Rect,pad:Float=0f){val l=max(0f,r.left-pad).toInt();val t=max(0f,r.top-pad).toInt();val rr=min(w.toFloat(),r.right+pad).toInt();val b=min(h.toFloat(),r.bottom+pad).toInt();glScissor(l,max(0,h-b),max(0,rr-l),max(0,b-t))}
    private fun drawTriangle()=glDrawArrays(GL_TRIANGLES,0,3)
    fun draw(){
        val rev=scene.revision.get();val dark=scene.dark;val reduced=scene.reduced
        if(reduced&&rev==lastRevision&&dark==lastDark&&reduced==lastReduced)return
        val origin=scene.origin;val items=scene.snapshot().map{it.copy(rect=it.rect.translate(-origin.left,-origin.top),clip=it.clip.translate(-origin.left,-origin.top),fade=it.fade?.let{f->f.copy(rect=f.rect.translate(-origin.left,-origin.top))})}.filter{it.rect.width>0 && it.rect.height>0 && it.clip.width>0 && it.clip.height>0}.sortedByDescending{it.rect.width*it.rect.height}
        val navigation=items.filter{it.kind==2}
        val labels=items.filter{it.kind==6&&it.bitmap!=null}.flatMap{item->navigation.mapNotNull{nav->
            val clipped=item.clip.intersect(nav.rect)
            if(clipped.width>0&&clipped.height>0)item.copy(clip=clipped)else null
        }}
        val backdropItems=(items.filter{it.kind==1||it.kind==3||it.kind==5}+labels).map{it.copy(pressure=0f)}
        glViewport(0,0,w,h);glActiveTexture(GL_TEXTURE0)
        if(lastRevision<0||backdropItems!=lastBackdropItems||dark!=lastDark){
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glDisable(GL_SCISSOR_TEST);glDisable(GL_BLEND)
            val bg=if(dark)floatArrayOf(.055f,.067f,.082f)else floatArrayOf(.98f,.984f,.996f)
            glClearColor(bg[0].pow(2.2f),bg[1].pow(2.2f),bg[2].pow(2.2f),1f);glClear(GL_COLOR_BUFFER_BIT)
            glUseProgram(background);f(background,"uDark",if(dark)1f else 0f);drawTriangle()
            glUseProgram(paint);glUniform2f(u(paint,"uResolution"),w.toFloat(),h.toFloat());i(paint,"uAtlas",0);f(paint,"uDark",if(dark)1f else 0f)
            glEnable(GL_SCISSOR_TEST);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA)
            val layers=items.filter{it.kind==1||it.kind==3}+items.filter{it.kind==5&&it.bitmap!=null}+labels
            var baseCopied=false
            for(item in layers){
                if(item.kind>=5&&!baseCopied){glBindTexture(GL_TEXTURE_2D,baseRt);glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,w,h);baseCopied=true}
                // Clear glass never receives an opaque backing panel.
                rect(paint,"uRect",item.rect);glUniform4f(u(paint,"uClip"),item.clip.left,item.clip.top,item.clip.right,item.clip.bottom);f(paint,"uRadius",item.radius)
                val fade=item.fade;glUniform4f(u(paint,"uFade"),fade?.rect?.top?:0f,fade?.rect?.bottom?:h.toFloat(),fade?.top?:0f,fade?.bottom?:0f)
                val image=item.bitmap
                if(image!=null){val tex=textures.getOrPut(image){texture().also{GLUtils.texImage2D(GL_TEXTURE_2D,0,image,0)}};glBindTexture(GL_TEXTURE_2D,tex);glUniform2f(u(paint,"uImageSize"),image.width.toFloat(),image.height.toFloat());i(paint,"uImage",if(item.kind>=5)3 else 1)}
                else{i(paint,"uImage",if(item.kind==1)2 else 0);glUniform2f(u(paint,"uImageSize"),1f,1f);glBindTexture(GL_TEXTURE_2D,0)}
                glUniform4f(u(paint,"uSource"),0f,0f,1f,1f)
                val c=if(dark).125f else .94f;glUniform4f(u(paint,"uColor"),c,c+.01f,c+.025f,1f)
                clip(item.clip.intersect(item.rect));drawTriangle()
            }
            if(!baseCopied){glBindTexture(GL_TEXTURE_2D,baseRt);glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,w,h)}
            glDisable(GL_BLEND);glDisable(GL_SCISSOR_TEST);glBindFramebuffer(GL_FRAMEBUFFER,0);buildMips()
            lastBackdropItems=backdropItems;lastDark=dark
        }
        lastRevision=rev
        val used=items.flatMap{listOfNotNull(it.bitmap,it.mask)}.toSet();textures.keys.filter{it !in used}.forEach{bitmap->glDeleteTextures(1,intArrayOf(textures.remove(bitmap)!!),0)}
        lastReduced=reduced
        glBindFramebuffer(GL_FRAMEBUFFER,0);glDisable(GL_BLEND);glDisable(GL_SCISSOR_TEST);glBindTexture(GL_TEXTURE_2D,baseRt);glUseProgram(present);i(present,"uTex",0);drawTriangle()
        glBindTexture(GL_TEXTURE_2D,rt)
        val material=scene.material
        glUseProgram(glass);glUniform2f(u(glass,"uRes"),w.toFloat(),h.toFloat());i(glass,"uSrc",0);f(glass,"uDpr",density);f(glass,"uMips",mipLevels.toFloat());i(glass,"uShapeCount",1)
        for(key in listOf("refraction","edgeReach","edgeWidth","dispersion","backdropBlur","body","absorption","rim","reflection","highlight","echo","hairline","hairWidth"))f(glass,"u"+key.replaceFirstChar{it.uppercase()},material[key]*(if(key=="refraction"||key=="backdropBlur")density else 1f))
        glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glEnable(GL_SCISSOR_TEST)
        for(item in items.filter{it.kind<3||it.kind==4||it.kind>=7}.sortedBy{when(it.kind){8->2;9->1;else->0}}){
            val short=min(item.rect.width,item.rect.height)
            glUniform2f(u(glass,"uShapeCenters[0]"),item.rect.center.x,h-item.rect.center.y);glUniform2f(u(glass,"uShapeHalves[0]"),item.rect.width/2,item.rect.height/2)
            i(glass,"uShapeTypes[0]",glassShapeType(item))
            f(glass,"uShapeRadii[0]",if(item.mask!=null)0f else min(short/2,item.radius*material["roundness"]/.47f))
            val frost=item.frost?:if(item.kind==4||item.kind==7)1f else 0f
            f(glass,"uShapeTints[0]",item.tint?:if(item.kind==7){if(dark)1.15f else .9f}else material["tint"])
            f(glass,"uBackdropBlur",32f*density*frost)
            f(glass,"uShapeTintLights[0]",if(item.darkSurface?:dark)0f else 1f);f(glass,"uShapeFrosts[0]",frost);f(glass,"uShapeOpacities[0]",1f);f(glass,"uShapePressures[0]",item.pressure);glUniform2f(u(glass,"uShapePressAxes[0]"),1f,1f)
            val fade=item.fade;glUniform4f(u(glass,"uAppFade"),fade?.rect?.top?:0f,fade?.rect?.bottom?:h.toFloat(),fade?.top?:0f,fade?.bottom?:0f)
            rect(glass,"uAppRect",item.rect);i(glass,"uAppHasMask",if(item.mask!=null)1 else 0);i(glass,"uAppMask",1)
            item.mask?.let{image->glActiveTexture(GL_TEXTURE1);val tex=textures.getOrPut(image){texture().also{GLUtils.texImage2D(GL_TEXTURE_2D,0,image,0)}};glBindTexture(GL_TEXTURE_2D,tex);glActiveTexture(GL_TEXTURE0)}
            val angle=material["lightAngle"]*PI.toFloat()/180f;glUniform2f(u(glass,"uLightDirs[0]"),cos(angle),sin(angle))
            clip(item.clip.intersect(Rect(item.rect.left-8*density,item.rect.top-8*density,item.rect.right+8*density,item.rect.bottom+8*density)));drawTriangle()
        }
        glDisable(GL_SCISSOR_TEST);glDisable(GL_BLEND);check(EGL14.eglSwapBuffers(display,window))
    }
    fun close(){glDeleteTextures(2,intArrayOf(rt,baseRt),0);textures.values.forEach{glDeleteTextures(1,intArrayOf(it),0)};glDeleteFramebuffers(1,intArrayOf(fbo),0);listOf(paint,present,glass,down,background).forEach{glDeleteProgram(it)};EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,window);EGL14.eglDestroyContext(display,eglContext);EGL14.eglTerminate(display)}
}
