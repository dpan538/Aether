import {concepts,families,renderGeometry} from './geometry.mjs';
const $=s=>document.querySelector(s);
let monochrome=false,selected=null,playing=false,phase=0,previous=null,raf=0,lastDraw=0;
const grid=$('#grid'),dialog=$('#detail'),stage=$('#stage');
const reduced=matchMedia('(prefers-reduced-motion: reduce)');
function asset(c,mono=monochrome){return `${mono?'mono':'icons'}/${c.file}`;}
function mount(){
 grid.innerHTML=concepts.map(c=>`<article class="card" data-family="${c.family}" data-id="${c.id}"><button class="card-button" data-open="${c.id}" aria-label="查看 ${c.id} ${c.name}"><span class="art"><img src="${asset(c)}" alt="${c.name}，${c.rule}" loading="eager"></span><span class="card-header"><span class="number">${c.id}</span><h2>${c.name}</h2><span class="card-english">${c.english}</span></span><p class="card-rule">${c.rule}</p></button><div class="card-tools"><div class="mini"><img src="${asset(c)}" alt="${c.id} 圆角方形32像素"><img src="${asset(c)}" class="circle-sample" alt="${c.id} 圆形32像素"><span>32 PX</span></div><button class="detail-link" data-open="${c.id}">几何 / 动态 ↗</button></div></article>`).join('');
 filter($('.toolbar nav .active').dataset.family);
}
function filter(family){for(const card of grid.children)card.hidden=family!=='all'&&card.dataset.family!==family;}
function paint(){if(!selected)return;stage.innerHTML=renderGeometry(selected.id,{phase,mono:monochrome});$('#timing').textContent=`${playing?'LINEAR TIME':'STATIC'} / PHASE ${phase.toFixed(3)}`;}
function syncPlay(){ $('#play').textContent=playing?'暂停动效':'播放几何动效';$('#play').setAttribute('aria-pressed',String(playing)); }
function pause(){playing=false;previous=null;cancelAnimationFrame(raf);raf=0;syncPlay();paint();}
function frame(now){
 if(!playing||!dialog.open||document.hidden){previous=null;return;}
 if(previous!==null)phase+=(now-previous)/1000*Number($('#speed').value)/100;
 previous=now;
 if(now-lastDraw>=1000/30){paint();lastDraw=now;}
 raf=requestAnimationFrame(frame);
}
function open(id){
 pause(); selected=concepts.find(c=>c.id===id);phase=0;
 $('#detail-title').textContent=selected.name;
 $('#detail-index').textContent=`${selected.id} / ${families[selected.family]}`;
 $('#detail-english').textContent=selected.english;
 $('#detail-rule').textContent=selected.rule;
 $('#detail-motion').textContent=selected.motion+(reduced.matches?' 系统已减少动态；当前静止，仍可手动播放。':'');
 $('#small').innerHTML=[48,64,96].map(size=>`<figure><img src="${asset(selected)}" width="${size}" height="${size}" alt="${size}像素${selected.name}"><figcaption>${size} PX</figcaption></figure>`).join('');
 $('#download-color').href=asset(selected,false);$('#download-mono').href=asset(selected,true);
 paint();dialog.showModal();
}
grid.addEventListener('click',e=>{const b=e.target.closest('[data-open]');if(b)open(b.dataset.open);});
$('.toolbar nav').addEventListener('click',e=>{const b=e.target.closest('[data-family]');if(!b)return;$('.toolbar nav .active').classList.remove('active');b.classList.add('active');filter(b.dataset.family);});
$('#mono').addEventListener('change',e=>{monochrome=e.target.checked;mount();paint();});
$('#circle').addEventListener('change',e=>document.body.classList.toggle('circle',e.target.checked));
$('#close').addEventListener('click',()=>dialog.close());
dialog.addEventListener('close',pause);
dialog.addEventListener('click',e=>{if(e.target===dialog){const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)dialog.close();}});
$('#play').addEventListener('click',()=>{if(playing){pause();return;}playing=true;previous=null;lastDraw=0;syncPlay();raf=requestAnimationFrame(frame);});
$('#reset').addEventListener('click',()=>{pause();phase=0;paint();});
$('#speed').addEventListener('input',()=>{$('#speed-value').textContent=(Number($('#speed').value)/100).toFixed(2).replace(/0$/,'')+'×';});
document.addEventListener('visibilitychange',()=>{if(document.hidden){previous=null;cancelAnimationFrame(raf);}else if(playing&&dialog.open){previous=null;raf=requestAnimationFrame(frame);}});
reduced.addEventListener('change',()=>{if(reduced.matches)pause();});
window.addEventListener('pagehide',()=>{playing=false;cancelAnimationFrame(raf);},{once:true});
mount();document.body.dataset.ready='true';
