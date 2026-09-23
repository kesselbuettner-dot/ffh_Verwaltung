/* Wehrleitung: qualification cards, tracked dates, manual driving check, mobile OCR data match.
   Photos are processed in memory only; the server accepts OCR TEXT, never images. */
(function(){
'use strict';
const BASE='/api/fire/qualifications';
let types=[],cards=[],people=[],driving=[],checks=[],visibleFilter='ALL',nameFilter='',typeFilter='',sortMode='name-asc',categoryFilter='ALL';
const el=id=>document.getElementById(id);
const safe=text=>esc(text==null?'':String(text));
const date=value=>value?new Date(value+'T12:00:00').toLocaleDateString('de-DE'):'–';
const inputDate=value=>value||'';
const rights=key=>hasPermission(key);
const notice=(host,message,kind='error')=>{if(host)host.innerHTML='<div class="message '+kind+'">'+safe(message)+'</div>';};
function header(title,info,action=''){return '<div class="title-row"><div><h1>'+title+'</h1><p class="sub">'+info+'</p></div>'+action+'</div>';}
const statusMap={VALID:'Gültig',DUE:'Prüfung fällig',OVERDUE:'Überfällig',UNSCHEDULED:'Termin nicht gesetzt',INACTIVE:'Inaktiv'};
const statusClass={VALID:'ok',DUE:'warning',OVERDUE:'off',UNSCHEDULED:'warning',INACTIVE:'off'};
function badge(card){return '<span class="badge '+(statusClass[card.status]||'')+'">'+safe(statusMap[card.status]||card.status)+'</span>';}
function cardTone(card){
 const code=String(card.code||card.title||'');
 let hash=0;for(const char of code)hash=(hash*31+char.charCodeAt(0))>>>0;
 return 'q-tone-'+hash%8;
}
function avatarHtml(person){
 const name=String(person.name||'?'),initials=name.trim().split(/\s+/).slice(0,2).map(x=>x[0]||'').join('').toUpperCase();
 const picture=typeof person.avatar==='string'&&/^data:image\/(jpeg|png|webp);base64,[a-zA-Z0-9+/=]+$/.test(person.avatar)?
  '<img src="'+safe(person.avatar)+'" alt="" loading="lazy">':safe(initials||'?');
 return '<span class="member-avatar" aria-hidden="true">'+picture+'</span>';
}
function cardHtml(card,forPrint=false){
 const abbr=String(card.shortLabel||card.code||'Q').slice(0,10);
 const label=card.title+' – '+(statusMap[card.status]||card.status)+', ausgestellt '+date(card.issuedOn)+', nächster Termin '+date(card.nextDueOn);
 const symbol=window.MenuDesigner?.iconHtml(card.icon)||'<span class="q-symbol-fallback">'+safe(card.icon||'📋')+'</span>';
 const content='<span class="q-symbol">'+symbol+'</span><span class="q-short">'+safe(abbr)+'</span>';
 const css='wehr-tile '+cardTone(card)+' is-'+safe(card.status||'VALID');
 return !forPrint&&rights('fire.qualifications.write')?
 '<button type="button" class="'+css+'" data-edit-card="'+Number(card.id)+'" title="'+safe(label)+'" aria-label="'+safe(label)+'">'+content+'</button>':
 '<span class="'+css+'" role="img" title="'+safe(label)+'" aria-label="'+safe(label)+'">'+content+'</span>';
}
function bind(host){host.querySelectorAll('[data-edit-card]').forEach(b=>b.onclick=()=>editCard(Number(b.dataset.editCard)));}
async function reload(){[types,cards,people]=await Promise.all([api(BASE+'/types'),api(BASE+'/cards'),api(BASE+'/people')]);}
function memberOptions(selected){return people.map(p=>'<option value="'+p.id+'"'+(selected===p.id?' selected':'')+'>'+safe(p.name)+'</option>').join('');}
function typeOptions(selected){return types.filter(t=>!t.sensitive||rights('fire.qualifications.sensitive.read')).map(t=>'<option value="'+t.id+'"'+(selected===t.id?' selected':'')+'>'+safe((window.MenuDesigner?.iconLabel(t.icon)||t.icon)+' · '+t.title)+'</option>').join('');}
async function page(){
 setActive('wehr-members');content.innerHTML=header('👥 Mitgliederverwaltung · Wehrleitung','Feuerwehrqualifikationen und Fälligkeiten; Stammdaten ausschließlich in der Administration.')+
 '<div class="panel">Qualifikationen werden geladen …</div>';
 try{await reload();drawPage();}catch(e){content.innerHTML+= '<div class="message error">'+safe(e.message)+'</div>';}
}
const WEHR_COLUMNS=[
 {code:'QUALIFICATION',title:'Qualifikationen'},
 {code:'CERTIFICATE_DOCUMENT',title:'Zertifikate / Dokumente'},
 {code:'SUITABILITY',title:'Tauglichkeiten'}
];
function filteredMemberRows(){
 const needle=nameFilter.trim().toLocaleLowerCase('de'),state=visibleFilter,type=typeFilter,category=categoryFilter;
 const matching=people.map(p=>({p,list:cards.filter(c=>c.memberId===p.id)
  .filter(c=>state==='ALL'||c.status===state).filter(c=>!type||c.code===type)
  .filter(c=>category==='ALL'||c.category===category)
  .sort((a,b)=>String(a.title).localeCompare(String(b.title),'de'))}))
 .filter(row=>(!type&&state==='ALL'&&category==='ALL'||row.list.length)&&
  (!needle||row.p.name.toLocaleLowerCase('de').includes(needle)||
   row.list.some(c=>c.title.toLocaleLowerCase('de').includes(needle)||String(c.shortLabel||'').toLocaleLowerCase('de').includes(needle))));
 const collator=new Intl.Collator('de',{sensitivity:'base',numeric:true});
 const soon=row=>Math.min(...row.list.map(c=>c.nextDueOn?Date.parse(c.nextDueOn+'T00:00:00'):Infinity));
 matching.sort((a,b)=>sortMode==='count-desc'?b.list.length-a.list.length||collator.compare(a.p.name,b.p.name):
  sortMode==='due-asc'?soon(a)-soon(b)||collator.compare(a.p.name,b.p.name):
  sortMode==='name-desc'?collator.compare(b.p.name,a.p.name):collator.compare(a.p.name,b.p.name));
 return matching;
}
function wehrLegend(){
 return '<div class="wehr-legend" aria-label="Legende der Kachelfarben">'+
 '<span class="wehr-legend-item"><i class="wehr-legend-swatch is-VALID"></i> Gültig (typbezogene Farbe)</span>'+
 '<span class="wehr-legend-item"><i class="wehr-legend-swatch is-DUE"></i> Bald fällig – schraffiert</span>'+
 '<span class="wehr-legend-item"><i class="wehr-legend-swatch is-OVERDUE"></i> Abgelaufen – grau</span>'+
 '<span class="wehr-legend-item"><i class="wehr-legend-swatch is-UNSCHEDULED"></i> Ohne Prüftermin – gepunktet</span></div>';
}
function groupedMemberTable(rows,print=false){
 const visible=rows;
 const head='<thead><tr><th scope="col">Mitglied</th>'+WEHR_COLUMNS.map(c=>
  '<th scope="col">'+safe(c.title)+'</th>').join('')+'</tr></thead>';
 const body=visible.map(({p,list})=>'<tr><th scope="row" class="wehr-name-cell">'+
  (print?'':avatarHtml(p))+'<strong>'+safe(p.name)+'</strong></th>'+
  WEHR_COLUMNS.map(column=>'<td data-label="'+safe(column.title)+'"><div class="wehr-card-list">'+
   list.filter(c=>c.category===column.code).map(c=>cardHtml(c,print)).join('')+
   '</div></td>').join('')+'</tr>').join('');
 return '<table class="wehr-overview-table">'+head+'<tbody>'+body+'</tbody></table>';
}
function drawPage(){
 const canWrite=rights('fire.qualifications.write'),canSensitive=rights('fire.qualifications.sensitive.read');
 content.innerHTML=header('👥 Mitgliederverwaltung · Wehrleitung',
  'Kacheln je Mitglied in drei Spalten. Anklicken, um Details und Prüftermine zu bearbeiten.',
  (canWrite?'<button class="btn primary" id="wehrAdd">＋ Kachel zuweisen</button>':'')+
  '<button class="btn secondary" id="wehrPrint">🖨 Druckbericht</button>')+
 '<div class="panel"><div class="ui-filterbar" role="search" aria-label="Mitglieder und Kacheln filtern">'+
 '<label class="ui-filterfield ui-filter-search"><span>Suche</span><input id="wehrSearch" type="search" placeholder="Mitglied oder Kachel" value="'+safe(nameFilter)+'"></label>'+
 '<label class="ui-filterfield"><span>Prüfstatus</span><select id="wehrStatus"><option value="ALL">Alle Prüfstatus</option>'+
 ['VALID','DUE','OVERDUE','UNSCHEDULED','INACTIVE'].map(k=>'<option value="'+k+'">'+statusMap[k]+'</option>').join('')+'</select></label>'+
 '<label class="ui-filterfield"><span>Kacheltyp</span><select id="wehrType"><option value="">Alle Kacheltypen</option>'+
 types.filter(t=>!t.sensitive||canSensitive).map(t=>'<option value="'+safe(t.code)+'">'+safe(t.title)+'</option>').join('')+'</select></label>'+
 '<label class="ui-filterfield"><span>Kategorie</span><select id="wehrCategory"><option value="ALL">Alle Kategorien</option>'+
 WEHR_COLUMNS.map(c=>'<option value="'+c.code+'">'+safe(c.title)+'</option>').join('')+'</select></label>'+
 '<label class="ui-filterfield"><span>Sortierung</span><select id="wehrSort"><option value="name-asc">Name A–Z</option><option value="name-desc">Name Z–A</option><option value="due-asc">Nächste Prüfung</option><option value="count-desc">Meiste Kacheln</option></select></label>'+
 '<div class="ui-filter-actions"><button class="btn secondary" type="button" id="wehrReset">Filter zurücksetzen</button>'+
 (canWrite?'<button class="btn secondary" type="button" id="wehrConfig">⚙️ Baukasten</button>':'')+'</div></div>'+
 wehrLegend()+'<div id="wehrRows" class="wehr-table-scroll"></div></div>';
 const search=el('wehrSearch'),status=el('wehrStatus'),type=el('wehrType'),sort=el('wehrSort'),category=el('wehrCategory');
 status.value=visibleFilter;type.value=typeFilter;sort.value=sortMode;category.value=categoryFilter;
 search.oninput=()=>{nameFilter=search.value;drawRows();};
 status.onchange=()=>{visibleFilter=status.value;drawRows();};
 type.onchange=()=>{typeFilter=type.value;drawRows();};
 category.onchange=()=>{categoryFilter=category.value;drawRows();};
 sort.onchange=()=>{sortMode=sort.value;drawRows();};
 el('wehrReset').onclick=()=>{nameFilter='';visibleFilter='ALL';typeFilter='';categoryFilter='ALL';sortMode='name-asc';drawPage();};
 if(el('wehrAdd'))el('wehrAdd').onclick=()=>editCard(null);
 if(el('wehrConfig'))el('wehrConfig').onclick=()=>configPage();
 el('wehrPrint').onclick=printWehrReport;
 drawRows();
}
function drawRows(){
 const host=el('wehrRows');if(!host)return;
 const rows=filteredMemberRows();
 host.innerHTML=rows.length?groupedMemberTable(rows):
  '<p class="empty">Keine Mitglieder oder Kacheln für diesen Filter.</p>';
 bind(host);
}
function printWehrReport(){
 // The report uses exactly the currently visible, server-authorized data and active filters.
 const rows=filteredMemberRows();
 const report=document.createElement('section');
 report.className='wehr-print-report';
 report.id='wehrPrintReport';
 report.innerHTML='<h1>Mitgliederverwaltung · Wehrleitung – Qualifikationsübersicht</h1>'+
 '<p class="wehr-report-date">Stand: '+safe(new Date().toLocaleString('de-DE'))+
 ' · Mitglieder: '+rows.length+'</p>'+
 wehrLegend()+(rows.length?groupedMemberTable(rows,true):'<p>Keine Einträge für die aktuellen Filter.</p>')+
 '<p class="wehr-print-note">Kürzel, Status und Gültigkeit sind in der Kachelübersicht nach den eingeblendeten Filtern dargestellt. Graue Kacheln sind abgelaufen; schraffierte sind bald fällig.</p>';
 const previous=document.getElementById('wehrPrintReport');
 if(previous)previous.remove();
 document.body.appendChild(report);
 const clean=()=>{report.remove();window.removeEventListener('afterprint',clean);};
 window.addEventListener('afterprint',clean,{once:true});
 try{window.print();}catch(error){clean();alert('Drucken nicht möglich: '+error.message);}
}
function inputField(label,id,value='',type='text',hint=''){return '<div class="field"><label for="'+id+'">'+safe(label)+'</label><input id="'+id+'" type="'+type+'" value="'+safe(value)+'">'+(hint?'<small class="sub">'+safe(hint)+'</small>':'')+'</div>';}
async function uploadCardPdf(id,file){
 if(file.size>5_000_000||file.size<8||!(file.name||'').toLowerCase().endsWith('.pdf'))
  throw Error('Nur PDF-Dateien bis 5 MB zulässig.');
 const bytes=await file.arrayBuffer(),sig=new TextDecoder().decode(bytes.slice(0,5));
 if(sig!=='%PDF-')throw Error('Die Datei ist kein gültiges PDF.');
 const response=await fetch('/api/fire/qualification-attachments/'+Number(id),{
  method:'PUT',headers:{'Authorization':headers().Authorization,'Content-Type':'application/pdf'},body:bytes,cache:'no-store'
 });
 if(response.status===401)throw Error('Nicht angemeldet');
 if(!response.ok){let detail='PDF konnte nicht gespeichert werden ('+response.status+').';
  try{const j=await response.json();detail=j.detail||j.message||detail;}catch{}
  throw Error(detail);
 }
 return response.json();
}
async function downloadCardPdf(id){
 try{
  const response=await fetch('/api/fire/qualification-attachments/'+Number(id),{headers:headers(),cache:'no-store'});
  if(!response.ok)throw Error('PDF konnte nicht geladen werden ('+response.status+').');
  const blob=await response.blob(),url=URL.createObjectURL(blob),link=document.createElement('a');
  link.href=url;link.download='qualifikation-'+Number(id)+'.pdf';link.click();
  setTimeout(()=>URL.revokeObjectURL(url),2000);
 }catch(error){notice(el('wehrDialogMsg'),error.message);}
}
const LICENSE_CLASSES=['AM','A1','A2','A','B','BE','B96','C1','C1E','C','CE','D1','D1E','D','DE','L','T'];
function editCard(id){
 if(!rights('fire.qualifications.write'))return;
 const c=id==null?null:cards.find(x=>x.id===id);
 modalTitle.textContent=c?'Qualifikationskachel bearbeiten':'Qualifikationskachel hinzufügen';
 modalBody.innerHTML='<div id="wehrDialogMsg"></div>'+
 (c?'<p><strong>'+safe(c.memberName)+'</strong> · '+safe(c.title)+'</p>':
 '<div class="field"><label>Mitglied</label><select id="wMember">'+memberOptions()+'</select></div>'+
 '<div class="field"><label>Qualifikationsart</label><select id="wType">'+typeOptions()+'</select></div>')+
 inputField('Ausstellungsdatum (optional)','wIssued',inputDate(c?.issuedOn),'date')+
 inputField('Gültig bis (optional)','wExpires',inputDate(c?.expiresOn),'date')+
 inputField('Nächster Termin (optional)','wDue',inputDate(c?.nextDueOn),'date')+
 inputField('Führerscheinnummer nur bei Führerschein: Referenz hinterlegen/ersetzen','wLicense','','text','Die Nummer wird lediglich als geschützter Vergleichswert gespeichert und nie wieder angezeigt.')+
 '<div class="field" id="wClassSection"><label>Führerscheinklassen</label><div class="wehr-license-classes">'+LICENSE_CLASSES.map(k=>'<label><input type="checkbox" data-license-class="'+k+'" '+(c?.licenseClasses?.includes(k)?'checked':'')+'> '+k+'</label>').join('')+'</div></div>'+
 '<div class="field"><label for="wPdf">PDF-Nachweis (optional, maximal 5 MB)</label><input id="wPdf" type="file" accept="application/pdf,.pdf"></div>'+ 
 (c?'<div class="quick"><button type="button" id="wViewPdf" class="btn secondary" '+(c.documentAttached?'':'disabled')+'>📄 PDF herunterladen</button><button type="button" id="wDeletePdf" class="btn secondary" '+(c.documentAttached?'':'disabled')+'>PDF entfernen</button></div>':'')+
 '<div class="quick"><button class="btn secondary" id="wehrCancel">Abbrechen</button>'+
 (c?'<button class="btn danger" id="wehrRemoveCard" type="button">Kachel löschen</button>':'')+
 '<button class="btn primary" id="wehrSave">Speichern</button></div>';
 const isLicense=()=>c?.code==='DRIVERS_LICENSE'||(!c&&types.find(t=>t.id===Number(el('wType')?.value))?.code==='DRIVERS_LICENSE');
 const updateLicense=()=>{const license=isLicense();el('wClassSection').hidden=!license;el('wLicense').closest('.field').hidden=!license;};
 if(el('wType'))el('wType').onchange=updateLicense;updateLicense();
 if(c){
  el('wViewPdf').onclick=()=>downloadCardPdf(c.id);
  el('wDeletePdf').onclick=async()=>{
   if(!confirm('PDF-Nachweis wirklich entfernen?'))return;
   try{await api('/api/fire/qualification-attachments/'+c.id,{method:'DELETE'});el('wViewPdf').disabled=true;el('wDeletePdf').disabled=true;}catch(e){notice(el('wehrDialogMsg'),e.message);}
  };
  el('wehrRemoveCard').onclick=async()=>{
   if(!confirm('Kachel „'+c.title+'“ bei '+c.memberName+' entfernen? Prüfhistorie und PDF bleiben archiviert.'))return;
   try{await api(BASE+'/cards/'+c.id,{method:'DELETE'});closeModal();await page();}catch(e){notice(el('wehrDialogMsg'),e.message);}
  };
 }
 el('wehrCancel').onclick=closeModal;el('wehrSave').onclick=async()=>{
  const typeId=c?c.typeId:Number(el('wType')?.value);
  const payload={typeId,issuedOn:el('wIssued').value||null,expiresOn:el('wExpires').value||null,
   nextDueOn:el('wDue').value||null,licenseNumber:isLicense()?el('wLicense').value||null:null,
   licenseClasses:isLicense()?[...modalBody.querySelectorAll('[data-license-class]:checked')].map(x=>x.dataset.licenseClass):null,active:true};
  if(id==null&&!typeId){notice(el('wehrDialogMsg'),'Qualifikationsart fehlt');return;}
  try{
   const assigned=await api(id==null?BASE+'/members/'+Number(el('wMember').value):BASE+'/cards/'+id,
    {method:id==null?'POST':'PUT',body:JSON.stringify(payload)});
   const file=el('wPdf')?.files?.[0];let pdfError=null;
   if(file){try{await uploadCardPdf(assigned.id,file);}catch(e){pdfError=e.message;}}
   el('wLicense').value='';closeModal();await page();
   if(pdfError)alert('Kachel gespeichert, aber der PDF-Nachweis konnte nicht hochgeladen werden: '+pdfError+'. Bitte die Kachel erneut öffnen und PDF hinzufügen.');
  }catch(error){el('wLicense').value='';notice(el('wehrDialogMsg'),error.message);}
 };
 modal.classList.remove('hidden');
}
async function configPage(){
 if(!rights('fire.qualifications.write'))return;
 await window.MenuDesigner?.loadIcons();
 setActive('wehr-config');
 content.innerHTML=header('⚙️ Qualifikationsbaukasten','Typen, Kacheln und Überwachungsfristen für Wehrleitung konfigurieren.',
 '<button class="btn secondary" id="backWehr">Zur Mitgliederverwaltung</button>')+
 '<div class="panel"><p>Qualifikationstypen werden geladen …</p></div>';
 try{types=await api(BASE+'/types');drawConfig();}catch(e){notice(content,e.message);}
}
function drawConfig(){
 content.innerHTML=header('⚙️ Qualifikationsbaukasten','Ausstellungsdatum optional. Bei überwachungspflichtigen Typen werden Vorwarnzeit und Prüfintervall verwendet.',
 '<button class="btn secondary" id="backWehr">Zur Mitgliederverwaltung</button>')+
 '<div class="panel"><div class="quick"><button class="btn secondary" id="defaultWehr">Standardkacheln hinzufügen</button><button class="btn primary" id="newWehrType">＋ Eigene Kachel</button></div>'+
 '<div class="table-wrap"><table class="table"><thead><tr><th>Kachel</th><th>Überwachung</th><th>Vorwarnzeit</th><th>Prüfintervall</th><th>Aktion</th></tr></thead><tbody>'+
 types.slice().sort((a,b)=>WEHR_COLUMNS.findIndex(c=>c.code===a.category)-WEHR_COLUMNS.findIndex(c=>c.code===b.category)||a.title.localeCompare(b.title,'de')).map(t=>'<tr><td><span class="wehr-type-icon">'+(window.MenuDesigner?.iconHtml(t.icon)||safe(t.icon))+'</span> '+safe(t.title)+'</td><td>+safe(WEHR_COLUMNS.find(c=>c.code===t.category)?.title||'Qualifikationen')+' · '+(t.tracked?'Ja':'Nein')+(t.sensitive?' · vertraulich':'')+'</td><td>'+t.warningDays+' Tage</td><td>'+t.intervalMonths+' Monate</td><td><button class="btn small secondary" data-type-id="'+t.id+'">Bearbeiten</button> <button class="btn small danger" data-delete-type="'+t.id+'">Löschen</button></td></tr>').join('')+
 '</tbody></table></div><div id="wehrConfigMsg"></div></div>';
 el('backWehr').onclick=()=>page();el('defaultWehr').onclick=async()=>{try{await api(BASE+'/types/defaults',{method:'POST'});types=await api(BASE+'/types');drawConfig();}catch(e){notice(el('wehrConfigMsg'),e.message);}};
 el('newWehrType').onclick=()=>editType(null);
 document.querySelectorAll('[data-type-id]').forEach(b=>b.onclick=()=>editType(Number(b.dataset.typeId)));
 document.querySelectorAll('[data-delete-type]').forEach(b=>b.onclick=async()=>{
  const type=types.find(t=>t.id===Number(b.dataset.deleteType));if(!type)return;
  if(!confirm('Kacheltyp „'+type.title+'“ dauerhaft löschen? Dies ist nur möglich, wenn er keinem Mitglied zugeordnet ist.'))return;
  try{await api(BASE+'/types/'+type.id,{method:'DELETE'});types=await api(BASE+'/types');drawConfig();}
  catch(error){notice(el('wehrConfigMsg'),error.message);}
 });
}
async function editType(id){
 await window.MenuDesigner?.loadIcons();
 const t=types.find(x=>x.id===id);
 modalTitle.textContent=t?'Kachel konfigurieren':'Eigene Kachel anlegen';
 modalBody.innerHTML='<div id="wehrTypeMsg"></div>'+
 (t?'<p><strong>'+safe(t.code)+'</strong></p>':inputField('Technische Kennung (A–Z, 0–9, _)','wCode'))+
 '<div class="field"><label>Kategorie</label><select id="wCategory"><option value="QUALIFICATION">Qualifikation</option><option value="CERTIFICATE_DOCUMENT">Zertifikat / Dokument</option><option value="SUITABILITY">Tauglichkeit</option></select></div>'+ 
 inputField('Name','wTitle',t?.title)+inputField('Kürzel (maximal 10 Zeichen)','wShort',t?.shortLabel||'','','Das Kürzel steht unter dem Symbol in der kleinen Kachel.')+
 '<div class="field"><label for="wIcon">Symbol aus der gemeinsamen Icon-Datenbank</label><div class="wehr-icon-picker"><span id="wIconPreview" class="wehr-type-icon">'+(window.MenuDesigner?.iconHtml(t?.icon||'📋')||safe(t?.icon||'📋'))+'</span><select id="wIcon">'+(window.MenuDesigner?.iconOptions(t?.icon||'📋')||'<option value="📋">📋</option>')+'</select></div><small>Eigene Icons kannst du unter Administration → Icon-Datenbank hochladen.</small></div>'+
 '<div class="field"><label><input id="wTracked" type="checkbox" '+(t?.tracked?'checked':'')+'> Überwachungspflichtig</label></div>'+
 '<div class="field"><label><input id="wSensitive" type="checkbox" '+(t?.sensitive?'checked':'')+'> Vertraulich (gesonderte Berechtigung)</label></div>'+
 inputField('Vorwarnzeit in Tagen (0–365)','wWarn',t?.warningDays??30,'number')+
 inputField('Wiederholung in Monaten (0–120)','wMonths',t?.intervalMonths??0,'number')+
 '<div class="quick"><button class="btn secondary" id="wehrTypeCancel">Abbrechen</button><button class="btn primary" id="wehrTypeSave">Speichern</button></div>';
 el('wCategory').value=t?.category||'QUALIFICATION';
 el('wIcon').onchange=()=>{el('wIconPreview').innerHTML=window.MenuDesigner?.iconHtml(el('wIcon').value)||safe(el('wIcon').value);};
 el('wehrTypeCancel').onclick=closeModal;el('wehrTypeSave').onclick=async()=>{
  const payload={code:t?.code||el('wCode').value.trim().toUpperCase(),title:el('wTitle').value,shortLabel:el('wShort').value.trim(),icon:el('wIcon').value,category:el('wCategory').value,
   tracked:el('wTracked').checked,sensitive:el('wSensitive').checked,warningDays:Number(el('wWarn').value),intervalMonths:Number(el('wMonths').value)};
  try{await api(BASE+'/types'+(t?'/'+t.id:''),{method:t?'PUT':'POST',body:JSON.stringify(payload)});
   closeModal();types=await api(BASE+'/types');drawConfig();
  }catch(e){notice(el('wehrTypeMsg'),e.message);}
 };modal.classList.remove('hidden');
}
async function drivingPage(){
 setActive('wehr-driving');
 content.innerHTML=header('🚘 Führerscheinkontrolle','Prüfliste, Fristen, manuelle Bestätigung und Prüfhistorie.')+'<div class="panel">Kontrollen werden geladen …</div>';
 try{[driving,checks]=await Promise.all([api(BASE+'/driving'),api(BASE+'/driving/checks')]);drawDriving();}
 catch(e){notice(content,e.message);}
}
function drawDriving(){
 const allow=rights('fire.drivingcheck.write');
 content.innerHTML=header('🚘 Führerscheinkontrolle','Automatischer positiver Status dokumentiert den OCR-Datenabgleich; alternativ kann die Wehrleitung die Prüfung manuell bestätigen.',
 '<button class="btn secondary" id="drivingExport">⬇ CSV exportieren</button><button class="btn secondary" id="drivingPrint">🖨 Drucken</button>')+
 '<div class="panel"><div class="toolbar"><input id="drivingSearch" placeholder="Mitglied suchen"><select id="drivingStatus"><option value="ALL">Alle</option><option value="DUE">Fällig</option><option value="OVERDUE">Überfällig</option><option value="VALID">Aktuell</option></select></div><div class="table-wrap"><table class="table"><thead><tr><th>Mitglied</th><th>Letzte Prüfung</th><th>Nächste Prüfung</th><th>Status</th><th>Aktion</th></tr></thead><tbody id="drivingRows"></tbody></table></div></div>'+
 '<div class="panel"><h3>Prüfprotokoll</h3><div class="table-wrap"><table class="table"><thead><tr><th>Mitglied</th><th>Datum</th><th>Prüfer / Benutzer-ID</th><th>Prüfweg</th><th>Ergebnis</th></tr></thead><tbody>'+
 checks.map(c=>'<tr><td>'+safe(driving.find(x=>x.memberId===c.memberId)?.memberName||'Mitglied #'+c.memberId)+'</td><td>'+safe(new Date(c.checkedAt).toLocaleString('de-DE'))+'</td><td>'+c.checkedByUserId+'</td><td>'+safe(c.method)+'</td><td>Positiv</td></tr>').join('')+
 '</tbody></table></div></div>';
 const render=()=>{
  const name=el('drivingSearch').value.toLocaleLowerCase('de'),status=el('drivingStatus').value;
  el('drivingRows').innerHTML=driving.filter(c=>(!name||c.memberName.toLocaleLowerCase('de').includes(name))&&(status==='ALL'||c.status===status)).map(c=>
   '<tr><td>'+safe(c.memberName)+'</td><td>'+date(c.lastCheckedOn)+'</td><td>'+date(c.nextDueOn)+'</td><td>'+badge(c)+'</td><td>'+
   (allow?'<button class="btn small success" data-manual="'+c.id+'">✓ Führerschein geprüft</button> <button class="btn small secondary" data-notify="'+c.id+'">✉ Prüfung senden</button>':'')+'</td></tr>').join('')||'<tr><td colspan="5" class="empty">Keine Einträge.</td></tr>';
  el('drivingRows').querySelectorAll('[data-manual]').forEach(b=>b.onclick=()=>manualCheck(Number(b.dataset.manual)));
  el('drivingRows').querySelectorAll('[data-notify]').forEach(b=>b.onclick=async()=>{try{const answer=await api(BASE+'/driving/'+Number(b.dataset.notify)+'/notify',{method:'POST'});alert(answer.message);}catch(e){alert(e.message);}});
 };
 el('drivingSearch').oninput=render;el('drivingStatus').onchange=render;render();
 el('drivingExport').onclick=exportDriving;el('drivingPrint').onclick=()=>window.print();
}
function manualCheck(id){
 if(!rights('fire.drivingcheck.write'))return;
 const entry=driving.find(c=>c.id===id);if(!entry)return;
 modalTitle.textContent='Führerscheinprüfung dokumentieren';
 modalBody.innerHTML='<p><strong>'+safe(entry.memberName)+'</strong></p>'+
 '<p>Bestätige nur, wenn du den Führerschein selbst geprüft hast. Der Vorgang wird mit deinem Benutzerkonto, Zeitstempel und dem Ergebnis „positiv“ protokolliert.</p>'+
 '<div id="drivingManualMsg"></div><div class="quick"><button class="btn secondary" id="drivingCancel">Abbrechen</button><button class="btn success" id="drivingConfirm">✓ Führerschein geprüft</button></div>';
 el('drivingCancel').onclick=closeModal;
 el('drivingConfirm').onclick=async()=>{
  try{await api(BASE+'/driving/'+id+'/manual',{method:'POST',body:JSON.stringify({confirmed:true})});closeModal();await drivingPage();}
  catch(e){notice(el('drivingManualMsg'),e.message);}
 };modal.classList.remove('hidden');
}
function exportDriving(){
 const escapeCsv=v=>'"'+String(v??'').replace(/"/g,'""')+'"';
 const lines=[['Mitglied','Letzte Prüfung','Nächster Termin','Status'],
   ...driving.map(c=>[c.memberName,c.lastCheckedOn,c.nextDueOn,statusMap[c.status]||c.status])];
 const audits=[['Prüfprotokoll','','',''],['Mitglied','Zeitpunkt','Prüfer-ID','Methode'],
 ...checks.map(c=>[driving.find(x=>x.memberId===c.memberId)?.memberName||c.memberId,c.checkedAt,c.checkedByUserId,c.method])];
 const data='\ufeff'+[...lines,['','','',''],...audits].map(row=>row.map(escapeCsv).join(';')).join('\r\n');
 const url=URL.createObjectURL(new Blob([data],{type:'text/csv;charset=utf-8'}));
 const link=document.createElement('a');link.href=url;link.download='fuehrerscheinkontrolle.csv';link.click();
 URL.revokeObjectURL(url);
}
async function myDrivingPage(){
 setActive('my-driving');
 content.innerHTML=header('🚘 Meine Führerscheinkontrolle','Auftrag am Smartphone erledigen: Foto bleibt ausschließlich im Arbeitsspeicher.')+
 '<div class="panel">Eigene Prüfung wird geladen …</div>';
 try{
  const list=await api(BASE+'/driving/my');
  content.innerHTML=header('🚘 Meine Führerscheinkontrolle','Automatischer positiver Abschluss nach vollständigem Namens- und Nummernabgleich; keine Dokumentfotos werden gespeichert.')+
  '<div class="panel">'+(list.length?list.map(c=>
   '<div class="wehr-my-check"><h3>'+safe(c.memberName)+'</h3>'+badge(c)+'<p>Nächster Termin: '+date(c.nextDueOn)+'</p>'+
   (!c.referencePresent?'<p class="message warning">Die Referenznummer wurde noch nicht von der Wehrleitung hinterlegt.</p>':
   '<label class="field">Führerschein fotografieren<input type="file" accept="image/*" capture="environment" data-scan-file="'+c.id+'"></label><div id="scanResult-'+c.id+'" aria-live="polite"></div>')+'</div>').join(''):'<p>Du hast keinen offenen Führerscheinauftrag.</p>')+'</div>';
  content.querySelectorAll('[data-scan-file]').forEach(f=>f.onchange=()=>scanPhoto(Number(f.dataset.scanFile),f));
 }catch(e){notice(content,e.message);}
}
async function scanPhoto(id,fileInput){
 const info=el('scanResult-'+id),file=fileInput.files?.[0];if(!info||!file)return;
 if(file.size>15*1024*1024){fileInput.value='';notice(info,'Foto ist zu groß (max. 15 MB).');return;}
 if(!('createImageBitmap' in window)){
  fileInput.value='';notice(info,'Dein Browser kann das Foto nicht verarbeiten. Bitte einen aktuellen Browser verwenden oder die Wehrleitung zur manuellen Kontrolle kontaktieren.');return;
 }
 notice(info,'Das Foto wird nur vorübergehend im Arbeitsspeicher verarbeitet. Die automatische Erkennung erfolgt auf dem FW-Cockpit-Server; es wird kein Bild gespeichert.','success');
 let bitmap=null,canvas=null,encoded=null;
 try{
  bitmap=await createImageBitmap(file);
  if(bitmap.width<400||bitmap.height<240)throw Error('Foto ist zu klein. Bitte neu aufnehmen.');
  const scale=Math.min(1,1800/Math.max(bitmap.width,bitmap.height));
  canvas=document.createElement('canvas');canvas.width=Math.round(bitmap.width*scale);canvas.height=Math.round(bitmap.height*scale);
  const ctx=canvas.getContext('2d',{alpha:false});if(!ctx)throw Error('Fotoverarbeitung nicht verfügbar.');
  ctx.drawImage(bitmap,0,0,canvas.width,canvas.height);
  encoded=canvas.toDataURL('image/jpeg',.88);
  if(encoded.length>7_500_000)throw Error('Das komprimierte Foto ist zu groß. Bitte erneut aufnehmen.');
  bitmap.close();bitmap=null;canvas.width=0;canvas.height=0;canvas=null;
  fileInput.value='';
  const answer=await api(BASE+'/driving/my/'+id+'/scan',{method:'POST',body:JSON.stringify({imageData:encoded})});
  info.innerHTML='<div class="message success">✓ Der automatische Abgleich von Name und Führerscheinnummer wurde positiv dokumentiert. Kein Dokumentfoto wurde gespeichert.</div>';
  return answer;
 }catch(error){notice(info,error.message||'Foto konnte nicht geprüft werden. Bitte erneut versuchen oder Wehrleitung kontaktieren.');}
 finally{bitmap?.close?.();if(canvas){canvas.width=0;canvas.height=0;}fileInput.value='';encoded=null;}
}
async function dashboard(){
 if(!rights('fire.qualifications.read'))return;
 try{
  const result=await api(BASE+'/dashboard');if(!result)return; // only assigned WEHRLEITER
  const grid=document.getElementById('dashboardWidgetGrid');if(!grid)return;
  const node=document.createElement('div');node.className='panel wehr-dashboard';
  node.innerHTML='<h3>🚒 Wehrleitung · Prüfungen</h3><p>'+result.overdue+' überfällig · '+result.due+' bald fällig</p>'+
   '<button class="btn secondary" type="button" id="wehrDashboardOpen">Qualifikationen prüfen</button>';
  grid.prepend(node);el('wehrDashboardOpen').onclick=()=>page();
 }catch(error){console.warn('Wehrleiter-Fälligkeiten konnten nicht geladen werden',error);}
}
window.WehrleiterUI={page,configPage,drivingPage,myDrivingPage,dashboard};
})();
