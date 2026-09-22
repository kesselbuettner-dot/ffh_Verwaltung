/* Shared touch/mouse signature field for device inspections. Server validates the PNG payload. */
(function(root){
 'use strict';
 const states=new WeakMap();
 function attach(canvas){
  if(!canvas)throw Error('Unterschriftsfeld fehlt.');
  if(states.has(canvas))return states.get(canvas);
  const ctx=canvas.getContext('2d');
  const state={drawn:false,down:false};
  ctx.fillStyle='#ffffff';ctx.fillRect(0,0,canvas.width,canvas.height);
  ctx.strokeStyle='#152b3c';ctx.fillStyle='#152b3c';ctx.lineWidth=2.8;ctx.lineCap='round';ctx.lineJoin='round';
  canvas.style.touchAction='none';
  function point(ev){const b=canvas.getBoundingClientRect();return {x:(ev.clientX-b.left)*canvas.width/b.width,y:(ev.clientY-b.top)*canvas.height/b.height};}
  canvas.addEventListener('pointerdown',ev=>{if(ev.button!==0&&ev.pointerType==='mouse')return;ev.preventDefault();canvas.setPointerCapture?.(ev.pointerId);state.down=true;state.drawn=true;const p=point(ev);ctx.beginPath();ctx.arc(p.x,p.y,1.5,0,Math.PI*2);ctx.fill();ctx.beginPath();ctx.moveTo(p.x,p.y);});
  canvas.addEventListener('pointermove',ev=>{if(!state.down)return;ev.preventDefault();const p=point(ev);ctx.lineTo(p.x,p.y);ctx.stroke();});
  const stop=()=>{state.down=false;};
  canvas.addEventListener('pointerup',stop);canvas.addEventListener('pointercancel',stop);canvas.addEventListener('lostpointercapture',stop);
  states.set(canvas,state);return state;
 }
 function data(canvas){const state=attach(canvas);if(!state.drawn)throw Error('Bitte die Prüfung vor dem Abschluss unterschreiben.');return canvas.toDataURL('image/png');}
 function clear(canvas){const state=attach(canvas),ctx=canvas.getContext('2d');ctx.clearRect(0,0,canvas.width,canvas.height);ctx.fillStyle='#ffffff';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.strokeStyle='#152b3c';ctx.fillStyle='#152b3c';state.drawn=false;}
 function field(label='Unterschrift des Prüfers'){
  const area=document.createElement('div');area.className='inspection-signature-field';
  const title=document.createElement('label');title.textContent=label;
  const canvas=document.createElement('canvas');canvas.width=600;canvas.height=170;canvas.setAttribute('aria-label',label);
  canvas.style.cssText='display:block;width:100%;height:150px;background:#fff;border:2px solid #a6b5c4;border-radius:10px;touch-action:none';
  const button=document.createElement('button');button.type='button';button.className='btn small secondary';button.textContent='Unterschrift löschen';
  button.addEventListener('click',()=>clear(canvas));
  area.append(title,canvas,button);attach(canvas);
  return {element:area,canvas,signature:()=>data(canvas)};
 }
 root.FWSignature=Object.freeze({attach,data,clear,field});
})(window);
