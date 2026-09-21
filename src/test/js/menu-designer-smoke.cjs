const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/menu-designer.js', 'utf8');
const html = fs.readFileSync('src/main/resources/static/index.html', 'utf8');
const required=['vehicles','device-inspection-plans','training-documents','admin-settings','dashboard'];
for (const id of required) assert(html.includes("id:'"+id+"'"),'Original 577e741 menu route missing: '+id);
assert(html.includes('function renderOriginalNavigation()'),'Original navigation fallback must remain');
assert(source.includes('/api/settings/menu-layout'),'Menu must save via dedicated endpoint');
const host={innerHTML:'',querySelector:()=>null,querySelectorAll:()=>[]};
const mobile={innerHTML:'',querySelectorAll:()=>[]};
const bar={style:{setProperty:(name,value)=>{bar[name]=value}}};
const sidebar={dataset:{},style:{}};
const document={getElementById:(id)=>id==='navContainer'?host:id==='sidebar'?sidebar:id==='mobileQuickNav'?mobile:null,
  querySelector:(q)=>q==='.mobile-nav'?bar:null,querySelectorAll:()=>[],
  documentElement:{style:{setProperty:()=>{}}}};
const permissions=new Set();
const role={name:'MEMBER'};
const definitions=[
  {id:'dashboard',section:'Übersicht',label:'Dashboard',icon:'🏠',roles:['*']},
  {id:'vehicles',section:'Gerätewart',label:'Fahrzeuge',icon:'🚒',permission:'fire.vehicles.read',roles:['*']},
  {id:'devices',section:'Gerätewart',label:'Geräte',icon:'🧰',permission:'fire.devices.read',roles:['*']},
  {id:'admin-settings',section:'Administration',label:'Einstellungen',icon:'⚙️',roles:['ADMIN']},
  {id:'unimplemented',section:'Weitere',label:'Nicht fertig',icon:'◻',roles:['*'],implemented:false}
];
const settings={menuOrder:definitions.map(d=>d.id),hiddenMenuItems:[],menuLayout:null,navColor:'#071827'};
const context={window:{currentPage:'dashboard'},document,menuDefinitions:definitions,appSettings:settings,
  token:null,console,api:async()=>[],closeMenu:()=>{},setActive:()=>{},
  menuAllowed:i=>i.implemented!==false&&(!i.permission||permissions.has(i.permission))&&(i.roles.includes('*')||i.roles.includes(role.name)),
  esc:v=>String(v??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]))};
const instrumented=source.replace('window.MenuDesigner={renderNavigation,',
  'window.MenuDesigner={__test:{normalized,moveTo,setDraft:v=>draft=v,layout:()=>draft},renderNavigation,');
vm.runInNewContext(instrumented,context,{filename:'menu-designer.js'});
const render=()=>{context.window.MenuDesigner.renderNavigation();return host.innerHTML};
let output=render();
assert(output.includes('data-nav-id="dashboard"'),'Dashboard must always be visible');
assert(!output.includes('data-nav-id="vehicles"'),'Vehicles denied without effective permission');
assert(!output.includes('data-nav-id="admin-settings"'),'Member cannot see administration');
assert(!output.includes('unimplemented'),'Unimplemented module must stay hidden');
permissions.add('fire.vehicles.read');
output=render();
assert(output.includes('data-nav-id="vehicles"'),'Effective permission enables vehicles');
assert(!output.includes('data-nav-id="devices"'),'Device permission remains separate');
role.name='ADMIN'; permissions.add('fire.devices.read');
output=render();
assert(output.includes('data-nav-id="admin-settings"'),'Admin must keep settings entry');
settings.menuLayout=JSON.stringify({version:2,groups:[{id:'prior',title:'Alte Gruppe',
  children:['dashboard','vehicles']}],entries:{dashboard:{hidden:true}},style:{background:'#123456'}});
output=render();
assert(output.includes('data-nav-id="dashboard"'),'Dashboard cannot be hidden by malformed old layout');
assert(output.includes('data-nav-id="admin-settings"'),'New 577 modules recover from older layout');
assert(!output.includes('data-nav-id="unimplemented"'),'Old layout cannot expose an unfinished feature');
assert.equal(sidebar.dataset.menuEffect,'gradient');

const test=context.window.MenuDesigner.__test;
const recovered=test.normalized();
test.setDraft(recovered);
test.moveTo([0,0],[]); // Move a nested page to root level without creating an empty fake group
assert(test.layout().groups.some(node=>node==='dashboard'),'Dashboard may be a standalone root item');
output=render();
assert(output.includes('nav-root'),'Root-level pages display as direct menu entries');
settings.menuLayout=JSON.stringify({version:2,groups:['dashboard',{id:'g',title:'Feuerwehr',children:['vehicles','devices']}],
 entries:{},quickNav:['dashboard','devices','vehicles','admin-settings'],style:{background:'#071827'}});
test.setDraft(null);
role.name='MEMBER';permissions.delete('fire.devices.read');permissions.delete('fire.vehicles.read');
output=render();
assert(mobile.innerHTML.includes('data-mobile-page="dashboard"'),'Authorized shortcut remains visible');
assert(!mobile.innerHTML.includes('data-mobile-page="devices"'),'Unauthorized device shortcut is filtered out');
assert(!mobile.innerHTML.includes('data-mobile-page="vehicles"'),'Unauthorized vehicle shortcut is filtered out');
assert(!mobile.innerHTML.includes('data-mobile-page="admin-settings"'),'Only three slots may be configured');
role.name='ADMIN';permissions.add('fire.devices.read');permissions.add('fire.vehicles.read');
output=render();
assert(mobile.innerHTML.includes('data-mobile-page="devices"')&&mobile.innerHTML.includes('data-mobile-page="vehicles"'),
  'Admin receives authorized quick links');
assert.equal((mobile.innerHTML.match(/data-mobile-page=/g)||[]).length,3,'At most three configurable shortcuts');
assert.equal(bar['--mobile-quick-count'],'4','Three shortcuts and the fixed menu button are four slots');
const manifest=JSON.parse(fs.readFileSync('src/main/resources/static/manifest.json','utf8'));
assert(manifest.icons.some(i=>i.src==='/icons/fw-cockpit-icon-192.png'),'PWA uses FW Cockpit 192 icon');
assert(manifest.icons.some(i=>i.src==='/icons/fw-cockpit-icon-512.png'),'PWA uses FW Cockpit 512 icon');
for(const size of ['192','512','maskable-512','180']){
 const buffer=fs.readFileSync('src/main/resources/static/icons/fw-cockpit-icon-'+size+'.png');
 assert(buffer.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])),'Real PNG generated for '+size);
}
assert(html.includes('onclick="openImprint()"'),'Impressum linked from logo and sidebar');
assert(html.includes('id="mobileMenuButton"'),'Permanent mobile drawer control retained');
console.log('PASS menu runtime smoke: standalone pages, quick links, rights, real app icons and read-only imprint');

