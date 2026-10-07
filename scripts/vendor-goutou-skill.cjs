const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const source = path.join(root, '.tools/vendor/goutoujunshi');
const destination = path.join(root, 'android/app/src/main/assets/skills/goutoujunshi');
const references = {};
for (const folder of ['knowledge', 'practical']) {
  for (const name of fs.readdirSync(path.join(source, 'references', folder)).sort()) {
    if (name.endsWith('.md')) references[`references/${folder}/${name}`] = fs.readFileSync(path.join(source, 'references', folder, name), 'utf8');
  }
}
// ASCII asset paths also work with Windows-hosted Android resource tests.
fs.mkdirSync(destination, { recursive: true });
fs.writeFileSync(path.join(destination, 'references.json'), JSON.stringify(references, null, 2) + '\n');
for (const name of ['SKILL.md', 'LICENSE']) fs.copyFileSync(path.join(source, name), path.join(destination, name));
console.log(`Bundled ${Object.keys(references).length} references with original contents and titles.`);
