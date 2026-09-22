/* Harmonized device appointments and signed reports. Backend remains authority for every action. */
(function(root){
 'use strict';
 const url='/api/device-planning';
 const fmt=x=>x?new Date(x+'T12:00:00').toLocaleDateString('de-DE'):'–';
 const permitted=(name)=>typeof hasPermission==='function'&&hasPermission(name);
 function button(label,handler,variant='secondary'){
  const b=document.createElement('button');b.type='button';b.className='btn small '+variant;b.textContent=label;b.addEventListener('click',handler);return b;
 }
 const node=(tag,textValue)=>{const x=document.createElement(tag);if(textValue!=null)x.textContent=textValue;return x;};
 function error(e){alert(e.message||'Die Aktion konnte nicht abgeschlossen werden.');}
 async function refresh(){
  setActive('device-inspection-plans');
  content.innerHTML='<div class="title-row"><div><h1>✅ Prüftermine</h1><span class="sub">Einzel- und Sammelprüfungen mit gemeinsamem Prüfnachweis</span></div><div class="quick" id="inspectionActions"></div></div><div class="panel"><h3>Geplante Prüftermine</h3><div id="inspectionSessions">Wird geladen …</div></div><div class="panel" style="margin-top:16px"><h3>Unterschriebene Prüfprotokolle</h3><div id="inspectionReports">Wird geladen …</div></div>';
  if(permitted('training.services.write'))document.getElementById('inspectionActions').append(button('＋ Prüftermin anlegen',()=>openServiceForm(true),'primary'));
  try{
   const [list,reports]=await Promise.all([api(url+'/sessions'),api(url+'/reports')]);
   const host=document.getElementById('inspectionSessions');
   host.replaceChildren();
   for(const item of list){
    const row=node('div');row.className='notice-item';
    const title=node('strong',fmt(item.date)+' · '+item.title);
    const meta=node('div',(item.locations||[]).join(', ')+' · '+(item.categories||[]).join(', ')+(item.total?' · '+item.completed+'/'+item.total+' bearbeitet':''));meta.className='sub';
    const actions=node('div');actions.className='quick';actions.style.marginTop='7px';
    actions.append(button(item.reportId?'Protokoll ansehen':'Prüfliste öffnen',
     ()=>item.reportId?openReport(item.reportId):openSession(item.eventId,item.date),'primary'));
    if(permitted('fire.devices.delete')&&permitted('training.services.delete'))
     actions.append(button('Termin löschen',()=>removeSession(item.eventId,item.date),'danger'));
    row.append(title,meta,actions);host.append(row);
   }
   if(!list.length)host.textContent='Keine geplanten Geräteprüftermine.';
   const reportsHost=document.getElementById('inspectionReports');reportsHost.replaceChildren();
   for(const r of reports){
    const row=node('div');row.className='notice-item';
    const label=node('strong',fmt(r.date)+' · '+r.title);
    const meta=node('div','Prüfer: '+r.inspector+' · abgeschlossen '+new Date(r.signedAt).toLocaleString('de-DE'));meta.className='sub';
    row.append(label,meta,button('Protokoll öffnen',()=>openReport(r.id),'secondary'));reportsHost.append(row);
   }
   if(!reports.length)reportsHost.textContent='Noch keine unterzeichneten Protokolle.';
  }catch(e){error(e);}
 }
 async function removeSession(id,date){
  const single=confirm('Diesen Prüftermin entfernen? Bei Serien wird nur dieser Tag entfernt. Unterschriebene Prüfprotokolle bleiben erhalten.');
  if(!single)return;
  try{await api(url+'/sessions/'+id+'/'+date,{method:'DELETE'});await refresh();}catch(e){error(e);}
 }
 async function openSession(eventId,date){
  try{
   const result=await api(url+'/sessions/'+eventId+'/'+date);
   if(result.session.reportId){await openReport(result.session.reportId);return;}
   modalTitle.textContent='Geräteprüfung · '+fmt(date);
   const frame=node('div');frame.className='inspection-session-modal';
   frame.append(node('p',result.session.title));
   const hint=node('p','Bitte für jedes Gerät ein Ergebnis speichern. Erst nach der gemeinsamen Unterschrift wird das Prüfprotokoll abgeschlossen.');
   hint.className='sub';frame.append(hint);
   for(const task of result.tasks){
    const section=node('div');section.className='inspection-task';
    const label=node('div');label.append(node('strong',task.deviceName),node('small',(task.inventoryNumber||'–')+' · '+task.location+' · '+task.category));label.lastChild.className='sub';
    const select=node('select');
    for(const [v,t] of [['PENDING','Offen'],['INSPECTED','Geprüft'],['NOT_INSPECTABLE','Nicht prüfbar'],['DEFECTIVE','Defekt']]){
     const option=node('option',t);option.value=v;option.selected=task.status===v;select.append(option);
    }
    const notes=node('input');notes.placeholder='Bemerkung';notes.maxLength=1000;notes.value=task.note||'';
    const save=button(task.inspectionId?'Bereits einzeln geprüft':'Ergebnis speichern',
      async()=>{try{
       if(['NOT_INSPECTABLE','DEFECTIVE'].includes(select.value)&&!notes.value.trim())throw Error('Bitte den Mangel beschreiben.');
       await api(url+'/tasks/'+task.id,{method:'PUT',body:JSON.stringify({status:select.value,note:notes.value})});
       await openSession(eventId,date);
      }catch(e){error(e);}},'primary');
    if(task.inspectionId){select.disabled=true;notes.disabled=true;save.disabled=true;}
    section.append(label,select,notes,save);frame.append(section);
   }
   if(!result.tasks.length)frame.append(node('p','Keine prüfpflichtigen Geräte zugeordnet. Bitte Standort, Kategorie und Gerätezyklus prüfen.'));
   const signature=root.FWSignature.field('Unterschrift zum Abschluss des gesamten Prüftermins');
   frame.append(signature.element);
   const actions=node('div');actions.className='quick';actions.style.marginTop='12px';
   actions.append(button('Schließen',closeModal),
    button('Prüfung abschließen und Protokoll erstellen',async()=>{
     try{
      const payload={signatureData:signature.signature()};
      const report=await api(url+'/sessions/'+eventId+'/'+date+'/complete',{method:'POST',body:JSON.stringify(payload)});
      await openReport(report.id);
      await refresh();
     }catch(e){error(e);}
    },'primary'));
   frame.append(actions);modalBody.replaceChildren(frame);modal.classList.remove('hidden');
  }catch(e){error(e);}
 }
 function printable(report){
  const items=report.items||[];
  const rows=items.map(i=>'<tr><td>'+esc(i.deviceName||'')+'</td><td>'+esc(i.inventoryNumber||'–')+
   '</td><td>'+esc(i.location||'–')+'</td><td>'+esc(i.result||'')+'</td><td>'+esc(i.note||'–')+
   '</td><td>'+esc(fmt(i.nextInspectionDate))+'</td></tr>').join('');
  return '<section class="inspection-report-print"><h1>Prüfprotokoll · '+esc(report.title)+'</h1><p>Termin: '+esc(fmt(report.date))+
   ' · Prüfer: '+esc(report.inspector)+' · unterzeichnet: '+esc(new Date(report.signedAt).toLocaleString('de-DE'))+
   '</p><table><thead><tr><th>Gerät</th><th>Inventar</th><th>Standort</th><th>Ergebnis</th><th>Bemerkung</th><th>Nächste Prüfung</th></tr></thead><tbody>'+
   rows+'</tbody></table><p>Unterschrift des verantwortlichen Prüfers:</p><img alt="Unterschrift" style="max-height:95px;max-width:320px" src="'+
   (report.signatureData?.startsWith('data:image/png;base64,')?report.signatureData:'')+'"></section>';
 }
 async function openReport(id){
  try{
   const report=await api(url+'/reports/'+id);
   modalTitle.textContent='Unterschriebenes Prüfprotokoll · '+fmt(report.date);
   const view=node('div');view.className='inspection-report-view';
   view.innerHTML=printable(report);
   const bar=node('div');bar.className='quick';bar.style.marginTop='12px';
   bar.append(button('Als PDF drucken',()=>{
    const old=document.getElementById('inspectionPrintReport');old?.remove();
    const print=node('div');print.id='inspectionPrintReport';print.className='print-report';print.innerHTML=printable(report);
    document.body.append(print);
    root.addEventListener('afterprint',()=>print.remove(),{once:true});
    root.print();
   },'primary'),button('Schließen',closeModal));
   modalBody.replaceChildren(view,bar);modal.classList.remove('hidden');
  }catch(e){error(e);}
 }
 // Existing navigation and menu continue to use the same public function names.
 root.deviceInspectionPlansPage=refresh;
 root.openInspectionSession=openSession;
 root.saveInspectionTask=async function(id,eventId,date){await openSession(eventId,date);};
 root.openDeviceInspectionReport=openReport;
 root.deleteInspectionSession=removeSession;
})(window);
