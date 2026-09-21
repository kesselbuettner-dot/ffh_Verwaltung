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
const sidebar={dataset:{},style:{}};
const document={getElementById:(id)=>id==='navContainer'?host:id==='sidebar'?sidebar:null,
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
vm.runInNewContext(source,context,{filename:'menu-designer.js'});
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
console.log('PASS menu runtime smoke: fallback, roles, permissions, old-layout migration and current routes');
