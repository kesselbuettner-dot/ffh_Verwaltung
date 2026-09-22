/* Meine Aufgaben / Geräteprüfung — shared FWPageTemplates.tasks and FWComponents.
 * No members, task decisions or roles are trusted from UI; backend enforces ownership.
 */
(function(root){
 'use strict';
 const URL='/api/my-tasks/device-inspections';
 const ui=()=>root.FWComponents,templates=()=>root.FWPageTemplates;
 let loaded=[],eligible=[],isManager=false,search='',status='OPEN';
 const d=value=>value?new Date(value+'T12:00:00').toLocaleDateString('de-DE'):'–';
 const n=(tag,css='',value)=>{const el=document.createElement(tag);if(css)el.className=css;if(value!==undefined)el.textContent=String(value);return el;};
 const append=(element,...children)=>{children.filter(Boolean).forEach(c=>element.appendChild(c));return element;};
 const pending=entry=>entry.status==='OPEN';
 const badge=entry=>ui().badge({label:pending(entry)?(entry.dueOn<new Date().toLocaleDateString('sv-SE')?'Überfällig':'Offen'):'Abgeschlossen',
   variant:pending(entry)?'warning':'success'});
 function message(host,value,isError=false){
  host.replaceChildren(n('p',isError?'ds-task-message is-error':'ds-task-message',value));
 }
 function assigned(entry){return entry.assignedName||'Gerätewart (Rollenpostfach)';}
 async function page(){
  setActive('device-cycle-tasks');
  content.replaceChildren(ui().page({title:'Geräteprüfung',description:'Prüfaufgaben aus den Prüfzylussen deiner Geräte werden geladen …'}));
  try{
   const [items,members]=await Promise.all([api(URL+'?includeDone=true'),api(URL+'/assignees').catch(()=>null)]);
   loaded=items||[];eligible=members||[];isManager=members!==null;
   drawPage();
  }catch(error){content.replaceChildren(ui().page({title:'Geräteprüfung',children:[ui().empty({message:error.message||'Prüfaufgaben konnten nicht geladen werden.'})]}));}
 }
 function rowMatches(entry){
  const term=search.toLocaleLowerCase('de');
  return (status==='ALL'||(status==='OPEN'&&pending(entry))||(status==='DONE'&&!pending(entry)))
   &&(!term||[entry.deviceName,entry.inventoryNumber,entry.location,entry.assignedName]
      .some(v=>String(v||'').toLocaleLowerCase('de').includes(term)));
 }
 function actions(entry){
  const box=n('div','ds-row-actions');
  if(!pending(entry)){
   const result=n('div','ds-row-actions');result.appendChild(n('span','u-text-muted',entry.result||'Abgeschlossen'));
   if(entry.inspectionId)result.appendChild(ui().button({label:'Protokoll',variant:'secondary',onClick:()=>openProtocol(entry)}));
   return result;
  }
  if(isManager)append(box,ui().button({label:'Weitergeben',variant:'secondary',onClick:()=>openAssign(entry)}));
  if(isManager||entry.assignedUserId)append(box,ui().button({label:'Prüfen',variant:'primary',onClick:()=>openComplete(entry)}));
  return box;
 }
 function columns(){return[
  {key:'deviceName',label:'Gerät'},
  {key:'location',label:'Standort',render:r=>r.location||'–'},
  {key:'dueOn',label:'Fällig am',render:r=>d(r.dueOn)},
  {key:'intervalMonths',label:'Zyklus',render:r=>r.intervalMonths?r.intervalMonths+' Monate':'–'},
  {key:'assignedName',label:'Zuständig',render:assigned},
  {key:'state',label:'Status',render:badge},
  {key:'controls',label:'Aktion',render:actions}
 ];}
 function renderFilteredTable(){
  const host=content.querySelector('.ds-template-tasks .ds-table-wrap');
  if(!host)return;
  const next=templates().tasks({rows:loaded.filter(rowMatches),columns:columns(),emptyMessage:'Für diesen Filter liegen keine Prüfaufgaben vor.'});
  const nextTable=next.querySelector('.ds-table-wrap');
  host.replaceWith(nextTable);
 }
 function drawPage(){
  const list=loaded.filter(rowMatches);
  const actionsTop=[{label:'↻ Aktualisieren',variant:'secondary',onClick:page}];
  if(isManager)actionsTop.push({label:'Geräteübersicht',variant:'secondary',onClick:()=>navigate('devices')});
  const result=templates().tasks({
   title:'Geräteprüfung',description:isManager?
    'Rollenpostfach Gerätewart · Prüffristen und einzelne Prüfaufgaben an Mitglieder weitergeben':
    'Meine persönlich zugewiesenen Prüfungen · Ergebnisse direkt am Gerät erfassen',
   listTitle:'Geräte und fällige Prüfungen',actions:actionsTop,
   rows:list,columns:columns(),emptyMessage:'Keine Geräteprüfungen in dieser Ansicht.',
   filters:[
    {label:'Gerät / Standort / Mitglied suchen',type:'search',value:search,placeholder:'Prüfaufgabe suchen',
     onInput:value=>{search=value;renderFilteredTable();}},
    {label:'Status',value:status,options:[{value:'OPEN',label:'Offen'},{value:'DONE',label:'Erledigt'},{value:'ALL',label:'Alle'}],
     onChange:value=>{status=value;renderFilteredTable();}}
   ]
  });
  const info=n('p','ds-task-hint',isManager?
   'Aufgaben werden ab 30 Tagen vor der Gerätefälligkeit automatisch erzeugt. Ohne Zuweisung bleibt jede Aufgabe beim Gerätewart.':
   'Prüfergebnisse werden nach Abschluss mit Datum und angemeldetem Benutzer im Geräteprüfprotokoll dokumentiert.');
  result.querySelector('.ds-card')?.prepend(info);
  content.replaceChildren(result);
 }
 function openAssign(entry){
  if(!isManager||!pending(entry))return;
  modalTitle.textContent='Geräteprüfung weitergeben · '+entry.deviceName;
  const selected=ui().field({label:'Prüfung an Mitglied weitergeben',value:String(entry.assignedUserId||''),
   options:[{value:'',label:'Gerätewart (Rollenpostfach)'},
    ...eligible.map(person=>({value:String(person.id),label:person.name}))]});
  const select=selected.querySelector('select');
  const notice=n('div','ds-task-dialog-message');
  const form=ui().modalContent({content:append(n('div','ds-task-dialog'),
   n('p','',entry.deviceName+' · '+(entry.location||'Standort unbekannt')+' · fällig '+d(entry.dueOn)),
   selected,notice),
   actions:[ui().button({label:'Abbrechen',variant:'secondary',onClick:closeModal}),
    ui().button({label:'Aufgabe zuweisen',variant:'primary',onClick:async()=>{
     const id=select.value?Number(select.value):null;
     try{await api(URL+'/'+entry.id+'/assignee',{method:'PUT',body:JSON.stringify({userId:id})});
      closeModal();await page();
     }catch(error){message(notice,error.message||'Zuweisung nicht möglich',true);}
    }})]});
  modalBody.replaceChildren(form);modal.classList.remove('hidden');
 }
 function openComplete(entry){
  if(!pending(entry)||!isManager&&!entry.assignedUserId)return;
  modalTitle.textContent='Geräteprüfung · '+entry.deviceName;
  const selectField=ui().field({label:'Prüfergebnis',value:'BESTANDEN',options:[
   {value:'BESTANDEN',label:'✓ Bestanden'},
   {value:'MIT_MANGEL',label:'! Mit Mangel'},
   {value:'NICHT_BESTANDEN',label:'✕ Nicht bestanden'}]});
  const select=selectField.querySelector('select');
  const noteField=append(n('div','ds-field'),n('label','', 'Prüfbemerkung (bei Mangel erforderlich)'));
  const textarea=n('textarea','ds-textarea');textarea.maxLength=1000;textarea.rows=3;
  noteField.appendChild(textarea);
  const notice=n('div','ds-task-dialog-message');
  const signature=root.FWSignature.field('Unterschrift zum Abschluss dieser Geräteprüfung');
  const body=append(n('div','ds-task-dialog'),n('p','',entry.deviceName+' · '+(entry.location||'–')+' · fällig '+d(entry.dueOn)),
   selectField,noteField,signature.element,notice);
  const form=ui().modalContent({content:body,actions:[
   ui().button({label:'Abbrechen',variant:'secondary',onClick:closeModal}),
   ui().button({label:'Prüfung abschließen',variant:'primary',onClick:async()=>{
    const result=select.value,note=textarea.value.trim();
    if(result!=='BESTANDEN'&&!note){message(notice,'Bei einem Mangel ist eine Bemerkung erforderlich.',true);return;}
    try{await api(URL+'/'+entry.id+'/complete',{method:'POST',body:JSON.stringify({result,note,signatureData:signature.signature()})});
      closeModal();await page();
    }catch(error){message(notice,error.message||'Prüfung konnte nicht abgeschlossen werden.',true);}
   }})
  ]});
  modalBody.replaceChildren(form);modal.classList.remove('hidden');
 }
 async function openProtocol(entry){
  try{
   const record=await api(URL+'/'+entry.id+'/protocol');
   modalTitle.textContent='Prüfprotokoll · '+entry.deviceName;
   const body=append(n('div','ds-task-dialog'),
    n('p','','Gerät: '+(record.deviceName||entry.deviceName)),
    n('p','','Standort: '+(record.location||'–')),
    n('p','','Prüfdatum: '+d(record.inspectionDate)),
    n('p','','Ergebnis: '+(record.result||'–')),
    n('p','','Prüfer: '+(record.inspector||'–')),
    n('p','','Bemerkung: '+(record.note||'–')),
    n('p','','Nächste Prüfung: '+d(record.nextInspectionDate)));
   if(record.signatureData?.startsWith('data:image/png;base64,')){
    const img=n('img');img.src=record.signatureData;img.alt='Unterschrift des Prüfers';img.style.cssText='max-width:100%;height:auto;background:#fff;border:1px solid #a6b5c4;border-radius:10px';
    body.appendChild(img);
   }
   modalBody.replaceChildren(body);modal.classList.remove('hidden');
  }catch(error){alert(error.message||'Protokoll konnte nicht geladen werden.');}
 }
 root.DeviceCycleTasks=Object.freeze({page});
})(window);
