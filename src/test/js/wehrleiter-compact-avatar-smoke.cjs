const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const js=fs.readFileSync('src/main/resources/static/wehrleiter.js','utf8');
const css=fs.readFileSync('src/main/resources/static/wehrleiter.css','utf8');
const ui=fs.readFileSync('src/main/resources/static/ui-theme.css','utf8');
const html=fs.readFileSync('src/main/resources/static/index.html','utf8');
const types=fs.readFileSync('src/main/java/de/bierverein/api/FireQualificationType.java','utf8');
const profile=fs.readFileSync('src/main/java/de/bierverein/api/AdminMemberExtraController.java','utf8');
const docs=fs.readFileSync('docs/UI_KOMPONENTEN.md','utf8');
new vm.Script(js,{filename:'wehrleiter.js'});
for(const [n,m] of [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].entries())
  new vm.Script(m[1],{filename:'index-inline-'+n+'.js'});
assert(js.includes('function avatarHtml(person)'),'Avatar renderer absent');
assert(js.includes("card.shortLabel||card.code"),'Kachel does not use type abbreviation');
assert(js.includes('data-edit-card'),'Compact tile lost edit action');
assert(js.includes('function drawRows()'),'Member list absent');
for(const q of ['id="wehrSearch"','id="wehrStatus"','id="wehrType"','id="wehrSort"','id="wehrReset"'])
 assert(js.includes(q),'Filter/sort control missing: '+q);
for(const q of ['name-asc','name-desc','due-asc','count-desc'])assert(js.includes(q),'Sort option missing '+q);
for(const q of ['.wehr-tile.is-DUE','repeating-linear-gradient','.wehr-tile.is-OVERDUE','background:#818995','.wehr-member-row'])
 assert(css.includes(q),'Missing visual state '+q);
for(const q of ['.ui-filterbar','.ui-filterfield','.ui-filter-actions','@media(max-width:600px)'])
 assert(ui.includes(q),'Missing reusable responsive filter rule '+q);
assert(html.includes('memberAvatarFile')&&html.includes('compressMemberAvatar'),'Admin member avatar picker missing');
assert(profile.includes('@PutMapping("/{id}/avatar")')&&profile.includes("hasRole('ADMIN')"),
 'Member avatar upload must be admin-only');
assert(types.includes('shortLabel'),'Qualification abbreviation field absent');
assert(docs.includes('## Verbindliche Such-/Filter-/Sortierzeile'),'Shared layout documentation missing');
// Evaluate the real tile template rather than merely relying on static checks.
const context={window:{},document:{},esc:v=>String(v),hasPermission:()=>true,
  setActive:()=>{},content:{},console};
const injected=js.replace('window.WehrleiterUI={page,configPage,drivingPage,myDrivingPage,dashboard};',
'window.WehrleiterUI={page,configPage,drivingPage,myDrivingPage,dashboard,__test:{cardHtml,avatarHtml}};');
vm.runInNewContext(injected,context,{filename:'wehrleiter-test.js'});
const card={id:5,code:'DRIVERS_LICENSE',title:'Führerschein',shortLabel:'FS',status:'DUE',
 issuedOn:'2025-09-01',nextDueOn:'2026-09-22'};
const tile=context.window.WehrleiterUI.__test.cardHtml(card);
assert(tile.includes('>FS</span>'),'Abbreviation is not displayed');
assert(!tile.includes('>Führerschein</strong>'),'Full name still visible in compact tile');
assert(tile.includes('is-DUE'),'Due pattern class missing');
assert(tile.includes('Führerschein')&&tile.includes('data-edit-card="5"'),'Title/details or edit lost');
assert(context.window.WehrleiterUI.__test.cardHtml({...card,status:'OVERDUE'}).includes('is-OVERDUE'),
 'Overdue gray state missing');
const noAvatar=context.window.WehrleiterUI.__test.avatarHtml({name:'Max Muster',avatar:null});
assert(noAvatar.includes('MM'),'Avatar initials absent');
console.log('PASS compact abbreviation tiles, patterned due/gray expired, photo/initial avatars, accessible details, filters/sort and shared UI rules');
