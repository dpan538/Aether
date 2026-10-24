// Native SVG export. Requires the installed sharp package, no image generation.
import {createRequire} from 'node:module';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {studies,render,colors} from './geometry.mjs';
const sharp=createRequire(import.meta.url)('sharp');
const root=new URL('./',import.meta.url);
await mkdir(new URL('./exports/',root),{recursive:true});
for(const s of studies)await sharp(Buffer.from(render(s.id))).png().toFile(new URL(`./exports/${s.id}-${s.slug}.png`,root).pathname);
const image=async(path,x,y,w,h)=>`<image x="${x}" y="${y}" width="${w}" height="${h}" preserveAspectRatio="xMidYMid meet" href="data:image/png;base64,${(await readFile(new URL(path,root))).toString('base64')}"/>`;
const nested=(s,x,y,size,mono=false)=>render(s.id,{mono}).replace(/<svg [^>]*>/,`<svg x="${x}" y="${y}" width="${size}" height="${size}" viewBox="0 0 512 512">`);
const t=(x,y,text,size=14,fill='#a9bbc4',extra='')=>`<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" ${extra}>${text}</text>`;
let content=`<rect width="1440" height="1370" fill="#111519"/>`;
content+=t(48,43,'AETHER / IDENTITY FROM THE INTERFACE / 03',12,'#91a3ac','letter-spacing="2"');
content+=t(48,106,'ONE VISUAL LANGUAGE.',48,'#e5e8e6','font-weight="500"');
content+=t(48,141,'PHASE / APERTURE / ECHO / CALIBRATION',12,'#91a3ac','letter-spacing="2"');
content+=await image('context/modern-ink.png',48,172,660,440);
content+=await image('context/immersive.png',732,172,660,440);
content+=t(48,640,'01 / MODERN — PRINTED CALIBRATION, INK &amp; SILVER',12);
content+=t(732,640,'02 / IMMERSIVE — ECCENTRIC MOON, APERTURE &amp; ECHO',12);
content+='<path d="M48 665H1392M48 729H1392" stroke="#35424b"/>';
content+=t(48,704,'SHARED PALETTE',11,'#8ba0ac','letter-spacing="1"');
for(const [i,fill] of [colors.background,colors.paper,colors.gold,colors.ice,colors.blue].entries())content+=`<rect x="${205+i*36}" y="688" width="28" height="15" fill="${fill}"/>`;
content+=t(440,704,'DARK FIELD / PALE PIGMENT / ICE-BLUE CONTOUR / QUIET LINEWORK',11);
const titles=['LUNAR DISC','PHASE ECHO','TUNING PHASE'];
const notes=['Original phase ratio and spindle aperture.','Reduced grain, wider aperture, stronger echoes.','The same lunar geometry with printed calibration.'];
for(const [i,s] of studies.entries()){
 const x=48+i*456;
 content+=nested(s,x,754,432);
 content+=t(x,1217,s.id,11,'#7f98a4')+t(x+36,1217,titles[i],22,'#e5e8e6');
 content+=t(x,1243,notes[i],11);
 content+=nested(s,x,1264,48)+nested(s,x+78,1256,64,true);
 content+=t(x+162,1292,'48 PX / MONO 64',10,'#7f98a4');
}
content+='<path d="M48 1334H1392" stroke="#35424b"/>';
content+=t(48,1356,'AETHER / THREE RELATED STUDIES — FINAL ICON NOT SELECTED',10,'#7f98a4');
const svg=`<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1440" height="1370" viewBox="0 0 1440 1370" font-family="Arial,Helvetica,sans-serif">${content}</svg>\n`;
await writeFile(new URL('./overview.svg',root),svg);
await sharp(Buffer.from(svg)).png().toFile(new URL('./overview.png',root).pathname);
console.log('Exported three 1024px PNGs and the 1440×1370 interface-context board.');
