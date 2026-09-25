/* Private document library: no sensitive file URLs or tokens in the DOM. */
(function(root){
'use strict';
const BASE='/api/document-center';
const e=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const labels={GENERAL:'Allgemein',DEVICE:'Geräte',TRAINING:'Schulungen',VEHICLE:'Fahrzeuge',CLUB:'Verein'};
let last=null,search='',timer=null,serial=0;
const fmtDate=v=>v?new Date(v+'T12:00:00').toLocaleDateString('de-DE'):'–';
function state(d){
 if(!d.expiresOn)return '';
 const today=new Date().toLocaleDateString('sv-SE');
 if(d.expiresOn<today)return '<span class="doc-tag late">Abgelaufen</span>';
 const remaining=(new Date(d.expiresOn+'T12:00:00')-new Date(today+'T12:00:00'))/86400000;
 return remaining<=30?'<span class="doc-tag soon">Läuft bald ab</span>':'';
}
function resultRows(data){
 const host=document.getElementById('documentResults');if(!host)return;
 host.innerHTML=data.documents.length?data.documents.map(d=>
  '<article class="doc-item"><div class="doc-item-head"><strong>'+e(d.title)+'</strong>'+state(d)+'</div>'+
  '<div class="doc-muted">'+e(labels[d.category]||d.category)+' · Version '+d.version+
  (d.visibility==='RESTRICTED'?' · 🔒 Vertraulich':' · Für berechtigte Mitglieder')+'</div>'+
  (d.description?'<p>'+e(d.description)+'</p>':'')+
  (d.expiresOn?'<div class="doc-muted">Ablauf: '+e(fmtDate(d.expiresOn))+'</div>':'')+
  '<div class="doc-actions"><button class="btn secondary" type="button" data-versions="'+d.id+'">Versionen und Datei</button>'+
  (d.canEdit?'<button class="btn secondary" type="button" data-edit="'+d.id+'">Bearbeiten</button>':'')+
  (d.canDelete?'<button class="btn danger" type="button" data-archive="'+d.id+'">Archivieren</button>':'')+
  '</div></article>').join(''):'<p class="empty">Keine passenden Dokumente gefunden.</p>';
 host.querySelectorAll('[data-versions]').forEach(b=>b.onclick=()=>versions(Number(b.dataset.versions)));
 host.querySelectorAll('[data-edit]').forEach(b=>b.onclick=()=>form(data.documents.find(d=>d.id===Number(b.dataset.edit))));
 host.querySelectorAll('[data-archive]').forEach(b=>b.onclick=()=>archive(Number(b.dataset.archive)));
}
async function refresh(){
 const sequence=++serial;
 try{
  const result=await api(BASE+(search?'?query='+encodeURIComponent(search):''));
  if(sequence!==serial||root.currentPage!=='documents')return;
  last=result;resultRows(result);
  root.setMenuNoticeCount?.('documents',result.documents.filter(d=>d.expiresOn&&d.expiresOn<=new Date(Date.now()+30*86400000).toLocaleDateString('sv-SE')).length);
 }catch(err){if(sequence===serial){const host=document.getElementById('documentResults');if(host)host.textContent='Dokumente konnten nicht geladen werden: '+err.message}}
}
async function page(){
 setActive('documents');closeMenu();serial++;
 content.innerHTML='<div class="title-row"><div><h1>Dokumentenverwaltung</h1><p class="sub">Geschützte Ablage · Versionen, Volltext und Fristen</p></div><button type="button" class="btn primary" id="docNew" hidden>＋ Dokument hochladen</button></div>'+
 '<div class="doc-tools"><label>Dokumente durchsuchen<input id="docSearch" type="search" placeholder="Titel, Beschreibung und durchsuchbarer Dateiinhalt …" value="'+e(search)+'" autocomplete="off"></label><button class="btn secondary" id="docRefresh">↻ Aktualisieren</button></div>'+
 '<div id="documentResults" class="doc-list"><p class="empty">Dokumente werden geladen …</p></div>'+
 '<p class="sub">PDF, Word und Text werden nach enthaltenem Text durchsucht. Gescannte PDFs und Bilder werden lokal durch OCR analysiert. Bei mehrseitigen Scans können nur die ersten Seiten erkannt werden. Angaben bitte immer kontrollieren.</p>';
 const input=document.getElementById('docSearch');
 input.oninput=()=>{search=input.value.trim();clearTimeout(timer);timer=setTimeout(refresh,320)};
 document.getElementById('docRefresh').onclick=refresh;
 try{await refresh();const add=document.getElementById('docNew');if(add&&last?.canWrite){add.hidden=false;add.onclick=()=>form(null)}}catch(err){content.textContent=err.message}
}
function categoriesOptions(current){return Object.entries(labels).map(([k,v])=>'<option value="'+k+'"'+(current===k?' selected':'')+'>'+e(v)+'</option>').join('')}
function form(doc){
 if(!last?.canWrite)return;
 const board=typeof role!=='undefined'&&['ADMIN','VORSTAND'].includes(role);
 modalTitle.textContent=doc?'Dokument bearbeiten':'Dokument hinzufügen';
 modalBody.innerHTML='<form id="docForm" class="doc-form">'+
  '<label>Titel *<input name="title" required maxlength="160" value="'+e(doc?.title||'')+'"></label>'+
  '<label>Beschreibung<textarea name="description" rows="3" maxlength="2000">'+e(doc?.description||'')+'</textarea></label>'+
  '<label>Kategorie<select name="category">'+categoriesOptions(doc?.category||'GENERAL')+'</select></label>'+
  '<label>Sichtbarkeit<select name="visibility">'+
   '<option value="MEMBERS"'+(doc?.visibility==='MEMBERS'?' selected':'')+'>Mitglieder mit Dokumenten-Leserecht</option>'+
   (board?'<option value="RESTRICTED"'+(!doc||doc.visibility==='RESTRICTED'?' selected':'')+'>Vertraulich (nur Vorstand/Administration)</option>':'')+
   '</select></label><label>Ablaufdatum (optional)<input name="expiresOn" type="date" value="'+e(doc?.expiresOn||'')+'"></label>'+
  (!doc?'<label>Datei *<input name="file" type="file" accept=".pdf,.docx,.txt,.jpg,.jpeg,.png" required></label>':'')+
  '<div class="doc-form-actions"><button class="btn primary" type="submit">Speichern</button><button type="button" class="btn secondary" id="docCancel">Abbrechen</button></div><div id="docError" role="alert"></div></form>';
 modal.classList.remove('hidden');
 document.getElementById('docCancel').onclick=closeModal;
 document.getElementById('docForm').onsubmit=async event=>{
  event.preventDefault();
  const form=event.currentTarget,button=form.querySelector('[type=submit]'),error=document.getElementById('docError');
  const fields=form.elements;
  const metadata={title:fields.title.value,description:fields.description.value,category:fields.category.value,visibility:fields.visibility.value,expiresOn:fields.expiresOn.value||null};
  button.disabled=true;error.textContent='';
  try{
   if(doc)await api(BASE+'/'+doc.id,{method:'PUT',body:JSON.stringify(metadata)});
   else{
    const fd=new FormData();fd.append('metadata',new Blob([JSON.stringify(metadata)],{type:'application/json'}));
    fd.append('file',fields.file.files[0]);
    const response=await fetch(BASE,{method:'POST',headers:{Authorization:'Bearer '+token},body:fd});
    if(!response.ok){let body={};try{body=await response.json()}catch{}throw Error(body.detail||body.message||'Hochladen fehlgeschlagen')}
   }
   closeModal();await refresh();
  }catch(err){error.textContent=err.message}finally{button.disabled=false}
 };
}
async function archive(id){
 if(!confirm('Dokument archivieren? Die Versionen bleiben für Nachweise und Sicherungen erhalten.'))return;
 try{await api(BASE+'/'+id,{method:'DELETE'});await refresh()}catch(err){alert(err.message)}
}
async function versions(id){
 modalTitle.textContent='Dokumentversionen';
 modalBody.innerHTML='<p>Versionen werden geladen …</p>';modal.classList.remove('hidden');
 try{
  const data=await api(BASE+'/'+id+'/versions');
  const doc=last?.documents.find(d=>d.id===id);
  modalTitle.textContent='Versionen: '+(doc?.title||'Dokument');
  modalBody.innerHTML='<div class="doc-revisions">'+data.map(v=>
   '<div class="doc-revision"><strong>Version '+v.version+'</strong> · '+e(v.fileName)+
   '<br><span class="doc-muted">'+e(new Date(v.uploadedAt).toLocaleString('de-DE'))+' · '+e(v.uploadedBy)+'</span>'+
   '<br><button class="btn secondary" type="button" data-download="'+v.version+'">Öffnen / Herunterladen</button> <button class="btn secondary" type="button" data-analyze="'+v.version+'">Erkannte Daten prüfen</button>'+
   '<div class="doc-muted">Erkennung: '+e(v.extractionMethod||'Noch nicht analysiert')+(v.extractionWarning?' · '+e(v.extractionWarning):'')+'</div>'+((doc?.canEdit&&v.extractionMethod==='FAILED')?'<button class="btn secondary" type="button" data-retry="'+v.version+'">OCR erneut ausführen</button>':'')+'</div>').join('')+'</div>'
   (doc?.canEdit?'<label class="doc-form">Neue Version hochladen<input type="file" id="docRevisionFile" accept=".pdf,.docx,.txt,.jpg,.jpeg,.png"></label><button class="btn primary" type="button" id="docUploadVersion">Neue Version speichern</button>':'')+
   '<button type="button" class="btn secondary" id="docVersionsReload">↻ Status aktualisieren</button><div id="docVersionError" role="alert"></div>';
  document.getElementById('docVersionsReload').onclick=()=>versions(id);
  modalBody.querySelectorAll('[data-download]').forEach(b=>b.onclick=()=>download(id,Number(b.dataset.download)));
  modalBody.querySelectorAll('[data-analyze]').forEach(b=>b.onclick=()=>analysis(id,Number(b.dataset.analyze)));
  modalBody.querySelectorAll('[data-retry]').forEach(b=>b.onclick=async()=>{
   b.disabled=true;try{await api(BASE+'/'+id+'/versions/'+Number(b.dataset.retry)+'/reanalyze',{method:'POST'});await versions(id)}
   catch(err){document.getElementById('docVersionError').textContent=err.message;b.disabled=false}
  });
  const upload=document.getElementById('docUploadVersion');
  if(upload)upload.onclick=async()=>{
   const file=document.getElementById('docRevisionFile').files[0];if(!file){document.getElementById('docVersionError').textContent='Bitte Datei auswählen';return}
   upload.disabled=true;try{
    const fd=new FormData();fd.append('file',file);
    const r=await fetch(BASE+'/'+id+'/versions',{method:'POST',headers:{Authorization:'Bearer '+token},body:fd});
    if(!r.ok){let body={};try{body=await r.json()}catch{}throw Error(body.detail||body.message||'Upload fehlgeschlagen')}
    await refresh();await versions(id);
   }catch(err){document.getElementById('docVersionError').textContent=err.message}finally{upload.disabled=false}
  };
 }catch(err){modalBody.textContent='Versionen konnten nicht geladen werden: '+err.message}
}
async function analysis(id,version){
 modalTitle.textContent='Dokumentenerkennung · Version '+version;
 modalBody.textContent='Erkannte Daten werden geladen …';modal.classList.remove('hidden');
 try{
  const result=await api(BASE+'/'+id+'/versions/'+version+'/analysis');
  const doc=last?.documents.find(d=>d.id===id),fields=result.suggestions;
  const line=(title,value)=>'<div class="doc-analysis-field"><b>'+e(title)+'</b><span>'+e(value?.value||'Nicht eindeutig erkannt')+'</span>'+
   (value?.evidence?'<small>Fundstelle: '+e(value.evidence)+'</small>':'')+'</div>';
  const candidates=result.matchedDevices||[];
  modalBody.innerHTML='<div class="doc-analysis">'+
    (result.warning?'<p class="message warn">'+e(result.warning)+'</p>':'')+
    '<p>Alle Angaben sind unverbindliche Vorschläge. Vor der Übernahme bitte mit dem Original vergleichen.</p>'+
    line('Erkannte Kategorie',{value:labels[fields.category]||fields.category})+
    line('Seriennummer',fields.serialNumber)+line('Hersteller',fields.manufacturer)+
    line('Prüfdatum',fields.inspectionDate)+line('Nächster Prüftermin',fields.nextInspectionDate)+
    '<p class="doc-muted">'+e(fields.note)+'</p>'+
    '<h3>Passende Geräte</h3>'+(candidates.length?candidates.map(d=>'<p>'+e(d.name)+' · '+e(d.inventoryNumber||'')+
      ' · Seriennummer '+e(d.serialNumber||'')+'</p>').join(''):'<p>Keine eindeutige Übereinstimmung gefunden oder kein Geräte-Leserecht vorhanden.</p>')+
    (doc?.canEdit?'<label class="doc-confirm"><input type="checkbox" id="docAnalysisConfirm"> Ich habe die Angaben anhand des Originaldokuments geprüft.</label>'+
      '<button class="btn primary" id="docAnalysisApply" disabled>Vorgeschlagene Kategorie und Frist übernehmen</button>':'')+
    '<p class="doc-muted">Die Geräteverwaltung und abgeschlossene Prüfungen werden durch diese Funktion nicht geändert.</p>'+
    '<button type="button" class="btn secondary" id="docAnalysisBack">Zur Versionsübersicht</button></div>';
  if(doc?.visibility==='MEMBERS'&&typeof role!=='undefined'&&['ADMIN','VORSTAND'].includes(role)){
   try{
    const state=await api(BASE+'/'+id+'/versions/'+version+'/external-status');
    if(state.available){
     const section=document.createElement('section');
     section.className='doc-external-review';
     section.innerHTML='<h3>Optionale KI-Auswertung</h3>'+
       '<p>Es wird nur der nachfolgende, von dir kontrollierte Text an den konfigurierten externen Dienst übertragen. Keine Originaldatei und kein vertrauliches Dokument.</p>'+
       '<label>Text vor der Übertragung prüfen und ggf. anonymisieren<textarea id="docExternalText" rows="6" maxlength="6000">'+e(result.reviewedTextPreview||'')+'</textarea></label>'+
       '<label class="doc-confirm"><input id="docExternalConsent" type="checkbox"> Ich habe diesen Text geprüft: keine vertraulichen oder personenbezogenen Angaben. Ich genehmige diese einmalige Übertragung.</label>'+
       '<button type="button" class="btn secondary" id="docExternalSend" disabled>Einmalig extern analysieren</button><div id="docExternalResult" aria-live="polite"></div>';
     modalBody.querySelector('.doc-analysis')?.appendChild(section);
     const consent=section.querySelector('#docExternalConsent'),send=section.querySelector('#docExternalSend'),text=section.querySelector('#docExternalText');
     const check=()=>{send.disabled=!consent.checked||!text.value.trim()||text.value.length>6000};
     consent.onchange=check;text.oninput=check;
     send.onclick=async()=>{
      send.disabled=true;section.querySelector('#docExternalResult').textContent='Externe Auswertung wird ausgeführt …';
      try{
       const response=await api(BASE+'/'+id+'/versions/'+version+'/external-review',{method:'POST',
        body:JSON.stringify({approvedNonConfidential:true,reviewedExcerpt:text.value})});
       section.querySelector('#docExternalResult').textContent='Unverbindliche KI-Vorschläge: '+response.suggestion+
        ' – Bitte sämtliche Angaben anhand des Originals prüfen.';
      }catch(err){section.querySelector('#docExternalResult').textContent='Externe Auswertung fehlgeschlagen: '+err.message}
      finally{check()}
     };
    }
   }catch(err){console.warn('Externe Analyse nicht verfügbar:',err.message)}
  }
  document.getElementById('docAnalysisBack').onclick=()=>versions(id);
  const confirm=document.getElementById('docAnalysisConfirm'),apply=document.getElementById('docAnalysisApply');
  if(confirm&&apply){
   confirm.onchange=()=>{apply.disabled=!confirm.checked};
   apply.onclick=async()=>{
    apply.disabled=true;try{
     await api(BASE+'/'+id,{method:'PUT',body:JSON.stringify({
      title:doc.title,description:doc.description,category:fields.category||doc.category,
      visibility:doc.visibility,expiresOn:fields.nextInspectionDate?.value||doc.expiresOn||null})});
     closeModal();await refresh();
    }catch(err){apply.disabled=false;alert(err.message)}
   };
  }
 }catch(err){modalBody.textContent='Die Analyse konnte nicht geladen werden: '+err.message}
}
async function download(id,version){
 const popup=window.open('','_blank');if(popup)popup.document.body.textContent='Datei wird geladen …';
 try{
  const rev=(await api(BASE+'/'+id+'/versions')).find(v=>v.version===version);
  const r=await fetch(BASE+'/'+id+'/versions/'+version+'/file',{headers:{Authorization:'Bearer '+token},cache:'no-store'});
  if(!r.ok)throw Error('Datei konnte nicht geladen werden (HTTP '+r.status+')');
  const url=URL.createObjectURL(await r.blob());
  if(rev?.contentType==='application/pdf'||rev?.contentType?.startsWith('image/')){
   if(popup)popup.location.replace(url);
   else{const a=document.createElement('a');a.href=url;a.target='_blank';a.rel='noopener';a.click()}
  }else{
   popup?.close();const a=document.createElement('a');a.href=url;a.download=rev?.fileName||'dokument';document.body.append(a);a.click();a.remove();
  }
  setTimeout(()=>URL.revokeObjectURL(url),120000);
 }catch(err){if(popup)popup.document.body.textContent=err.message;else alert(err.message)}
}
root.DocumentCenter=Object.freeze({page,refresh});
})(window);
