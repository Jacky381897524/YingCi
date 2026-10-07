/* Independent, offline visual prototype. No Android storage or API access. */
const A = window.PREVIEW_ASSETS;
const $ = s => document.querySelector(s);
const phone = $('#phone'), scroller = $('#scroller'), content = $('#content');
const toolbar = $('#toolbar'), controls = $('#glass-controls');
const items = [
  {id:'poster',name:'雪山日落摄影',image:A.poster},
  {id:'fantasy',name:'地球光影',image:A.fantasy},
  {id:'lake',name:'湖光自然摄影',image:A.lake},
  {id:'city',name:'都市夜景光影',image:A.city},
  {id:'lines',name:'流动线条艺术',image:A.lines},
  {id:'blocks',name:'色彩构成',image:A.blocks},
];
const prompt = '请以我新上传的照片为内容来源，保留主体身份、数量、轮廓与核心叙事关系。不要把风格参考图中的具体人物或场景替换进新照片。\n\n整体采用摄影与手绘拼贴结合的表现方式。上半部分保留照片的真实质感与自然光影，适度调整色彩；下半部分提取新照片最具识别性的形态，以细腻铅笔线条、纸张纹理和少量撕纸色块重新表达。\n\n保留清晰的分区、平衡的留白与克制的色彩关系。背景环境和装饰细节围绕新照片本身组织，不强制出现山丘、座椅、建筑或其他参考图独有的物体。\n\n人物照片保留本人的面部特征，产品照片保留主要结构，风景照片保留空间关系。通过笔触、材质和光影语言统一画风，而不是复刻参考图的内容。';
let page=phone.dataset.start||'library', search=false, query='', theme='system', font=1, reference=A.poster, detail=items[0], variant=0;
let refraction=84, glassStrength=.72, toastTimer, needMeasure=true, needsFrame=false, animateUntil=0;
let cached=[], glassButtons=[], rasterIconCache=new Map();
const media=matchMedia('(prefers-color-scheme: dark)');
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=(name)=>`<i data-lucide="${name}"></i>`;
const text=(s,tag='p',cls='')=>`<${tag} data-paint class="${cls}">${esc(s)}</${tag}>`;
const anchor=(label,action,cls='inline-anchor',ic='')=>`<div class="${cls}" data-anchor data-label="${esc(label)}" data-action="${action}" data-icon="${ic}"></div>`;
function toast(message){$('#toast').textContent=message;$('#toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').classList.remove('visible'),2600);}
function dark(){return theme==='dark'||(theme==='system'&&media.matches);}
function applyTheme(){phone.classList.toggle('dark',dark());phone.style.setProperty('--size',font);invalidate(true);}
media.addEventListener('change',applyTheme);
function header(title,back=false){
  toolbar.innerHTML=back?`<button class="round" data-action="back" data-shape data-radius="22" aria-label="返回" title="返回">${icon('arrow-left')}</button><h1 class="back-title" data-paint>${title}</h1>`:`<h1 data-paint>${title}</h1>`;
  if(page==='library'){
    if(search)toolbar.innerHTML=`<div class="search-wrap" data-shape data-radius="22">${icon('search')}<input id="search" aria-label="搜索名称或提示词" placeholder="搜索名称或提示词" value="${esc(query)}"><button data-action="close-search" aria-label="关闭搜索" title="关闭搜索">${icon('x')}</button></div>`;
    else toolbar.insertAdjacentHTML('beforeend',`<button class="round" data-action="search" aria-label="搜索" title="搜索" data-shape data-radius="22">${icon('search')}</button>`);
    toolbar.insertAdjacentHTML('beforeend',`<button class="round" data-action="new" aria-label="新建收藏" title="新建收藏" data-shape data-radius="22">${icon('plus')}</button>`);
  }
}
function collection(){
  const filtered=items.filter(x=>(x.name+(x.customPrompt||prompt)).includes(query.trim()));
  content.innerHTML=filtered.length?`<div class="grid">${filtered.map(x=>`<article class="cover" data-action="detail:${x.id}" tabindex="0" role="button" aria-label="${esc(x.name)}"><img data-paint src="${x.image}" alt="${esc(x.name)}">${anchor(x.name,`detail:${x.id}`,'title-anchor')}</article>`).join('')}</div>`:text('没有匹配的收藏','div','empty');
}
function settingsRow(ic,title,sub,action){return `<button class="settings-row" data-action="${action}"><span data-paint-icon>${icon(ic)}</span><span class="row-text"><span class="row-title" data-paint>${title}</span><span class="row-subtitle" data-paint>${sub}</span></span>${icon('chevron-right')}</button>`;}
function render(reset=true){
  controls.replaceChildren();
  if(reset)scroller.scrollTop=0;
  if(page==='library'){header('映词');collection();}
  if(page==='reverse'){
    header('风格反推');
    content.innerHTML=`<div class="image-wrap"><img data-paint src="${reference}" alt="示例风格参考图">${anchor('更换参考图','replace','replace-anchor')}</div>`+
      text('示例结果 · 此预览不会上传图片或调用模型','p','subtle')+anchor('重新反推','analyze','inline-anchor','scan-text')+
      text('可迁移提示词','h2')+text('摄影手绘拼贴','p','prompt-name')+text(prompt,'p','prompt-text')+
      anchor('复制提示词','copy','inline-anchor','copy')+anchor('收藏这份风格','collect','inline-anchor','bookmark-plus')+anchor('用这份提示词生图','generation-from-reverse','inline-anchor','image-plus');
  }
  if(page==='settings'){
    header('设置');
    content.innerHTML=settingsRow('sliders-horizontal','显示','液态玻璃','display')+settingsRow('type','外观与文字','主题模式与字号','appearance')+
      settingsRow('image','主题与壁纸','逐页设置背景','planned')+settingsRow('key-round','模型连接','识图与生图','planned')+
      text('本机数据','h2')+settingsRow('images','生成记录','本机保存与清理','generation-history')+settingsRow('upload','导出完整备份','提示词、原始版及封面','planned')+
      settingsRow('download','从备份恢复','合并导入，不覆盖已有收藏','planned')+settingsRow('brush-cleaning','清理图片缓存','保留收藏和原始封面','planned')+
      text('映词','h2')+text('V1.0.2 · 设计预览（非 APK）','p','subtle')+text('提示词与封面保存在本机。备份不含 API Key。卸载前请先导出备份。')+
      text('读取模型列表、测试连接与反推时访问你配置的模型服务。当前预览不会连接任何模型服务。')+
      text('Liquid Glass · Oliver Nemo · MIT','p','subtle');
  }
  if(page==='appearance'){
    header('外观与文字',true);
    content.innerHTML=text('外观','h2')+`<div class="segmented" aria-label="主题模式">${[['system','跟随系统'],['light','浅色'],['dark','深色']].map(([v,n])=>`<button data-action="theme:${v}" class="${theme===v?'selected':''}" aria-pressed="${theme===v}">${n}</button>`).join('')}</div>`+
      text('文字大小','h2')+`<input id="font-size" aria-label="文字大小" type="range" min="0" max="4" step="1" value="${[.85,1,1.15,1.3,1.45].indexOf(font)}"><div class="size-labels"><span>小</span><span>标准</span><span>中</span><span>大</span><span>特大</span></div>`+
      text('文字预览','h2')+text('摄影手绘拼贴','p','prompt-name')+text('保留照片的主体与情绪，让画面呈现细腻的手绘质感。')+
      anchor('玻璃文字预览','sample','inline-anchor','type')+text(prompt,'p','prompt-text');
  }
  if(page==='display'){
    header('显示',true);
    content.innerHTML=`<div class="image-wrap"><img data-paint src="${A.lake}" alt="湖景材质预览">${anchor('液态玻璃','sample','replace-anchor')}</div>`+
      text('折射强度','h2')+`<input id="refraction" aria-label="折射强度" type="range" min="0" max="120" value="${refraction}">`+
      text('玻璃厚度','h2')+`<input id="body-strength" aria-label="玻璃厚度" type="range" min="0" max="1" step=".01" value="${glassStrength}">`+
      text('文字与图片','h2')+text(prompt,'p','prompt-text');
  }
  if(page==='detail'){
    header('提示词详情',true);
    content.innerHTML=`<div class="image-wrap"><img data-paint src="${detail.image}" alt="${esc(detail.name)}"></div>`+text(detail.name,'h2')+
      `<div class="segmented"><button data-action="variant:0" class="${variant===0?'selected':''}">原始版本</button><button data-action="variant:1" class="${variant===1?'selected':''}">我的版本</button></div>`+
      text(variant===0?(detail.customPrompt||prompt):'保留新照片主体，以摄影和铅笔拼贴重构画面。\n\n'+(detail.customPrompt||prompt),'p','prompt-text')+anchor('复制提示词','copy','inline-anchor','copy')+anchor('用这份提示词生图','generation-from-detail','inline-anchor','image-plus');
  }
  if(page==='new'){
    header('新建收藏',true);
    content.innerHTML=text('收藏名称','h2')+`<input id="new-name" class="plain-input" aria-label="收藏名称" maxlength="16">`+text('0 / 16','p','subtle name-count')+
      text('提示词','h2')+`<textarea id="new-prompt" class="plain-input" aria-label="提示词" rows="7"></textarea>`+anchor('保存示例收藏','save-sample','inline-anchor','check');
  }
  if(generationPages.includes(page))generationRender();
  const active=['appearance','display'].includes(page)?'settings':['new','detail'].includes(page)?'library':generationPages.includes(page)?'generate':page;
  document.querySelectorAll('#nav button').forEach(b=>{b.classList.toggle('active',b.dataset.tab===active);b.setAttribute('aria-current',b.dataset.tab===active?'page':'false');});
  $('#nav').style.display=['new','detail','generation-result','generation-history'].includes(page)?'none':'grid';
  mountGlassControls();
  lucide.createIcons();
  content.querySelectorAll('img').forEach(img=>{if(!img.complete)img.addEventListener('load',()=>invalidate(true),{once:true});});
  invalidate(true);
}
function mountGlassControls(){
  controls.replaceChildren();glassButtons=[];
  content.querySelectorAll('[data-anchor]').forEach(el=>{
    const b=document.createElement('button');b.className='glass-button';b.dataset.action=el.dataset.action;
    b.dataset.radius='25';b.setAttribute('aria-label',el.dataset.label);
    b.innerHTML=(el.dataset.icon?icon(el.dataset.icon):'')+`<span class="glass-label">${esc(el.dataset.label)}</span>`;
    controls.appendChild(b);glassButtons.push({el,b});
  });
}
function positionGlass(){
  const parent=controls.getBoundingClientRect();
  const nav=$('#nav').getBoundingClientRect();
  for(const {el,b}of glassButtons){
    const r=el.getBoundingClientRect();const visible=r.bottom>=parent.top&&r.top<=parent.bottom;
    b.hidden=!visible;b.style.display=visible?'flex':'none';
    b.style.left=(r.left-parent.left)+'px';b.style.top=(r.top-parent.top)+'px';b.style.width=r.width+'px';b.style.height=r.height+'px';
    b.style.clipPath=nav.height&&r.bottom>nav.top?`inset(0 0 ${Math.min(r.height,r.bottom-nav.top)}px 0)`:'none';
    const label=b.querySelector('.glass-label');let size=14*font;label.style.fontSize=size+'px';
    while(visible&&label.scrollHeight>label.clientHeight+1&&size>11){size-=.5;label.style.fontSize=size+'px';}
  }
}
function navigate(next){page=next;render();}
document.addEventListener('click',async e=>{
  const tab=e.target.closest('[data-tab]');if(tab)return navigate(tab.dataset.tab);
  const el=e.target.closest('[data-action]');if(!el)return;const action=el.dataset.action;
  if(action==='search'){search=true;header('映词');lucide.createIcons();invalidate(true);$('#search').focus();}
  else if(action==='close-search'){search=false;query='';render(false);}
  else if(action.startsWith('detail:')){detail=items.find(x=>x.id===action.slice(7));variant=0;navigate('detail');}
  else if(action.startsWith('theme:')){theme=action.slice(6);applyTheme();render(false);}
  else if(action.startsWith('variant:')){variant=Number(action.slice(8));render(false);}
  else if(['appearance','display','new','generation-history'].includes(action))navigate(action);
  else if(action==='back'){if(generationPages.includes(page))generationBack();else navigate(['detail','new'].includes(page)?'library':'settings');}
  else if(action.startsWith('generation-'))generationAction(action);
  else if(action==='replace')$('#photo-picker').click();
  else if(action==='copy'){try{await navigator.clipboard.writeText(page==='detail'?(variant===0?(detail.customPrompt||prompt):'保留新照片主体，以摄影和铅笔拼贴重构画面。\n\n'+(detail.customPrompt||prompt)):prompt);toast('已复制示例提示词');}catch{toast('浏览器未允许访问剪贴板。');}}
  else if(action==='save-sample'){
    const name=$('#new-name').value.trim();if(!name)return toast('请输入收藏名称');
    const customPrompt=$('#new-prompt').value.trim();if(!customPrompt)return toast('请输入提示词');
    items.unshift({id:'sample-'+Date.now(),name,image:A.lake,customPrompt});navigate('library');toast('已加入本次预览，关闭页面后不保留');
  }else if(action==='collect'){toast('当前是示例结果，不会写入正式收藏。');}
  else if(action==='planned')toast('此入口仅展示位置，本次预览不执行该操作。');
  else if(action==='analyze')toast('当前使用示例提示词，没有发起模型请求。');
  else if(action==='sample')toast('可以按住观察玻璃与文字的同步形变。');
});
document.addEventListener('input',e=>{
  if(e.target.id==='search'){query=e.target.value;collection();mountGlassControls();lucide.createIcons();invalidate(true);}
  if(e.target.id==='font-size'){font=[.85,1,1.15,1.3,1.45][e.target.value];applyTheme();}
  if(e.target.id==='refraction'){refraction=Number(e.target.value);invalidate();}
  if(e.target.id==='body-strength'){glassStrength=Number(e.target.value);invalidate();}
  if(e.target.id==='new-name')$('.name-count').textContent=`${Array.from(e.target.value).length} / 16`;
});
$('#photo-picker').addEventListener('change',e=>{
  const file=e.target.files[0];if(!file)return;
  if(!file.type.startsWith('image/'))return toast('请选择图片文件');
  const reader=new FileReader();reader.onload=()=>{reference=reader.result;render(false);};reader.readAsDataURL(file);
});
let pressed,drag=null,dragged=false;
phone.addEventListener('pointerdown',e=>{const b=e.target.closest('.glass-button,.round,#nav');if(!b)return;pressed=b;b.classList.add('pressed');dragged=false;if(b.classList.contains('glass-button')){drag={y:e.clientY,scroll:scroller.scrollTop};b.setPointerCapture(e.pointerId);}animateUntil=performance.now()+500;invalidate();});
phone.addEventListener('pointermove',e=>{if(drag&&Math.abs(e.clientY-drag.y)>5){dragged=true;scroller.scrollTop=drag.scroll-(e.clientY-drag.y);}});
phone.addEventListener('click',e=>{if(dragged){e.preventDefault();e.stopPropagation();dragged=false;}},true);
function release(){drag=null;if(pressed){pressed.classList.remove('pressed');pressed=null;animateUntil=performance.now()+350;invalidate();}}
window.addEventListener('pointerup',release);window.addEventListener('pointercancel',release);
scroller.addEventListener('scroll',()=>invalidate(),{passive:true});

