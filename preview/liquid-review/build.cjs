const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '../..');
const ref = path.resolve(root, '../Huitu skill/reference/webgl-apple-liquid-glass');
const assets = {};
for (const [name, file] of Object.entries({
  poster: path.join(ref, 'assets/wallpapers/home-page-sunset.png'),
  fantasy: path.join(ref, 'assets/wallpapers/earth-black.png'),
  lake: path.join(ref, 'assets/wallpapers/natural-lake.webp'),
  city: path.join(ref, 'assets/wallpapers/night-city.webp'),
  lines: path.join(ref, 'assets/wallpapers/abstract-lines.webp'),
  blocks: path.join(ref, 'assets/wallpapers/color-blocks.webp'),
})) {
  const mime = file.endsWith('.png') ? 'image/png' : file.endsWith('.jpg') ? 'image/jpeg' : 'image/webp';
  assets[name] = `data:${mime};base64,${fs.readFileSync(file).toString('base64')}`;
}
assets.vertex = fs.readFileSync(path.join(root, 'android/app/src/main/assets/glass/fullscreen.vert'), 'utf8');
assets.fragment = fs.readFileSync(path.join(root, 'android/app/src/main/assets/glass/reference-v2.frag'), 'utf8');
fs.writeFileSync(path.join(__dirname, 'assets.js'), `window.PREVIEW_ASSETS=${JSON.stringify(assets)};`);
const lucide = path.dirname(require.resolve('lucide/package.json'));
fs.copyFileSync(path.join(lucide, 'dist/umd/lucide.min.js'), path.join(__dirname, 'lucide.min.js'));
fs.copyFileSync(path.join(lucide, 'LICENSE'), path.join(__dirname, 'LUCIDE-LICENSE'));
fs.copyFileSync(path.join(ref, 'LICENSE'), path.join(__dirname, 'GLASS-LICENSE'));
let html=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
html=html.replace('<link rel="stylesheet" href="style.css">',()=>`<style>${fs.readFileSync(path.join(__dirname,'style.css'),'utf8')}</style>`);
for(const script of ['assets.js','lucide.min.js','generation-state.js','generation.js','app.js']){
  const js=fs.readFileSync(path.join(__dirname,script),'utf8').replace(/<\/script/gi,'<\\/script');
  html=html.replace(`<script src="${script}"></script>`,()=>`<script>${js}</script>`);
}
fs.writeFileSync(path.join(__dirname,'YingCi-preview.html'),html);
fs.writeFileSync(path.join(__dirname,'YingCi-generation-preview.html'),html.replace('data-start="library"','data-start="generate"'));
console.log('Offline, single-file prototype ready.');
