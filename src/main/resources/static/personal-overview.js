/* Self-only member dashboard. All personal records originate from /api/me/overview. */
(function(root){
 'use strict';
 const safe=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const date=v=>v?new Date((String(v).length===10?v+'T12:00:00':v)).toLocaleDateString('de-DE'):'Nicht hinterlegt';
 const currency=v=>Number(v||0).toLocaleString('de-DE',{style:'currency',currency:'EUR'});
 const cat={QUALIFICATION:'Qualifikation',CERTIFICATE_DOCUMENT:'Zertifikat / Dokument',SUITABILITY:'Tauglichkeit'};
 const statuses={VALID:'Aktuell',DUE:'Bald fällig',OVERDUE:'Überfällig',UNSCHEDULED:'Ohne Frist'};
 const response={YES:'Zugesagt',NO:'Abgesagt',PENDING:'Offen'};
 const receipt={COMPLETED:'Abgeschlossen',CANCELLED:'Storniert',NEW:'Offen',CONFIRMED:'Bestätigt',PREPARING:'In Bearbeitung',READY:'Abholbereit'};
 function cell(label,value){
  return '<div class="self-detail"><small>'+safe(label)+'</small><strong>'+safe(value||'Nicht hinterlegt')+'</strong></div>';
 }
 function target(page){if(typeof navigate==='function')navigate(page)}
 const action=(page,label)=>'<button type="button" class="btn secondary" data-self-page="'+safe(page)+'">'+safe(label)+'</button>';
 function section(title,body,side=''){
  return '<section class="self-panel"><div class="self-panel-heading"><h2>'+safe(title)+'</h2>'+side+'</div>'+body+'</section>';
 }
 function tabs(selected){return '<nav class="quick" aria-label="Meine Daten und Aufgaben" style="margin:14px 0;flex-wrap:wrap">'+
  '<button type="button" class="btn '+(selected==='data'?'primary':'secondary')+'" onclick="PersonalOverview.page()" aria-current="'+(selected==='data'?'page':'false')+'">Meine Daten / Übersicht</button>'+
  '<button type="button" class="btn '+(selected==='tasks'?'primary':'secondary')+'" onclick="UnifiedTasks.page(true)" aria-current="'+(selected==='tasks'?'page':'false')+'">Meine Aufgaben</button></nav>';}
 function wire(){
  content.querySelectorAll('[data-self-page]').forEach(b=>{
   b.onclick=()=>target(b.dataset.selfPage);
  });
  document.getElementById('self-refresh')?.addEventListener('click',page);
 }
 async function page(){
  root.ffhPersonalTab='data';
  setActive('my-profile');closeMenu();closeProfileMenu();
  content.innerHTML=tabs('data')+'<div class="self-shell"><div class="self-wait">Meine Daten werden geladen …</div></div>';
  try{
   const data=await api('/api/me/overview');
   if(root.currentPage!=='my-profile'||root.ffhPersonalTab!=='data')return;
   if(!data.linked){
    content.innerHTML=tabs('data')+'<div class="self-shell">'+section('Meine Daten',
     '<p>Deinem Benutzerkonto ist noch kein Mitglied zugeordnet. Bitte lasse die Zuordnung in der Mitgliederverwaltung von der Administration prüfen.</p>')+'</div>';
    return;
   }
   const p=data.member||{},q=data.qualifications||[],events=data.appointments||[],answers=data.responses||[],orders=data.purchases||[];
   const due=q.filter(x=>x.status==='DUE'||x.status==='OVERDUE').length;
   const unanswered=events.filter(x=>x.registrationRequired&&!x.response).length;
   const intro='<header class="self-hero"><div><span>MEIN FW-COCKPIT</span><h1>'+safe(p.name||p.username)+'</h1>'+
    '<p>Deine persönlichen Daten, Dienste, Qualifikationen und dein Getränkekonto auf einen Blick.</p></div>'+
    '<button class="btn secondary" id="self-refresh" type="button">↻ Aktualisieren</button></header>'+
    '<div class="self-kpis"><div><small>Getränkeguthaben</small><strong>'+currency(data.balance)+'</strong></div>'+
    '<div><small>Anstehende Termine</small><strong>'+events.length+'</strong></div>'+
    '<div><small>Offene Rückmeldungen</small><strong>'+unanswered+'</strong></div>'+
    '<div><small>Qualifikationen mit Frist</small><strong>'+due+'</strong></div></div>';
   const personal=section('Persönliche Mitgliedsdaten','<div class="self-details">'+
    cell('Name',p.name)+cell('Benutzername',p.username)+cell('E-Mail',p.email)+
    cell('Telefon',p.phone)+cell('Anschrift',p.address)+cell('Mitglied seit',p.joinedOn?date(p.joinedOn):'')+
    cell('Geburtsdatum',p.birthDate?date(p.birthDate):'')+
    '</div><p class="self-muted">Änderungen an Stammdaten erfolgen über die zuständige Mitgliederverwaltung.</p>');
   const qualifications=section('Meine Qualifikationen',
    q.length?'<div class="self-list">'+q.map(x=>{
     const status=x.status||'VALID';
     return '<div class="self-line"><div><strong>'+safe(x.title)+'</strong><small>'+safe(cat[x.category]||x.category||'Qualifikation')+
      (x.nextDueOn?' · Nächste Frist: '+safe(date(x.nextDueOn)):'')+
      '</small></div><span class="self-status '+safe(status.toLowerCase())+'">'+safe(statuses[status]||status)+'</span></div>';
    }).join('')+'</div>':'<p class="self-empty">Noch keine Qualifikationen hinterlegt.</p>',
    action('my-driving','Meine Führerscheinkontrolle'));
   const appointments=section('Nächste Dienste und Termine',
    events.length?'<div class="self-list">'+events.map(x=>
     '<div class="self-line"><div><strong>'+safe(x.title)+'</strong><small>'+safe(date(x.startAt||x.date))+
     ' · '+safe(x.type||'Termin')+'</small></div>'+
     (x.registrationRequired?'<span class="self-status '+(x.response?'valid':'due')+'">'+
       safe(response[x.response]||x.response||'Rückmeldung offen')+'</span>':'')+'</div>'
    ).join('')+'</div>':'<p class="self-empty">Keine bevorstehenden Termine vorhanden.</p>',
    action('calendar','Kalender öffnen'));
   const duties=section('Meine Dienst-Rückmeldungen',
    answers.length?'<div class="self-list">'+answers.slice(0,10).map(x=>
     '<div class="self-line"><div><strong>'+safe(date(x.date))+'</strong><small>Gespeicherte Dienst-Rückmeldung</small></div>'+
     '<span class="self-status '+(x.status==='YES'?'valid':x.status==='NO'?'overdue':'due')+'">'+
     safe(response[x.status]||x.status)+'</span></div>').join('')+'</div>'+
     '<p class="self-muted">Dies sind Zu- und Absagen, kein bestätigter Anwesenheits- oder Stunden-Nachweis.</p>'
     :'<p class="self-empty">Keine Dienst-Rückmeldungen aus den letzten zwölf Monaten vorhanden.</p>',
     action('service-reminders','Offene Rückmeldungen'));
   const account=section('Mein Getränkekonto',
    '<div class="self-balance"><small>Aktuelles Guthaben</small><strong>'+currency(data.balance)+'</strong></div>'+
    (orders.length?'<h3>Letzte Bestellungen</h3><div class="self-list">'+orders.map(x=>
      '<div class="self-line"><div><strong>'+safe(date(x.date))+'</strong><small>'+safe(receipt[x.status]||x.status)+'</small></div>'+
      '<strong>'+currency(x.total)+'</strong></div>').join('')+'</div>':
     '<p class="self-empty">Noch keine Bestellungen gefunden.</p>')+
    '<p class="self-muted">Die Bestellübersicht zeigt Buchungen, nicht sämtliche Guthabenaufladungen.</p>',
    action('theke','Zur Kasse'));
   const warnings=(data.warnings||[]).map(x=>'<p class="self-warning">'+safe(x)+'</p>').join('');
   content.innerHTML=tabs('data')+'<div class="self-shell">'+intro+warnings+'<div class="self-grid">'+
     personal+qualifications+appointments+duties+account+
     section('Meine Aufgaben','<p>Offene Vereinsaufgaben, Geräteprüfungen und Dienst-Rückmeldungen findest du in deiner Aufgabenübersicht.</p>'+
       '<button type="button" class="btn secondary" onclick="UnifiedTasks.page(true)">Meine Aufgaben öffnen</button>')+'</div></div>';
   wire();
  }catch(err){
   if(root.currentPage==='my-profile'&&root.ffhPersonalTab==='data')
    content.innerHTML=tabs('data')+'<div class="self-shell"><div class="self-panel"><h1>Meine Daten</h1>'+
      '<p class="self-warning">Die persönlichen Daten konnten nicht geladen werden: '+safe(err.message)+'</p>'+
      '<button type="button" class="btn secondary" onclick="PersonalOverview.page()">Erneut versuchen</button></div></div>';
  }
 }
 root.PersonalOverview=Object.freeze({page,tabs});
})(window);