// Snapshot only the background: foreground glass labels/icons never enter the lens texture.
// Text glyph positions come from DOM Ranges, so the refracted copy follows real wrapping.
function measureSource(){
  cached=[];const origin=phone.getBoundingClientRect();
  document.querySelectorAll('#phone [data-paint]').forEach(el=>{
    const inScroll=scroller.contains(el),offset=inScroll?scroller.scrollTop:0;
    const local=r=>({x:r.left-origin.left,y:r.top-origin.top+offset,w:r.width,h:r.height,inScroll});
    const style=getComputedStyle(el);
    if(el.tagName==='IMG'){cached.push({...local(el.getBoundingClientRect()),img:el,radius:8,contain:style.objectFit==='contain'});return;}
    const walker=document.createTreeWalker(el,NodeFilter.SHOW_TEXT);
    while(walker.nextNode()){
      const node=walker.currentNode;let i=0;
      for(const ch of node.textContent){const range=document.createRange();range.setStart(node,i);i+=ch.length;range.setEnd(node,i);const r=range.getBoundingClientRect();if(r.width&&r.height)cached.push({...local(r),ch,color:style.color,font:`${style.fontWeight} ${style.fontSize} ${style.fontFamily}`,fontSize:parseFloat(style.fontSize)});}
    }
  });
  document.querySelectorAll('#phone [data-paint-icon] svg').forEach(svg=>{
    const r=svg.getBoundingClientRect(),node=svg.cloneNode(true);node.setAttribute('xmlns','http://www.w3.org/2000/svg');node.setAttribute('stroke',getComputedStyle(svg).color);
    const markup=new XMLSerializer().serializeToString(node);
    if(!rasterIconCache.has(markup)){const img=new Image();img.onload=()=>invalidate();img.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(markup);rasterIconCache.set(markup,img);}
    cached.push({img:rasterIconCache.get(markup),x:r.left-origin.left,y:r.top-origin.top+scroller.scrollTop,w:r.width,h:r.height,inScroll:true});
  });
}
const source=document.createElement('canvas'),ctx=source.getContext('2d');
const canvas=$('#glass');let gl,program,texture,ready=false,frames=0,lastShapes=0,lastError='';
try{
  gl=canvas.getContext('webgl2',{alpha:true,premultipliedAlpha:true,preserveDrawingBuffer:true,antialias:false});if(!gl)throw Error('WebGL 2 unavailable');
  program=gl.createProgram();
  for(const[type,code]of[[gl.VERTEX_SHADER,A.vertex],[gl.FRAGMENT_SHADER,A.fragment]]){const shader=gl.createShader(type);gl.shaderSource(shader,code);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(shader));gl.attachShader(program,shader);}
  gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));
  texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);ready=true;
}catch(e){lastError=e.message;phone.classList.add('fallback');toast('此浏览器无法运行折射预览，请用较新的 Chrome 或 Edge。');}
function paintSource(w,h,dpr){
  if(source.width!==canvas.width||source.height!==canvas.height){source.width=canvas.width;source.height=canvas.height;}
  ctx.setTransform(dpr,0,0,dpr,0,0);ctx.fillStyle=getComputedStyle(phone).getPropertyValue('--bg');ctx.fillRect(0,0,w,h);
  const pr=phone.getBoundingClientRect(),sr=scroller.getBoundingClientRect();
  for(const item of cached){
    const y=item.y-(item.inScroll?scroller.scrollTop:0);if(y+item.h<0||y>h)continue;
    ctx.save();if(item.inScroll){ctx.beginPath();ctx.rect(sr.left-pr.left,sr.top-pr.top,sr.width,sr.height);ctx.clip();}
    if(item.img&&item.img.complete&&item.img.naturalWidth){
      ctx.beginPath();ctx.roundRect(item.x,y,item.w,item.h,item.radius||0);ctx.clip();
      const scale=(item.contain?Math.min:Math.max)(item.w/item.img.naturalWidth,item.h/item.img.naturalHeight);
      const iw=item.img.naturalWidth*scale,ih=item.img.naturalHeight*scale;
      ctx.drawImage(item.img,item.x+(item.w-iw)/2,y+(item.h-ih)/2,iw,ih);
    }else if(item.ch){ctx.font=item.font;ctx.fillStyle=item.color;ctx.textBaseline='alphabetic';const m=ctx.measureText(item.ch);const ascent=m.fontBoundingBoxAscent||item.fontSize*.88,descent=m.fontBoundingBoxDescent||item.fontSize*.2;ctx.fillText(item.ch,item.x,y+(item.h-ascent-descent)/2+ascent);}
    ctx.restore();
  }
  // Labels that pass behind the navigation become background text there only.
  const nr=$('#nav').getBoundingClientRect();
  if(nr.height){
    ctx.save();ctx.beginPath();ctx.rect(nr.left-pr.left,nr.top-pr.top,nr.width,nr.height);ctx.clip();
    for(const{b}of glassButtons){
      const label=b.querySelector('.glass-label'),br=b.getBoundingClientRect();if(b.hidden||br.bottom<nr.top)continue;
      const style=getComputedStyle(label),r=label.getBoundingClientRect();
      ctx.font=`${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
      ctx.textBaseline='middle';ctx.textAlign='center';ctx.lineWidth=1.1;ctx.strokeStyle=dark()?'white':'#17191d';ctx.fillStyle=style.color;
      ctx.strokeText(label.textContent,r.left-pr.left+r.width/2,r.top-pr.top+r.height/2,r.width);
      ctx.fillText(label.textContent,r.left-pr.left+r.width/2,r.top-pr.top+r.height/2,r.width);
    }
    ctx.restore();
  }
}
const locations=new Map();function loc(name){if(!locations.has(name))locations.set(name,gl.getUniformLocation(program,name));return locations.get(name);}
function draw(){
  needsFrame=false;positionGlass();if(needMeasure){measureSource();needMeasure=false;}
  if(!ready)return;
  const box=phone.getBoundingClientRect(),dpr=Math.min(devicePixelRatio||1,1.5),w=Math.round(box.width*dpr),h=Math.round(box.height*dpr);
  if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
  paintSource(box.width,box.height,dpr);
  gl.bindTexture(gl.TEXTURE_2D,texture);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
  gl.texImage2D(gl.TEXTURE_2D,0,gl.SRGB8_ALPHA8,gl.RGBA,gl.UNSIGNED_BYTE,source);gl.generateMipmap(gl.TEXTURE_2D);
  gl.viewport(0,0,w,h);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);gl.useProgram(program);
  const f=(n,v)=>gl.uniform1f(loc(n),v);
  gl.uniform1i(loc('uSrc'),0);gl.uniform2f(loc('uRes'),w,h);f('uDpr',dpr);f('uMips',1+Math.floor(Math.log2(Math.max(w,h))));
  const fixed=[...document.querySelectorAll('#phone [data-shape]')].filter(el=>el.getBoundingClientRect().width&&getComputedStyle(el).display!=='none');
  const inline=glassButtons.filter(x=>!x.b.hidden).map(x=>x.b).slice(0,16);
  lastShapes=fixed.length+inline.length;
  function renderShapes(shapes){
    if(!shapes.length)return;
    gl.uniform1i(loc('uShapeCount'),shapes.length);
    shapes.forEach((el,i)=>{
    const r=el.getBoundingClientRect();gl.uniform2f(loc(`uShapeCenters[${i}]`),(r.left-box.left+r.width/2)*dpr,(box.height-r.top+box.top-r.height/2)*dpr);
    gl.uniform2f(loc(`uShapeHalves[${i}]`),r.width*dpr/2,r.height*dpr/2);gl.uniform1i(loc(`uShapeTypes[${i}]`),0);
    f(`uShapeRadii[${i}]`,Number(el.dataset.radius||24)*dpr);f(`uShapeTints[${i}]`,dark()?.05:.01);f(`uShapeTintLights[${i}]`,dark()?0:1);
    f(`uShapeFrosts[${i}]`,0);f(`uShapeOpacities[${i}]`,1);f(`uShapePressures[${i}]`,0);
    gl.uniform2f(loc(`uShapePressAxes[${i}]`),1,1);gl.uniform2f(loc(`uLightDirs[${i}]`),-.719,.695);
    });
    gl.drawArrays(gl.TRIANGLES,0,3);
  }
  for(const[k,v]of Object.entries({Refraction:refraction,EdgeReach:.14,EdgeWidth:.21,Dispersion:1.5,BackdropBlur:0,Body:glassStrength,Absorption:.25,Rim:.24,Reflection:.31,Highlight:.34,Echo:.28,Hairline:.92,HairWidth:.52}))f('u'+k,v);
  gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE_MINUS_SRC_ALPHA);
  gl.enable(gl.SCISSOR_TEST);gl.scissor(0,0,w,Math.max(0,h-Math.round(116*dpr)));renderShapes(inline);gl.disable(gl.SCISSOR_TEST);
  renderShapes(fixed);frames++;
  const error=gl.getError();if(error)lastError=String(error);
  if(performance.now()<animateUntil)invalidate();
}
function invalidate(measure=false){needMeasure=needMeasure||measure;if(!needsFrame){needsFrame=true;requestAnimationFrame(draw);}}
new ResizeObserver(()=>invalidate(true)).observe(phone);
document.fonts.ready.then(()=>invalidate(true));
window.previewDebug={state:()=>({page,theme,dark:dark(),frames,ready,lastShapes,error:lastError,textGlyphs:cached.filter(x=>x.ch).length}),invalidate:()=>invalidate(true),source:()=>source.toDataURL(),pixels:()=>{const out=new Uint8Array(canvas.width*canvas.height*4);gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,out);let visible=0,sum=0;for(let i=0;i<out.length;i+=4){if(out[i+3])visible++;sum+=out[i]+out[i+1]+out[i+2];}return{visible,sum};}};
initializeGeneration();applyTheme();render();
