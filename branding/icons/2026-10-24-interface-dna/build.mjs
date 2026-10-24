import {mkdir,writeFile} from 'node:fs/promises';
import {studies,render,colors} from './geometry.mjs';
for(const directory of ['icons','mono'])await mkdir(new URL('./'+directory+'/',import.meta.url),{recursive:true});
for(const s of studies){
 await writeFile(new URL(`./icons/${s.id}-${s.slug}.svg`,import.meta.url),render(s.id));
 await writeFile(new URL(`./mono/${s.id}-${s.slug}.svg`,import.meta.url),render(s.id,{mono:true}));
}
await writeFile(new URL('./metadata.json',import.meta.url),JSON.stringify({date:'2026-10-24',status:'UI-derived studies, not approved launcher',colors,studies},null,2)+'\n');
console.log('Built three UI-derived color SVGs and three monochrome SVGs.');
