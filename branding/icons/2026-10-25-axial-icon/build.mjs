import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import {render,geometry,colors} from './geometry.mjs';
const sharp=createRequire(import.meta.url)('sharp'),root=new URL('./',import.meta.url);
const write=(path,data)=>writeFile(new URL(path,root),data);
async function png(size){
 const {data,info}=await sharp(Buffer.from(render(size,{singleHalf:true}))).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 const a=await sharp(data,{raw:info}).extract({left:0,top:0,width:size/2,height:size}).png().toBuffer();
 const b=await sharp(a).flop().png().toBuffer();
 return sharp({create:{width:size,height:size,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).composite([{input:a,left:0,top:0},{input:b,left:size/2,top:0}]).png().toBuffer();
}
if(!process.argv.includes('--reuse-flow')){
 await write('axial-icon.svg',render());
 await write('preview.png',await png(1024));
 if(!process.argv.includes('--preview'))await write('axial-icon-3072.png',await png(3072));
}
// Round the existing native J3-01 master and its matching production exports.
// No color or geometry in the interior is re-rendered for these PNG copies.
let greenSVG=await readFile(new URL('../2026-10-25-symmetric-gradient/svg/J3-01.svg',root),'utf8');
greenSVG=greenSVG.replace('</defs>',`<clipPath id="rounded-green"><rect width="1024" height="1024" rx="${geometry.cornerRadius}"/></clipPath></defs><g clip-path="url(#rounded-green)">`).replace('</svg>','</g></svg>');
await write('green-white-rounded.svg',greenSVG);
async function roundCopy(source,size,target){
 const mask=Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024"><rect width="1024" height="1024" rx="${geometry.cornerRadius}" fill="#fff"/></svg>`);
 const {data,info}=await sharp(source).ensureAlpha().composite([{input:mask,blend:'dest-in'}]).raw().toBuffer({resolveWithObject:true});
 const a=await sharp(data,{raw:info}).extract({left:0,top:0,width:size/2,height:size}).png().toBuffer(),b=await sharp(a).flop().png().toBuffer();
 await write(target,await sharp({create:{width:size,height:size,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).composite([{input:a,left:0,top:0},{input:b,left:size/2,top:0}]).png().toBuffer());
}
await roundCopy(await readFile(new URL('../2026-10-25-symmetric-gradient/previews/J3-01.png',root)),896,'green-white-rounded.png');
if(!process.argv.includes('--preview'))await roundCopy(await readFile(new URL('../2026-10-25-symmetric-gradient/exports/J3-01.png',root)),3072,'green-white-rounded-3072.png');
function page(green){
 const stem=green?'green-white-rounded':'axial-icon',src=green?'green-white-rounded.png':'preview.png';
 return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aether · ${green?'绿白圆角图标':'圆角流光图标'}</title><style>*{box-sizing:border-box}:root{background:#e9e9e5;color:#343c37;font-family:Arial,"PingFang SC",sans-serif}body{margin:0}header{min-height:62px;display:flex;align-items:center;gap:24px;padding:16px 26px;font-size:11px;letter-spacing:1.5px;flex-wrap:wrap}header span{flex:1}a{color:inherit;text-decoration:none}a:hover{text-decoration:underline}main{height:calc(100dvh - 62px);display:flex;align-items:center;justify-content:center;padding:22px}img{display:block;width:min(77vh,85vw);height:auto;max-height:100%;object-fit:contain}a:focus-visible{outline:1px solid;outline-offset:4px}@media(max-width:650px){header{gap:15px;font-size:10px;letter-spacing:.7px}header span{flex-basis:100%}main{height:calc(100dvh - 90px)}}</style><header><span>AETHER / AXIAL ICON</span><a href="index.html">绿白圆角</a><a href="flow.html">流光修订</a><a href="${stem}.svg" target="_blank" rel="noopener">SVG</a><a href="${stem}-3072.png" target="_blank" rel="noopener">3072 PX</a></header><main><img src="${src}" alt="${green?'原绿白图形，仅新增圆角，透明四角':'浅粉与浅紫的光向下流向上移的明亮绿色星芒，圆角黑底'}"></main></html>`;
}
await write('index.html',page(true));
await write('flow.html',page(false));
if(!process.argv.includes('--preview')){
 const files=[];
 for(const file of ['README.md','geometry.mjs','build.mjs','index.html','flow.html','axial-icon.svg','preview.png','axial-icon-3072.png','green-white-rounded.svg','green-white-rounded.png','green-white-rounded-3072.png','references/previous.png','references/green-white-original.png']){const b=await readFile(new URL(file,root));files.push({file,bytes:b.length,sha256:createHash('sha256').update(b).digest('hex')});}
 await write('manifest.json',JSON.stringify({date:'2026-10-25',geometry,colors,source:'../2026-10-25-fine-studies/contours.json',sourceSha256:createHash('sha256').update(await readFile(new URL('../2026-10-25-fine-studies/contours.json',root))).digest('hex'),files},null,2)+'\n');
}
console.log(process.argv.includes('--preview')?'Rounded axial icon preview ready.':'Rounded axial icon SVG, 3072px PNG, preview and manifest ready.');
