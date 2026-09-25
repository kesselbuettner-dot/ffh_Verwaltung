/* FW-Cockpit: complete page templates built from shared FWComponents.
 * Each template accepts its own data/actions, but all structure and styling
 * lives here and in page-templates.css. No direct API calls or privileges.
 */
(function(root){
 'use strict';
 const TYPES=Object.freeze([
  {id:'overview',title:'Übersichtsseite',description:'Kennzahlen, Schnellaktionen und Statuskacheln'},
  {id:'management',title:'Verwaltungsseite',description:'Suche, Filter, sortierbare Liste und Zeilenaktionen'},
  {id:'detail',title:'Detailseite',description:'Objektkopf, Daten, Kacheln und Historie'},
  {id:'form',title:'Formularseite',description:'Geordnete Eingaben, Prüfung und Abschlussaktionen'},
  {id:'tasks',title:'Prüf- und Aufgabenliste',description:'Fristen, Verantwortliche, Status und Prüfergebnis'},
  {id:'settings',title:'Einstellungsseite',description:'Einstellungsgruppen, Seitennavigation und Speichern'}
 ]);
 const KEYS=new Set(TYPES.map(t=>t.id));
 const ui=()=>{if(!root.FWComponents)throw Error('FWComponents nicht geladen');return root.FWComponents;};
 function elem(tag,className='',text){
  const e=document.createElement(tag);if(className)e.className=className;
  if(text!==undefined&&text!==null)e.textContent=String(text);return e;
 }
 function put(parent,...values){values.flat().filter(v=>v!==null&&v!==undefined).forEach(v=>parent.appendChild(typeof v==='string'?document.createTextNode(v):v));return parent;}
 function labeledValue(label,value){return put(elem('div','ds-detail-field'),elem('dt','ds-detail-label',label),elem('dd','ds-detail-value',value??'–'));}
 function actions(items){return items.filter(Boolean).map(item=>item instanceof Node?item:ui().button(item));}
 function section(title,content,extraClass=''){
  return ui().card({title,content,extraClass});
 }
 function overview(opts={}){
  const metrics=elem('div','ds-metric-grid');
  (opts.metrics||[]).forEach(item=>{
   const tile=elem('article','ds-metric');
   put(tile,elem('div','ds-metric-label',item.label),elem('strong','ds-metric-value',item.value??'–'));
   if(item.note)put(tile,elem('small','ds-metric-note',item.note));
   metrics.appendChild(tile);
  });
  const panels=(opts.panels||[]).map(p=>section(p.title,p.content));
  const children=[metrics,...panels];
  if(opts.emptyMessage&&!opts.metrics?.length&&!panels.length)children.push(ui().empty({message:opts.emptyMessage}));
  return ui().page({title:opts.title||'Übersicht',description:opts.description||'',actions:actions(opts.actions||[]),children,className:'ds-template-overview'});
 }
 function management(opts={}){
  const toolbar=elem('div','ds-template-toolbar');
  if(opts.search!==false){
   put(toolbar,ui().field({label:opts.searchLabel||'Suche',type:'search',value:opts.searchValue||'',placeholder:opts.searchPlaceholder||'Eintrag suchen',onInput:opts.onSearch}));
  }
  (opts.filters||[]).forEach(f=>put(toolbar,ui().field(f)));
  if(opts.onReset)put(toolbar,ui().button({label:'Filter zurücksetzen',variant:'secondary',onClick:opts.onReset}));
  const table=ui().table({columns:opts.columns||[],rows:opts.rows||[],emptyMessage:opts.emptyMessage||'Keine Einträge',onRowClick:opts.onRowClick});
  return ui().page({title:opts.title||'Verwaltung',description:opts.description||'',actions:actions(opts.actions||[]),
   children:[section(opts.listTitle||'Einträge',put(elem('div','ds-template-list'),toolbar,table))],className:'ds-template-management'});
 }
 function detail(opts={}){
  const header=elem('div','ds-detail-header');
  if(opts.status)put(header,ui().badge(typeof opts.status==='string'?{label:opts.status}:opts.status));
  const grid=elem('dl','ds-detail-grid');
  (opts.fields||[]).forEach(item=>put(grid,labeledValue(item.label,item.value)));
  const children=[];
  if(opts.status)children.push(header);
  children.push(section(opts.dataTitle||'Stammdaten',grid));
  if(opts.tiles?.length){const tiles=elem('div','ds-detail-tiles');opts.tiles.forEach(t=>put(tiles,section(t.title,t.content)));children.push(tiles);}
  if(opts.history)children.push(section(opts.historyTitle||'Historie',opts.history));
  return ui().page({title:opts.title||'Detail',description:opts.description||'',actions:actions(opts.actions||[]),
   children,className:'ds-template-detail'});
 }
 function form(opts={}){
  const host=elem('form','ds-template-form');
  const groups=(opts.groups||[]).length?opts.groups:[{title:opts.sectionTitle||'Eingaben',fields:opts.fields||[]}];
  groups.forEach(group=>{
   const body=elem('div','ds-form-grid');
   (group.fields||[]).forEach(item=>put(body,item instanceof Node?item:ui().field(item)));
   put(host,section(group.title||'Eingaben',body));
  });
  if(opts.message)put(host,elem('p','ds-form-message',opts.message));
  const bar=elem('div','ds-modal-actions ds-form-actions');
  put(bar,ui().button({label:opts.cancelLabel||'Abbrechen',variant:'secondary',onClick:opts.onCancel}),
    ui().button({label:opts.submitLabel||'Speichern',variant:'primary',type:'submit',disabled:!opts.onSubmit}));
  host.appendChild(bar);
  host.addEventListener('submit',event=>{
   event.preventDefault();
   if(typeof opts.onSubmit==='function')opts.onSubmit(host);
  });
  return ui().page({title:opts.title||'Formular',description:opts.description||'',children:[host],className:'ds-template-form-page'});
 }
 function tasks(opts={}){
  const filters=elem('div','ds-template-toolbar');
  if(opts.filters?.length)opts.filters.forEach(f=>put(filters,ui().field(f)));
  const rows=(opts.rows||[]).map(r=>({...r,statusNode:ui().badge(typeof r.status==='string'?{label:r.status}:r.status||{label:'Offen'}),
   actionNode:r.action&&r.action.label?ui().button(r.action):''}));
  const columns=opts.columns||[
   {key:'name',label:'Eintrag'},{key:'due',label:'Fällig am'},
   {key:'assignee',label:'Verantwortlich'},
   {key:'statusNode',label:'Status',render:r=>r.statusNode},
   {key:'actionNode',label:'Aktion',render:r=>r.actionNode}
  ];
  return ui().page({title:opts.title||'Aufgaben und Prüfungen',description:opts.description||'',actions:actions(opts.actions||[]),
   children:[section(opts.listTitle||'Fälligkeiten',put(elem('div','ds-template-list'),filters,ui().table({columns,rows,emptyMessage:opts.emptyMessage||'Keine offenen Prüfungen'})))],
   className:'ds-template-tasks'});
 }
 function settings(opts={}){
  const layout=elem('div','ds-settings-layout'),navigation=elem('nav','ds-settings-nav'),host=elem('div','ds-settings-panels');
  navigation.setAttribute('aria-label','Einstellungsbereiche');
  const sections=opts.sections||[];
  const render=(index)=>{

   host.replaceChildren();
   const target=sections[index];
   if(target)put(host,opts.rawContent?(target.content||ui().empty({message:'Keine Einstellungen'})):section(target.title,target.content||ui().empty({message:'Keine Einstellungen'})));
   Array.from(navigation.children).forEach((button,i)=>button.setAttribute('aria-current',i===index?'page':'false'));
  };
  sections.forEach((group,i)=>{
   const button=ui().button({label:group.title||'Einstellungen',variant:'secondary',
     onClick:()=>typeof opts.onSelect==='function'?opts.onSelect(group.id||String(i)):render(i)});
   button.classList.add('ds-settings-link');
   if(group.id)button.id='stab-'+group.id;
   navigation.appendChild(button);
  });
  put(layout,navigation,host);
  const selected=sections.findIndex(item=>item.id===opts.activeId);
  if(sections.length)render(selected<0?0:selected);else put(host,ui().empty({message:'Keine Einstellungsbereiche'}));
  const content=[layout];
  if(opts.onSave)content.push(put(elem('div','ds-modal-actions'),ui().button({label:opts.saveLabel||'Einstellungen speichern',variant:'primary',onClick:opts.onSave})));
  return ui().page({title:opts.title||'Einstellungen',description:opts.description||'',children:content,className:'ds-template-settings'});
 }
 const RENDER=Object.freeze({overview,management,detail,form,tasks,settings});
 function render(id,options={}){if(!KEYS.has(id))throw Error('Unbekanntes Seiten-Template: '+id);return RENDER[id](options);}
 // Self-contained sample data: no real members, medical records or live APIs shown in preview.
 function preview(id){
  switch(id){
   case 'overview':return overview({title:'Dashboard',description:'Beispiel einer Übersichtsseite',metrics:[
     {label:'Mitglieder',value:'48',note:'Beispieldaten'},{label:'Offene Aufgaben',value:'3'},{label:'Fällige Prüfungen',value:'2'}],
     actions:[{label:'Aktion',variant:'primary'}],panels:[{title:'Aktuelles',content:'Statusmeldungen und Schnellzugriffe erscheinen hier.'}]});
   case 'management':return management({title:'Mitgliederverwaltung',description:'Beispiel einer Verwaltungsseite',
     actions:[{label:'＋ Mitglied',variant:'primary'}],filters:[{label:'Status',options:[{value:'',label:'Alle'},{value:'active',label:'Aktiv'}]}],
     columns:[{key:'name',label:'Name'},{key:'status',label:'Status'},{key:'date',label:'Eintritt'}],
     rows:[{name:'Max Muster',status:'Aktiv',date:'12.01.2024'},{name:'Erika Beispiel',status:'Aktiv',date:'05.02.2025'}]});
   case 'detail':return detail({title:'Gerät · Beispiel',description:'Detailansicht mit Historie',status:{label:'Einsatzbereit',variant:'success'},
     fields:[{label:'Bezeichnung',value:'Gerät A'},{label:'Standort',value:'LF 20 · Fach 1'}],
     tiles:[{title:'Prüfung',content:'Nächste Prüfung: 02.10.2026'}],history:'Letzte Änderung: Beispiel-Datensatz'});
   case 'form':return form({title:'Mitglied anlegen',description:'Einheitliches Formular mit Abschnitten',groups:[
     {title:'Person',fields:[{label:'Vorname',name:'first',required:true},{label:'Nachname',name:'last',required:true}]},
     {title:'Weitere Angaben',fields:[{label:'Eintritt',type:'date',name:'joined'}]}],onSubmit:()=>{}});
   case 'tasks':return tasks({title:'Geräteprüfung',description:'Fälligkeiten und Abschlussaktionen',rows:[
     {name:'Gerät A',due:'02.10.2026',assignee:'Gerätewart',status:{label:'Bald fällig',variant:'warning'},action:{label:'Prüfen',variant:'primary'}},
     {name:'Gerät B',due:'20.09.2026',assignee:'Gerätewart',status:{label:'Überfällig',variant:'danger'},action:{label:'Prüfen',variant:'primary'}}]});
   case 'settings':return settings({title:'Administration',description:'Gruppierte Einstellungen',sections:[
     {title:'Allgemein',content:ui().field({label:'Bezeichnung',value:'Beispielorganisation'})},
     {title:'Darstellung',content:ui().field({label:'Theme',options:[{value:'dark',label:'Dunkel'},{value:'light',label:'Hell'}]})}],onSave:()=>{}});
   default:throw Error('Unbekanntes Seiten-Template');
  }
 }
 root.FWPageTemplates=Object.freeze({list:()=>TYPES.map(t=>({...t})),render,preview,overview,management,detail,form,tasks,settings});
})(window);
