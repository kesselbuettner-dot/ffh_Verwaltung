/* Static regression for the restricted document center and shared TV. */
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const base='src/main/resources/static/';
function read(name){return fs.readFileSync(base+name,'utf8')}
function inline(html){return [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(m=>m[1]).filter(Boolean)}
const main=read('index.html'),display=read('display.html'),documents=read('document-center.js');
for(const [name,script] of [['document-center.js',documents],...inline(main).map((x,i)=>['index block '+i,x]),...inline(display).map((x,i)=>['display block '+i,x])]){
 assert.doesNotThrow(()=>new vm.Script(script),name+' invalid JavaScript');
}
assert.match(main,/id:'documents'.*DocumentCenter\.page\(\)/);
assert.match(main,/id:'wallboard'.*display\.html/);
assert.match(main,/id="dmWallboard"/);
assert.match(documents,/docExternalConsent/);
assert.match(documents,/approvedNonConfidential:true/);
assert.match(documents,/reviewedExcerpt:text\.value/);
assert.match(display,/\/api\/dashboard\/messages\/wallboard/);
assert.doesNotMatch(display,/\/api\/training\/events\/dashboard/);
assert.match(read('sw.js'),/\/document-center\.js\?v=/);
console.log('PASS: JavaScript syntax, document review consent, and private TV feed safeguards.');
