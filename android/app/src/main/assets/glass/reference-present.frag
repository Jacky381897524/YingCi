#version 300 es
precision highp float;
in vec2 vUV;
uniform sampler2D uTex;
out vec4 outColor;
vec3 linearToSrgb(vec3 c) {
  c = max(c, 0.0);
  return mix(1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055,
             12.92 * c,
             lessThanEqual(c, vec3(0.0031308)));
}
void main() { outColor = vec4(linearToSrgb(texture(uTex, vUV).rgb), 1.0); }