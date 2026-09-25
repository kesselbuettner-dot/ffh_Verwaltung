/* Aggregated personal worklist. Specialist workflows remain authoritative and open in place. */
(function(root){
 'use strict';
 const BASE='/api/my-tasks/general';
 const escHtml=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const date=v=>v?new Date(String(v).length===10?v+'T12:00:00':v).toLocaleDateString('de-DE'):'Keine Frist';
 const today=()=>new Date().toLocaleDateString('sv-SE');
 let last=null,showDone=false,filter='ALL',search='';
 const categories={GENERAL:'Vereinsaufgabe',DEVICE:'Geräteprüfung',SERVICE:'Dienst-Rückmeldung',DRIVING:'Führerscheinkontrolle',MESSAGE:'Nachricht'};
 function row(source,id,title,description,dueOn,status,action,extra={}){
  return {source,id,title,description,dueOn,status,action,...extra};
 }
 async function load(){
  const [manual,devices,services,messages,driving]=await Promise.all([
   api(BASE),api('/api/my-tasks/device-inspections?includeDone=false').catch(()=>[]),
   api('/api/training/events/reminders').catch(()=>[]),
   api('/api/dashboard/messages').catch(()=>[]),
   api('/api/fire/qualifications/driving/my').catch(()=>[])
  ]);
  const items=(manual.tasks||[]).map(t=>row('GENERAL',String(t.id),t.title,t.description,t.dueOn,t.status,
   ()=>editTask(t),{raw:t,canEdit:t.canEdit,canChangeStatus:t.canChangeStatus,assigneeName:t.assigneeName}));
  (Array.isArray(devices)?devices:[]).filter(x=>x.status==='OPEN').forEach(x=>items.push(row('DEVICE',String(x.id),x.deviceName,
   [x.location,x.inventoryNumber].filter(Boolean).join(' · '),x.dueOn,'OPEN',()=>DeviceCycleTasks.page())));
  (Array.isArray(services)?services:[]).forEach(x=>items.push(row('SERVICE',x.eventId+':'+x.occurrenceDate,x.title,
   new Date(x.startAt).toLocaleString('de-DE'),x.occurrenceDate,'OPEN',()=>serviceRemindersPage())));
  (Array.isArray(messages)?messages:[]).filter(x=>x.type==='MESSAGE'&&x.active&&!x.read).forEach(x=>items.push(
   row('MESSAGE',String(x.id),x.title,x.body||'',null,'OPEN',()=>openMobileMessages())));
  // Only genuinely due driving checks appear as tasks, never a permanent dummy task.
  (Array.isArray(driving)?driving:[]).filter(c=>['DUE','OVERDUE'].includes(c.status)).forEach(c=>items.push(
   row('DRIVING',String(c.id),'Meine Führerscheinkontrolle',c.referencePresent?'Führerscheinprüfung durchführen':'Referenzdaten durch die Wehrleitung erforderlich',
    c.nextDueOn||null,'OPEN',()=>WehrleiterUI.myDrivingPage())));

  last={manual,items};return last;
 }
 function badge(task){
  if(task.status==='DONE')return '<span class="mytask-state done">Erledigt</span>';
  if(task.status==='IN_PROGRESS')return '<span class="mytask-state progress">In Bearbeitung</span>';
  if(task.status==='INFO')return '<span class="mytask-state info">Prüfstatus ansehen</span>';
  if(task.dueOn&&task.dueOn<today())return '<span class="mytask-state overdue">Überfällig</span>';
  return '<span class="mytask-state open">Offen</span>';
 }
 function visible(task){
  if(!showDone&&task.status==='DONE')return false;
  if(filter!=='ALL'&&task.source!==filter)return false;
  return !search||[task.title,task.description,task.assigneeName,categories[task.source]].some(v=>String(v||'').toLocaleLowerCase('de').includes(search));
 }
 function filteredRows(){return last?last.items.filter(visible).sort((a,b)=>(a.status==='DONE')-(b.status==='DONE')||(a.dueOn||'9999').localeCompare(b.dueOn||'9999')):[];}
 function taskListMarkup(rows){return (rows.length?rows.map((t,i)=>'<article class="mytasks-item"><div class="mytasks-item-top"><small>'+escHtml(categories[t.source]||t.source)+'</small>'+badge(t)+'</div><strong>'+escHtml(t.title)+'</strong><p>'+escHtml(t.description||'')+'</p><div class="mytasks-item-bottom"><span>'+escHtml(date(t.dueOn))+(t.assigneeName?' · '+escHtml(t.assigneeName):'')+'</span><button type="button" class="btn secondary" data-task-open="'+i+'">'+(t.source==='GENERAL'?'Bearbeiten':'Öffnen')+' →</button></div></article>').join(''):'<p class="empty">Für diese Auswahl liegen keine Aufgaben vor.</p>');}
 function wireTaskRows(rows){content.querySelectorAll('[data-task-open]').forEach(button=>button.onclick=()=>rows[Number(button.dataset.taskOpen)].action());}
 function refreshTaskList(){const list=content.querySelector('.mytasks-list');if(!list||!last)return;const rows=filteredRows();list.innerHTML=taskListMarkup(rows);wireTaskRows(rows);}
 function render(){
  if(!last)return;
  const {manual,items}=last;
  const active=items.filter(t=>!['DONE','INFO'].includes(t.status));
  const overdue=active.filter(t=>t.dueOn&&t.dueOn<today()).length;
  const inProgress=active.filter(t=>t.status==='IN_PROGRESS').length;
  root.setMenuNoticeCount?.('my-tasks',items.filter(t=>t.source==='GENERAL'&&t.status!=='DONE'&&t.raw?.assigneeId===manual.currentUserId).length);
  const rows=filteredRows();
  content.innerHTML='<section class="mytasks-page"><div class="title-row"><div><h1>Meine Aufgaben</h1><p class="sub">Alle persönlichen Aufgaben aus der Vereins- und Feuerwehrverwaltung</p></div>'+
   (manual.canCreate?'<button class="btn primary" id="mytasksCreate">＋ Aufgabe anlegen</button>':'')+'</div>'+
   '<div class="mytasks-metrics"><div><b>'+active.length+'</b><span>Offen</span></div><div><b>'+overdue+'</b><span>Überfällig</span></div><div><b>'+inProgress+'</b><span>In Bearbeitung</span></div></div>'+
   '<div class="mytasks-toolbar"><input id="mytasksSearch" aria-label="Aufgaben suchen" placeholder="Aufgaben suchen …" value="'+escHtml(search)+'"><select id="mytasksFilter" aria-label="Aufgabenart filtern">'+
   [['ALL','Alle Aufgaben'],...Object.entries(categories).map(([k,v])=>[k,v])].map(([k,v])=>'<option value="'+k+'"'+(filter===k?' selected':'')+'>'+escHtml(v)+'</option>').join('')+
   '</select><label><input type="checkbox" id="mytasksShowDone"'+(showDone?' checked':'')+'> Erledigte anzeigen</label><button class="btn secondary" id="mytasksReload">↻ Aktualisieren</button></div>'+
   '<div class="mytasks-list">'+taskListMarkup(rows)+'</div>'+
   '<p class="sub">Geräteprüfungen und Führerscheinkontrollen werden ausschließlich in ihren Fachmodulen abgeschlossen. Das Öffnen einer Aufgabe ersetzt keine vorgeschriebene Prüfung oder Unterschrift.</p></section>';
  document.getElementById('mytasksCreate')?.addEventListener('click',()=>editTask(null));
  document.getElementById('mytasksReload').onclick=page;
  document.getElementById('mytasksSearch').oninput=e=>{search=e.target.value.toLocaleLowerCase('de');refreshTaskList()};
  document.getElementById('mytasksFilter').onchange=e=>{filter=e.target.value;render()};
  document.getElementById('mytasksShowDone').onchange=e=>{showDone=e.target.checked;render()};
  wireTaskRows(rows);
 }
 async function page(){
  setActive('my-tasks');closeMenu();
  content.innerHTML='<div class="panel"><h1>Meine Aufgaben</h1><p class="sub">Aufgaben werden geladen …</p></div>';
  try{await load();if(root.currentPage==='my-tasks')render()}
  catch(e){if(root.currentPage==='my-tasks')content.innerHTML='<div class="panel"><h1>Meine Aufgaben</h1><div class="message error">'+escHtml(e.message)+'</div></div>'}
 }
 function editTask(t){
  const management=!t||(t.canEdit&&t.status!=='DONE'),own=t&&t.canChangeStatus;
  if(!last||(!management&&!own))return;
  modalTitle.textContent=t?'Aufgabe · '+t.title:'Neue Vereinsaufgabe';
  const users=last.manual.assignees||[];
  const select=users.map(p=>'<option value="'+p.id+'"'+(t&&t.assigneeId===p.id?' selected':'')+'>'+escHtml(p.name)+'</option>').join('');
  const form='<form id="mytasksEditForm" class="mytasks-edit">'+
   (management?'<label>Titel *<input name="title" required maxlength="160" value="'+escHtml(t?.title||'')+'"></label>'+
    '<label>Beschreibung<textarea name="description" rows="3" maxlength="4000">'+escHtml(t?.description||'')+'</textarea></label>'+
    '<label>Zuständiges Mitglied<select name="assigneeId" required>'+select+'</select></label>'+
    '<label>Fällig am<input type="date" name="dueOn" value="'+escHtml(t?.dueOn||'')+'"></label>':
    '<p>'+escHtml(t.title)+'</p><p>'+escHtml(t.description||'')+'</p>')+
   (t&&own?'<label>Status<select name="status"><option value="OPEN"'+(t.status==='OPEN'?' selected':'')+'>Offen</option><option value="IN_PROGRESS"'+(t.status==='IN_PROGRESS'?' selected':'')+'>In Bearbeitung</option><option value="DONE"'+(t.status==='DONE'?' selected':'')+'>Erledigt</option></select></label>':'')+
   '<div id="mytasksEditError" role="alert"></div><div class="quick"><button class="btn primary" type="submit">Speichern</button>'+
   (t&&t.canEdit?'<button type="button" class="btn danger" id="mytasksDelete">Löschen</button>':'')+
   '<button type="button" class="btn secondary" id="mytasksCancel">Abbrechen</button></div></form>';
  modalBody.innerHTML=form;modal.classList.remove('hidden');
  document.getElementById('mytasksCancel').onclick=closeModal;
  document.getElementById('mytasksDelete')?.addEventListener('click',async()=>{
   if(!confirm('Diese Vereinsaufgabe dauerhaft löschen?'))return;
   try{await api(BASE+'/'+t.id,{method:'DELETE'});closeModal();await page()}
   catch(e){document.getElementById('mytasksEditError').textContent=e.message}
  });
  document.getElementById('mytasksEditForm').onsubmit=async e=>{
   e.preventDefault();const f=e.currentTarget,save=f.querySelector('[type="submit"]');save.disabled=true;
   const status=f.elements.status?.value;
   try{
    if(management){
     const data={title:f.elements.title.value,description:f.elements.description.value,assigneeId:Number(f.elements.assigneeId.value),
      dueOn:f.elements.dueOn.value||null};
     if(!t)await api(BASE,{method:'POST',body:JSON.stringify(data)});
     else await api(BASE+'/'+t.id,{method:'PUT',body:JSON.stringify(data)});
    }
    if(t&&own&&status&&status!==t.status)await api(BASE+'/'+t.id+'/status',{method:'PATCH',body:JSON.stringify({status})});
    closeModal();await page();
   }catch(error){document.getElementById('mytasksEditError').textContent=error.message}
   finally{save.disabled=false}
  };
 }
 root.UnifiedTasks={page,refresh:async()=>{if(typeof token==='undefined'||!token)return;try{const tasks=await api(BASE);const open=(tasks.tasks||[]).filter(t=>t.status!=='DONE'&&t.assigneeId===tasks.currentUserId);root.setMenuNoticeCount?.('my-tasks',open.length)}catch{}}};
})(window);
