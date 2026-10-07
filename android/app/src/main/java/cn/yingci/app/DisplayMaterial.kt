package cn.yingci.app

import org.json.JSONObject
import kotlin.math.round

internal data class DisplayParameter(val key:String,val label:String,val group:String,val default:Float,val min:Float,val max:Float,val step:Float)
internal val DisplayParameters=listOf(
    DisplayParameter("refraction","折射强度","光学",84f,0f,110f,1f),
    DisplayParameter("edgeReach","边缘采样范围","光学",.14f,0f,1.6f,.01f),
    DisplayParameter("edgeWidth","折射边缘宽度","光学",.21f,0f,.55f,.01f),
    DisplayParameter("dispersion","色散","光学",2f,0f,7f,.1f),
    DisplayParameter("frost","磨砂柔化","质感",0f,0f,1f,.01f),
    DisplayParameter("backdropBlur","背景模糊","质感",0f,0f,64f,1f),
    DisplayParameter("body","材质厚重感","质感",.72f,0f,1.5f,.01f),
    DisplayParameter("absorption","光线吸收","质感",.58f,0f,2f,.01f),
    DisplayParameter("tint","染色强度","质感",0f,0f,1.5f,.01f),
    DisplayParameter("rim","边缘反光","光照",.24f,0f,1f,.01f),
    DisplayParameter("reflection","环境反射","光照",.31f,0f,1.5f,.01f),
    DisplayParameter("highlight","高光强度","光照",.34f,0f,1.5f,.01f),
    DisplayParameter("lightAngle","光源角度","光照",136f,-180f,180f,1f),
    DisplayParameter("echo","内部反射","光照",.28f,0f,1.5f,.01f),
    DisplayParameter("hairline","轮廓线强度","轮廓",.92f,0f,1.5f,.01f),
    DisplayParameter("hairWidth","轮廓线宽度","轮廓",.52f,0f,1f,.01f),
    DisplayParameter("roundness","圆角比例","轮廓",.47f,.05f,.6f,.01f)
)
data class DisplayMaterial(val values:Map<String,Float> = emptyMap()) {
    operator fun get(key:String):Float {val p=DisplayParameters.first{it.key==key};return values[key]?.takeIf{it.isFinite()}?.coerceIn(p.min,p.max)?:p.default}
    fun with(key:String,value:Float):DisplayMaterial {val p=DisplayParameters.first{it.key==key};val safe=if(value.isFinite())(p.min+round((value-p.min)/p.step)*p.step).coerceIn(p.min,p.max)else p.default;return copy(values=values+(key to safe))}
    fun json()=JSONObject().apply{DisplayParameters.forEach{put(it.key,this@DisplayMaterial[it.key])}}.toString()
    companion object {
        fun parse(raw:String):DisplayMaterial=runCatching{val o=JSONObject(raw);DisplayMaterial(DisplayParameters.associate{p->p.key to o.optDouble(p.key,p.default.toDouble()).toFloat()})}.getOrDefault(DisplayMaterial())
    }
}
