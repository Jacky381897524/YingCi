const fs=require('fs'),path=require('path'),assert=require('assert');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'android/app/src/main/assets/glass');
const reference=fs.readFileSync(path.join(root,'../Huitu skill/reference/webgl-apple-liquid-glass/src/v2-shaders.js'),'utf8');
const imported=fs.readFileSync(path.join(assets,'reference-v2.frag'),'utf8');
assert(reference.includes(imported),'Reference shader must remain verbatim');
const shaders=Object.fromEntries(['fullscreen.vert','reference-v2.frag','reference-down.frag','reference-present.frag','paint.frag','background.frag'].map(f=>[f,fs.readFileSync(path.join(assets,f),'utf8')]));
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true,args:['--enable-webgl','--ignore-gpu-blocklist']});
 try{
 const page=await browser.newPage({viewport:{width:760,height:500}});
 await page.setContent('<body style="margin:20px;background:#edf1f7"><canvas width="720" height="420"></canvas></body>');
 const result=await page.evaluate((s)=>{
  const gl=document.querySelector('canvas').getContext('webgl2',{preserveDrawingBuffer:true});if(!gl)throw Error('WebGL2 unavailable');
  const programs={};for(const name of Object.keys(s).filter(n=>n.endsWith('.frag'))){const p=gl.createProgram();for(const [type,source]of [[gl.VERTEX_SHADER,s['fullscreen.vert']],[gl.FRAGMENT_SHADER,s[name]]]){const sh=gl.createShader(type);gl.shaderSource(sh,source);gl.compileShader(sh);if(!gl.getShaderParameter(sh,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(sh));gl.attachShader(p,sh);}gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));programs[name]=p;}
  const tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);const pixels=new Uint8Array(720*420*4);for(let y=0;y<420;y++)for(let x=0;x<720;x++){const n=(y*720+x)*4;const stripe=Math.floor(x/34)%2;pixels[n]=stripe?190:36;pixels[n+1]=Math.round(90+y/420*125);pixels[n+2]=stripe?202:230;pixels[n+3]=255;}
  gl.texStorage2D(gl.TEXTURE_2D,7,gl.RGBA8,720,420);gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,720,420,gl.RGBA,gl.UNSIGNED_BYTE,pixels);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
  const fb=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,fb);const down=programs['reference-down.frag'];gl.useProgram(down);gl.uniform1i(gl.getUniformLocation(down,'uTex'),0);
  for(let level=1;level<7;level++){gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_BASE_LEVEL,level-1);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAX_LEVEL,level-1);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,tex,level);if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('Mip framebuffer');gl.viewport(0,0,Math.max(1,720>>level),Math.max(1,420>>level));gl.uniform2f(gl.getUniformLocation(down,'uTexel'),1/(720>>(level-1)),1/(420>>(level-1)));gl.drawArrays(gl.TRIANGLES,0,3);if(gl.getError())throw Error('Mip feedback');}
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_BASE_LEVEL,0);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAX_LEVEL,6);gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,720,420);
  const p=programs['reference-v2.frag'];const loc=n=>gl.getUniformLocation(p,n);const f=(n,v)=>gl.uniform1f(loc(n),v);
  function draw(frost,refraction){gl.disable(gl.BLEND);gl.useProgram(programs['reference-present.frag']);gl.uniform1i(gl.getUniformLocation(programs['reference-present.frag'],'uTex'),0);gl.drawArrays(gl.TRIANGLES,0,3);gl.useProgram(p);gl.uniform1i(loc('uSrc'),0);gl.uniform2f(loc('uRes'),720,420);f('uDpr',1);f('uMips',7);gl.uniform1i(loc('uShapeCount'),1);gl.uniform2f(loc('uShapeCenters[0]'),360,210);gl.uniform2f(loc('uShapeHalves[0]'),240,105);gl.uniform1i(loc('uShapeTypes[0]'),0);f('uShapeRadii[0]',49);f('uShapeTints[0]',0);f('uShapeTintLights[0]',1);f('uShapeFrosts[0]',frost);f('uShapeOpacities[0]',1);f('uShapePressures[0]',0);gl.uniform2f(loc('uShapePressAxes[0]'),1,1);gl.uniform2f(loc('uLightDirs[0]'),-.719,.695);
   for(const[k,v]of Object.entries({Refraction:refraction,EdgeReach:.14,EdgeWidth:.21,Dispersion:2,BackdropBlur:0,Body:.72,Absorption:.58,Rim:.24,Reflection:.31,Highlight:.34,Echo:.28,Hairline:.92,HairWidth:.52}))f('u'+k,v);gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE_MINUS_SRC_ALPHA);gl.drawArrays(gl.TRIANGLES,0,3);const out=new Uint8Array(720*420*4);gl.readPixels(0,0,720,420,gl.RGBA,gl.UNSIGNED_BYTE,out);if(gl.getError())throw Error('Draw failed');return out;}
  const a=draw(0,84),b=draw(1,84),c=draw(0,0);let frostChange=0,bendChange=0;for(let i=0;i<a.length;i+=4){frostChange+=Math.abs(a[i]-b[i]);bendChange+=Math.abs(a[i]-c[i]);}draw(0,84);return{compiled:Object.keys(programs).length,frostChange,bendChange};
 },shaders);
 assert(result.frostChange>1000);assert(result.bendChange>1000);
 await page.screenshot({path:path.join(root,'.tools/material-check.png')});
 console.log(JSON.stringify({verbatimReference:true,...result}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
