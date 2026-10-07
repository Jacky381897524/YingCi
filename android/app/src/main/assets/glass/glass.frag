#version 300 es
precision highp float;
uniform vec2 uResolution;
uniform sampler2D paintComposeRT;
uniform vec4 uRect;
uniform float uRadius;
uniform int uShape;
uniform vec4 uMergeCircle;
uniform float uNormalTransition;
uniform float uGlassThickness;
uniform float uBackgroundDistance;
uniform float uIOR;
uniform float uDispersion;
uniform float uRoughness;
uniform float uFaceLod;
uniform float uTime;
uniform vec4 uTint;
uniform float uDark;
uniform int uDebug;
out vec4 fragColor;
const float PI=3.141592653589793;

vec2 toCorrected(vec2 uv){return (uv-0.5)*vec2(uResolution.x/uResolution.y,1.0);}
vec2 toUV(vec2 p){return p/vec2(uResolution.x/uResolution.y,1.0)+0.5;}
float sdCircle(vec2 p,float radius){return length(p)-radius;}
float sdRoundedBox(vec2 p,vec2 halfSize,float radius){
    vec2 q=abs(p)-halfSize+radius;
    return min(max(q.x,q.y),0.0)+length(max(q,0.0))-radius;
}
float polynomialSmoothMin(float a,float b,float k){
    if(k<=0.0)return min(a,b);
    float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0);
    return mix(b,a,h)-k*h*(1.0-h);
}
float glassSurfaceSdf(vec2 p){
    vec2 center=toCorrected((uRect.xy+uRect.zw*.5)/uResolution);
    vec2 local=p-center;
    float d=uShape==1?sdCircle(local,uRadius/uResolution.y):
        sdRoundedBox(local,uRect.zw/uResolution.y*.5,uRadius/uResolution.y);
    if(uMergeCircle.z>0.0){
        float b=sdCircle(p-toCorrected(uMergeCircle.xy/uResolution),uMergeCircle.z/uResolution.y);
        d=polynomialSmoothMin(d,b,uMergeCircle.w/uResolution.y);
    }
    return d;
}
float quintic(float t){return t*t*t*(t*(t*6.0-15.0)+10.0);}
float heightAt(vec2 p){
    float interior=max(-glassSurfaceSdf(p),0.0);
    float bevel=max(uNormalTransition*.82,1e-6);
    float t=clamp(interior/bevel,0.0,1.0);
    // Exact plateau: there is no central bump or second rounded rectangle.
    return uGlassThickness*(t>=1.0?1.0:quintic(t));
}
vec3 surfaceNormal(vec2 p,out float curvature){
    float stepSize=clamp(uNormalTransition*.13,.003,.012);
    // p is already aspect-corrected; a horizontal step corresponds to step/aspect in UV.
    float left=heightAt(p-vec2(stepSize,0.0));
    float right=heightAt(p+vec2(stepSize,0.0));
    float up=heightAt(p+vec2(0.0,stepSize));
    float down=heightAt(p-vec2(0.0,stepSize));
    vec2 gradient=vec2(right-left,up-down)/(2.0*stepSize);
    float laplacian=abs(right+left+up+down-4.0*heightAt(p))/(stepSize*stepSize);
    curvature=laplacian/(laplacian+22.0);
    return normalize(vec3(-gradient,1.0));
}
vec2 traceRay(vec2 p,float height,vec3 frontNormal,float ior){
    vec3 incident=vec3(0.0,0.0,-1.0);
    vec3 insideRay=refract(incident,frontNormal,1.0/ior);
    if(dot(insideRay,insideRay)<1e-8||insideRay.z>=-.025)insideRay=incident;
    vec3 front=vec3(p,height);
    float glassPath=height/max(-insideRay.z,.025);
    vec3 back=front+insideRay*glassPath;
    vec3 exitRay=refract(insideRay,vec3(0.0,0.0,1.0),ior);
    if(dot(exitRay,exitRay)<1e-8||exitRay.z>=-.025)exitRay=incident;
    float airPath=(back.z+uBackgroundDistance)/max(-exitRay.z,.025);
    return toUV((back+exitRay*airPath).xy);
}
vec3 backgroundAt(vec2 uv,float lod){
    vec2 inset=.5/uResolution;
    uv=clamp(uv,inset,1.0-inset);
    return textureLod(paintComposeRT,vec2(uv.x,1.0-uv.y),lod).rgb;
}
vec3 spectrumAt(vec2 uv,vec2 redDelta,vec2 blueDelta,float lod){
    return vec3(backgroundAt(uv+redDelta,lod).r,backgroundAt(uv,lod).g,backgroundAt(uv+blueDelta,lod).b);
}
float ggxDistribution(float NoH,float alpha){
    float a2=alpha*alpha;
    float denominator=NoH*NoH*(a2-1.0)+1.0;
    return a2/max(PI*denominator*denominator,1e-6);
}
float smithHeightCorrelated(float NoV,float NoL,float alpha){
    float a2=alpha*alpha;
    float v=NoL*sqrt(NoV*NoV*(1.0-a2)+a2);
    float l=NoV*sqrt(NoL*NoL*(1.0-a2)+a2);
    return .5/max(v+l,1e-5);
}
float schlick(float cosine,float f0){float m=1.0-clamp(cosine,0.0,1.0);return f0+(1.0-f0)*m*m*m*m*m;}
vec3 dielectricLighting(vec3 n,float support,float curvature){
    vec3 view=vec3(0.0,0.0,1.0);
    float f0=pow((uIOR-1.0)/(uIOR+1.0),2.0);
    float alpha=uRoughness*uRoughness;
    float NoV=max(dot(n,view),1e-4);
    vec3 lighting=vec3(0.0);
    for(int i=0;i<4;i++){
        float angle=uTime*.055+float(i)*PI*.5+.38;
        float z=.28+.14*(.5+.5*sin(float(i)*2.1));
        vec3 light=normalize(vec3(cos(angle)*.94,sin(angle)*.94,z));
        vec3 halfVector=normalize(view+light);
        float NoL=max(dot(n,light),0.0);
        vec3 lightColor=(i==0||i==2)?vec3(.91,.96,1.0):vec3(1.0,.96,.91);
        float specular=ggxDistribution(max(dot(n,halfVector),0.0),alpha)*
            smithHeightCorrelated(NoV,NoL,alpha)*schlick(dot(view,halfVector),f0)*NoL;
        lighting+=lightColor*specular*1.45;
    }
    vec3 reflected=reflect(-view,n);
    vec3 environment=mix(vec3(.24,.30,.40),vec3(.92,.96,1.0),reflected.y*.5+.5);
    lighting+=environment*schlick(NoV,f0)*.46;
    return lighting*support*(.35+.65*curvature);
}
void main(){
    vec2 uv=vec2(gl_FragCoord.x/uResolution.x,1.0-gl_FragCoord.y/uResolution.y);
    vec2 p=toCorrected(uv);
    float sdf=glassSurfaceSdf(p);
    float aa=max(fwidth(sdf),.6/uResolution.y);
    float mask=1.0-smoothstep(-aa,aa,sdf);
    float shifted=glassSurfaceSdf(p-vec2(0.0,2.0/uResolution.y));
    float shadow=exp(-pow(max(shifted,0.0)*uResolution.y/6.0,2.0))*.13*(1.0-mask);
    vec3 under=backgroundAt(uv,0.0);
    if(mask<.001){fragColor=vec4(0.0,0.0,0.0,shadow);return;}
    float interior=max(-sdf,0.0);
    float bevel=max(uNormalTransition*.82,1e-6);
    float t=clamp(interior/bevel,0.0,1.0);
    float height=heightAt(p);
    float curvature;
    vec3 normal=surfaceNormal(p,curvature);
    vec2 greenRayUV=traceRay(p,height,normal,uIOR);
    vec2 redRayUV=traceRay(p,height,normal,uIOR-uDispersion);
    vec2 blueRayUV=traceRay(p,height,normal,uIOR+uDispersion);
    vec2 rimDirection=normal.xy/max(length(normal.xy),1e-5);
    vec2 uvDirection=rimDirection/vec2(uResolution.x/uResolution.y,1.0);
    float rimDisplacement=uGlassThickness*.34;
    vec2 opticalCenter=(uRect.xy+uRect.zw*.5)/uResolution;
    vec2 faceUV=mix(opticalCenter+(uv-opticalCenter)*.975,greenRayUV,.075);
    vec2 innerUV=greenRayUV-uvDirection*rimDisplacement;
    vec2 outerUV=uv+uvDirection*rimDisplacement*1.28;
    float faceWeight=smoothstep(.34,.88,t);
    float outerWeight=(1.0-faceWeight)*(1.0-smoothstep(.10,.44,t));
    float innerWeight=max(0.0,1.0-faceWeight-outerWeight);
    vec3 weights=vec3(faceWeight,innerWeight,outerWeight);
    weights/=max(dot(weights,vec3(1.0)),1e-5);
    vec3 face=backgroundAt(faceUV,uFaceLod);
    vec3 inner=spectrumAt(innerUV,redRayUV-greenRayUV,blueRayUV-greenRayUV,.65);
    vec3 outer=spectrumAt(outerUV,redRayUV-greenRayUV,blueRayUV-greenRayUV,uFaceLod+.7);
    vec3 refracted=face*weights.x+inner*weights.y+outer*weights.z;
    // Composition: complete refraction -> external shadow -> white/cool tint -> dielectric light.
    vec3 color=refracted*(1.0-shadow);
    color=mix(color,pow(uTint.rgb,vec3(2.2)),uTint.a);
    float outerBevelSupport=(1.0-smoothstep(.36,.56,t))*smoothstep(.015,.15,t);
    color+=dielectricLighting(normal,outerBevelSupport,curvature);
    if(uDebug==1){fragColor=vec4(normal*.5+.5,1.0);return;}
    if(uDebug==2){fragColor=vec4(weights,1.0);return;}
    if(uDebug==3){fragColor=vec4(vec3(outerBevelSupport),1.0);return;}
    if(uDebug==4){fragColor=vec4(vec3(height/max(uGlassThickness,1e-6)),1.0);return;}
    // Premultiplied coverage preserves previously composed sibling glass outside this shape.
    fragColor=vec4(pow(clamp(color,0.0,1.0),vec3(1.0/2.2))*mask,mask+shadow*(1.0-mask));
}
