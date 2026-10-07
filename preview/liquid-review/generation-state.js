/* Session-only preview data. This module never sends requests or writes storage. */
(function(root) {
  class GenerationPreview {
    constructor(assets) {
      this.assets = assets;
      this.sequence = 0;
      this.draft = {separate:false, references:[], prompt:'远山与湖面，日落时分的自然光，细腻的摄影质感。保留清晰的空间层次，色彩克制，画面安静。', ratio:'3:4', quality:'标准', count:1};
      this.records = [
        {id:'demo-lake', name:'湖光与远山', image:assets.lake},
        {id:'demo-sunset', name:'日落山色', image:assets.poster},
      ].map(x => ({...x, prompt:this.draft.prompt, mode:'text', ratio:'3:4', quality:'标准', createdAt:'示例图片', status:'示例', referenceCount:0}));
    }
    set(name, value) {
      const options = {separate:[false,true], ratio:['1:1','3:4','4:3','9:16','16:9'], quality:['标准','高清'], count:[1,2,3,4]};
      if (name === 'prompt') {this.draft.prompt=String(value);return;}
      if (!options[name]?.includes(value)) throw new Error('不支持的选项');
      this.draft[name]=value;
    }
    addReferences(images) {
      const valid=images.filter(x=>/^data:image\//.test(x.image));
      this.draft.references.push(...valid.slice(0,6-this.draft.references.length));
    }
    removeReference(index) {
      if(!Number.isInteger(index)||index<0||index>=this.draft.references.length)return;
      this.draft.references.splice(index,1);
      if(this.draft.references.length<2)this.draft.separate=false;
    }
    mode() {return this.draft.references.length?'reference':'text';}
    referenceMode() {
      const length=this.draft.references.length;
      return length===0?'none':length===1?'single':this.draft.separate?'batch':'joint';
    }
    activeReferences() {
      return this.draft.references;
    }
    outputCount() {
      return this.draft.count * (this.referenceMode()==='batch' ? this.activeReferences().length : 1);
    }
    loadPrompt(value) {this.draft.prompt=value;}
    createSamples() {
      if(!this.draft.prompt.trim()) throw new Error('请先输入提示词');
      const images=[this.assets.lake,this.assets.poster,this.assets.city,this.assets.lines];
      const results=Array.from({length:this.outputCount()},(_,i)=>({
        id:'sample-'+(++this.sequence), name:'画面 '+this.sequence,
        image:images[i%images.length], prompt:this.draft.prompt,
        mode:this.mode(), referenceMode:this.referenceMode(),
        ratio:this.draft.ratio, quality:this.draft.quality, count:this.draft.count,
        references:this.activeReferences().map(x=>({...x})),
        referenceCount:this.activeReferences().length, createdAt:'本次预览', status:'示例',
      }));
      this.records.unshift(...results);
      return results;
    }
    delete(ids) {
      const selected=new Set(ids);
      this.records=this.records.filter(x=>!selected.has(x.id));
    }
    reuse(id) {
      const record=this.records.find(x=>x.id===id);
      if(!record) throw new Error('这条记录已删除');
      this.draft={separate:record.referenceMode==='batch'&&(record.references||[]).length>1,
        references:(record.references||[]).map(x=>({...x})), prompt:record.prompt,
        ratio:record.ratio, quality:record.quality, count:record.count||1};
    }
    collectionCopy(id,name) {
      const record=this.records.find(x=>x.id===id);
      if(!record) throw new Error('这条记录已删除');
      if(!name.trim()||Array.from(name.trim()).length>16) throw new Error('收藏名称需要 1 到 16 个字');
      return {id:'collection-'+(++this.sequence), name:name.trim(), image:record.image, customPrompt:record.prompt};
    }
  }
  if(typeof module!=='undefined') module.exports=GenerationPreview;
  else root.GenerationPreview=GenerationPreview;
})(typeof window!=='undefined'?window:globalThis);
