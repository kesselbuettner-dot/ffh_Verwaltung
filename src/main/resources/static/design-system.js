/* FW-Cockpit UI component templates: reusable, centrally configurable and XSS-safe.
 * Each template returns a DOM element. Never inject untrusted HTML into components.
 * Read docs/DESIGN_SYSTEM.md and docs/UI_KOMPONENTEN.md before changing component defaults.
 */
(function(root){
 'use strict';
 const ELEMENTS={button:'button',card:'section',page:'section',field:'div',table:'table',badge:'span',empty:'div',modal:'section'};
 const config=Object.freeze({version:1,defaultButtonVariant:'secondary',defaultBadgeVariant:'neutral',pageWidth:'wide'});
 function node(tag,classes='',text){
  const element=document.createElement(tag);
  if(classes)element.className=classes;
  if(text!==undefined&&text!==null)element.textContent=String(text);
  return element;
 }
 function append(parent,...children){
  children.flat().filter(Boolean).forEach(child=>parent.appendChild(typeof child==='string'?document.createTextNode(child):child));
  return parent;
 }
 function button({label,variant='secondary',onClick,disabled=false,type='button',title}={}){
  const allowed=['primary','secondary','danger'];
  if(!allowed.includes(variant))variant='secondary';
  const el=node('button','ds-btn ds-btn-'+variant,label||'Aktion');
  el.type=type==='submit'?'submit':'button';el.disabled=!!disabled;
  if(title)el.title=String(title);
  if(typeof onClick==='function')el.addEventListener('click',onClick);
  return el;
 }
 function badge({label,variant='neutral'}={}){
  const allowed=['neutral','success','warning','danger'];
  if(!allowed.includes(variant))variant='neutral';
  return node('span','ds-badge'+(variant==='neutral'?'':' is-'+variant),label||'–');
 }
 function card({title,content,actions,extraClass=''}={}){
  const el=node('section','ds-card'+(extraClass?' '+String(extraClass).replace(/[^\w -]/g,''):''));
  if(title)append(el,node('h2','ds-card-title',title));
  if(content!==undefined)append(el,typeof content==='string'?node('p','',content):content);
  if(actions?.length){const row=node('div','ds-page-actions u-mt-4');append(row,actions);append(el,row);}
  return el;
 }
 function page({title,description='',actions=[],children=[],className=''}={}){
  const host=node('section','ds-page'+(className?' '+String(className).replace(/[^\w -]/g,''):''));
  const head=node('header','ds-page-header'),heading=node('div','ds-page-heading');
  append(heading,node('h1','ds-page-title',title||'Neue Seite'));
  if(description)append(heading,node('p','ds-page-description',description));
  append(head,heading);
  if(actions.length)append(head,append(node('div','ds-page-actions'),actions));
  append(host,head,children);return host;
 }
 function field({label,value='',type='text',name='',placeholder='',required=false,options=null,onChange}={}){
  const wrapper=node('div','ds-field'),identifier='ds-'+Math.random().toString(36).slice(2);
  const lbl=node('label','',label||'Feld');lbl.htmlFor=identifier;
  const input=Array.isArray(options)?node('select','ds-select'):node('input','ds-input');
  input.id=identifier;if(name)input.name=String(name);
  if(Array.isArray(options)){
   options.forEach(option=>{const opt=node('option','',option.label??option.value);
    opt.value=String(option.value);input.appendChild(opt);});
   input.value=String(value);
  }else{
   input.type=['text','email','date','number','search','tel','password'].includes(type)?type:'text';
   input.value=value==null?'':String(value);input.placeholder=String(placeholder);
  }
  input.required=!!required;
  if(typeof onChange==='function')input.addEventListener('change',()=>onChange(input.value));
  append(wrapper,lbl,input);return wrapper;
 }
 function table({columns=[],rows=[],emptyMessage='Keine Einträge vorhanden',onRowClick}={}){
  const frame=node('div','ds-table-wrap'),el=node('table','ds-table');
  const head=node('thead'),header=node('tr');
  columns.forEach(column=>append(header,node('th','',column.label||column.key||'')));
  append(head,header);el.appendChild(head);
  const body=node('tbody');
  if(!rows.length){
   const tr=node('tr'),cell=node('td','ds-empty',emptyMessage);cell.colSpan=Math.max(1,columns.length);
   append(tr,cell);append(body,tr);
  }
  rows.forEach(row=>{
   const tr=node('tr');
   columns.forEach(column=>{
    const cell=node('td');
    const value=typeof column.render==='function'?column.render(row):row[column.key];
    append(cell,value instanceof Node?value:String(value??'–'));append(tr,cell);
   });
   if(typeof onRowClick==='function'){tr.tabIndex=0;tr.addEventListener('click',()=>onRowClick(row));}
   append(body,tr);
  });
  append(el,body);append(frame,el);return frame;
 }
 function empty({message='Keine Einträge vorhanden'}={}){return node('div','ds-empty',message);}
 function modalContent({title,content,actions=[]}={}){
  const host=node('section','ds-modal-content');
  if(title)append(host,node('h2','ds-card-title',title));
  if(content)append(host,content);
  if(actions.length)append(host,append(node('footer','ds-modal-actions'),actions));
  return host;
 }
 // Admin may adjust tokens with validated values. No generated style rules, selectors or CSS imports.
 const allowedTokens={
  space:'--ds-space-4',radius:'--ds-radius-lg',controlHeight:'--ds-control-height',
  pageWidth:'--ds-max-page',textSize:'--ds-text-base',shadow:'--ds-shadow',
  templateColumns:'--ds-template-columns',templateGap:'--ds-template-row-gap'
 };
 const tokenValidators={
  space:v=>/^(?:[4-9]|[1-3]\d|40)px$/.test(v),
  radius:v=>/^(?:[4-9]|1\d|2[0-4])px$/.test(v),
  controlHeight:v=>/^(?:4[4-9]|[5-6]\d)px$/.test(v),
  pageWidth:v=>/^(?:9\d\d|1[0-6]\d\d)px$/.test(v),
  textSize:v=>/^(?:1[4-9]|20)px$/.test(v),
  shadow:v=>v==='none'||v==='0 4px 20px #00000015'||v==='0 8px 28px #00000030',
  templateColumns:v=>/^[234]$/.test(v),
  templateGap:v=>/^(?:8|12|16|20|24|32)px$/.test(v)
 };
 function applyTokens(values={}){
  const applied={};
  for(const [name,cssVar] of Object.entries(allowedTokens)){
   const value=values[name];
   if(value===undefined||value===null)continue;
   if(typeof value!=='string'||!tokenValidators[name](value))continue;
   document.documentElement.style.setProperty(cssVar,value);applied[name]=value;
  }
  return applied;
 }
 root.FWComponents=Object.freeze({config,button,badge,card,page,field,table,empty,modalContent,applyTokens});
})(window);
