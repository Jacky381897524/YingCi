#version 300 es
precision highp float;
uniform vec2 uResolution;
uniform vec4 uRect;
uniform vec4 uClip;
uniform float uRadius;
uniform vec4 uColor;
uniform sampler2D uAtlas;
uniform vec4 uSource;
uniform vec2 uImageSize;
uniform int uImage;
uniform float uDark;
uniform vec4 uFade;
out vec4 fragColor;
float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}
void main() {
    vec2 pixel = vec2(gl_FragCoord.x, uResolution.y-gl_FragCoord.y);
    if (pixel.x<uClip.x || pixel.y<uClip.y || pixel.x>uClip.z || pixel.y>uClip.w) discard;
    float aspect = uResolution.x/uResolution.y;
    vec2 uv=pixel/uResolution;
    vec2 p=(uv-0.5)*vec2(aspect,1.0);
    vec2 center=((uRect.xy+uRect.zw*0.5)/uResolution-0.5)*vec2(aspect,1.0);
    float sd=roundedBox(p-center, uRect.zw/uResolution.y*0.5, uRadius/uResolution.y);
    float aa=max(fwidth(sd),0.5/uResolution.y);
    float mask=1.0-smoothstep(-aa,aa,sd);
    vec4 col=uColor;
    if(uImage==2){
        // Blue liquid belongs to the background pass; glass refracts it later.
        vec2 local=(pixel-uRect.xy)/uRect.zw;
        float pool=exp(-pow((local.y-.18)/.55,2.0));
        float sweep=.5+.5*sin(local.x*4.2+local.y*2.0);
        vec3 low=mix(vec3(.045,.24,.77),vec3(.78,.55,.06),uDark);
        vec3 high=mix(vec3(.10,.49,.98),vec3(1.0,.85,.30),uDark);
        col=vec4(mix(low,high,pool*.75+sweep*.15),1.0);
    }
    if(uImage==1){
        vec2 texUV=(pixel-uRect.xy)/uRect.zw;
        float imageAspect=(uSource.z*uImageSize.x)/(uSource.w*uImageSize.y);
        float frameAspect=uRect.z/uRect.w;
        if(imageAspect>frameAspect) texUV.x=(texUV.x-.5)*frameAspect/imageAspect+.5;
        else texUV.y=(texUV.y-.5)*imageAspect/frameAspect+.5;
        col=texture(uAtlas,uSource.xy+texUV*uSource.zw);
    }
    if(uImage==3){
        col=texture(uAtlas,(pixel-uRect.xy)/uRect.zw);
        if(col.a>0.001)col.rgb/=col.a;
    }
    // Text glyphs enter only the refraction texture; the base surface stays text-free.
    col.rgb=mix(pow((max(col.rgb,vec3(0.0))+.055)/1.055,vec3(2.4)),col.rgb/12.92,lessThanEqual(col.rgb,vec3(.04045)));
    float fade=1.0;
    if(uFade.z>0.0) fade*=smoothstep(0.0,1.0,(pixel.y-uFade.x)/uFade.z);
    if(uFade.w>0.0) fade*=smoothstep(0.0,1.0,(uFade.y-pixel.y)/uFade.w);
    fragColor=vec4(col.rgb,col.a*mask*fade);
}
