import {mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {dirname,join} from 'node:path';
import {concepts,families,renderGeometry} from './geometry.mjs';
const root=dirname(fileURLToPath(import.meta.url));
await Promise.all(['icons','mono'].map(p=>mkdir(join(root,p),{recursive:true})));
for(const c of concepts){
 await writeFile(join(root,'icons',c.file),renderGeometry(c.id));
 await writeFile(join(root,'mono',c.file),renderGeometry(c.id,{mono:true}));
}
await writeFile(join(root,'concepts.json'),JSON.stringify(concepts.map(c=>({...c,familyName:families[c.family]})),null,2)+'\n');
let cells='';
for(const c of concepts.slice(0,16)){
 const i=Number(c.id)-1,x=48+(i%4)*294,y=158+Math.floor(i/4)*322;
 const svg=renderGeometry(c.id).replace('<svg ','<svg x="'+x+'" y="'+y+'" width="270" height="270" ');
 cells+=svg+`<text x="${x}" y="${y+294}" font-size="15" fill="#c7c9c4">${c.id}  ${c.name}</text><text x="${x+270}" y="${y+294}" text-anchor="end" font-size="9" letter-spacing=".6" fill="#8f9696">${c.english}</text>`;
}
const board=`<svg xmlns="http://www.w3.org/2000/svg" width="1248" height="1490" viewBox="0 0 1248 1490"><rect width="1248" height="1490" fill="#111517"/><g font-family="Arial, PingFang SC, sans-serif"><text x="48" y="53" fill="#eeece3" font-size="18" letter-spacing="3">AETHER / GEOMETRIC STUDIES</text><text x="48" y="101" fill="#eeece3" font-size="30">漫频 · 十六种不可见的频率</text><text x="1200" y="52" fill="#a9b0b0" font-size="12" text-anchor="end">16 ORIGINAL SVG MARKS · 2026.10.24</text><text x="1200" y="99" fill="#a9b0b0" font-size="13" text-anchor="end">平面几何 / 波纹 / 点阵 / 轨道 / 负形</text>${cells}<text x="48" y="1470" fill="#8f9696" font-size="11">GEOMETRY FIRST · SOLID INK · NO GRADIENTS · NO RASTER GENERATION</text></g></svg>`;
await writeFile(join(root,'overview.svg'),board);
let extra='';
for(const c of concepts.slice(16)){
 const x=c.id==='17'?48:636;
 extra+=renderGeometry(c.id).replace('<svg ','<svg x="'+x+'" y="145" width="540" height="540" ');
 extra+=`<text x="${x}" y="727" font-size="24" fill="#e7e9e5">${c.id} · ${c.name}</text><text x="${x}" y="756" font-size="13" fill="#8c9b9d">${c.rule}</text>`;
}
await writeFile(join(root,'blue-studies.svg'),`<svg xmlns="http://www.w3.org/2000/svg" width="1224" height="810" viewBox="0 0 1224 810"><rect width="1224" height="810" fill="#111517"/><g font-family="Arial, PingFang SC, sans-serif"><text x="48" y="53" fill="#e7e9e5" font-size="18" letter-spacing="3">AETHER / BLUE MEDIUM</text><text x="48" y="103" fill="#e7e9e5" font-size="28">蓝色介质 · 以线条形成空间</text>${extra}</g></svg>`);
console.log(`Built ${concepts.length} authored color SVGs, ${concepts.length} monochrome SVGs and two vector overviews.`);
