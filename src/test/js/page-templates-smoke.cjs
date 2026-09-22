const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const core=fs.readFileSync('src/main/resources/static/design-system.js','utf8');
const code=fs.readFileSync('src/main/resources/static/page-templates.js','utf8');
const css=fs.readFileSync('src/main/resources/static/page-templates.css','utf8');
const html=fs.readFileSync('src/main/resources/static/index.html','utf8');
const security=fs.readFileSync('src/main/java/de/bierverein/api/SecurityConfig.java','utf8');
const sw=fs.readFileSync('src/main/resources/static/sw.js','utf8');
const docs=fs.readFileSync('docs/UI_KOMPONENTEN.md','utf8');
class FakeNode {
 constructor(tag){this.tagName=tag;this.children=[];this.events={};this.style={};this.textContent='';this.className='';this.attributes={};this.disabled=false;this.classList={add:name=>{this.className+=(this.className?' ':'')+name;}};}
 appendChild(node){this.children.push(node);return node;}
 replaceChildren(...nodes){this.children=nodes;}
 addEventListener(type,callback){this.events[type]=callback;}
 setAttribute(name,value){this.attributes[name]=String(value);}
 getAttribute(name){return this.attributes[name];}
}
const doc={documentElement:{style:{setProperty(){}}},createElement:tag=>new FakeNode(tag),
 createTextNode:text=>{const element=new FakeNode('#text');element.textContent=text;return element;}};
const window={};
const ctx={window,document:doc,Node:FakeNode,Math,console};
vm.runInNewContext(core,ctx,{filename:'design-system.js'});
vm.runInNewContext(code,ctx,{filename:'page-templates.js'});
const api=window.FWPageTemplates;
const ids=['overview','management','detail','form','tasks','settings'];
assert.deepEqual(Array.from(api.list(),x=>x.id),ids,'All six named page templates available');
for(const id of ids){
 const view=api.preview(id);
 assert.equal(view.tagName,'section',id+' must be an actual DOM section');
 assert(view.className.includes('ds-template-'+id),'Missing template-specific class for '+id);
 assert(view.className.includes('ds-page'),'Missing common page shell for '+id);
}
function textOf(node){return [node.textContent,...node.children.map(textOf)].join(' ');}
const injection='<img src=x onerror=alert(1)>';
const management=api.render('management',{title:'Inventar',columns:[{key:'name',label:'Name'}],rows:[{name:injection}]});
assert(textOf(management).includes(injection),'Text remains escaped and is rendered as text');
assert.equal(management.children.find(x=>x.tagName==='header').tagName,'header');
let lastSearch='';
const searchable=api.management({columns:[{key:'name',label:'Name'}],rows:[],
 onSearch:value=>{lastSearch=value;}});
const searchField=searchable.children.find(x=>x.className==='ds-card')
 .children.find(x=>x.className==='ds-template-list').children.find(x=>x.className==='ds-template-toolbar').children[0].children[1];
assert.equal(searchField.type,'search');
searchField.value='Helm';
searchField.events.input();
assert.equal(lastSearch,'Helm','Management search must react during typing, not only after focus leaves field');
const before=textOf(management);
assert(!management.children.some(c=>c.tagName==='img'),'No injected HTML element may be created');
assert.throws(()=>api.render('not-a-real-template'),/Unbekanntes Seiten-Template/);
let saved=false,cancelled=false;
const form=api.form({fields:[{label:'Name',name:'name',required:true}],onSubmit:()=>{saved=true;},onCancel:()=>{cancelled=true;}});
const htmlForm=form.children.find(x=>x.tagName==='form');
assert(htmlForm,'Form template must contain real <form>');
htmlForm.events.submit({preventDefault(){}});
assert(saved,'Submitting calls the supplied business callback');
assert(htmlForm.children.length>=2,'Form includes input group and completion actions');
const settings=api.settings({sections:[{title:'Allgemein',content:'Erster Bereich'},{title:'Darstellung',content:'Zweiter Bereich'}]});
const layout=settings.children.find(x=>x.className==='ds-settings-layout');
assert(layout&&layout.children[0].tagName==='nav','Settings use accessible section navigation');
assert.equal(layout.children[0].attributes['aria-label'],'Einstellungsbereiche');
assert(textOf(layout.children[1]).includes('Erster Bereich'));
layout.children[0].children[1].events.click();
assert(textOf(layout.children[1]).includes('Zweiter Bereich'),'Switching settings section changes visible content');
const due=api.tasks({rows:[{name:'Prüfung A',due:'2026-10-01',assignee:'Gerätewart',status:{label:'Fällig',variant:'warning'}}]});
assert(textOf(due).includes('Fällig'));
for(const cls of ['ds-metric-grid','ds-template-toolbar','ds-detail-grid','ds-form-grid','ds-settings-layout','ds-template-gallery','@media(max-width:700px)'])
 assert(css.includes(cls),'Central template style missing: '+cls);
for(const asset of ['page-templates.css?v=1','page-templates.js?v=2'])
 assert(html.includes(asset)&&sw.includes('/'+asset),'Page template asset missing from page or PWA cache: '+asset);
for(const asset of ['"/page-templates.js"','"/page-templates.css"'])
 assert(security.includes(asset),'Static asset not public: '+asset);
assert(sw.includes('ffh-verwaltung-unified-inspections-v45'));
assert(html.includes("['templates','📐 Seiten-Templates']")&&html.includes('onSelect:key=>adminSettingsPage(key)'),'Template gallery entry in shared admin settings must route correctly');
assert(html.includes("lib.preview(selectedPageTemplate)")&&html.includes("const lib=window.FWPageTemplates"),'Interactive gallery preview missing');
assert(docs.includes('FWPageTemplates.render'),'Binding page layout docs missing');
assert(/if\s*\(section==='templates'\)\s*return\s+adminPageTemplatesPage\(\)/.test(html),'Admin settings routing for the gallery missing');
assert(html.includes("selectedPageTemplate=entry.id"),'Template gallery selection must update live preview');
assert(html.includes("adminSettingsPage('design')"),'Common layout editor must be reachable from the template gallery');
for(const token of ['templateColumns','templateGap']){
 const setting=fs.readFileSync('src/main/java/de/bierverein/api/DesignSystemController.java','utf8');
 const core=fs.readFileSync('src/main/resources/static/design-system.js','utf8');
 assert(setting.includes('"'+token+'"')&&core.includes(token),'Global layout token must be validated server-side and used client-side: '+token);
}
assert(!code.includes('/api/'),'Page templates must contain no direct backend access');
console.log('PASS six pages, real DOM and callbacks, accessible settings, safe text, responsive CSS, gallery, PWA cache, permissions and docs');
