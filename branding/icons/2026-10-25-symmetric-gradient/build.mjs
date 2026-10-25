import {createRequire} from 'node:module';
import {readFile,writeFile,mkdir,readdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {render,studies,variants,palette,samples,variations} from './geometry.mjs';
const sharp=createRequire(import.meta.url)('sharp');
const root=new URL('./',import.meta.url),previewsOnly=process.argv.includes('--previews');
const onlyGroup=process.argv.find(a=>a.startsWith('--group='))?.split('=')[1];
const write=(name,data)=>writeFile(new URL(name,root),data);
const asset=(s,v)=>`${s.id}-${v.id}`;
// Rasterize one authored half, then reflect its pixels. This also avoids
// platform-specific turbulence/antialias rounding on the reflected instance.
async function symmetricPNG(svg,id){
 // Inline the single half for rasterization; the editable SVG keeps its two
 // reusable instances. This avoids expanding thousands of stipple instances.
 const halfDefinition=svg.match(/<g id="[^"]+-half"[\s\S]*?(?=<\/defs>)/)?.[0];
 if(!halfDefinition)throw new Error('Missing shared half');
 const rasterSVG=svg.replace(halfDefinition,'')
  .replace(/<use href="#[^"]+-half"\/>/,halfDefinition)
  .replace(/<use href="#[^"]+-half" transform="translate\(1024 0\) scale\(-1 1\)"\/>/,'');
 const {data,info}=await sharp(Buffer.from(rasterSVG)).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 const half=info.width/2,left=id==='J6'?half:0;
 const a=await sharp(data,{raw:info}).extract({left,top:0,width:half,height:info.height}).png().toBuffer();
 const b=await sharp(a).flop().png().toBuffer();
 return sharp({create:{width:info.width,height:info.height,channels:4,background:'#080808'}})
  .composite([{input:a,left,top:0},{input:b,left:half-left,top:0}]).png().toBuffer();
}
for(const dir of ['svg','exports','previews','details'])await mkdir(new URL(`./${dir}/`,root),{recursive:true});
for(const s of studies){
  if(process.argv.includes('--review-only')||(onlyGroup&&s.id!==onlyGroup))continue;
  for(const [i,v] of variants.entries()){
    const name=asset(s,v),svg=render(s.id,i+1);
    await write(`./svg/${name}.svg`,svg);
    await write(`./previews/${name}.png`,await symmetricPNG(render(s.id,i+1,{width:896,height:896}),s.id));
    if(!previewsOnly){
      const png=await symmetricPNG(svg,s.id);
      await write(`./exports/${name}.png`,png);
      const [x,y,w,h]=s.detail;
      await sharp(png).extract({left:x*3,top:y*3,width:w*3,height:h*3}).resize(1440,Math.round(1440*h/w)).png().toFile(new URL(`./details/${name}.png`,root).pathname);
    }
    console.log(`Rendered ${s.id}-${v.id}${previewsOnly?' preview':' / SVG, 3072px PNG, preview and detail'}.`);
  }
}
const t=(x,y,text,size=13,color='#bcbcbc',extra='')=>`<text x="${x}" y="${y}" font-size="${size}" fill="${color}" ${extra}>${String(text).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')}</text>`;
async function sheet(selected,name){
  const width=2500,height=selected.length*570+190;
  let body=`<rect width="${width}" height="${height}" fill="#151515"/>`+t(40,40,'AETHER / SYMMETRIC GRADIENT / 03 × 05',15,'#cccccc','letter-spacing="3"');
  body+=t(40,89,'MIRROR SYMMETRY / GREEN-GOLD / SOFT PINK AND LAVENDER',29,'#f3f3f3','letter-spacing="1"');
  for(const [j,s] of selected.entries()){
    const y=130+j*570;
    for(const [i,v] of variants.entries()){
      const data=await readFile(new URL(`./previews/${asset(s,v)}.png`,root));
      const x=40+i*492;
      body+=`<image x="${x}" y="${y}" width="452" height="452" href="data:image/png;base64,${data.toString('base64')}"/>`;
      body+=t(x,y+482,`${s.id} / ${v.id}`,15,'#eeeeee','letter-spacing="2"');
    }
    body+=t(40,y+520,s.en,18,'#dddddd','letter-spacing="2"');
  }
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" font-family="Arial,Helvetica,sans-serif">${body}</svg>`;
  await write(`./${name}.svg`,svg);
  await sharp(Buffer.from(svg)).png().toFile(new URL(`./${name}.png`,root).pathname);
}
await sheet(studies,'overview');
for(const s of studies)await sheet([s],`overview-${s.id}`);
const notes={
 J3:['下垂 28 / 绿黄','下垂 32 / 浅橙','下垂 38 / 浅粉与浅紫','下垂 42 / 浅紫','下垂 46 / 浅粉紫'],
 J5:['同源半幅镜像','略宽略大','横向展开','纵向略展','轻微收束'],
 J6:['224 条细线','232 条细线','240 条细线','248 条细线','256 条细线']
};
const cards=studies.map(s=>`<section id="${s.id}"><div class="section-head"><h2><span>${s.id}</span>${s.name}</h2><p>${s.note}</p><a href="overview-${s.id}.png" target="_blank" rel="noopener">本组五张 ↗</a></div><div class="row">${variants.map((v,i)=>{const name=asset(s,v);return `<figure><button class="picture" data-src="exports/${name}.png" data-title="${s.id} / ${v.id} ${v.name}" aria-label="放大 ${s.id} ${v.id}"><img src="previews/${name}.png" alt="${s.name} ${v.id} ${v.name}" width="896" height="896" loading="lazy"/></button><figcaption><b>${v.id} / ${v.name}</b><span>${notes[s.id][i]}</span><nav><a href="svg/${name}.svg" target="_blank" rel="noopener">SVG</a><a href="exports/${name}.png" target="_blank" rel="noopener">3072px</a><a href="details/${name}.png" target="_blank" rel="noopener">细节</a></nav></figcaption></figure>`;}).join('')}</div></section>`).join('');
const html=`<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aether · 三组十五版</title><style>
:root{color-scheme:dark;background:#111;color:#eee;font-family:Arial,"PingFang SC",sans-serif}*{box-sizing:border-box}body{margin:0}header,main,footer{max-width:1900px;margin:auto;padding:0 34px}header{padding-top:44px}.eyebrow{font:11px monospace;letter-spacing:3px;color:#aaa}h1{font-weight:500;font-size:36px;letter-spacing:.05em;margin:24px 0 16px}.lead{font-size:14px;color:#aaa;line-height:1.9;max-width:800px}.palette{display:flex;gap:22px;align-items:center;flex-wrap:wrap;padding:22px 0 28px;border-bottom:1px solid #3b3b3b}.chip{font:11px monospace;color:#aaa;display:flex;align-items:center;gap:8px}.chip i{display:inline-block;width:21px;height:21px;border:1px solid #555}.palette>a{font-size:11px;margin-left:auto}a{color:#bcbcbc;text-decoration:none;border-bottom:1px solid #555;padding-bottom:3px}a:hover{color:white;border-color:white}section{padding:30px 0 34px;border-bottom:1px solid #3b3b3b}.section-head{display:flex;align-items:baseline;gap:24px;flex-wrap:wrap;padding-bottom:22px}h2{font-size:20px;font-weight:500;margin:0}h2 span{font:12px monospace;color:#888;margin-right:12px}.section-head p{font-size:12px;line-height:1.8;color:#999;flex:1;margin:0}.section-head>a{font-size:11px}.row{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:20px}figure{margin:0}.picture{display:block;width:100%;padding:0;background:none;border:0;cursor:zoom-in}.picture img{display:block;width:100%;height:auto}figcaption{padding-top:14px;display:flex;flex-direction:column;gap:9px;font-size:12px;color:#bdbdbd}figcaption b{font-weight:400}figcaption span{font-size:10px;color:#888}nav{display:flex;gap:14px;font:10px monospace}footer{padding-top:28px;padding-bottom:42px;font-size:12px;line-height:1.9;color:#888}footer nav{margin-bottom:16px;flex-wrap:wrap;font:12px Arial}button:focus-visible,a:focus-visible,input:focus-visible{outline:1px solid white;outline-offset:4px}dialog{background:#111;border:1px solid #555;color:#eee;padding:0;width:calc(100vw - 32px);height:calc(100vh - 32px);max-width:none;max-height:none}dialog::backdrop{background:#000d}.toolbar{height:60px;border-bottom:1px solid #444;display:flex;align-items:center;gap:18px;padding:0 18px;font-size:12px}.toolbar b{font-weight:400;flex:1}.toolbar button{background:none;color:#ddd;border:1px solid #555;padding:7px 10px;cursor:pointer}.toolbar input{width:150px;accent-color:#ccc}.viewport{height:calc(100% - 60px);overflow:auto;display:grid;place-items:center}.viewport img{display:block;max-width:none;height:auto}@media(max-width:1100px){.row{gap:15px;grid-template-columns:repeat(3,minmax(0,1fr))}.section-head p{flex-basis:100%;order:3}}@media(max-width:600px){header,main,footer{padding-left:20px;padding-right:20px}h1{font-size:27px}.row{grid-template-columns:repeat(2,minmax(0,1fr));gap:22px 14px}.palette{gap:15px}.palette>a{margin-left:0}.toolbar{gap:9px}.toolbar input{width:85px}.toolbar b{font-size:10px}.toolbar button{font-size:11px}}
</style><header><div class="eyebrow">AETHER / SYMMETRIC GRADIENT / 03 × 05</div><h1>三组形态，十五个变化。</h1><p class="lead">J3、J5、J6 各五版，全部左右镜像对称。保留原绿／黄主色相，收回过强曝光与饱和度；浅橙、浅粉、浅紫通过宽幅渐变进入高光。</p><div class="palette">${Object.entries(palette).map(([key,hex])=>`<span class="chip"><i style="background:${hex}"></i>${hex.toUpperCase()}</span>`).join('')}<a href="references/original-green-gold.png" target="_blank" rel="noopener">配色方向 ↗</a></div></header><main>${cards}</main><footer><nav><a href="overview.png" target="_blank" rel="noopener">全部十五张</a><a href="../2026-10-25-fine-studies/index.html" target="_blank" rel="noopener">上一轮黑白与双色</a><a href="README.md">来源与修改记录</a></nav>本轮为配色与形态变化研究；当前临时 E1 图标保持原样。</footer><dialog id="zoom"><div class="toolbar"><b id="title"></b><button id="fit">适合窗口</button><input id="scale" type="range" min="25" max="300" step="5" aria-label="缩放比例"><output id="percent"></output><button id="close">关闭</button></div><div class="viewport" id="viewport"><img id="large" alt=""></div></dialog><script>
const modal=document.getElementById('zoom'),large=document.getElementById('large'),slider=document.getElementById('scale'),viewport=document.getElementById('viewport');
const resize=()=>{large.style.width=(1024*slider.value/100)+'px';document.getElementById('percent').textContent=slider.value+'%';};
const fit=()=>{slider.value=Math.max(25,Math.min(100,Math.floor(Math.min((viewport.clientWidth-24)/1024,(viewport.clientHeight-24)/1024)*20)*5));resize();};
document.querySelectorAll('[data-src]').forEach(button=>button.addEventListener('click',()=>{large.src=button.dataset.src;large.alt=button.dataset.title;document.getElementById('title').textContent=button.dataset.title;modal.showModal();requestAnimationFrame(fit);}));
slider.addEventListener('input',resize);document.getElementById('fit').addEventListener('click',fit);document.getElementById('close').addEventListener('click',()=>modal.close());
</script></html>`;
await write('./index.html',html);
await write('./studies.json',JSON.stringify({date:'2026-10-25',count:15,status:'Three groups, five strictly mirror-symmetric variants each; green/gold hues, soft pale highlight ramps, no color overlays',palette,samples,variants,studies,variations},null,2)+'\n');
if(!previewsOnly){
 const paths=[];
 for(const dir of ['references','svg','exports','previews','details'])for(const file of (await readdir(new URL(`./${dir}/`,root))).sort())paths.push(`${dir}/${file}`);
 paths.push('overview.png','overview.svg',...studies.flatMap(s=>[`overview-${s.id}.png`,`overview-${s.id}.svg`]),'geometry.mjs','build.mjs','index.html','studies.json','README.md','verify.py');
 const files=[];
 for(const file of paths){const data=await readFile(new URL(file,root));files.push({file,bytes:data.length,sha256:createHash('sha256').update(data).digest('hex')});}
 await write('./manifest.json',JSON.stringify({date:'2026-10-25',count:15,method:'Shared half geometry mirrored once; direct native gradients for axial flow and radial sphere, continuous tone ramp for folds; broad pale highlights; no color overlay masks or imagegen',geometrySource:'../2026-10-25-fine-studies/contours.json',geometrySha256:createHash('sha256').update(await readFile(new URL('../2026-10-25-fine-studies/contours.json',root))).digest('hex'),palette,samples,files},null,2)+'\n');
}
console.log(previewsOnly?'All fifteen previews and three comparison sheets ready.':'All fifteen masters, 3072px exports, details, gallery and manifest ready.');
