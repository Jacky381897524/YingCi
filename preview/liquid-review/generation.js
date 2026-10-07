/* Preview screens only. The samples are bundled images, not model output. */
const generation=new window.GenerationPreview(window.PREVIEW_ASSETS);
let resultId='demo-lake', selectingHistory=false, selectedHistory=new Set(), modalReturnFocus=null, lastValidCollectionName='';
const generationPages=['generate','generation-result','generation-history'];
const genButton=(ic,label,action,cls='icon-button')=>`<button class="${cls}" data-gen="${action}" aria-label="${esc(label)}" title="${esc(label)}">${icon(ic)}</button>`;
function genSelect(id,label,values,current) {
  return `<label class="parameter"><span data-paint>${esc(label)}</span><span class="select-value"><select id="${id}" aria-label="${esc(label)}">${values.map(v=>`<option value="${esc(v)}" ${String(v)===String(current)?'selected':''}>${esc(v)}</option>`).join('')}</select>${icon('chevron-down')}</span></label>`;
}
function resultTile(record, selectable=false) {
  const selected=selectedHistory.has(record.id);
  return `<button class="result-tile ${selected?'is-selected':''}" data-gen="${selectable?'select':'result'}:${record.id}" ${selectable?`aria-pressed="${selected}"`:''} aria-label="${selectable?'选择':'查看'}${esc(record.name)}">
    <img data-paint src="${record.image}" alt="${esc(record.name)}">
    <span class="sample-label">示例</span>${selectable?`<span class="selection-mark">${icon(selected?'circle-check':'circle')}</span>`:''}
    <span class="result-caption" data-paint>${esc(record.name)}</span></button>`;
}
function generationRender() {
  const d=generation.draft;
  if(page==='generate') {
    header('生图');
    toolbar.insertAdjacentHTML('beforeend',genButton('history','生成记录','history','round'));
    toolbar.lastElementChild.dataset.shape='';toolbar.lastElementChild.dataset.radius='22';
    content.innerHTML=`<div class="generation-page">
      ${referenceSection()}
      <div class="section-heading"><label for="generation-prompt" data-paint>提示词</label><button class="text-action" data-gen="choose-prompt">从收藏选择 ${icon('chevron-right')}</button></div>
      <textarea id="generation-prompt" class="prompt-editor" rows="5" aria-label="生图提示词" placeholder="描述想要的画面…">${esc(d.prompt)}</textarea>
      <div class="model-line"><span data-paint>模型</span><button data-gen="model" class="model-name">已连接的生图模型 ${icon('chevron-down')}</button></div>
      <div class="parameter-grid">${genSelect('generation-ratio','画面比例',['1:1','3:4','4:3','9:16','16:9'],d.ratio)}${genSelect('generation-quality','清晰度',['标准','高清'],d.quality)}${genSelect('generation-count',generation.referenceMode()==='batch'?'每张输出':'生成张数',[1,2,3,4],d.count)}</div>
      <details id="generation-advanced" class="advanced"><summary><span>更多参数</span>${icon('chevron-down')}</summary><p class="subtle" data-paint>离线预览尚未检测模型能力。正式版仅显示模型支持的参数。</p></details>
      ${anchor('预览生成结果'+(generation.outputCount()>1?' · '+generation.outputCount()+' 张':''),'generation-demo','inline-anchor','sparkles')}
      ${text('离线示例 · 不发送图片、不调用 API、不计费','p','subtle preview-disclaimer')}
      <div class="section-heading recent-heading"><h2 data-paint>最近生成</h2><button class="text-action" data-gen="history">全部 ${icon('chevron-right')}</button></div>
      <div class="results-grid">${generation.records.slice(0,2).map(r=>resultTile(r)).join('')||text('暂无生成记录','p','subtle')}</div>
    </div>`;
  }
  if(page==='generation-result') {
    const record=generation.records.find(x=>x.id===resultId);
    if(!record){page='generation-history';return generationRender();}
    header('生成结果',true);
    toolbar.insertAdjacentHTML('beforeend',genButton('ellipsis','更多操作','result-menu','round'));
    toolbar.lastElementChild.dataset.shape='';toolbar.lastElementChild.dataset.radius='22';
    content.innerHTML=`<div class="generation-result">
      <div class="result-image"><img data-paint src="${record.image}" alt="${esc(record.name)}"><span class="sample-label">示例图片 · 非实际生成</span></div>
      <div class="result-info"><span data-paint>${esc(record.name)}</span><span class="subtle" data-paint>${esc(record.createdAt)}</span></div>
      <div class="result-actions">${anchor('导出图片','generation-export','inline-anchor','download')}${anchor('再次使用','generation-reuse','inline-anchor','rotate-ccw')}</div>
      <div class="section-heading"><h2 data-paint>提示词</h2>${genButton('copy','复制提示词','copy-result')}</div>
      ${text(record.prompt,'p','prompt-text')}
      <dl class="result-parameters"><div><dt>参考图片</dt><dd>${record.referenceCount?record.referenceCount+' 张'+(record.referenceMode==='batch'?' · 分别生成':''):'无'}</dd></div><div><dt>所选比例</dt><dd>${esc(record.ratio)}</dd></div><div><dt>所选清晰度</dt><dd>${esc(record.quality)}</dd></div></dl>
      ${text('示例图片用于查看布局，不代表当前参数的实际生成效果。','p','subtle')}
    </div>`;
  }
  if(page==='generation-history') {
    header('生成记录',true);
    toolbar.insertAdjacentHTML('beforeend',genButton(selectingHistory?'x':'list-check',selectingHistory?'结束选择':'选择记录','select-mode','round'));
    toolbar.lastElementChild.dataset.shape='';toolbar.lastElementChild.dataset.radius='22';
    content.innerHTML=`<div class="history-summary"><span data-paint>${selectingHistory?'已选择 '+selectedHistory.size+' 张':generation.records.length+' 张 · 本次预览'}</span>${generation.records.length?`<button class="text-action ${selectingHistory?'':'danger-text'}" data-gen="${selectingHistory?'select-all':'clear-history'}">${selectingHistory?'全选':'清空'}</button>`:''}</div>
      ${selectingHistory?`<div class="history-tools"><button class="text-action danger-text" data-gen="delete-selected">${icon('trash-2')}<span>删除所选${selectedHistory.size?'（'+selectedHistory.size+'）':''}</span></button></div>`:''}
      ${generation.records.length?`<div class="results-grid">${generation.records.map(r=>resultTile(r,selectingHistory)).join('')}</div>`:`<div class="empty">${icon('images')}${text('暂无生成记录')}${text('生成后的图片会保存在这里','p','subtle')}</div>`}`;
  }
}
function referenceSection() {
  const d=generation.draft;
  const refs=generation.activeReferences();
  if(!refs.length)return `<div class="reference-section"><button class="reference-empty" data-gen="add-reference">${icon('image-plus')}<span>添加图片</span><span class="optional-label">可选</span></button></div>`;
  return `<div class="reference-section">
    <div class="reference-strip">${refs.map((r,i)=>`<div class="reference-thumb"><img data-paint src="${r.image}" alt="参考图 ${i+1}">${genButton('x','移除参考图 '+(i+1),'remove-reference:'+i)}<span>${i+1}</span></div>`).join('')}
    ${refs.length<6?genButton('image-plus','添加图片','add-reference','reference-add'):''}</div>
    ${refs.length>1?`<label class="separate-option" for="generation-separate"><span data-paint>分别生成</span><input id="generation-separate" type="checkbox" role="switch" ${d.separate?'checked':''}></label>`:''}
  </div>`;
}
function refreshGeneration(reset=false) {render(reset);}
function useGenerationPrompt(value) {generation.loadPrompt(value);navigate('generate');}
function generationBack() {navigate(page==='generation-result'?'generation-history':'generate');}
function showSheet(title,body) {
  const layer=$('#sheet-layer');
  if(layer.hidden)modalReturnFocus=document.activeElement;
  layer.innerHTML=`<div class="sheet-backdrop" data-gen="close-sheet"></div><section class="action-sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title" tabindex="-1"><div class="sheet-handle"></div><header><h2 id="sheet-title">${esc(title)}</h2>${genButton('x','关闭','close-sheet')}</header>${body}</section>`;
  layer.hidden=false;
  [...phone.children].forEach(el=>{if(el!==layer&&el.id!=='toast')el.inert=true;});
  lucide.createIcons();
  layer.querySelector('input,button')?.focus();
}
function closeSheet() {
  $('#sheet-layer').hidden=true;$('#sheet-layer').replaceChildren();
  [...phone.children].forEach(el=>el.inert=false);
  if(modalReturnFocus?.isConnected)modalReturnFocus.focus();
  modalReturnFocus=null;invalidate(true);
}
function sheetRow(ic,label,action,danger=false) {return `<button class="sheet-row ${danger?'danger-text':''}" data-gen="${action}">${icon(ic)}<span>${esc(label)}</span>${icon('chevron-right')}</button>`;}
function recordMenu() {
  showSheet('图片操作',sheetRow('image','设为已有收藏封面','pick-cover')+sheetRow('bookmark-plus','图片与提示词存为新收藏','new-collection')+sheetRow('download','导出图片','export')+sheetRow('copy','复制提示词','copy-result')+sheetRow('trash-2','删除这张图片','delete-result',true));
}
function confirmDelete(ids) {
  if(!ids.length)return toast('请先选择图片');
  showSheet('删除 '+ids.length+' 张图片？',`<p class="sheet-copy">已设为收藏封面的图片将保留。这里只删除本次预览中的示例记录。</p><div class="dialog-actions"><button data-gen="close-sheet">取消</button><button class="danger-text" id="confirm-delete">删除</button></div>`);
  $('#confirm-delete').addEventListener('click',()=>{generation.delete(ids);selectedHistory.clear();selectingHistory=false;closeSheet();navigate('generation-history');toast('已删除所选示例记录，收藏封面未受影响');},{once:true});
}
function exportResult() {
  const record=generation.records.find(x=>x.id===resultId);if(!record)return;
  const link=document.createElement('a');link.href=record.image;link.download=`映词-示例-${record.id}.${record.image.startsWith('data:image/png')?'png':'webp'}`;
  document.body.appendChild(link);link.click();link.remove();toast('已发起示例图片下载');
}
document.addEventListener('click',async e=>{
  const button=e.target.closest('[data-gen]');if(!button)return;
  const action=button.dataset.gen;
  if(action==='history'){selectingHistory=false;selectedHistory.clear();navigate('generation-history');}
  else if(action.startsWith('result:')){resultId=action.slice(7);navigate('generation-result');}
  else if(action.startsWith('select:')){const id=action.slice(7);selectedHistory.has(id)?selectedHistory.delete(id):selectedHistory.add(id);refreshGeneration();}
  else if(action==='select-mode'){selectingHistory=!selectingHistory;selectedHistory.clear();refreshGeneration();}
  else if(action==='select-all'){generation.records.forEach(r=>selectedHistory.add(r.id));refreshGeneration();}
  else if(action==='clear-history')confirmDelete(generation.records.map(r=>r.id));
  else if(action==='delete-selected')confirmDelete([...selectedHistory]);
  else if(action==='delete-result')confirmDelete([resultId]);
  else if(action==='close-sheet')closeSheet();
  else if(action==='result-menu')recordMenu();
  else if(action==='add-reference'){
    if(generation.draft.references.length>=6)return toast('预览最多选择 6 张参考图');
    const input=$('#generation-photo-picker');input.multiple=true;input.value='';input.click();
  }
  else if(action.startsWith('remove-reference:')){generation.removeReference(Number(action.slice(17)));refreshGeneration();}
  else if(action==='choose-prompt')showSheet('选择收藏提示词',items.map(x=>`<button class="collection-choice" data-gen="prompt:${x.id}"><img src="${x.image}" alt=""><span>${esc(x.name)}</span>${icon('chevron-right')}</button>`).join(''));
  else if(action.startsWith('prompt:')){const item=items.find(x=>x.id===action.slice(7));if(!item)return;generation.loadPrompt(item.customPrompt||prompt);closeSheet();refreshGeneration();}
  else if(action==='model')showSheet('生图模型',`<p class="sheet-copy">正式版使用你在“模型连接”中保存的服务地址、密钥和生图模型。此离线预览不读取配置，也不测试连接。</p>`);
  else if(action==='pick-cover')showSheet('选择要更换封面的收藏',items.map(x=>`<button class="collection-choice" data-gen="cover:${x.id}"><img src="${x.image}" alt=""><span>${esc(x.name)}</span>${icon('chevron-right')}</button>`).join(''));
  else if(action.startsWith('cover:')){
    const item=items.find(x=>x.id===action.slice(6));const record=generation.records.find(x=>x.id===resultId);if(!item||!record)return;
    showSheet('更换收藏封面？',`<p class="sheet-copy">将“${esc(item.name)}”的封面更换为这张图片，提示词保持不变。</p><div class="dialog-actions"><button data-gen="close-sheet">取消</button><button id="confirm-cover">更换</button></div>`);
    $('#confirm-cover').addEventListener('click',()=>{item.image=record.image;closeSheet();toast('已更换本次预览中的收藏封面');},{once:true});
  }
  else if(action==='new-collection'){
    lastValidCollectionName='';
    showSheet('存为新收藏',`<label for="result-collection-name" class="sheet-copy">收藏名称</label><input id="result-collection-name" class="plain-input" placeholder="输入名称" aria-describedby="result-name-count"><p id="result-name-count" class="subtle">0 / 16 · 最多两行</p><div class="dialog-actions"><button data-gen="close-sheet">取消</button><button data-gen="save-result-collection">收藏</button></div>`);
    $('#result-collection-name').focus();
  }
  else if(action==='save-result-collection'){
    try{items.unshift(generation.collectionCopy(resultId,$('#result-collection-name').value));closeSheet();toast('图片与提示词已加入本次预览收藏');}catch(error){toast(error.message);}
  }
  else if(action==='export'){closeSheet();exportResult();}
  else if(action==='copy-result'){
    const record=generation.records.find(x=>x.id===resultId);if(!record)return;
    try{await navigator.clipboard.writeText(record.prompt);toast('已复制提示词');}catch{toast('浏览器未允许访问剪贴板');}
  }
});
document.addEventListener('input',e=>{
  if(e.target.id==='generation-prompt')generation.set('prompt',e.target.value);
  if(e.target.id==='result-collection-name'){
    if(!e.isComposing){
      if(Array.from(e.target.value).length>16){e.target.value=lastValidCollectionName;toast('名称最多 16 个字，请缩短后输入');}
      else lastValidCollectionName=e.target.value;
    }
    const length=Array.from(e.target.value).length;
    $('#result-name-count').textContent=length+' / 16 · 最多两行';
  }
});
document.addEventListener('compositionend',e=>{
  if(e.target.id!=='result-collection-name')return;
  if(Array.from(e.target.value).length>16){e.target.value=lastValidCollectionName;toast('名称最多 16 个字，请缩短后输入');}
  else lastValidCollectionName=e.target.value;
  $('#result-name-count').textContent=Array.from(e.target.value).length+' / 16 · 最多两行';
});
document.addEventListener('beforeinput',e=>{
  if(e.target.id!=='result-collection-name'||e.isComposing||!e.data)return;
  const el=e.target,next=el.value.slice(0,el.selectionStart)+e.data+el.value.slice(el.selectionEnd);
  if(Array.from(next).length>16){e.preventDefault();toast('名称最多 16 个字，请缩短后输入');}
});
document.addEventListener('paste',e=>{
  if(e.target.id!=='result-collection-name')return;
  const el=e.target,next=el.value.slice(0,el.selectionStart)+e.clipboardData.getData('text')+el.value.slice(el.selectionEnd);
  if(Array.from(next).length>16){e.preventDefault();toast('粘贴内容超过 16 个字，未截断或替换原名称');}
});
document.addEventListener('change',e=>{
  if(e.target.id==='generation-separate'){generation.set('separate',e.target.checked);refreshGeneration();$('#generation-separate')?.focus({preventScroll:true});}
  const keys={'generation-ratio':'ratio','generation-quality':'quality','generation-count':'count'};
  if(keys[e.target.id]){generation.set(keys[e.target.id],e.target.id==='generation-count'?Number(e.target.value):e.target.value);refreshGeneration();}
});
document.addEventListener('toggle',e=>{if(e.target.id==='generation-advanced')invalidate(true);},true);
document.addEventListener('keydown',e=>{
  if($('#sheet-layer').hidden)return;
  if(e.key==='Escape'){e.preventDefault();closeSheet();}
  if(e.key==='Tab'){
    const focusable=[...$('#sheet-layer').querySelectorAll('button,input,[tabindex="0"]')];
    const first=focusable[0],last=focusable.at(-1);
    if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}
    else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}
  }
});
function initializeGeneration() {
  $('#generation-photo-picker').addEventListener('change',async e=>{
    const files=[...e.target.files].slice(0,6-generation.draft.references.length);
    const refs=[];
    for(const file of files){
      if(!file.type.startsWith('image/')){toast('请选择图片文件');continue;}
      if(file.size>20*1024*1024){toast('预览单张图片限制为 20 MB');continue;}
      try{
        const image=await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=reject;r.readAsDataURL(file);});
        await new Promise((resolve,reject)=>{const img=new Image();img.onload=resolve;img.onerror=reject;img.src=image;});
        refs.push({name:file.name,image});
      }catch{toast('图片无法读取，请换一张图片');}
    }
    generation.addReferences(refs);if(page==='generate')refreshGeneration();
  });
}
function generationAction(action) {
  if(action==='generation-demo'){
    try{const results=generation.createSamples();resultId=results[0].id;navigate('generation-result');toast('已展示 '+results.length+' 张内置示例，并非 AI 生成');}catch(error){toast(error.message);}
  }
  if(action==='generation-export')exportResult();
  if(action==='generation-reuse'){try{generation.reuse(resultId);navigate('generate');}catch(error){toast(error.message);}}
  if(action==='generation-from-reverse')useGenerationPrompt(prompt);
  if(action==='generation-from-detail')useGenerationPrompt(variant===0?(detail.customPrompt||prompt):'保留新照片主体，以摄影和铅笔拼贴重构画面。\n\n'+(detail.customPrompt||prompt));
}
