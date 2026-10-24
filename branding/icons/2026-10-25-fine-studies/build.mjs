import {createRequire} from 'node:module';
import {readFile,writeFile,mkdir,readdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {studies,variants,render,palette,colorAnchors} from './geometry.mjs';
const sharp=createRequire(import.meta.url)('sharp');
const root=new URL('./',import.meta.url);
const write=(path,data)=>writeFile(new URL(path,root),data);
const txt=(x,y,text,size=14,fill='#b0b0b0',extra='')=>`<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" ${extra}>${text}</text>`;
for(const dir of ['svg','exports','previews','details','reference-checks'])await mkdir(new URL(`./${dir}/`,root),{recursive:true});
const asset=(s,v)=>`${s.id}-${s.slug}-${v.id}`;
const only=process.argv.find(arg=>arg.startsWith('--only='))?.slice(7).split(',');
for(const study of studies){
  if(only&&!only.includes(study.id))continue;
  for(const variant of variants){
    const base=asset(study,variant),svg=render(study.id,{variant:variant.id});
    await write(`./svg/${base}.svg`,svg);
    await sharp(Buffer.from(svg)).png().toFile(new URL(`./exports/${base}.png`,root).pathname);
    await sharp(Buffer.from(svg)).resize(896,896).png().toFile(new URL(`./previews/${base}.png`,root).pathname);
    const [x,y,w,h]=study.detail;
    const detail=render(study.id,{variant:variant.id,width:1440,height:Math.round(1440*h/w),viewBox:`${x} ${y} ${w} ${h}`});
    await sharp(Buffer.from(detail)).png().toFile(new URL(`./details/${base}.png`,root).pathname);
  }
  console.log(`Exported ${study.id}: mono + green/gold + coral/gold, 3072px and vector masters.`);
}
const picture=async(s,v,x,y,size)=>`<image x="${x}" y="${y}" width="${size}" height="${size}" href="data:image/png;base64,${(await readFile(new URL(`./previews/${asset(s,v)}.png`,root))).toString('base64')}"/>`;
async function board(selected,name){
  const width=1800,height=selected.length*620+220;
  let body=`<rect width="${width}" height="${height}" fill="#151515"/>`;
  body+=txt(48,42,'AETHER / FOUR FORMS',14,'#cccccc','letter-spacing="3"');
  body+=txt(48,91,'FOUR FORMS / TWELVE STUDIES',32,'#f1f1f1','font-weight="500"');
  for(const [i,v] of variants.entries())body+=txt(48+i*584,137,`0${i+1} / ${v.en}`,12,'#bbbbbb','letter-spacing="1.5"');
  for(const [i,s]of selected.entries()){
    const y=166+i*620;
    for(const [j,v]of variants.entries())body+=await picture(s,v,48+j*584,y,536);
    body+=txt(48,y+568,s.id+' / '+s.en,17,'#eeeeee','letter-spacing="1.4"');
    body+=txt(48,y+591,'SAME STRUCTURE / FULL DETAIL IN INDIVIDUAL SVG AND 3072 PX PNG',10,'#929292','letter-spacing=".7"');
  }
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" font-family="Arial,Helvetica,sans-serif">${body}</svg>`;
  await write(`./${name}.svg`,svg);
  await sharp(Buffer.from(svg)).png().toFile(new URL(`./${name}.png`,root).pathname);
}
await board(studies,'overview');
await board(studies.slice(0,2),'overview-01');
await board(studies.slice(2),'overview-02');
const referenceCrops={J1:[136,228,464],J3:[0,0,736],J5:[-24,76,784],J6:[0,0,1024]};
for(const s of studies){
  const ref=await readFile(new URL(`./references/${s.reference}`,root)),meta=await sharp(ref).metadata();
  const [x,y,size]=referenceCrops[s.id],k=600/size;
  const art=await readFile(new URL(`./previews/${asset(s,variants[0])}.png`,root));
  const comparison=`<svg xmlns="http://www.w3.org/2000/svg" width="1232" height="644"><rect width="1232" height="644" fill="#151515"/><defs><clipPath id="reference-crop"><rect width="600" height="600"/></clipPath></defs><g transform="translate(0 32)" clip-path="url(#reference-crop)"><rect width="600" height="600" fill="#f1f1f1"/><image x="${-x*k}" y="${-y*k}" width="${meta.width*k}" height="${meta.height*k}" href="data:image/png;base64,${ref.toString('base64')}"/></g><image x="632" y="32" width="600" height="600" href="data:image/png;base64,${art.toString('base64')}"/><g font-family="Arial" fill="#dddddd" font-size="12"><text x="12" y="21">${s.id} / REFERENCE — COMPOSITION NORMALIZED</text><text x="644" y="21">VECTOR RECONSTRUCTION / GRAYSCALE</text></g></svg>`;
  await sharp(Buffer.from(comparison)).png().toFile(new URL(`./reference-checks/${s.id}.png`,root).pathname);
}
let scale='<rect width="1440" height="710" fill="#151515"/>'+txt(40,42,'AETHER / ACTUAL PIXEL SIZES',18,'#eeeeee','letter-spacing="1"');
for(const [i,s]of studies.entries()){
  const x=40+(i%2)*700,y=85+Math.floor(i/2)*305;
  scale+=txt(x,y,s.id+' / '+s.en,13,'#cccccc');
  for(const [j,size]of [48,96,192].entries()){
    const px=x+[0,90,230][j];
    scale+=`<image x="${px}" y="${y+25}" width="${size}" height="${size}" href="data:image/png;base64,${(await readFile(new URL(`./previews/${asset(s,variants[0])}.png`,root))).toString('base64')}"/>`;
    scale+=txt(px,y+245,`${size} PX`,10);
  }
}
scale+=txt(40,690,'Detail check only. Current temporary E1 icon remains unchanged.',11);
await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="710" font-family="Arial,Helvetica,sans-serif">${scale}</svg>`)).png().toFile(new URL('./scale-check.png',root).pathname);
const cards=studies.map(s=>`<section class="study" id="${s.id}"><div class="section-heading"><h2><span>${s.id}</span> ${s.name}</h2><p>${s.note}</p><a href="references/${s.reference}" target="_blank" rel="noopener">原图 ↗</a><a href="reference-checks/${s.id}.png" target="_blank" rel="noopener">对照 ↗</a></div><div class="row">${variants.map(v=>{const base=asset(s,v);return `<figure><button class="picture" data-large="svg/${base}.svg" data-title="${s.id} ${s.name} / ${v.name}" aria-label="放大 ${s.name} ${v.name}"><img src="previews/${base}.png" alt="${s.name} ${v.name}" width="896" height="896" loading="lazy"/></button><figcaption><span>${v.name}</span><nav><a href="svg/${base}.svg" target="_blank" rel="noopener">SVG</a><a href="exports/${base}.png" target="_blank" rel="noopener">3072px</a><a href="details/${base}.png" target="_blank" rel="noopener">细节</a></nav></figcaption></figure>`;}).join('')}</div></section>`).join('');
const html=`<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aether · 四组图形</title><style>
:root{color-scheme:dark;font-family:Arial,"PingFang SC","Noto Sans CJK SC",sans-serif;background:#111;color:#eee}*{box-sizing:border-box}body{margin:0}header,main,footer{max-width:1660px;margin:auto;padding:0 40px}header{padding-top:52px}.eyebrow{font:11px monospace;letter-spacing:3px;color:#aaa}h1{font-size:38px;font-weight:500;margin:23px 0 14px;letter-spacing:.04em}.lead{color:#aaa;font-size:14px;line-height:1.9;max-width:760px}.palette{display:flex;gap:22px;padding:22px 0 28px;border-bottom:1px solid #3d3d3d;align-items:center;flex-wrap:wrap}.swatch{display:flex;align-items:center;gap:9px;font:11px monospace;color:#bbb}.swatch i{width:22px;height:22px;border:1px solid #555}.palette a{color:#aaa;font-size:12px;margin-left:auto}.study{padding:34px 0 25px;border-bottom:1px solid #383838}.section-heading{display:flex;align-items:baseline;gap:24px;flex-wrap:wrap;padding-bottom:20px}h2{font-size:19px;font-weight:500;margin:0}h2 span{font:12px monospace;letter-spacing:1px;color:#888;margin-right:8px}.section-heading p{margin:0;color:#999;font-size:12px;line-height:1.7;flex:1}.section-heading>a{font-size:11px;white-space:nowrap}a{color:#bbb;text-decoration:none;border-bottom:1px solid #555;padding-bottom:3px}a:hover{color:white;border-color:white}.row{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:22px}figure{margin:0}.picture{border:0;padding:0;background:none;display:block;width:100%;cursor:zoom-in}.picture img{display:block;width:100%;height:auto;aspect-ratio:1}figcaption{display:flex;align-items:center;justify-content:space-between;padding-top:15px;font-size:12px;color:#bbb}figcaption nav{display:flex;gap:14px;font:10px monospace}button:focus-visible,a:focus-visible,input:focus-visible{outline:1px solid white;outline-offset:5px}footer{padding-top:32px;padding-bottom:52px;font-size:12px;color:#888;line-height:1.9}footer nav{display:flex;gap:24px;margin-bottom:15px}dialog{width:calc(100vw - 40px);height:calc(100vh - 40px);max-width:none;max-height:none;padding:0;border:1px solid #555;background:#111;color:#eee}dialog::backdrop{background:#000d}.toolbar{height:60px;border-bottom:1px solid #444;display:flex;gap:20px;align-items:center;padding:0 20px;font-size:12px}.toolbar strong{font-weight:400;margin-right:auto}.toolbar button{background:none;color:#eee;border:1px solid #555;padding:7px 12px;cursor:pointer}.viewport{height:calc(100% - 60px);overflow:auto;display:grid;place-items:center}.viewport img{display:block;max-width:none;height:auto}input[type=range]{width:160px;accent-color:#ccc}@media(max-width:800px){header,main,footer{padding-left:20px;padding-right:20px}.row{gap:10px}h1{font-size:27px}figcaption{align-items:flex-start;flex-direction:column;gap:9px}figcaption nav{gap:9px}.section-heading{gap:10px}.section-heading p{flex-basis:100%}.palette a{margin-left:0}.toolbar{gap:10px}.toolbar input{width:90px}.toolbar strong{font-size:10px}}
</style><header><div class="eyebrow">AETHER / FORM STUDIES / 04 × 03</div><h1>四种结构，十二张对照。</h1><p class="lead">每组依次为黑白、绿／金、珊瑚／金。同组保持同一套轮廓、开口、线条与颗粒。颜色仅从指定的 CD 色段取样；点击图形可放大检查。</p><div class="palette">${Object.entries(colorAnchors).map(([name,hex])=>`<span class="swatch"><i style="background:${hex}"></i>${hex.toUpperCase()}</span>`).join('')}<a href="references/cd-palette-crop.png" target="_blank" rel="noopener">配色来源 ↗</a></div></header><main>${cards}</main><footer><nav><a href="overview.png" target="_blank" rel="noopener">十二张总览</a><a href="scale-check.png" target="_blank" rel="noopener">实际尺寸检查</a><a href="README.md">来源与制作记录</a></nav>本轮为参考图形复刻及配色研究，保留输入来源；未替换目前暂用的 E1 应用图标。</footer><dialog id="zoom"><div class="toolbar"><strong id="zoom-title"></strong><button id="fit">适合窗口</button><input id="size" type="range" min="25" max="200" step="5" aria-label="缩放比例"><output id="percent"></output><button id="close">关闭</button></div><div class="viewport" id="viewport"><img id="zoom-image" alt=""></div></dialog><script>
const dialog=document.getElementById('zoom'),large=document.getElementById('zoom-image'),slider=document.getElementById('size'),percent=document.getElementById('percent'),viewport=document.getElementById('viewport');
const resize=()=>{large.style.width=(1024*Number(slider.value)/100)+'px';percent.textContent=slider.value+'%';};
const fit=()=>{slider.value=Math.max(25,Math.min(100,Math.floor(Math.min((viewport.clientWidth-32)/1024,(viewport.clientHeight-32)/1024)*20)*5));resize();};
document.querySelectorAll('[data-large]').forEach(button=>button.addEventListener('click',()=>{large.src=button.dataset.large;large.alt=button.dataset.title;document.getElementById('zoom-title').textContent=button.dataset.title;dialog.showModal();requestAnimationFrame(fit);}));
slider.addEventListener('input',resize);document.getElementById('fit').addEventListener('click',fit);document.getElementById('close').addEventListener('click',()=>dialog.close());
</script></html>`;
await write('./index.html',html);
await write('./studies.json',JSON.stringify({date:'2026-10-25',status:'Four selected reference studies, three variants each; proposals only',palette,colorAnchors,variants,studies},null,2)+'\n');
const paths=[];
for(const dir of ['references','svg','exports','previews','details','reference-checks'])for(const file of (await readdir(new URL(`./${dir}/`,root))).sort())paths.push(`${dir}/${file}`);
paths.push('overview.svg','overview.png','overview-01.svg','overview-01.png','overview-02.svg','overview-02.png','scale-check.png','index.html','geometry.mjs','build.mjs','studies.json','contours.json','reconstruct.py','verify.py','README.md');
const files=[];
for(const file of paths){const data=await readFile(new URL(file,root));files.push({file,bytes:data.length,sha256:createHash('sha256').update(data).digest('hex')});}
await write('./manifest.json',JSON.stringify({date:'2026-10-25',method:'Reference-fitted cubic SVG contours for J1/J3/J5; parametric 224-ray geometry for J6. No imagegen or embedded raster in icon masters.',status:'Four groups × monochrome and two limited-color variants = twelve studies; current E1 app icon unchanged',palette:{source:'references/cd-palette-crop.png',method:'Median RGB of specified 7×7 patches; black and white remain neutral',anchors:colorAnchors,samples:{green:[28,176],gold:[28,64],coral:[115,20]}},exports:{masterPNG:3072,previewPNG:896},files},null,2)+'\n');
console.log('Exported twelve studies, two-page contact sheets, zoomable gallery and provenance manifest.');
