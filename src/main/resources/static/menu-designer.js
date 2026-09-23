/* FW-Cockpit menu designer: server-persisted configuration, no localStorage shadow state. */
(function () {
'use strict';
const ICONS = [
 ['🏠','Start'],['👥','Mitglieder'],['🍺','Theke'],['🛒','Einkauf'],['📦','Lager'],['📅','Kalender'],
 ['🚒','Löschfahrzeug'],['🧯','Feuerlöscher'],['👨‍🚒','Feuerwehr'],['🔥','Flamme'],['🚨','Alarm'],
 ['💧','Wasser'],['🪜','Leiter'],['📟','Funk'],['🔧','Werkzeug'],['🧰','Geräte'],['📝','Prüfung'],
 ['📋','Dienstplan'],['🎓','Schulung'],['📄','Dokumente'],['⚙️','Einstellungen'],['🔐','Berechtigung'],
 ['🎨','Darstellung'],['💶','Finanzen'],['🔴','Offen'],['✓','Erledigt'],['⌂','Haus'],['☰','Menü']
];
const STATIC_ICONS = [['helmet','Feuerwehrhelm'],['engine','Löschfahrzeug'],['extinguisher','Feuerlöscher'],['radio','Funkgerät'],['hose','Schlauch'],['ladder','Leiter'],['flame','Flamme']];
const TABLER_LABELS = [['layout-dashboard','Dashboard'],['users','Mitglieder'],['calendar','Kalender'],['settings','Einstellungen'],['truck','Fahrzeug'],['clipboard-check','Geräteprüfung'],['file-text','Dokument'],['shield-check','Berechtigungen'],['school','Ausbildung'],['wallet','Finanzen'],['shopping-cart','Einkauf'],['list-check','Prüfliste'],['tool','Werkzeug'],['archive','Lager'],['certificate','Qualifikation'],['user-shield','Administration'],['book','Unterlagen'],['menu-2','Menü'],['photo','Bild'],['search','Suche'],['flame','Flamme'],['home','Startseite'],['bell','Benachrichtigung'],['user-check','Mitglied geprüft']];

const DEFAULT_STYLE = {background:'#071827',active:'#1479e9',text:'#dce7ee',font:'Inter',size:15,weight:500,width:250,gap:3,depth:2,effect:'gradient'};
let draft=null, icons=[], iconReady=false, iconLoading=false, iconPromise=null, dragPath=null, message='';
const safe = value => esc(value);
const adminRequired = new Set(['admin','admin-members','admin-users','admin-settings']);
const PRODUCT_ICON = '/icons/fw-cockpit-brand.svg';
const known = () => new Map(menuDefinitions.filter(item => item.implemented !== false).map(item => [item.id,item]));
function group(id,title,children,icon){return {id,title,icon:icon||'☰',children:children||[]};}
function originalLayout(){
 const groups=[];
 const byName=new Map();
 const order=appSettings?.menuOrder?.length?appSettings.menuOrder:menuDefinitions.map(x=>x.id);
 const rank=new Map(order.map((x,i)=>[x,i]));
 [...menuDefinitions.filter(x=>x.implemented!==false)].sort((a,b)=>(rank.get(a.id)??999)-(rank.get(b.id)??999)).forEach(item=>{
   if(!byName.has(item.section)){const node=group('section-'+groups.length,item.section,[],item.section==='Gerätewart'?'🚒':'☰');groups.push(node);byName.set(item.section,node);}
   byName.get(item.section).children.push(item.id);
 });
 const entries={};(appSettings?.hiddenMenuItems||[]).forEach(item=>{entries[item]={hidden:true};});
 return {version:2,groups,entries,quickNav:['dashboard','theke','messages'],style:{...DEFAULT_STYLE,background:appSettings?.navColor||'#071827'}};
}
function parseLayout(){
 try {
  const parsed=JSON.parse(appSettings?.menuLayout||'null');
  if(parsed&&parsed.version===2&&Array.isArray(parsed.groups)&&parsed.entries&&parsed.style)return parsed;
 }catch(e){console.warn('Menükonfiguration wird auf Standard zurückgesetzt',e);}
 return originalLayout();
}
function normalized(){
 const value=JSON.parse(JSON.stringify(parseLayout()));
 const byId=known(),seen=new Set(),groupIds=new Set();
 function tidy(nodes,depth){
  if(!Array.isArray(nodes))return [];
  return nodes.slice(0,100).flatMap(node=>{
    if(typeof node==='string'){if(!byId.has(node)||seen.has(node))return [];seen.add(node);return [node];}
    if(!node||typeof node!=='object'||Array.isArray(node)||depth>1)return [];
    const id=String(node.id||'').slice(0,48);
    if(!/^[a-zA-Z0-9_-]{1,48}$/.test(id)||groupIds.has(id))return [];
    groupIds.add(id);
    return [group(id,String(node.title||'Gruppe').slice(0,60),tidy(node.children,depth+1),String(node.icon||'☰').slice(0,45))];
  });
 }
 value.groups=tidy(value.groups,0);
 const missing=menuDefinitions.filter(x=>x.implemented!==false&&!seen.has(x.id));
 missing.forEach(item=>{
   let target=value.groups.find(g=>g.title===item.section);
   if(!target){target=group('new-'+item.id,item.section,[]);value.groups.push(target);}
   target.children.push(item.id);
 });
 value.entries=Object.fromEntries(Object.entries(value.entries||{}).filter(([id,v])=>byId.has(id)&&v&&typeof v==='object')
  .map(([id,v])=>[id,{label:String(v.label||'').slice(0,60),icon:String(v.icon||'').slice(0,45),hidden:!!v.hidden}]));
 value.style={...DEFAULT_STYLE,...(value.style||{})};
 value.quickNav=Array.isArray(value.quickNav)?[...new Set(value.quickNav.filter(x=>x==='messages'||byId.has(x)))].slice(0,3):['dashboard','theke','messages'];
 return value;
}
function active(){return draft||normalized();}
function isGroup(n){return n!==null&&typeof n==='object'&&!Array.isArray(n);}
function nodeAt(path,layout){let list=layout.groups,node;for(const index of path){node=list[index];if(node===undefined)return null;list=isGroup(node)?node.children:[];}return node;}
function parentAt(path,layout){let list=layout.groups;for(const index of path.slice(0,-1)){if(!isGroup(list[index]))return null;list=list[index].children;}return list;}
function eachNode(nodes,fn,path=[]){nodes.forEach((node,i)=>{let p=[...path,i];fn(node,p);if(isGroup(node))eachNode(node.children,fn,p);});}
function findRef(node,layout){let found=null;eachNode(layout.groups,(n,p)=>{if(n===node)found=p;});return found;}
function iconSrc(value){
 const builtin=STATIC_ICONS.find(([key])=>value==='builtin:'+key);
 if(builtin)return '/icons/menu/'+builtin[0]+'.svg';
 if(!String(value||'').startsWith('custom:'))return null;
 let image=icons.find(i=>String(i.id)===value.slice(7));
 return image?.uri?.startsWith('data:image/')?image.uri:null;
}
function iconHtml(value){
 const tabler=String(value||'').startsWith('tabler:')?String(value).slice(7):'';
 if(tabler&&Object.prototype.hasOwnProperty.call(window.FWTablerIcons||{},tabler))return '<span class="menu-icon-tabler" aria-hidden="true">'+window.FWTablerIcons[tabler]+'</span>';
 const src=iconSrc(value);
 if(src)return '<img class="menu-icon-image" src="'+safe(src)+'" alt="">';
 const glyph=String(value||'☰').replace(/^emoji:/,'');
 return '<span class="menu-icon-glyph" aria-hidden="true">'+safe(glyph.slice(0,8))+'</span>';
}
function loadIcons(force=false){
 if(iconPromise)return iconPromise;
 if(iconReady&&!force||!token)return Promise.resolve();
 iconLoading=true;
 iconPromise=(async()=>{
  try{icons=await api('/api/settings/menu-icons');iconReady=true;renderNavigation();if(draft&&document.getElementById('menuDesigner'))renderEditor();if(document.getElementById('iconLibraryPanel'))iconLibraryPage();}
  catch(error){console.warn('Icon-Bibliothek nicht verfügbar',error);}
  finally{iconLoading=false;iconPromise=null;}
 })();
 return iconPromise;
}
function styleValue(raw,key){
 if(key==='font')return ['Inter','Roboto','Segoe UI','Arial','system-ui'].includes(raw)?raw:'Inter';
 if(key==='effect')return ['flat','gradient','gloss'].includes(raw)?raw:'gradient';
 if(['size','weight','width','gap','depth'].includes(key)){
   const n=Number(raw),ranges={size:[12,22],weight:[400,600],width:[210,380],gap:[0,16],depth:[1,2]};
   return Number.isFinite(n)?Math.min(ranges[key][1],Math.max(ranges[key][0],Math.round(n))):DEFAULT_STYLE[key];
 }
 return /^#[a-f\d]{6}$/i.test(String(raw))?raw:DEFAULT_STYLE[key];
}
function applyStyle(){
 const s=(draft||normalized()).style||DEFAULT_STYLE;
 const sidebar=document.getElementById('sidebar');if(!sidebar)return;
 const css=document.documentElement.style;
 ['background','active','text','font','size','weight','width','gap','depth','effect'].forEach(k=>{s[k]=styleValue(s[k],k);});
 css.setProperty('--menu-width',s.width+'px');
 css.setProperty('--menu-ink',s.text);
 css.setProperty('--menu-active',s.active);
 css.setProperty('--menu-size',s.size+'px');
 css.setProperty('--menu-weight',String(s.weight));
 css.setProperty('--menu-gap',s.gap+'px');
 css.setProperty('--menu-font',s.font+',system-ui,sans-serif');
 css.setProperty('--menu-background',s.background);
 sidebar.dataset.menuEffect=s.effect;
 sidebar.style.background=s.effect==='flat'?s.background:
  s.effect==='gloss'?'linear-gradient(180deg,rgba(255,255,255,.20),transparent 26%),linear-gradient(150deg,'+s.background+',#0b2234)':
  'linear-gradient(180deg,'+s.background+',#10304a 52%,'+s.background+')';
}
function renderQuickNav(){
 const host=document.getElementById('mobileQuickNav');if(!host)return;
 const entries=active().entries||{},allowed=known();
 const choice=active().quickNav||['dashboard','theke','messages'];
 const links=[...new Set(choice)].filter(id=>id==='messages'||(allowed.has(id)&&menuAllowed(allowed.get(id))&&!entries[id]?.hidden)).slice(0,3);
 host.innerHTML=links.map(id=>{
  const message=id==='messages',item=allowed.get(id),cfg=entries[id]||{};
  const label=message?'Meldungen':cfg.label||item?.label||id;
  const icon=message?'✉️':cfg.icon||item?.icon||'☰';
  return '<button type="button" data-mobile-page="'+safe(id)+'" title="'+safe(label)+'" aria-label="'+safe(label)+'">'+iconHtml(icon)+'<span class="mobile-quick-label">'+safe(label)+'</span>'+(message?'<b id="mobileMessageBadge" class="message-badge hidden">0</b>':'')+'</button>';
 }).join('');
 host.querySelectorAll('[data-mobile-page]').forEach(button=>button.onclick=()=>{
  const id=button.dataset.mobilePage;
  if(id==='messages')openMobileMessages();else{const item=allowed.get(id);if(item&&menuAllowed(item))item.action();}
 });
 const bar=document.querySelector('.mobile-nav');if(bar)bar.style.setProperty('--mobile-quick-count',String(links.length+1));
 document.querySelectorAll('[data-mobile-page]').forEach(button=>button.classList.toggle('active',button.dataset.mobilePage===window.currentPage));
}
function renderNavigation(){
 const host=document.getElementById('navContainer');if(!host)return;
 const layout=active(), allowed=known();
 function nodesHtml(nodes,depth){
  return nodes.map(node=>{
   if(typeof node==='string'){
    const item=allowed.get(node),custom=layout.entries[node]||{};
    if(!item||item.implemented===false||!menuAllowed(item)||(custom.hidden&&!adminRequired.has(node)&&node!=='dashboard'))return '';
    const icon=custom.icon||item.icon,label=custom.label||item.label;
    const inspection=node==='my-inspections',pending=Number(window.ffhPendingInspectionCount||0);
    return '<button type="button" class="nav-item '+(depth===0?'nav-root':'nav-child')+(inspection&&pending?' pending-inspection':'')+'"'+(inspection&&!pending?' style="display:none"':'')+' data-page="'+safe(node)+'" data-nav-id="'+safe(node)+'">'+iconHtml(icon)+' <span>'+safe(inspection&&pending?'Offene Geräteprüfung ('+pending+')':label)+'</span></button>';
   }
   if(depth>1)return '';
   const body=nodesHtml(node.children||[],depth+1);
   if(!body.trim())return '';
   return '<div class="nav-group" data-section="'+safe(node.title)+'"><button class="nav-group-toggle" type="button" aria-expanded="false">'+iconHtml(node.icon||'☰')+' <span class="menu-group-label">'+safe(node.title)+'</span><span class="chevron">›</span></button><div class="nav-group-items"><div class="nav-group-inner">'+body+'</div></div></div>';
  }).join('');
 }
 host.innerHTML=nodesHtml(layout.groups,0);
 host.querySelectorAll('.nav-group-toggle').forEach(button=>{
  button.onclick=()=>{
   const target=button.closest('.nav-group'),wasOpen=target.classList.contains('expanded');
   host.querySelectorAll('.nav-group').forEach(g=>{const open=g.contains(target)&&g!==target;g.classList.toggle('expanded',open);g.querySelector('.nav-group-toggle').setAttribute('aria-expanded',String(open));});
   if(!wasOpen){target.classList.add('expanded');button.setAttribute('aria-expanded','true');}
  };
 });
 host.querySelectorAll('[data-nav-id]').forEach(button=>button.onclick=()=>{closeMenu();const item=allowed.get(button.dataset.navId);if(item&&menuAllowed(item))item.action();});
 renderQuickNav();
 applyStyle();
 if(typeof setActive==='function')setActive(window.currentPage||'dashboard');
 if(!iconReady&&!iconLoading)loadIcons();
}
function ensureDraft(){if(!draft)draft=normalized();}
function start(){ensureDraft();renderEditor();loadIcons();}
function id(){return 'custom-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,6);}
function rename(path,value){let n=nodeAt(path,draft);if(isGroup(n))n.title=String(value||'').trim().slice(0,60)||'Gruppe';else if(typeof n==='string'){draft.entries[n]??={};draft.entries[n].label=String(value||'').slice(0,60);}}
function setIcon(path,value){let n=nodeAt(path,draft);if(isGroup(n))n.icon=value;else if(typeof n==='string'){draft.entries[n]??={};draft.entries[n].icon=value;}renderNavigation();}
function visibility(item,checked){draft.entries[item]??={};draft.entries[item].hidden=!checked;renderNavigation();}
function move(path,delta){const list=parentAt(path,draft),i=path.at(-1),j=i+delta;if(!list||j<0||j>=list.length)return;[list[i],list[j]]=[list[j],list[i]];renderEditor();}
function addGroup(path){const n=path?nodeAt(path,draft):null;
 if(path&&(!isGroup(n)||path.length>1))return;
 const nodes=path?n.children:draft.groups;
 nodes.push(group(id(),'Neue Gruppe',[], '☰'));renderEditor();
}
function removeGroup(path){
 const g=nodeAt(path,draft),list=parentAt(path,draft);
 if(!isGroup(g)||!list)return;
 if(g.children.length){message='Gruppe enthält Menüpunkte. Bitte diese vorher verschieben.';renderEditor();return;}
 list.splice(path.at(-1),1);renderEditor();
}
function moveTo(path,target){
 const source=nodeAt(path,draft),destination=target.length?nodeAt(target,draft):null;
 if(!source||target.length&&(!isGroup(destination)||target.length>2)
    ||isGroup(source)&&target.length>0&&(target.length>1||source.children.some(isGroup)))return;
 if(target.length&&target.slice(0,path.length).join('.')===path.join('.'))return;
 const from=parentAt(path,draft);if(!from)return;
 from.splice(path.at(-1),1);
 const dest=target.length?findRef(destination,draft):[];
 if(target.length&&!dest){from.splice(path.at(-1),0,source);return;}
 const list=target.length?nodeAt(dest,draft).children:draft.groups;
 list.push(source);renderEditor();renderNavigation();
}
function dropOn(srcPath,dstPath,inside){
 const source=nodeAt(srcPath,draft),target=nodeAt(dstPath,draft);
 if(!source||!target||srcPath.join('.')===dstPath.join('.'))return;
 if(inside){
  if(!isGroup(target)||dstPath.length>2)return;
  moveTo(srcPath,dstPath);return;
 }
 const from=parentAt(srcPath,draft);if(!from)return;
 if(srcPath.length!==dstPath.length){ // allow positioning at different nesting levels
  const targetParent=dstPath.slice(0,-1);
  const parent=targetParent.length?nodeAt(targetParent,draft):null;
  if(isGroup(source)&&targetParent.length&&(targetParent.length>1||source.children.some(isGroup)))return;
  if(targetParent.length&&!isGroup(parent))return;
 }
 if(dstPath.slice(0,srcPath.length).join('.')===srcPath.join('.')&&isGroup(source))return;
 from.splice(srcPath.at(-1),1);
 const destination=findRef(target,draft);
 if(!destination){from.splice(srcPath.at(-1),0,source);return;}
 const list=parentAt(destination,draft);
 list.splice(destination.at(-1),0,source);renderEditor();renderNavigation();
}
function iconLabel(value){
 const raw=String(value||'');
 if(raw.startsWith('tabler:'))return TABLER_LABELS.find(([key])=>raw==='tabler:'+key)?.[1]||raw;
 if(raw.startsWith('builtin:'))return STATIC_ICONS.find(([key])=>raw==='builtin:'+key)?.[1]||raw;
 if(raw.startsWith('custom:'))return icons.find(item=>'custom:'+item.id===raw)?.name||'Eigenes Icon (nicht verfügbar)';
 return ICONS.find(([glyph])=>glyph===raw)?.[1]||raw||'Standardicon';
}
function options(value){
 const selected=String(value||'');
 const choices=[...STATIC_ICONS.map(([key,name])=>['builtin:'+key,'Feuerwehr · '+name]),
  ...TABLER_LABELS.filter(([key])=>Object.prototype.hasOwnProperty.call(window.FWTablerIcons||{},key)).map(([key,name])=>['tabler:'+key,'Modern · '+name]),
  ...ICONS.map(([glyph,name])=>[glyph,'Emoji · '+name]),
  ...icons.map(item=>['custom:'+item.id,'Eigenes Icon · '+item.name])];
 const known=choices.some(([key])=>key===selected);
 return '<option value="">Standardicon</option>'+
  (!known&&selected?'<option value="'+safe(selected)+'" selected>Vorhandenes Symbol · '+safe(iconLabel(selected))+'</option>':'')+
  choices.map(([key,name])=>'<option value="'+safe(key)+'"'+(selected===key?' selected':'')+'>'+safe(name)+'</option>').join('');
}
function iconPickerChoices(value){
 const current=String(value||'');
 const list=[['','Standardicon'],...STATIC_ICONS.map(([key,name])=>['builtin:'+key,name]),
  ...TABLER_LABELS.filter(([key])=>Object.prototype.hasOwnProperty.call(window.FWTablerIcons||{},key)).map(([key,name])=>['tabler:'+key,name]),
  ...ICONS.map(([glyph,name])=>[glyph,name]),...icons.map(item=>['custom:'+item.id,item.name])];
 if(current&&!list.some(([key])=>key===current))list.unshift([current,iconLabel(current)]);
 return list;
}
function closeIconPicker(){
 const dialog=document.getElementById('designerIconDialog');
 if(!dialog)return;
 const returnTo=dialog._returnFocus;
 dialog.remove();
 if(returnTo?.isConnected)returnTo.focus();
}
function chooseIcon(current,trigger,onChoose,fallback='☰',showLibrary=true){
 if(typeof onChoose!=='function')return;
 closeIconPicker();
 const dialog=document.createElement('div');
 dialog.id='designerIconDialog';
 dialog.className='designer-icon-dialog';
 dialog.setAttribute('role','dialog');
 dialog.setAttribute('aria-modal','true');
 dialog.setAttribute('aria-labelledby','designerIconDialogTitle');
 dialog._returnFocus=trigger;
 dialog.innerHTML='<div class="designer-icon-backdrop" data-icon-close></div>'+
  '<section class="designer-icon-surface"><div class="designer-icon-heading"><h3 id="designerIconDialogTitle">Icon auswählen</h3>'+
  '<button type="button" class="btn secondary small" data-icon-close aria-label="Icon-Auswahl schließen">✕</button></div>'+
  '<input type="search" id="designerIconSearch" placeholder="Icon suchen …" aria-label="Icon suchen">'+
  '<div class="designer-icon-grid" id="designerIconChoices"></div>'+
  '<div class="designer-icon-footer"><button type="button" class="btn secondary" id="designerIconLibrary">Icon-Datenbank verwalten</button>'+
  '<button type="button" class="btn secondary" data-icon-close>Abbrechen</button></div></section>';
 document.body.appendChild(dialog);
 const search=dialog.querySelector('#designerIconSearch');
 const grid=dialog.querySelector('#designerIconChoices');
 function renderChoices(){
  const needle=search.value.trim().toLocaleLowerCase('de');
  const matching=iconPickerChoices(current).filter(([value,name])=>name.toLocaleLowerCase('de').includes(needle)||value.toLocaleLowerCase('de').includes(needle));
  grid.innerHTML=matching.map(([value,name])=>'<button type="button" class="designer-icon-choice'+(value===current?' selected':'')+
    '" data-choose-icon="'+safe(value)+'" title="'+safe(name)+'" aria-label="'+safe(name)+'" aria-pressed="'+String(value===current)+'">'+
    iconHtml(value||fallback)+'</button>').join('')||'<p class="sub">Kein Icon gefunden.</p>';
  grid.querySelectorAll('[data-choose-icon]').forEach(button=>button.onclick=()=>{
   const chosen=button.dataset.chooseIcon;
   closeIconPicker();
   onChoose(chosen);
  });
 }
 renderChoices();
 search.oninput=renderChoices;
 dialog.querySelectorAll('[data-icon-close]').forEach(button=>button.onclick=closeIconPicker);
 if(showLibrary)dialog.querySelector('#designerIconLibrary').onclick=()=>{closeIconPicker();adminSettingsPage('icons');};
 else dialog.querySelector('#designerIconLibrary').remove();
 dialog.onkeydown=event=>{
  if(event.key==='Escape'){event.preventDefault();closeIconPicker();}
  if(event.key==='Tab'){
   const focusable=[...dialog.querySelectorAll('button:not([disabled]),input:not([disabled])')].filter(el=>el.getClientRects().length);
   if(!focusable.length)return;
   const first=focusable[0],last=focusable.at(-1);
   if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
   else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
  }
 };
 search.focus();
}
function openIconPicker(path,trigger){
 const node=nodeAt(path,draft),isFolder=isGroup(node),page=isFolder?null:known().get(node);
 if(!node)return;
 const current=isFolder?(node.icon||'☰'):(draft.entries[node]?.icon||page?.icon||'☰');
 chooseIcon(current,trigger,chosen=>{
  setIcon(path,chosen);renderEditor();
  document.querySelector('[data-icon-picker="'+path.join('.')+'"]')?.focus();
 },page?.icon||'☰');
}
function rowHtml(node,path){
 const p=path.join('.'),depth=path.length-1,g=isGroup(node),item=g?null:known().get(node),cfg=g?null:draft.entries[node]||{};
 const label=g?node.title:(cfg.label||item?.label||node),icon=g?node.icon:(cfg.icon||item?.icon||'☰');
 const settings='<button type="button" class="designer-icon-trigger" data-icon-picker="'+p+'" title="Icon wählen: '+safe(iconLabel(icon))+'" aria-label="Icon für '+safe(label)+' wählen">'+iconHtml(icon)+'</button>';
 const controls='<button type="button" class="btn small secondary" data-up="'+p+'" title="Nach oben">↑</button>'+
  '<button type="button" class="btn small secondary" data-down="'+p+'" title="Nach unten">↓</button>';
 return '<div class="designer-row'+(g?' designer-group':'')+'" draggable="true" data-path="'+p+'" style="--level:'+depth+'">'+
  '<span class="designer-grip" title="Am PC ziehen">⠿</span>'+ 
  '<div class="designer-entry"><input aria-label="Beschriftung" maxlength="60" value="'+safe(label)+'" data-title="'+p+'"><small>'+safe(g?'Gruppe · Ebene '+(depth+1):'Seite · '+node)+'</small></div>'+
  settings+controls+
  (g?'<button type="button" class="btn small secondary" data-add="'+p+'">+ Untergruppe</button><button type="button" class="btn small danger" data-delete="'+p+'">✕</button>':
  '<label class="designer-visible"><input type="checkbox" data-visible="'+safe(node)+'" '+((adminRequired.has(node)||!cfg.hidden)?'checked':'')+(adminRequired.has(node)?' disabled':'')+'> Sichtbar</label>')+
  '<select class="designer-move" data-move="'+p+'" aria-label="In Gruppe verschieben"><option value="">In Gruppe…</option><option value="root">Hauptmenü (ohne Untergruppe)</option>'+
  draft.groups.flatMap((top,i)=>[[''+i,top.title],...(top.children||[]).flatMap((sub,j)=>isGroup(sub)?[[''+i+'.'+j,'↳ '+sub.title]]:[])]).filter(([dest])=>dest!==p).map(([dest,name])=>'<option value="'+dest+'">'+safe(name)+'</option>').join('')+'</select></div>'+
  (g?'<div class="designer-children">'+node.children.map((child,i)=>rowHtml(child,[...path,i])).join('')+'</div>':'');
}
function renderEditor(){
 const host=document.getElementById('settingsContent');if(!host)return;
 applyStyle();
 host.innerHTML='<div class="panel" id="menuDesigner"><h2>☰ Menü-Designer</h2>'+
 '<p class="sub">Gruppen und Seiten ziehen oder mit ↑ ↓ und „In Gruppe“ sortieren. Änderungen werden erst mit „Speichern“ dauerhaft übernommen. Die Berechtigungen bleiben unverändert.</p>'+
 '<div class="quick"><button type="button" class="btn primary" id="designerAdd">+ Hauptgruppe</button><button type="button" class="btn secondary" id="designerSave">Menü speichern</button><button type="button" class="btn secondary" id="designerReset">Änderungen verwerfen</button><button type="button" class="btn secondary" id="designerOpenIcons">Icon-Datenbank</button></div>'+
 '<div id="designerNotice" aria-live="polite"></div>'+
 '<div class="designer-layout"><section class="designer-list" id="designerList">'+draft.groups.map((g,i)=>rowHtml(g,[i])).join('')+'</section>'+
 '<section class="designer-options"><h3>Darstellung</h3>'+
 ['background|Menühintergrund','active|Aktiver Menüpunkt','text|Schriftfarbe'].map(s=>{const [key,title]=s.split('|');return '<label class="field">'+title+' <input type="color" data-style="'+key+'" value="'+safe(styleValue(draft.style[key],key))+'"></label>';}).join('')+
 '<label class="field">Schriftart <select data-style="font">'+['Inter','Roboto','Segoe UI','Arial','system-ui'].map(x=>'<option'+(draft.style.font===x?' selected':'')+'>'+x+'</option>').join('')+'</select></label>'+
 ['size|Schriftgröße|12|22|px','weight|Schriftstärke|400|600|','width|Menübreite|210|380|px','gap|Abstand|0|16|px'].map(value=>{const [key,title,min,max,unit]=value.split('|');return '<label class="field">'+title+' <strong data-value="'+key+'">'+safe(draft.style[key])+unit+'</strong><input type="range" data-style="'+key+'" min="'+min+'" max="'+max+'" step="'+(key==='weight'?100:1)+'" value="'+safe(draft.style[key])+'"></label>';}).join('')+
 '<label class="field">Effekt <select data-style="effect"><option value="flat">Einfarbig</option><option value="gradient">Farbverlauf</option><option value="gloss">Farbverlauf + Reflexion</option></select></label>'+
 '<h3>Schnellzugriff Handy & Tablet</h3><p class="sub">Unten stehen maximal drei frei wählbare Seiten und der feste Menüknopf (insgesamt vier Symbole). Nicht berechtigte Seiten werden nicht angezeigt.</p>'+ 
 '<div class="designer-quick-settings">'+[0,1,2].map(index=>'<label>Platz '+(index+1)+'<select data-quick-position="'+index+'"><option value="">Kein Eintrag</option>'+[['messages','✉️ Meldungen'],...menuDefinitions.filter(i=>i.implemented!==false).map(i=>[i.id,i.label])].map(([id,label])=>'<option value="'+safe(id)+'"'+(draft.quickNav?.[index]===id?' selected':'')+'>'+safe(label)+'</option>').join('')+'</select></label>').join('')+'</div>'+ 
 '<p class="sub">Eigene Icons und die gemeinsame Bibliothek verwaltest du unter Einstellungen → Icon-Datenbank.</p>'+
 '</section></div></div>';
 document.querySelector('[data-style="effect"]').value=draft.style.effect;
 if(message){document.getElementById('designerNotice').innerHTML='<p class="message error">'+safe(message)+'</p>';message='';}
 bindEditor();
}
function parsePath(str){return /^\d+(?:\.\d+)*$/.test(str)?str.split('.').map(Number):[];}
function bindEditor(){
 const root=document.getElementById('menuDesigner');
 root.querySelector('#designerAdd').onclick=()=>addGroup(null);
 root.querySelector('#designerSave').onclick=save;
 root.querySelector('#designerReset').onclick=()=>{draft=normalized();renderEditor();renderNavigation();};
 root.querySelector('#designerOpenIcons').onclick=()=>adminSettingsPage('icons');
 root.querySelectorAll('[data-title]').forEach(input=>input.onchange=()=>{rename(parsePath(input.dataset.title),input.value);renderNavigation();});
 root.querySelectorAll('[data-icon-picker]').forEach(button=>button.onclick=()=>openIconPicker(parsePath(button.dataset.iconPicker),button));
 root.querySelectorAll('[data-visible]').forEach(input=>input.onchange=()=>visibility(input.dataset.visible,input.checked));
 root.querySelectorAll('[data-up]').forEach(button=>button.onclick=()=>move(parsePath(button.dataset.up),-1));
 root.querySelectorAll('[data-down]').forEach(button=>button.onclick=()=>move(parsePath(button.dataset.down),1));
 root.querySelectorAll('[data-add]').forEach(button=>button.onclick=()=>addGroup(parsePath(button.dataset.add)));
 root.querySelectorAll('[data-delete]').forEach(button=>button.onclick=()=>removeGroup(parsePath(button.dataset.delete)));
 root.querySelectorAll('[data-move]').forEach(input=>input.onchange=()=>{if(input.value)moveTo(parsePath(input.dataset.move),input.value==='root'?[]:parsePath(input.value));});
 root.querySelectorAll('[data-quick-position]').forEach(input=>input.onchange=()=>{const picks=[...root.querySelectorAll('[data-quick-position]')].map(s=>s.value).filter(Boolean);if(new Set(picks).size!==picks.length){message='Jede Schnellzugriffsseite darf nur einmal ausgewählt werden.';renderEditor();return;}draft.quickNav=picks;renderQuickNav();});
 root.querySelectorAll('[data-style]').forEach(input=>input.oninput=()=>{
  const key=input.dataset.style;draft.style[key]=styleValue(input.value,key);
  const value=root.querySelector('[data-value="'+key+'"]');if(value)value.textContent=draft.style[key]+(key==='size'||key==='width'||key==='gap'?'px':'');
  applyStyle();
 });
 root.querySelectorAll('.designer-row').forEach(row=>{
  row.ondragstart=event=>{if(event.target.closest('input,select,button')){event.preventDefault();return;}dragPath=parsePath(row.dataset.path);event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',row.dataset.path);};
  row.ondragover=event=>{event.preventDefault();event.stopPropagation();row.classList.add('designer-drop');};
  row.ondragleave=()=>row.classList.remove('designer-drop');
  row.ondrop=event=>{event.preventDefault();event.stopPropagation();row.classList.remove('designer-drop');if(dragPath)dropOn(dragPath,parsePath(row.dataset.path),isGroup(nodeAt(parsePath(row.dataset.path),draft))&&event.offsetX>45);dragPath=null;};
 });
}
function iconLibraryPage(){
 const host=document.getElementById('settingsContent');if(!host)return;
 if(!iconReady){
  host.innerHTML='<div class="panel" id="iconLibraryPanel"><h2>Icon-Datenbank</h2><p>Icons werden geladen …</p></div>';
  if(!iconLoading)loadIcons(true).then(()=>{if(document.getElementById('iconLibraryPanel')&&!document.getElementById('iconLibraryUpload'))iconLibraryPage();});
  return;
 }
 const predefined=[...TABLER_LABELS.filter(([key])=>Object.prototype.hasOwnProperty.call(window.FWTablerIcons||{},key)).map(([key,name])=>({value:'tabler:'+key,name,group:'Moderne Icons'})),
  ...STATIC_ICONS.map(([key,name])=>({value:'builtin:'+key,name,group:'Feuerwehr-Icons'}))];
 const gallery=(items)=>items.map(item=>'<div class="icon-library-item" data-icon-search="'+safe(item.name.toLocaleLowerCase('de'))+'">'+iconHtml(item.value)+'<span>'+safe(item.name)+'</span><small>'+safe(item.group)+'</small></div>').join('');
 host.innerHTML='<div class="panel" id="iconLibraryPanel"><h2>🖼 Icon-Datenbank</h2>'+
  '<p class="sub">Gemeinsame Auswahl für Menüpunkte und Qualifikationsbausteine. Die Standard-Icons sind offline verfügbar. Das FW-Cockpit-Logo bleibt unverändert.</p>'+
  '<div class="quick"><button class="btn secondary" type="button" id="iconLibraryBack">Zum Menü-Designer</button>'+
  '<input type="search" id="iconLibrarySearch" placeholder="Icon nach Name suchen" aria-label="Icons durchsuchen"></div>'+
  '<h3>Moderne Icons (Tabler)</h3><div class="icon-library-grid">'+gallery(predefined.filter(item=>item.group==='Moderne Icons'))+'</div>'+
  '<h3>Feuerwehr-Icons</h3><div class="icon-library-grid">'+gallery(predefined.filter(item=>item.group==='Feuerwehr-Icons'))+'</div>'+
  '<h3>Eigene Icons ('+icons.length+'/50)</h3><div class="icon-library-grid">'+icons.map(item=>
   '<div class="icon-library-item" data-icon-search="'+safe(item.name.toLocaleLowerCase('de'))+'">'+iconHtml('custom:'+item.id)+'<span>'+safe(item.name)+'</span>'+
   '<button class="btn small danger" type="button" data-library-delete="'+item.id+'" aria-label="'+safe(item.name)+' löschen">Löschen</button></div>').join('')+'</div>'+
  '<div class="icon-library-upload"><label class="field">Bezeichnung <input id="iconLibraryName" maxlength="60" placeholder="z. B. Atemschutz"></label>'+
  '<label class="field">Eigenes SVG/PNG (max. 160 KB) <input id="iconLibraryFile" type="file" accept=".svg,.png,image/svg+xml,image/png"></label>'+
  '<button class="btn primary" type="button" id="iconLibraryUpload">Icon hochladen</button></div>'+
  '<div id="iconLibraryNotice" aria-live="polite"></div></div>';
 host.querySelector('#iconLibraryBack').onclick=()=>adminSettingsPage('menu');
 host.querySelector('#iconLibrarySearch').oninput=event=>{
  const needle=event.target.value.trim().toLocaleLowerCase('de');
  host.querySelectorAll('[data-icon-search]').forEach(node=>{node.hidden=!node.dataset.iconSearch.includes(needle);});
 };
 host.querySelector('#iconLibraryUpload').onclick=async()=>{
  const file=host.querySelector('#iconLibraryFile').files?.[0],name=host.querySelector('#iconLibraryName').value.trim(),notice=host.querySelector('#iconLibraryNotice');
  if(!file||!name){notice.textContent='Bitte Name und Icon-Datei auswählen.';return;}
  if(file.size>160000){notice.textContent='Datei ist größer als 160 KB.';return;}
  const fd=new FormData();fd.append('file',file);fd.append('name',name);
  try{
   const response=await fetch('/api/settings/menu-icons',{method:'POST',headers:{Authorization:'Bearer '+token},body:fd});
   if(!response.ok){let err={};try{err=await response.json();}catch(_){}throw Error(err.detail||err.message||'Upload fehlgeschlagen ('+response.status+')');}
   iconReady=false;await loadIcons(true);iconLibraryPage();
   document.getElementById('iconLibraryNotice').textContent='✓ Icon gespeichert und in beiden Auswahllisten verfügbar.';
  }catch(error){notice.textContent=error.message;}
 };
 host.querySelectorAll('[data-library-delete]').forEach(button=>button.onclick=async()=>{
  if(!confirm('Dieses Icon löschen? Bereits verwendete Menüeinträge benötigen anschließend ein anderes Symbol.'))return;
  try{await api('/api/settings/menu-icons/'+button.dataset.libraryDelete,{method:'DELETE'});iconReady=false;await loadIcons(true);iconLibraryPage();}
  catch(error){document.getElementById('iconLibraryNotice').textContent=error.message;}
 });
}
async function upload(){
 const file=document.getElementById('designerIconFile')?.files?.[0],name=document.getElementById('designerIconName')?.value?.trim();
 if(!file||!name){message='Bitte Icon und Namen auswählen.';renderEditor();return;}
 if(file.size>160000){message='Icon ist größer als 160 KB.';renderEditor();return;}
 const fd=new FormData();fd.append('file',file);fd.append('name',name);
 try{
  const response=await fetch('/api/settings/menu-icons',{method:'POST',headers:{Authorization:'Bearer '+token},body:fd});
  if(!response.ok){let error;try{error=await response.json();}catch(e){}throw Error(error?.detail||error?.message||'Upload fehlgeschlagen ('+response.status+')');}
  iconReady=false;await loadIcons(true);renderEditor();
 }catch(e){message=e.message;renderEditor();}
}
async function save(){
 const button=document.getElementById('designerSave');if(button)button.disabled=true;
 try {
  const payload=JSON.parse(JSON.stringify(draft));
  appSettings=await api('/api/settings/menu-layout',{method:'PUT',body:JSON.stringify(payload)});
  draft=null;
  renderNavigation();
  if(typeof refreshInspectionNotice==='function')refreshInspectionNotice();
  draft=normalized();
  renderEditor();
  const msg=document.getElementById('designerNotice');
  if(msg)msg.innerHTML='<p class="message success">✓ Menükonfiguration dauerhaft gespeichert.</p>';
 }catch(e){message=e.message;renderEditor();}
}
window.MenuDesigner={renderNavigation,renderQuickNav,applyStyle,start,loadIcons,iconHtml,iconOptions:options,iconLabel,iconLibraryPage,chooseIcon,activatePage(page){const target=document.querySelector('#navContainer [data-page="'+String(page).replace(/[^a-z0-9-]/gi,'')+'"]');if(!target)return;document.querySelectorAll('#navContainer .nav-item').forEach(n=>n.classList.toggle('active',n===target));document.querySelectorAll('#navContainer .nav-group').forEach(g=>{const open=g.contains(target);g.classList.toggle('expanded',open);g.querySelector('.nav-group-toggle')?.setAttribute('aria-expanded',String(open));});}};
})();