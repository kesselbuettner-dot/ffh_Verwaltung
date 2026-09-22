const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const js=fs.readFileSync('src/main/resources/static/wehrleiter.js','utf8');
const css=fs.readFileSync('src/main/resources/static/wehrleiter.css','utf8');
const java=fs.readFileSync('src/main/java/de/bierverein/api/FireQualificationController.java','utf8');
new vm.Script(js,{filename:'wehrleiter.js'});
const named=["QUALIFICATION","CERTIFICATE_DOCUMENT","SUITABILITY"];
named.forEach(k=>assert(js.includes(k),'Missing column or category '+k));
assert(js.includes('id="wehrPrint"')&&js.includes('printWehrReport'),'Print action missing');
assert(js.includes('filteredMemberRows()')&&js.includes('groupedMemberTable(rows,true)'),'Print data must respect current filters');
assert(js.includes('wehrLegend()')&&js.includes('wehr-print-report'),'Print report must include legend');
assert(js.includes('map(c=>cardHtml(c,print))'),'Print must use noninteractive tiles');
assert(java.includes('"MEDICAL_DUE".equals(t.code)')&&java.includes('"SUITABILITY"'),'Existing medical exams must render as suitability');
assert(java.includes('Set.of("QUALIFICATION","CERTIFICATE_DOCUMENT","SUITABILITY")'),'Type category validation missing');
for(const snippet of ['@page{size:A4 landscape','print-color-adjust:exact','body > *:not(#wehrPrintReport)','display:table-header-group','.wehr-overview-table']){
 assert(css.includes(snippet),'Print/table CSS missing '+snippet);
}
// Test the real grouping and sorting implementation with mock data.
const start=js.indexOf('const WEHR_COLUMNS=['),end=js.indexOf('function drawPage(){',start);
assert(start>0&&end>start,'Grouping functions absent');
const context={nameFilter:'',visibleFilter:'ALL',typeFilter:'',categoryFilter:'ALL',sortMode:'name-asc',
 people:[{id:1,name:'Erika Muster'},{id:2,name:'Bernd Beispiel'}],
 cards:[{id:1,memberId:1,title:'Atemschutz',code:'AT',category:'SUITABILITY',status:'DUE',shortLabel:'AT',nextDueOn:'2026-10-01'},
        {id:2,memberId:1,title:'Maschinist',code:'MA',category:'QUALIFICATION',status:'VALID',shortLabel:'MA'},
        {id:3,memberId:1,title:'Führerschein',code:'FS',category:'CERTIFICATE_DOCUMENT',status:'OVERDUE',shortLabel:'FS'}],
 Intl,Date,Math,String,Number,console,
 safe:value=>String(value),avatarHtml:p=>'<i>'+p.name+'</i>',cardHtml:(c,p)=>'<span data-print="'+!!p+'">'+c.shortLabel+'</span>'};
vm.runInNewContext(js.slice(start,end),context);
const rows=context.filteredMemberRows();
assert.equal(rows.length,2);
const view=context.groupedMemberTable(rows);
assert(view.indexOf('Qualifikationen')<view.indexOf('Zertifikate / Dokumente'));
assert(view.indexOf('Zertifikate / Dokumente')<view.indexOf('Tauglichkeiten'));
const memberRow=view.slice(view.indexOf('Erika Muster'),view.indexOf('</tr>',view.indexOf('Erika Muster')));
assert(memberRow.indexOf('MA')<memberRow.indexOf('FS')&&memberRow.indexOf('FS')<memberRow.indexOf('AT'),'Three categories must remain in separate ordered columns');
assert(context.groupedMemberTable(rows,true).includes('data-print="true"'),'Print uses noninteractive tiles');
context.categoryFilter='SUITABILITY';assert.equal(context.filteredMemberRows().length,1);
context.categoryFilter='ALL';context.visibleFilter='DUE';assert.equal(context.filteredMemberRows().length,1);
console.log('PASS three categories, preserved medical tiles, filter-aware grouped table, printable noninteractive status tiles, CSS color and legend');
