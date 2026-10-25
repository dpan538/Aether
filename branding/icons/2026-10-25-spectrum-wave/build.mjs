import {writeFile,readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
const sharp=createRequire(import.meta.url)('sharp');
const root=new URL('./',import.meta.url);
const write=(name,bytes)=>writeFile(new URL(name,root),bytes);
// Median samples of the inner strip in the supplied crop. No bezel is copied.
const colors=['#46633b','#6a9051','#8fba65','#b0be66','#d3c067','#d2a160','#c27258','#c36e73','#c87798','#aa6ea5','#7c5da7','#5752a2','#434e93','#2f4a82'];
const curves=[
 'M0 118 C33 136 59 177 103 180 C144 181 177 127 207 127 C248 125 268 171 309 205 C345 235 366 251 405 229 C435 218 468 186 493 192 C528 203 546 242 583 250 C624 264 660 205 690 163 C718 124 745 104 772 116',
 'M0 193 C57 233 123 294 180 301 C247 318 292 248 334 199 C373 146 411 93 461 87 C518 79 563 126 620 132 C677 139 736 97 772 60',
 'M0 245 C65 272 127 197 183 166 C248 128 311 175 369 193 C426 213 474 189 527 144 C575 103 613 51 661 65 C708 76 730 109 772 116'
];
const f=n=>Number(n.toFixed(4));
function mapPath(d){
 return d.replace(/([MC])([^MC]*)/g,(_,c,raw)=>{
  const a=raw.trim().split(/\s+/).map(Number);
  for(let i=0;i<a.length;i+=2){a[i]=f(a[i]*1024/772);a[i+1]=f(512+(a[i+1]-185)*.68);}
  return c+a.join(' ');
 });
}
function render(size){
 const stops=colors.map((c,i)=>`<stop offset="${f(i/(colors.length-1))}" stop-color="${c}"/>`).join('');
 return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024" role="img" aria-label="Aether spectrum and white sound waves"><defs><linearGradient id="spectrum" gradientUnits="userSpaceOnUse" x1="0" y1="512" x2="1024" y2="512" color-interpolation="sRGB">${stops}</linearGradient></defs><rect width="1024" height="1024" fill="#000000"/><rect data-part="spectrum-band" x="0" y="404" width="1024" height="216" fill="url(#spectrum)"/><g data-part="sound-waves" fill="none" stroke="#ffffff" stroke-width="1.85" stroke-linecap="round" stroke-linejoin="round">${curves.map(d=>`<path d="${mapPath(d)}"/>`).join('')}</g></svg>`;
}
await write('spectrum-wave.svg',render(3072));
await sharp(Buffer.from(render(3072))).png().toFile(new URL('spectrum-wave-3072.png',root).pathname);
await sharp(Buffer.from(render(1024))).png().toFile(new URL('preview.png',root).pathname);
await write('index.html',`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aether · 色带与声波</title><style>:root{color-scheme:dark;background:#000;color:#aaa;font-family:Arial,"PingFang SC",sans-serif}*{box-sizing:border-box}body{margin:0}header{position:fixed;top:0;left:0;right:0;z-index:1;height:56px;padding:0 24px;display:flex;align-items:center;gap:24px;font-size:11px;letter-spacing:1.5px}header span{flex:1}a{color:#aaa;text-decoration:none}a:hover{color:#fff}main{height:100dvh;display:flex;align-items:center;justify-content:center;overflow:hidden}img{display:block;width:100vw;height:auto;aspect-ratio:1;flex-shrink:0}a:focus-visible{outline:1px solid #fff;outline-offset:5px}@media(max-width:520px){header{padding:0 16px;gap:16px;font-size:10px;letter-spacing:1px}}</style><header><span>AETHER / SPECTRUM WAVE</span><a href="spectrum-wave.svg" target="_blank" rel="noopener">SVG</a><a href="spectrum-wave-3072.png" target="_blank" rel="noopener">3072 PX</a></header><main><img src="spectrum-wave.svg" alt="纯黑背景，正中贯穿左右边缘的渐变色带，叠加三条纯白细声波线" width="1024" height="1024"></main></html>`);
const files=['build.mjs','README.md','index.html','spectrum-wave.svg','spectrum-wave-3072.png','preview.png','references/gradient.png','references/waveform.png'];
const entries=[];
for(const file of files){const b=await readFile(new URL(file,root));entries.push({file,bytes:b.length,sha256:createHash('sha256').update(b).digest('hex')});}
await write('manifest.json',JSON.stringify({date:'2026-10-25',study:'Spectrum wave',size:3072,viewBox:1024,band:{x:0,y:404,width:1024,height:216},waveCount:3,stroke:'#ffffff',strokeWidth:1.85,background:'#000000',colors,provenance:'Palette sampled from the supplied gradient crop; three cubic curves reconstructed from the supplied waveform. Native flat SVG study, no generated raster, grid, frame, glow or text inside the artwork.',files:entries},null,2)+'\n');
console.log('Rendered one centered edge-to-edge spectrum-wave study: SVG, 3072px PNG and 1024px preview.');
