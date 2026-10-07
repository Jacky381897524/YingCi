package cn.yingci.app

// Keep the MIT upstream shader intact. The host adds native bubble clipping and viewport fading.
internal fun glassOverlayShader(source:String):String {
    val output="outColor = vec4(premultiplied * surfaceOpacity, alpha * surfaceOpacity);"
    require(output in source)
    return source.replace("uniform vec2 uRes;","""
uniform vec2 uRes;
uniform sampler2D uAppMask;
uniform int uAppHasMask;
uniform vec4 uAppRect;
uniform vec4 uAppFade;
""".trimIndent()).replace(output,"""
vec2 appPixel = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y);
float appAlpha = 1.0;
if (uAppHasMask == 1) appAlpha *= texture(uAppMask, (appPixel - uAppRect.xy) / uAppRect.zw).a;
if (uAppFade.z > 0.0) appAlpha *= smoothstep(0.0, 1.0, (appPixel.y - uAppFade.x) / uAppFade.z);
if (uAppFade.w > 0.0) appAlpha *= smoothstep(0.0, 1.0, (uAppFade.y - appPixel.y) / uAppFade.w);
outColor = vec4(premultiplied * surfaceOpacity, alpha * surfaceOpacity) * appAlpha;
""".trimIndent())
}
