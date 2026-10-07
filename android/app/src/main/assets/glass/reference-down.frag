#version 300 es
precision highp float;
in vec2 vUV;
uniform sampler2D uTex;
uniform vec2 uTexel;   // texel size of the SOURCE level
out vec4 outColor;
void main() {
  vec2 t = uTexel;
  vec4 a = texture(uTex, vUV) * 0.125;
  vec4 b = (texture(uTex, vUV + vec2(-t.x, -t.y)) +
            texture(uTex, vUV + vec2( t.x, -t.y)) +
            texture(uTex, vUV + vec2(-t.x,  t.y)) +
            texture(uTex, vUV + vec2( t.x,  t.y))) * 0.125;
  vec4 c = (texture(uTex, vUV + vec2(-2.0 * t.x, 0.0)) +
            texture(uTex, vUV + vec2( 2.0 * t.x, 0.0)) +
            texture(uTex, vUV + vec2(0.0, -2.0 * t.y)) +
            texture(uTex, vUV + vec2(0.0,  2.0 * t.y))) * 0.0625;
  vec4 d = (texture(uTex, vUV + vec2(-2.0 * t.x, -2.0 * t.y)) +
            texture(uTex, vUV + vec2( 2.0 * t.x, -2.0 * t.y)) +
            texture(uTex, vUV + vec2(-2.0 * t.x,  2.0 * t.y)) +
            texture(uTex, vUV + vec2( 2.0 * t.x,  2.0 * t.y))) * 0.03125;
  // RGB stores radiance. Alpha stores normalized optical density, so the mip
  // chain can blur both representations with exactly the same footprint.
  outColor = a + b + c + d;
}