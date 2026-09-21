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
const DEFAULT_STYLE = {background:'#071827',active:'#1479e9',text:'#dce7ee',font:'Inter',size:15,weight:500,width:250,gap:3,depth:2,effect:'gradient'};
let draft=null, icons=[], iconReady=false, iconLoading=false, dragPath=null, message='';
const safe = value => esc(value);
const adminRequired = new Set(['admin','admin-members','admin-users','admin-settings']);
const known = () => new Map(menuDefinitions.map(item => [item.id,item]));
function group(id,title,children,icon){return {id,title,icon:icon||'☰',children:children||[]};}
function originalLayout(){
 const groups=[];
 const byName=new Map();
 const order=appSettings?.menuOrder?.length?appSettings.menuOrder:menuDefinitions.map(x=>x.id);
 const rank=new Map(order.map((x,i)=>[x,i]));
 [...menuDefinitions].sort((a,b)=>(rank.get(a.id)??999)-(rank.get(b.id)??999)).forEach(item=>{
   if(!byName.has(item.section)){const node=group('section-'+groups.length,item.section,[],item.section==='Gerätewart'?'🚒':'☰');groups.push(node);byName.set(item.section,node);}
   byName.get(item.section).children.push(item.id);
 });
 const entries={};(appSettings?.hiddenMenuItems||[]).forEach(item=>{entries[item]={hidden:true};});
 return {version:2,groups,entries,style:{...DEFAULT_STYLE,background:appSettings?.navColor||'#071827'}};
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
    if(typeof node==='string'){if(!byId.has(node)||seen.has(node)||depth===0)return [];seen.add(node);return [node];}
    if(!node||typeof node!=='object'||Array.isArray(node)||depth>1)return [];
    const id=String(node.id||'').slice(0,48);
    if(!/^[a-zA-Z0-9_-]{1,48}$/.test(id)||groupIds.has(id))return [];
    groupIds.add(id);
    return [group(id,String(node.title||'Gruppe').slice(0,60),tidy(node.children,depth+1),String(node.icon||'☰').slice(0,45))];
  });
 }
 value.groups=tidy(value.groups,0);
 const missing=menuDefinitions.filter(x=>!seen.has(x.id));
 missing.forEach(item=>{
   let target=value.groups.find(g=>g.title===item.section);
   if(!target){target=group('new-'+item.id,item.section,[]);value.groups.push(target);}
   target.children.push(item.id);
 });
 value.entries=Object.fromEntries(Object.entries(value.entries||{}).filter(([id,v])=>byId.has(id)&&v&&typeof v==='object')
  .map(([id,v])=>[id,{label:String(v.label||'').slice(0,60),icon:String(v.icon||'').slice(0,45),hidden:!!v.hidden}]));
 value.style={...DEFAULT_STYLE,...(value.style||{})};
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
 const src=iconSrc(value);
 if(src)return '<img class="menu-icon-image" src="'+safe(src)+'" alt="">';
 const glyph=String(value||'☰').replace(/^emoji:/,'');
 return '<span class="menu-icon-glyph" aria-hidden="true">'+safe(glyph.slice(0,8))+'</span>';
}
async function loadIcons(force=false){
 if(iconLoading||iconReady&&!force||!token)return;
 iconLoading=true;
 try {icons=await api('/api/settings/menu-icons');iconReady=true;renderNavigation();if(draft&&document.getElementById('menuDesigner'))renderEditor();}
 catch(e){console.warn('Icon-Bibliothek nicht verfügbar',e);}
 finally {iconLoading=false;}
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
 const s=active().style||DEFAULT_STYLE;
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
function renderNavigation(){
 const host=document.getElementById('navContainer');if(!host)return;
 const layout=active(), allowed=known();
 function nodesHtml(nodes,depth){
  return nodes.map(node=>{
   if(typeof node==='string'){
    const item=allowed.get(node),custom=layout.entries[node]||{};
    if(!item||!menuAllowed(item)||(custom.hidden&&!adminRequired.has(node)))return '';
    const icon=custom.icon||item.icon,label=custom.label||item.label;
    const inspection=node==='my-inspections',pending=Number(window.ffhPendingInspectionCount||0);
    return '<button type="button" class="nav-item nav-child'+(inspection&&pending?' pending-inspection':'')+'"'+(inspection&&!pending?' style="display:none"':'')+' data-page="'+safe(node)+'" data-nav-id="'+safe(node)+'">'+iconHtml(icon)+' <span>'+safe(inspection&&pending?'Offene Geräteprüfung ('+pending+')':label)+'</span></button>';
   }
   if(depth>2)return '';
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
 host.querySelectorAll('[data-nav-id]').forEach(button=>button.onclick=()=>allowed.get(button.dataset.navId)?.action());
 applyStyle();
 setActive(window.currentPage||'dashboard');
 if(!iconReady)loadIcons();
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
 if(!source||target.length&&!isGroup(destination)||target.length>2||isGroup(source)&&target.length>1||target.length===0&&!isGroup(source))return;
 if(target.length&&target.slice(0,path.length).join('.')===path.join('.'))return;
 if(isGroup(source)&&target.length&&source.children.some(isGroup))return;
 const own=parentAt(path,draft);if(!own)return;
 own.splice(path.at(-1),1);
 const dest=target.length?findRef(destination,draft):[];
 if(target.length&&!dest)return;
 (target.length?nodeAt(dest,draft).children:draft.groups).push(source);
 renderEditor();
}
function dropOn(srcPath,dstPath,inside){
 const source=nodeAt(srcPath,draft),target=nodeAt(dstPath,draft);
 if(!source||!target||srcPath.join('.')===dstPath.join('.'))return;
 const srcParent=parentAt(srcPath,draft),dstParent=parentAt(dstPath,draft);
 if(!srcParent||!dstParent)return;
 if(isGroup(source)&&dstPath.length===1&&!inside){} // root-group ordering is allowed
 else if(!inside&&dstPath.length===1&&!isGroup(source))return;
 if(inside&&(!isGroup(target)||dstPath.length>1||isGroup(source)&&source.children.some(isGroup)))return;
 if(inside&&dstPath.slice(0,srcPath.length).join('.')===srcPath.join('.'))return;
 srcParent.splice(srcPath.at(-1),1);
 const updatedPath=findRef(target,draft);
 if(!updatedPath){srcParent.splice(Math.min(srcPath.at(-1),srcParent.length),0,source);return;}
 const targetParent=parentAt(updatedPath,draft);
 const newDepth=inside?updatedPath.length+1:updatedPath.length;
 if(isGroup(source)&&(newDepth>2||newDepth===2&&source.children.some(isGroup))||!isGroup(source)&&newDepth===1){srcParent.push(source);return;}
 if(inside)target.children.push(source);
 else targetParent.splice(updatedPath.at(-1),0,source);
 renderEditor();
}
function options(value){
 return '<option value="">Standardicon</option>'+STATIC_ICONS.map(([key,name])=>'<option value="builtin:'+key+'"'+(value==='builtin:'+key?' selected':'')+'>'+safe('▣ '+name)+'</option>').join('')+ICONS.map(([glyph,name])=>'<option value="'+safe(glyph)+'"'+(value===glyph?' selected':'')+'>'+safe(glyph+' '+name)+'</option>').join('')+
 icons.map(x=>'<option value="custom:'+x.id+'"'+(value==='custom:'+x.id?' selected':'')+'>'+safe('🖼 '+x.name)+'</option>').join('');
}
function rowHtml(node,path){
 const p=path.join('.'),depth=path.length-1,g=isGroup(node),item=g?null:known().get(node),cfg=g?null:draft.entries[node]||{};
 const label=g?node.title:(cfg.label||item?.label||node),icon=g?node.icon:(cfg.icon||item?.icon||'☰');
 const settings='<select aria-label="Symbol" data-icon="'+p+'">'+options(icon)+'</select>';
 const controls='<button type="button" class="btn small secondary" data-up="'+p+'" title="Nach oben">↑</button>'+
  '<button type="button" class="btn small secondary" data-down="'+p+'" title="Nach unten">↓</button>';
 return '<div class="designer-row'+(g?' designer-group':'')+'" draggable="true" data-path="'+p+'" style="--level:'+depth+'">'+
  '<span class="designer-grip" title="Am PC ziehen">⠿</span>'+iconHtml(icon)+
  '<div class="designer-entry"><input aria-label="Beschriftung" maxlength="60" value="'+safe(label)+'" data-title="'+p+'"><small>'+safe(g?'Gruppe · Ebene '+(depth+1):'Seite · '+node)+'</small></div>'+
  settings+controls+
  (g?'<button type="button" class="btn small secondary" data-add="'+p+'">+ Untergruppe</button><button type="button" class="btn small danger" data-delete="'+p+'">✕</button>':
  '<label class="designer-visible"><input type="checkbox" data-visible="'+safe(node)+'" '+((adminRequired.has(node)||!cfg.hidden)?'checked':'')+(adminRequired.has(node)?' disabled':'')+'> Sichtbar</label>')+
  '<select class="designer-move" data-move="'+p+'" aria-label="In Gruppe verschieben"><option value="">In Gruppe…</option>'+
  draft.groups.flatMap((top,i)=>[[''+i,top.title],...(top.children||[]).flatMap((sub,j)=>isGroup(sub)?[[''+i+'.'+j,'↳ '+sub.title]]:[])]).filter(([dest])=>dest!==p).map(([dest,name])=>'<option value="'+dest+'">'+safe(name)+'</option>').join('')+'</select></div>'+
  (g?'<div class="designer-children">'+node.children.map((child,i)=>rowHtml(child,[...path,i])).join('')+'</div>':'');
}
function renderEditor(){
 const host=document.getElementById('settingsContent');if(!host)return;
 applyStyle();
 host.innerHTML='<div class="panel" id="menuDesigner"><h2>☰ Menü-Designer</h2>'+
 '<p class="sub">Gruppen und Seiten ziehen oder mit ↑ ↓ und „In Gruppe“ sortieren. Änderungen werden erst mit „Speichern“ dauerhaft übernommen. Die Berechtigungen bleiben unverändert.</p>'+
 '<div class="quick"><button type="button" class="btn primary" id="designerAdd">+ Hauptgruppe</button><button type="button" class="btn secondary" id="designerSave">Menü speichern</button><button type="button" class="btn secondary" id="designerReset">Änderungen verwerfen</button></div>'+
 '<div id="designerNotice" aria-live="polite"></div>'+
 '<div class="designer-layout"><section class="designer-list" id="designerList">'+draft.groups.map((g,i)=>rowHtml(g,[i])).join('')+'</section>'+
 '<section class="designer-options"><h3>Darstellung</h3>'+
 ['background|Menühintergrund','active|Aktiver Menüpunkt','text|Schriftfarbe'].map(s=>{const [key,title]=s.split('|');return '<label class="field">'+title+' <input type="color" data-style="'+key+'" value="'+safe(styleValue(draft.style[key],key))+'"></label>';}).join('')+
 '<label class="field">Schriftart <select data-style="font">'+['Inter','Roboto','Segoe UI','Arial','system-ui'].map(x=>'<option'+(draft.style.font===x?' selected':'')+'>'+x+'</option>').join('')+'</select></label>'+
 ['size|Schriftgröße|12|22|px','weight|Schriftstärke|400|600|','width|Menübreite|210|380|px','gap|Abstand|0|16|px'].map(value=>{const [key,title,min,max,unit]=value.split('|');return '<label class="field">'+title+' <strong data-value="'+key+'">'+safe(draft.style[key])+unit+'</strong><input type="range" data-style="'+key+'" min="'+min+'" max="'+max+'" step="'+(key==='weight'?100:1)+'" value="'+safe(draft.style[key])+'"></label>';}).join('')+
 '<label class="field">Effekt <select data-style="effect"><option value="flat">Einfarbig</option><option value="gradient">Farbverlauf</option><option value="gloss">Farbverlauf + Reflexion</option></select></label>'+
 '<h3>Eigene Icons</h3><div class="field"><label>Bezeichnung <input id="designerIconName" maxlength="60" placeholder="z. B. Feuerwehrhelm"></label></div>'+
 '<div class="field"><label>Icon-Datei (PNG oder SVG, max. 160 KB) <input id="designerIconFile" type="file" accept=".png,.svg,image/png,image/svg+xml"></label></div>'+
 '<button type="button" class="btn primary small" id="designerUpload">Icon hochladen</button><div id="designerIconList">'+
 icons.map(x=>'<div class="designer-custom-icon">'+iconHtml('custom:'+x.id)+' <span>'+safe(x.name)+'</span><button class="btn small danger" type="button" data-delete-icon="'+x.id+'" title="Icon löschen">✕</button></div>').join('')+'</div>'+
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
 root.querySelector('#designerUpload').onclick=upload;
 root.querySelectorAll('[data-title]').forEach(input=>input.onchange=()=>{rename(parsePath(input.dataset.title),input.value);renderNavigation();});
 root.querySelectorAll('[data-icon]').forEach(input=>input.onchange=()=>{setIcon(parsePath(input.dataset.icon),input.value);renderEditor();});
 root.querySelectorAll('[data-visible]').forEach(input=>input.onchange=()=>visibility(input.dataset.visible,input.checked));
 root.querySelectorAll('[data-up]').forEach(button=>button.onclick=()=>move(parsePath(button.dataset.up),-1));
 root.querySelectorAll('[data-down]').forEach(button=>button.onclick=()=>move(parsePath(button.dataset.down),1));
 root.querySelectorAll('[data-add]').forEach(button=>button.onclick=()=>addGroup(parsePath(button.dataset.add)));
 root.querySelectorAll('[data-delete]').forEach(button=>button.onclick=()=>removeGroup(parsePath(button.dataset.delete)));
 root.querySelectorAll('[data-move]').forEach(input=>input.onchange=()=>{if(input.value)moveTo(parsePath(input.dataset.move),parsePath(input.value));});
 root.querySelectorAll('[data-style]').forEach(input=>input.oninput=()=>{
  const key=input.dataset.style;draft.style[key]=styleValue(input.value,key);
  const value=root.querySelector('[data-value="'+key+'"]');if(value)value.textContent=draft.style[key]+(key==='size'||key==='width'||key==='gap'?'px':'');
  applyStyle();
 });
 root.querySelectorAll('[data-delete-icon]').forEach(button=>button.onclick=async()=>{
   if(!confirm('Dieses Icon löschen? Bereits verwendete Menüeinträge zeigen dann ihr Standardsymbol.'))return;
   try {await api('/api/settings/menu-icons/'+button.dataset.deleteIcon,{method:'DELETE'});iconReady=false;await loadIcons(true);renderEditor();}catch(e){alert(e.message);}
 });
 root.querySelectorAll('.designer-row').forEach(row=>{
  row.ondragstart=event=>{if(event.target.closest('input,select,button')){event.preventDefault();return;}dragPath=parsePath(row.dataset.path);event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',row.dataset.path);};
  row.ondragover=event=>{event.preventDefault();event.stopPropagation();row.classList.add('designer-drop');};
  row.ondragleave=()=>row.classList.remove('designer-drop');
  row.ondrop=event=>{event.preventDefault();event.stopPropagation();row.classList.remove('designer-drop');if(dragPath)dropOn(dragPath,parsePath(row.dataset.path),isGroup(nodeAt(parsePath(row.dataset.path),draft))&&event.offsetX>45);dragPath=null;};
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
  const payload={appName:appSettings?.appName||'FFH Verwaltung',primaryColor:appSettings?.primaryColor||'#c51f2d',
   navColor:appSettings?.navColor||'#071827',accentColor:appSettings?.accentColor||'#1479e9',
   menuOrder:appSettings?.menuOrder||[],hiddenMenuItems:[],
   menuLayout:JSON.stringify(draft)};
  appSettings=await api('/api/settings',{method:'PUT',body:JSON.stringify(payload)});
  draft=null;
  renderNavigation();
  if(typeof refreshInspectionNotice==='function')refreshInspectionNotice();
  draft=normalized();
  renderEditor();
  const msg=document.getElementById('designerNotice');
  if(msg)msg.innerHTML='<p class="message success">✓ Menükonfiguration dauerhaft gespeichert.</p>';
 }catch(e){message=e.message;renderEditor();}
}
window.MenuDesigner={renderNavigation,applyStyle,start,loadIcons};
})();