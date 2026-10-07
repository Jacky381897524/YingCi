#version 300 es
precision highp float;
uniform vec2 uResolution;
uniform sampler2D paintComposeRT;
out vec4 fragColor;
void main(){
    vec3 color=textureLod(paintComposeRT,gl_FragCoord.xy/uResolution,0.0).rgb;
    fragColor=vec4(pow(max(color,vec3(0.0)),vec3(1.0/2.2)),1.0);
}
