import { createDisc as createOriginalDisc } from './source/disc-original.js';
import { createDisc as createOpticalDisc } from './source/disc-optical.js';

export const studies = [
  { id:'C1', name:'原版 · 黑底', file:'01-cd-original.png', noise:false, distortion:0, description:'保留页面中的虹彩、盘芯与三道同心环线。' },
  { id:'C2', name:'颗粒 · 黑底', file:'02-cd-particles.png', noise:true, distortion:0, description:'同样的圆盘，加入疏密有变化的白灰颗粒。' },
  { id:'C3', name:'旋律 · 折光', file:'03-cd-melodic.png', noise:true, distortion:1, description:'色带与细环轻微起伏，形成连续的节奏与折射。' },
];
const size=1024, diameter=896, inset=(size-diameter)/2;
const query=new URLSearchParams(location.search);
if(query.has('board'))document.body.classList.add('board');

// Fixed seeds keep noise identical between C2/C3, so only optical rhythm changes.
function rng(seed) {
  return () => { seed=(Math.imul(1664525,seed)+1013904223)>>>0; return seed/4294967296; };
}
function particles(ctx) {
  const random=rng(10424);
  for(let i=0;i<5700;i++) {
    const x=random()*size,y=random()*size;
    const dx=(x-size/2)/(diameter/2),dy=(y-size/2)/(diameter/2);
    const r=Math.hypot(dx,dy),theta=Math.atan2(dy,dx);
    const curve=0.86+0.065*Math.sin(3*theta+0.7);
    const sweep=Math.exp(-(((r-curve)/0.16)**2));
    if(random()>0.24+0.63*sweep)continue;
    const tone=118+Math.floor(random()*112);
    const alpha=0.09+random()*0.36;
    const radius=random()<0.94?0.32+random()*0.50:0.85+random()*0.45;
    ctx.fillStyle=`rgba(${tone},${tone+1},${Math.min(255,tone+3)},${alpha})`;
    ctx.beginPath();ctx.arc(x,y,radius,0,Math.PI*2);ctx.fill();
  }
  // Three quiet clusters suggest CD pigment scatter, without point-light stars.
  for(let i=0;i<1650;i++) {
    const theta=-2.65+random()*2.35;
    const r=0.92+(random()-0.5)*0.26;
    const x=size/2+Math.cos(theta)*diameter/2*r;
    const y=size/2+Math.sin(theta)*diameter/2*r;
    if(random()>0.52)continue;
    const tone=145+Math.floor(random()*85);
    ctx.fillStyle=`rgba(${tone},${tone},${tone},${0.08+random()*0.22})`;
    ctx.fillRect(x,y,0.6+random()*0.8,0.6+random()*0.8);
  }
}

function draw(study,canvas) {
  canvas.width=size;canvas.height=size;
  const source=document.createElement('canvas');
  source.className='render-source';document.body.append(source);
  const factory=study.distortion?createOpticalDisc:createOriginalDisc;
  const disc=factory(source,{animate:false,material:'spectral',pixelRatio:1,distortion:study.distortion});
  if(source.dataset.renderer!=='webgl2')throw new Error('WebGL2 unavailable; CD export not rendered.');
  const ctx=canvas.getContext('2d');
  ctx.fillStyle='#050608';ctx.fillRect(0,0,size,size);
  ctx.save();ctx.translate(size/2,size/2);ctx.rotate(-Math.PI/2);
  ctx.drawImage(source,-diameter/2,-diameter/2,diameter,diameter);ctx.restore();
  if(study.noise)particles(ctx);
  disc.destroy();source.remove();
  canvas.dataset.rendered='true';canvas.dataset.study=study.id;
  canvas.dataset.diameter=String(diameter);canvas.dataset.inset=String(inset);
  return canvas.toDataURL('image/png');
}

try {
  for(const study of studies) {
    const article=document.createElement('article');article.className='card';
    article.innerHTML=`<canvas class="artwork" role="img" aria-label="${study.id} ${study.name}"></canvas><div class="caption"><span>${study.id}</span><h2>${study.name}</h2><a download="${study.file}" aria-label="下载 ${study.id} PNG">PNG ↙</a></div><p class="description">${study.description}</p>`;
    document.getElementById('grid').append(article);
    const url=draw(study,article.querySelector('canvas'));
    article.querySelector('a').href=url;
    const sample=document.createElement('div');sample.className='sample-set';sample.dataset.study=study.id;
    sample.innerHTML=`<figure><img width="48" height="48" src="${url}" alt="${study.id} 48像素"><figcaption>48 PX</figcaption></figure><figure><img width="96" height="96" src="${url}" alt="${study.id} 96像素"><figcaption>96 PX</figcaption></figure><figure><img class="round" width="72" height="72" src="${url}" alt="${study.id} 圆形裁切"><figcaption>CIRCLE</figcaption></figure>`;
    document.getElementById('samples').append(sample);
  }
  document.getElementById('status').textContent='已绘制 3 / 3 · 1024 × 1024 PNG · 固定颗粒种子';
} catch(error) {
  document.getElementById('status').textContent=`绘制失败：${error.message}`;
  document.getElementById('status').dataset.error='true';
  console.error(error);
}
