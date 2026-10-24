import {studies} from './geometry.mjs';
if(new URLSearchParams(location.search).has('board'))document.body.classList.add('board');
const list=document.getElementById('studies');
for(const s of studies){
 const path=`icons/${s.id}-${s.slug}.svg`,mono=`mono/${s.id}-${s.slug}.svg`;
 const card=document.createElement('article');card.dataset.study=s.id;
 card.innerHTML=`<img class="hero" src="${path}" alt="${s.id} ${s.name}"><div class="caption"><span>${s.id}</span><h2>${s.name}</h2><a href="${path}" download>SVG ↙</a></div><p class="rule">${s.rule}</p><p class="note">${s.note}</p><div class="samples"><figure><img src="${path}" width="48" height="48" alt="${s.id} 48像素"><figcaption>48 PX</figcaption></figure><figure class="medium"><img src="${path}" width="96" height="96" alt="${s.id} 96像素"><figcaption>96 PX</figcaption></figure><figure><img src="${mono}" width="64" height="64" class="mono" alt="${s.id} 单色"><figcaption>MONO / 64</figcaption></figure><a class="png-export" href="exports/${s.id}-${s.slug}.png" aria-label="下载 ${s.id} PNG" download="${s.id}-${s.slug}.png">PNG ↙</a></div>`;
 list.append(card);
}
try{
 for(const card of list.children){
  const img=card.querySelector('.hero');await img.decode();
  card.dataset.exportReady='true';
 }
 document.getElementById('status').textContent='3 / 3 已生成 · 可编辑 SVG + 1024px PNG · 静态对照';
}catch(error){document.getElementById('status').textContent=`导出失败：${error.message}`;console.error(error);}
