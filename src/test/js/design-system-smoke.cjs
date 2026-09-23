const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const css=fs.readFileSync('src/main/resources/static/design-system.css','utf8');
const src=fs.readFileSync('src/main/resources/static/design-system.js','utf8');
const html=fs.readFileSync('src/main/resources/static/index.html','utf8');
const sw=fs.readFileSync('src/main/resources/static/sw.js','utf8');
const security=fs.readFileSync('src/main/java/de/bierverein/api/SecurityConfig.java','utf8');
const doc=fs.readFileSync('docs/DESIGN_SYSTEM.md','utf8');
const agents=fs.readFileSync('AGENTS.md','utf8');
for(const tag of ['.ds-page','.ds-card','.ds-btn','.ds-table','.ds-field','.ds-badge','.ds-empty','.ds-modal-content','.u-flex','.u-grid-2','@media(max-width:720px)'])
 assert(css.includes(tag),'Central design component missing: '+tag);
for(const name of ['design-system.css?v=1','design-system.js?v=1'])
 assert(html.includes(name),'Design system not loaded: '+name);
for(const path of ['"/design-system.css"','"/design-system.js"'])
 assert(security.includes(path),'Unauthenticated browser could not fetch '+path);
assert(sw.includes('ffh-verwaltung-print-pagebreak-fix-v49'),'PWA cache not invalidated after page template update');
assert(sw.includes('/design-system.css?v=1')&&sw.includes('/design-system.js?v=1'),'New design assets not offline-cacheable');
assert(html.includes("adminSettingsPage('design')"),'Central design admin tab missing');
assert(html.includes("'/api/settings/design-system'"),'Central design save/load API not used');
assert(doc.includes('window.FWComponents')&&agents.includes('FWComponents'),'Design documentation not enforceable');

class FakeNode{
 constructor(tag){this.tagName=tag;this.children=[];this.events={};this.style={};this.textContent='';this.className='';this.disabled=false;}
 appendChild(child){this.children.push(child);return child;}
 addEventListener(name,cb){this.events[name]=cb;}
}
const style={values:{},setProperty(name,value){this.values[name]=value}};
const document={documentElement:{style},createElement:tag=>new FakeNode(tag),createTextNode:value=>{const n=new FakeNode('#text');n.textContent=value;return n;}};
const window={};
vm.runInNewContext(src,{window,document,Node:FakeNode,Math});
const u=window.FWComponents;
assert.deepEqual(Object.keys(u).sort(),['applyTokens','badge','button','card','config','empty','field','modalContent','page','table'].sort());
const action=u.button({label:'Löschen',variant:'danger',onClick:()=>{action.clicked=true;}});
assert.equal(action.className,'ds-btn ds-btn-danger');action.events.click();assert.equal(action.clicked,true);
const field=u.field({label:'Mitglied',value:'Max Muster'});
assert(field.children.some(n=>n.textContent==='Mitglied'),'Label missing');
const script='<img src=x onerror=alert(1)>';
const table=u.table({columns:[{key:'name',label:'Mitglied'}],rows:[{name:script}]});
const cell=table.children[0].children[1].children[0].children[0];
assert.equal(cell.children[0].textContent,script,'User text must remain literal');
assert.equal(cell.children[0].tagName,'#text','Never turn record content into HTML');
const page=u.page({title:'Mitglieder',actions:[action],children:[u.card({title:'Übersicht',content:table})]});
assert.equal(page.className,'ds-page');assert(page.children.length>=2);
assert.equal(u.table({columns:[{key:'x',label:'Eintrag'}],rows:[]}).children[0].children[1].children[0].children[0].textContent,'Keine Einträge vorhanden');
assert.equal(u.applyTokens({radius:'12px',space:'20px',pageWidth:'1400px',textSize:'17px'}).radius,'12px');
assert.equal(style.values['--ds-radius-lg'],'12px');
assert.equal(u.applyTokens({radius:'url(evil)',space:'900px',foo:'10px'}).radius,undefined);
assert.equal(style.values['--ds-radius-lg'],'12px','Invalid value must not override central theme');
for(const block of [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)])new vm.Script(block[1]);
console.log('PASS centralized design templates, XSS-safe table and text, validated admin tokens, cache, CSS/JS loading and inline JS');
