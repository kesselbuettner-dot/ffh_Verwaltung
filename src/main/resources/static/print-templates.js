/* Shared FW-Cockpit organisation print layouts. Reports contain no interactive app chrome. */
(function(root){
 'use strict';
 const FALLBACK='/icons/fw-cockpit-brand.svg';
 const html=value=>String(value==null?'':value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const berlinTime=x=>new Date(x||Date.now()).toLocaleString('de-DE',{timeZone:'Europe/Berlin',dateStyle:'short',timeStyle:'short'});
 function settings(){
  return (typeof appSettings!=='undefined'&&appSettings)||{};
 }
 function reportName(s){return String(s.organizationName||s.appName||'FFH Verwaltung').trim();}
 function logoSrc(s){return s.logoAvailable?'/api/settings/logo?print='+Date.now():FALLBACK;}
 function address(s){return [s.street,[s.postalCode,s.city].filter(Boolean).join(' ')].filter(Boolean).join(' · ');}
 function contact(s){return [s.contactPhone?'Tel.: '+s.contactPhone:'',s.contactEmail||''].filter(Boolean).join(' · ');}
 function waitLogo(img){
  return new Promise(resolve=>{
   if(!img)return resolve();
   const finish=()=>resolve();
   img.onload=finish;
   img.onerror=()=>{if(!img.dataset.fallback){img.dataset.fallback='1';img.onerror=finish;img.src=FALLBACK;}else finish();};
   if(img.complete){if(img.naturalWidth)finish();else img.onerror();}
  });
 }
 function render(opts,s=settings()){
  const orientation=opts.orientation==='landscape'?'landscape':'portrait';
  const title=String(opts.title||'Druckbericht');
  const organizationName=reportName(s);
  const report=document.createElement('section');
  report.className='ffh-print-root ffh-'+orientation;
  report.setAttribute('aria-label',title);
  const detail=[address(s),contact(s),s.legalRepresentative?'Vertretung: '+s.legalRepresentative:''].filter(Boolean);
  report.innerHTML='<header class="ffh-print-header"><img class="ffh-print-logo" alt="Logo der Organisation"><div class="ffh-print-identity"><strong>'+
   html(organizationName)+'</strong>'+detail.map(t=>'<span>'+html(t)+'</span>').join('')+'</div></header>'+
   '<div class="ffh-print-body"><div class="ffh-print-title"><h1>'+html(title)+'</h1>'+
   (opts.subtitle?'<p>'+html(opts.subtitle)+'</p>':'')+'</div>'+String(opts.body||'')+'</div>'+
   '<footer class="ffh-print-footer"><span>'+html(organizationName)+' · '+html(title)+'</span>'+
   '<span>Erstellt: '+html(berlinTime())+(opts.footerNote?' · '+html(opts.footerNote):'')+'</span></footer>';
  report.querySelector('.ffh-print-logo').src=logoSrc(s);
  return report;
 }
 async function print(opts){
  if(!opts||!['landscape','portrait'].includes(opts.orientation||'portrait'))throw Error('Ungültiges Druckformat.');
  // The admin gallery contains a nested preview; only an active body-level print document blocks a new print.
  if(document.querySelector('body > .ffh-print-root'))throw Error('Ein Druckbericht ist bereits geöffnet.');
  const node=render(opts);
  document.body.append(node);
  await waitLogo(node.querySelector('.ffh-print-logo'));
  const priorTitle=document.title;
  document.title=reportName(settings());
  const cleanup=()=>{document.title=priorTitle;node.remove();root.removeEventListener('afterprint',cleanup);};
  root.addEventListener('afterprint',cleanup,{once:true});
  try{root.print();}catch(e){cleanup();throw e;}
 }
 function preview(orientation){
  const options={orientation,title:'Druckvorlage – '+(orientation==='landscape'?'Querformat':'Hochformat'),
   subtitle:'Musterbericht zur Prüfung des gemeinsamen Briefkopfs und der Fußzeile',
   body:'<table class="ffh-print-table"><thead><tr><th>Bezeichnung</th><th>Bemerkung</th></tr></thead><tbody><tr><td>Beispielzeile</td><td>Alle Druckberichte verwenden diese Vorlage.</td></tr></tbody></table>'};
  return print(options);
 }
 function table(headings,rows,empty='Keine Einträge vorhanden.'){
  return '<table class="ffh-print-table"><thead><tr>'+headings.map(x=>'<th>'+html(x)+'</th>').join('')+
   '</tr></thead><tbody>'+(rows.length?rows.map(cells=>'<tr>'+cells.map(x=>'<td>'+html(x==null?'–':x)+'</td>').join('')+'</tr>').join(''):
   '<tr><td colspan="'+headings.length+'">'+html(empty)+'</td></tr>')+'</tbody></table>';
 }
 root.FWPrintTemplates={print,preview,render,table,html,berlinTime,reportName};
})(window);
