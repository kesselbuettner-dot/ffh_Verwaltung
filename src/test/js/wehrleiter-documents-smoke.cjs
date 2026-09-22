const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const root='src/main/resources/static/',js=fs.readFileSync(root+'wehrleiter.js','utf8');
const html=fs.readFileSync(root+'index.html','utf8'),css=fs.readFileSync(root+'ui-theme.css','utf8');
new vm.Script(js,{filename:'wehrleiter.js'});
for(const [i,script] of [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].entries())
 new vm.Script(script[1],{filename:'index-inline-'+i+'.js'});
for(const key of ["id=\"wehrCategory\"","CERTIFICATE_DOCUMENT","categoryFilter","data-license-class","wRemoveCard","wehrRemoveCard","wViewPdf","wPdf","data-delete-type","wCategory","uploadCardPdf","downloadCardPdf","licenseClasses"])
 assert(js.includes(key),"Missing category/PDF/deletion/driver class behavior: "+key);
assert(js.includes("headers().Authorization,'Content-Type':'application/pdf'"),"PDF upload MUST not use JSON content type");
assert(html.includes('id="vfLicenseClass"')&&html.includes('requiredLicenseClass:vfLicenseClass.value'),"Vehicle license dropdown or update missing");
assert(css.includes(':has(#wehrCategory)'),"Five-column responsive toolbar missing");
const types=fs.readFileSync('src/main/java/de/bierverein/api/FireQualificationController.java','utf8');
for(const needle of ['@DeleteMapping("/cards/{id}")','q.active=false','@DeleteMapping("/types/{id}")',
 '"CERTIFICATE_DOCUMENT"','licenseClasses','LICENSE_CLASSES'])assert(types.includes(needle),"Missing backend: "+needle);
const vehicles=fs.readFileSync('src/main/java/de/bierverein/api/FireVehicleController.java','utf8');
assert(vehicles.includes('requiredLicenseClass')&&vehicles.includes('LICENSE_CLASSES'),'Missing vehicle API metadata');
const att=fs.readFileSync('src/main/java/de/bierverein/api/FireQualificationAttachmentController.java','utf8');
assert(att.includes('hasPermission')||att.includes("allowed(authentication,'fire.qualifications.read')"),'PDF must be role-protected');
assert(att.includes('no-store')&&att.includes('5_000_000'),'PDF size/cache restriction missing');
console.log('PASS category and document filters, card/template deletion, PDF content type, license classes, vehicle requirement and inline JS syntax');
