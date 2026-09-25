const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const index=fs.readFileSync('src/main/resources/static/index.html','utf8');
const theme=fs.readFileSync('src/main/resources/static/ui-theme.css','utf8');
const agent=fs.readFileSync('AGENTS.md','utf8');
for(const doc of ['docs/DESIGN_SYSTEM.md','docs/UI_KOMPONENTEN.md','docs/MITGLIEDER_WEHRLEITUNG.md']){
 assert(fs.existsSync(doc), 'Missing binding layout document: '+doc);
 assert(agent.includes(doc), 'Agents must read design document '+doc);
}
const scripts=[...index.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)];
for(const [n,script] of scripts.entries())new vm.Script(script[1],{filename:'inline-'+n+'.js'});
for(const id of ['members','accounts','roles','rights']){
 assert(index.includes("['"+id+"','"),'Dedicated administrator section missing: '+id);
 assert(index.includes("if(section==='"+id+"')return "),'Administration must route to the dedicated section: '+id);
}
assert(index.includes('window.FWPageTemplates.settings({')&&index.includes('onSelect:key=>adminSettingsPage(key)'),
 'Admin navigation must use shared page template and preserve per-section routing');
assert(index.includes('adminMembersSettingsPage()'),'Admin-only member master view missing');
assert(index.includes('saveMemberMaster('),'Member master update callback missing');
assert(index.includes('loginEnabled:null'),'Member editing must not modify login status');
assert(index.includes('function adminPermissionsSettingsPage()'),'Rights page missing');
assert(index.includes('function adminRolesSettingsPage()'),'Roles page missing');
assert(index.includes("loadManagedRolesPanel('rights')"),'Rights page not scoped to assignments');
assert(index.includes("loadManagedRolesPanel('roles')"),'Role page not scoped to definitions');
assert(index.includes("implemented:false}"),'Legacy menu entries not disabled');
for(const purpose of ['--ui-primary','--ui-secondary','--ui-danger','--ui-warning','--ui-success','--ui-focus'])
 assert(theme.includes(purpose),'Missing semantic UI color token '+purpose);
assert(/\/ui-theme\.css\?v=\d+/.test(index),'Shared theme stylesheet not loaded');
assert(index.includes('renderThemePresets()'),'Theme selection missing');
assert(index.includes('UI_THEME_PRESETS'),'Theme presets missing');
console.log('PASS shared design docs, separated member/accounts/roles/rights pages, preserved logins and theme tokens; checked '+scripts.length+' inline scripts');
