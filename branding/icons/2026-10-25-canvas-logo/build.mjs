import {writeFile,readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
const sharp=createRequire(import.meta.url)('sharp'),root=new URL('./',import.meta.url);
const write=(name,data)=>writeFile(new URL(name,root),data);
const f=n=>Number(n.toFixed(6));
export function render(logicalSize=512,outputSize=logicalSize,rounded=false){
 const c=logicalSize/2,maxRadius=logicalSize*.4,coreRadius=maxRadius*.5;
 const clip=`<clipPath id="icon"><rect width="${logicalSize}" height="${logicalSize}" rx="${logicalSize*200/1024}"/></clipPath>`;
 const circles=Array.from({length:120},(_,i)=>{
  const p=i/120,r=maxRadius*(.3+.7*p);
  return `<circle data-index="${i}" cx="${c}" cy="${f(c-10*p)}" r="${f(r)}" fill="none" stroke="#ffffff" stroke-opacity="${f((1-p)*.4)}" stroke-width="1"/>`;
 }).join('');
 return `<svg xmlns="http://www.w3.org/2000/svg" width="${outputSize}" height="${outputSize}" viewBox="0 0 ${logicalSize} ${logicalSize}" role="img" aria-label="Aether supplied Canvas logo study"><defs><radialGradient id="core" gradientUnits="userSpaceOnUse" cx="${c}" cy="${c}" r="${coreRadius}" color-interpolation="sRGB"><stop offset="0" stop-color="#fde8e0"/><stop offset=".3" stop-color="#f3c785"/><stop offset=".6" stop-color="#91b879"/><stop offset="1" stop-color="#000000" stop-opacity="0"/></radialGradient>${rounded?clip:''}</defs><g${rounded?' clip-path="url(#icon)"':''}><rect width="${logicalSize}" height="${logicalSize}" fill="#0b0d12"/><circle data-part="core" cx="${c}" cy="${c}" r="${coreRadius}" fill="url(#core)"/>${circles}</g></svg>`;
}
const source=render(512,3072),icon=render(512,3072,true);
await write('canvas-original.svg',source);
await write('canvas-logo.svg',icon);
await sharp(Buffer.from(icon)).png().toFile(new URL('canvas-logo-3072.png',root).pathname);
await sharp(Buffer.from(render(512,1024,true))).png().toFile(new URL('preview.png',root).pathname);
await write('small-code-48.svg',render(48,48,true));
await sharp(Buffer.from(render(48,288,true))).png().toFile(new URL('small-code-48.png',root).pathname);
await sharp(Buffer.from(render(512,48,true))).png().toFile(new URL('scaled-icon-48.png',root).pathname);
await write('index.html',`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aether · Canvas Logo</title><style>*{box-sizing:border-box}:root{font-family:Arial,"PingFang SC",sans-serif;color:#414844;background:#e9e9e5}body{margin:0}header{min-height:62px;display:flex;align-items:center;gap:24px;padding:18px 26px;font-size:11px;letter-spacing:1.3px;flex-wrap:wrap}header span{flex:1}a{color:inherit;text-decoration:none}a:hover{text-decoration:underline}main{min-height:calc(100dvh - 62px);display:flex;align-items:center;justify-content:center;gap:48px;padding:28px}.hero{display:block;width:min(72vh,70vw);height:auto}.samples{display:flex;flex-direction:column;gap:28px;width:180px;font-size:11px;line-height:1.7}.samples figure{margin:0}.samples img{display:block;width:96px;height:96px;margin-bottom:10px}.note{color:#727a74;font-size:10px}a:focus-visible{outline:1px solid;outline-offset:4px}@media(max-width:800px){main{flex-direction:column;gap:24px}.hero{width:min(72vh,85vw)}.samples{width:auto;display:flex;flex-direction:row;gap:30px}}</style><header><span>AETHER / CANVAS LOGO / 120</span><a href="canvas-original.svg" target="_blank" rel="noopener">原代码比例</a><a href="canvas-logo.svg" target="_blank" rel="noopener">圆角 SVG</a><a href="canvas-logo-3072.png" target="_blank" rel="noopener">3072 PX</a><a href="AetherLogoCanvas.kt" target="_blank" rel="noopener">KOTLIN</a></header><main><img class="hero" src="canvas-logo.svg" alt="深蓝黑底，中央淡粉金绿渐变圆，外围120条逐渐上移和变淡的细白圆环"><div class="samples"><figure><img src="scaled-icon-48.png" alt="将512基准设计整体缩到48"><figcaption>设计整体缩到 48<div class="note">等比缩放后放大 2× 查看</div></figcaption></figure><figure><img src="small-code-48.svg" alt="48逻辑尺寸下仍保持1单位笔宽与10单位位移的原代码"><figcaption>原参数直接画在 48<div class="note">固定线宽和位移，放大 2× 查看</div></figcaption></figure></div></main></html>`);
const files=[];
for(const file of ['README.md','AetherLogoCanvas.kt','build.mjs','index.html','canvas-original.svg','canvas-logo.svg','canvas-logo-3072.png','preview.png','small-code-48.svg','small-code-48.png','scaled-icon-48.png']){const b=await readFile(new URL(file,root));files.push({file,bytes:b.length,sha256:createHash('sha256').update(b).digest('hex')});}
await write('manifest.json',JSON.stringify({date:'2026-10-25',method:'SVG reconstruction of user-provided Compose Canvas parameters; not Android-rendered evidence',logicalReferenceSize:512,referenceDensity:1,lineCount:120,lineWidth:1,centerYShift:10,coreRadiusFraction:.2,outerRadiusFraction:.4,background:'#0B0D12',coreColors:['#FDE8E0','#F3C785','#91B879','transparent'],roundedPresentationRadiusFraction:200/1024,files},null,2)+'\n');
console.log('Canvas logo preview ready: supplied 120-ring logic, rounded presentation, and small-size comparisons.');
