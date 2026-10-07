#version 300 es
precision highp float;
in vec2 vUV;
uniform float uDark;
out vec4 outColor;
vec3 linearize(vec3 c){return mix(pow((c+.055)/1.055,vec3(2.4)),c/12.92,lessThanEqual(c,vec3(.04045)));}
void main(){
 vec3 base=mix(vec3(.969,.973,.980),vec3(.082,.086,.094),uDark);
 outColor=vec4(linearize(base),1.);
}
