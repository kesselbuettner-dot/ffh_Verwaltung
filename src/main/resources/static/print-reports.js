/* Print actions for members, shopping, device inventory and annual device inspection audits. */
(function(root){
 'use strict';
 const t=()=>root.FWPrintTemplates;
 const date=x=>x?new Date(x+'T12:00:00').toLocaleDateString('de-DE'):'–';
 const labelLocation=d=>d.compartmentName?[d.vehicleName,d.compartmentName].filter(Boolean).join(' / '):(d.vehicleName||d.location||'Ohne Standort');
 const text=x=>String(x==null?'–':x);
 const byLocation=(a,b)=>String(a.location||'').localeCompare(String(b.location||''),'de')||
  String(a.name||a.deviceName||'').localeCompare(String(b.name||b.deviceName||''),'de');
 const yearOptions=(all=false)=>{
  const year=Number(new Intl.DateTimeFormat('en',{year:'numeric',timeZone:'Europe/Berlin'}).format(new Date()));
  let html=all?'<option value="all">Alle Jahre</option>':'';
  for(let y=year+1;y>=2000;y--)html+='<option value="'+y+'"'+(y===year?' selected':'')+'>'+y+'</option>';
  return html;
 };
 async function shopping(){
  const data=await api('/api/inventory/shopping-list');
  const rows=data.sort((a,b)=>a.name.localeCompare(b.name,'de')).map(d=>
   [d.name,d.stock,d.warningThreshold,d.suggestedQuantity,
    d.lastPurchasePrice==null?'–':money(d.lastPurchasePrice),
    d.marktguruOffer?.price==null?'–':money(d.marktguruOffer.price)]);
  return t().print({orientation:'portrait',title:'Einkaufsliste',subtitle:data.length+' Artikel unterhalb der Warnschwelle',
   body:t().table(['Artikel','Bestand','Warnwert','Einkauf','Letzter EK','Angebot'],rows)});
 }
 async function memberOverview(){
  const q=(document.getElementById('memberSearch')?.value||'').toLocaleLowerCase('de');
  const list=(await api('/api/members')).filter(m=>m.name.toLocaleLowerCase('de').includes(q)).sort((a,b)=>a.name.localeCompare(b.name,'de'));
  return t().print({orientation:'portrait',title:'Mitgliederliste',subtitle:list.length+' Mitglieder'+(q?' · aktuelle Suchauswahl':''),
    body:t().table(['Mitglied','Rolle','Kontostand'],list.map(m=>[m.name,roleLabels[m.role]||m.role||'–',money(m.balance)]))});
 }
 async function membersAdmin(){
  const q=(document.getElementById('memberSearch')?.value||'').toLocaleLowerCase('de');
  const list=(await api('/api/members')).filter(m=>m.name.toLocaleLowerCase('de').includes(q)).sort((a,b)=>a.name.localeCompare(b.name,'de'));
  return t().print({orientation:'portrait',title:'Mitgliederliste',subtitle:list.length+' Mitglieder · Mitgliederstammdaten',
    body:t().table(['Mitglied','E-Mail','Telefon','Status'],list.map(m=>[m.name,m.email||'–',m.phone||'–',m.active?'Aktiv':'Inaktiv']))});
 }
 function deviceFilters(){
  return {year:document.getElementById('deviceReportYear')?.value||'all',
    location:document.getElementById('deviceReportLocation')?.value||''};
 }
 function deviceYear(value){return String(value||'').slice(0,4);}
 async function devices(){
  const {year,location}=deviceFilters();
  const list=(await api('/api/devices')).map(d=>({...d,printLocation:labelLocation(d)}))
   .filter(d=>(!location||d.printLocation===location)&&
    (year==='all'||deviceYear(d.nextInspectionDate)===year))
   .sort((a,b)=>byLocation({location:a.printLocation,name:a.name},{location:b.printLocation,name:b.name}));
  const subtitle='Standort: '+(location||'Alle')+' · Nächste Prüfung: '+(year==='all'?'alle Jahre':year)+' · '+list.length+' Geräte';
  return t().print({orientation:'landscape',title:'Geräteliste',subtitle,
   body:t().table(['Standort','Gerät','Inventar','Kategorie','Letzte Prüfung','Nächste Prüfung','Zustand'],
    list.map(d=>[d.printLocation,d.name,d.inventoryNumber,d.category,date(d.lastInspectionDate),
     date(d.nextInspectionDate),d.operationalStatus||'OK']))});
 }
 function inspectFilters(){
  return {year:document.getElementById('inspectionReportYear')?.value||
    String(new Intl.DateTimeFormat('en',{year:'numeric',timeZone:'Europe/Berlin'}).format(new Date())),
   location:document.getElementById('inspectionReportLocation')?.value||''};
 }
 async function refreshInspectionLocations(){
  const select=document.getElementById('inspectionReportLocation');if(!select)return;
  const year=inspectFilters().year,old=select.value;
  const data=await api('/api/devices/print-report?year='+encodeURIComponent(year));
  select.replaceChildren(new Option('Alle Standorte',''),...data.locations.map(x=>new Option(x,x)));
  if(data.locations.includes(old))select.value=old;
 }
 async function inspectionHistory(){
  const {year,location}=inspectFilters();
  const report=await api('/api/devices/print-report?year='+encodeURIComponent(year)+'&location='+encodeURIComponent(location));
  const lines=report.entries.sort(byLocation);
  return t().print({orientation:'portrait',title:'Geräteprüfprotokoll – Jahresbericht '+year,
   subtitle:'Prüfjahr: '+year+' · Standort: '+(location||'Alle')+' · '+lines.length+' dokumentierte Prüfungen',
   body:t().table(['Standort','Gerät / Inventar','Prüfdatum','Ergebnis','Prüfer','Nachweis'],
     lines.map(i=>[i.location,i.deviceName+' / '+(i.inventoryNumber||'–'),date(i.inspectionDate),
      i.result||'–',i.inspector||'–',i.signed?'Unterschrieben':'Ohne Unterschrift']))+
      '<p class="ffh-print-note">Die Übersicht enthält dokumentierte Einzel- und Sammelprüfungen im ausgewählten Kalenderjahr. Einzelunterschriften sind im jeweiligen Originalprotokoll hinterlegt.</p>'});
 }
 async function checklist(){
  const {year,location}=inspectFilters();
  const list=(await api('/api/devices')).filter(d=>d.inspectionRequired)
    .map(d=>({...d,printLocation:labelLocation(d)}))
    .filter(d=>(!location||d.printLocation===location)&&
      (year==='all'||!d.nextInspectionDate||deviceYear(d.nextInspectionDate)===year))
    .sort((a,b)=>byLocation({location:a.printLocation,name:a.name},{location:b.printLocation,name:b.name}));
  return t().print({orientation:'portrait',title:'Prüfliste Geräte',
   subtitle:'Fälligkeitsjahr: '+year+' · Standort: '+(location||'Alle')+' · '+list.length+' prüfpflichtige Geräte',
   body:t().table(['Standort','Gerät / Inventar','Fällig','Geprüft','Bemerkung'],
    list.map(d=>[d.printLocation,d.name+' / '+(d.inventoryNumber||'–'),date(d.nextInspectionDate),'☐','________________']))});
 }
 async function signedSession(report){
  const items=[...(report.items||[])].sort(byLocation);
  const textValue=x=>text(x).replace(/^null$/,'–');
  return t().print({orientation:'portrait',title:'Geräteprüfprotokoll · '+report.title,
   subtitle:'Prüftermin: '+date(report.date)+' · Prüfjahr: '+String(report.date).slice(0,4)+
     ' · Prüfer: '+report.inspector+' · '+items.length+' Geräte',
   body:t().table(['Standort','Gerät / Inventar','Ergebnis','Bemerkung','Nächste Prüfung'],
     items.map(i=>[i.location,i.deviceName+' / '+(i.inventoryNumber||'–'),i.result,textValue(i.note),date(i.nextInspectionDate)]))+
     '<p class="ffh-print-note">Abgeschlossen: '+t().html(t().berlinTime(report.signedAt))+' · Verantwortlicher Prüfer: '+t().html(report.inspector)+'</p>'+
     (report.signatureData?.startsWith('data:image/png;base64,')?
       '<div class="ffh-print-signature"><strong>Unterschrift</strong><br><img alt="Prüferunterschrift" src="'+report.signatureData+'"></div>':'')});
 }
 function invoke(method){
  return async(...args)=>{try{await method(...args);}catch(e){alert('Druckbericht konnte nicht erstellt werden: '+(e.message||e));}};
 }
 root.FWPrintReports={shopping:invoke(shopping),memberOverview:invoke(memberOverview),membersAdmin:invoke(membersAdmin),
   devices:invoke(devices),inspectionHistory:invoke(inspectionHistory),checklist:invoke(checklist),
   signedSession:invoke(signedSession),refreshInspectionLocations,yearOptions,labelLocation};
})(window);
