const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const GenerationPreview=require('./generation-state.js');
const assets={lake:'data:image/webp;base64,bGFrZQ==',poster:'data:image/png;base64,cG9zdGVy',city:'data:image/webp;base64,Y2l0eQ==',lines:'data:image/webp;base64,bGluZXM='};
const make=()=>new GenerationPreview(assets);
const refs=[{name:'one',image:assets.lake},{name:'two',image:assets.city}];
test('starts with separate draft and clearly marked sample records',()=>{
  const state=make();assert.equal(state.mode(),'text');assert.equal(state.records.length,2);
  assert.ok(state.records.every(x=>x.status==='示例'));
});
test('text mode creates the selected number and retains a prompt snapshot',()=>{
  const state=make();state.set('count',4);state.set('prompt','本次提示词');const created=state.createSamples();
  state.set('prompt','新提示词');assert.equal(created.length,4);assert.equal(created[0].prompt,'本次提示词');
  assert.equal(new Set(state.records.map(x=>x.id)).size,state.records.length);
});
test('empty prompt is rejected, but images remain optional',()=>{
  const state=make();state.set('prompt',' ');assert.throws(()=>state.createSamples(),/提示词/);
  assert.equal(state.records.length,2);
  state.set('prompt','风格');assert.equal(state.createSamples()[0].mode,'text');
});
test('adding images automatically selects single or joint reference without a mode control',()=>{
  const state=make();state.addReferences([refs[0]]);assert.equal(state.mode(),'reference');assert.equal(state.referenceMode(),'single');
  state.addReferences([refs[1]]);assert.equal(state.activeReferences().length,2);assert.equal(state.referenceMode(),'joint');
});
test('batch multiplies reference count, joint does not',()=>{
  const state=make();state.addReferences(refs);state.set('separate',true);state.set('count',3);
  assert.equal(state.outputCount(),6);assert.equal(state.createSamples().length,6);
  state.set('separate',false);assert.equal(state.outputCount(),3);assert.equal(state.createSamples().length,3);
});
test('turning separate generation on and off preserves all selected images',()=>{
  const state=make();state.addReferences(refs);state.set('separate',true);assert.equal(state.activeReferences().length,2);
  state.set('separate',false);assert.deepEqual(state.activeReferences(),refs);
});
test('removing the last image automatically returns to text generation and resets separate',()=>{
  const state=make();state.addReferences(refs);state.set('separate',true);state.set('count',2);
  state.removeReference(1);assert.equal(state.draft.separate,false);assert.equal(state.referenceMode(),'single');assert.equal(state.outputCount(),2);
  state.removeReference(0);assert.equal(state.mode(),'text');assert.equal(state.outputCount(),2);assert.equal(state.createSamples()[0].referenceCount,0);
});
test('reference import accepts only image data and caps at six',()=>{
  const state=make();state.addReferences([{name:'bad',image:'https://example.com/x.png'}]);
  assert.equal(state.draft.references.length,0);state.addReferences(Array(20).fill(refs[0]));assert.equal(state.draft.references.length,6);
});
test('invalid parameters are rejected',()=>{
  const state=make();for(const pair of [['count',0],['count',5],['mode','reference'],['separate','true'],['ratio','99:1'],['unknown','x']])assert.throws(()=>state.set(...pair));
});
test('result reuse restores prompt, parameters and a separate reference snapshot',()=>{
  const state=make();state.addReferences(refs);state.set('separate',true);state.set('count',2);state.set('ratio','16:9');
  const record=state.createSamples()[0];state.draft.references[0].name='changed';state.set('prompt','changed');state.set('count',1);
  state.reuse(record.id);assert.equal(state.draft.prompt,record.prompt);assert.equal(state.draft.ratio,'16:9');assert.equal(state.draft.count,2);
  assert.equal(state.draft.references[0].name,'one');assert.notEqual(state.draft.references[0],record.references[0]);
  assert.equal(state.draft.separate,true);assert.equal(state.referenceMode(),'batch');
});
test('single and bulk deletion retain unrelated records',()=>{
  const state=make();const created=state.createSamples();state.delete([created[0].id]);assert.equal(state.records.length,2);
  state.delete(['demo-lake','missing']);assert.deepEqual(state.records.map(x=>x.id),['demo-sunset']);
  state.delete(state.records.map(x=>x.id));assert.equal(state.records.length,0);
});
test('collection image and original prompt survive history deletion',()=>{
  const state=make();const record=state.records[0];const copy=state.collectionCopy(record.id,'湖光');
  state.delete([record.id]);assert.equal(copy.image,record.image);assert.equal(copy.customPrompt,record.prompt);
  assert.notEqual(copy,record);assert.throws(()=>state.collectionCopy(record.id,'不存在'),/已删除/);
});
test('collection titles accept 16 Unicode characters but reject 17 or whitespace',()=>{
  const state=make();assert.equal(Array.from(state.collectionCopy('demo-lake','图'.repeat(16)).name).length,16);
  assert.equal(Array.from(state.collectionCopy('demo-lake','😀'.repeat(16)).name).length,16);
  assert.throws(()=>state.collectionCopy('demo-lake','图'.repeat(17)),/16/);assert.throws(()=>state.collectionCopy('demo-lake',' '),/16/);
});
test('changing saved collection does not mutate history',()=>{
  const state=make();const before=state.records[0].image;const copy=state.collectionCopy('demo-lake','标题');copy.image=assets.city;
  assert.equal(state.records[0].image,before);
});
test('generation templates render all states without executing a browser',()=>{
  const handlers={};const context=vm.createContext({window:{GenerationPreview,PREVIEW_ASSETS:assets},
    document:{addEventListener:(name,fn)=>{handlers[name]=fn;}},
    content:{innerHTML:''},toolbar:{insertAdjacentHTML(){},lastElementChild:{dataset:{}}},
    esc:s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),
    icon:n=>`<i data-lucide="${n}"></i>`,header:()=>{},text:(s)=>`<p>${s}</p>`,anchor:(s)=>`<button>${s}</button>`,page:'generate'});
  vm.runInContext(fs.readFileSync(path.join(__dirname,'generation.js'),'utf8'),context);
  vm.runInContext('generationRender()',context);assert.match(context.content.innerHTML,/generation-prompt/);assert.match(context.content.innerHTML,/不计费/);
  assert.match(context.content.innerHTML,/add-reference/);assert.doesNotMatch(context.content.innerHTML,/generation-mode|reference-modes|generation-separate/);
  vm.runInContext('generation.addReferences([{image:generation.assets.lake}]);generationRender()',context);
  assert.match(context.content.innerHTML,/remove-reference:0/);assert.doesNotMatch(context.content.innerHTML,/generation-separate/);
  vm.runInContext('generation.addReferences([{image:generation.assets.city}]);generationRender()',context);
  assert.match(context.content.innerHTML,/generation-separate/);assert.match(context.content.innerHTML,/分别生成/);
  assert.doesNotMatch(context.content.innerHTML,/单图转换|共同参考|文生图|参考图生图/);
  vm.runInContext("generation.set('separate',true);generationRender()",context);assert.match(context.content.innerHTML,/每张输出/);
  vm.runInContext('generation.removeReference(1);generation.removeReference(0);generationRender()',context);
  assert.doesNotMatch(context.content.innerHTML,/generation-separate/);assert.match(context.content.innerHTML,/添加图片/);
  context.page='generation-result';vm.runInContext('generationRender()',context);assert.match(context.content.innerHTML,/非实际生成/);
  context.page='generation-history';vm.runInContext('generationRender()',context);assert.match(context.content.innerHTML,/clear-history/);
  vm.runInContext('selectingHistory=true;generationRender()',context);assert.match(context.content.innerHTML,/select:demo-lake/);
  vm.runInContext('generation.delete(generation.records.map(x=>x.id));generationRender()',context);assert.match(context.content.innerHTML,/暂无生成记录/);
});
test('prompt text in templates is escaped',()=>{
  const source=fs.readFileSync(path.join(__dirname,'generation.js'),'utf8');
  assert.match(source,/esc\(d\.prompt\)/);assert.match(source,/esc\(record\.name\)/);assert.match(source,/esc\(x\.name\)/);
});
