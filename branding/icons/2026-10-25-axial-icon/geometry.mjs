import {artwork} from '../2026-10-25-fine-studies/geometry.mjs';
export const geometry={viewBox:1024,cornerRadius:200,ringDrop:136,starLift:56};
export const colors={green:'#8de5b1',gold:'#ffe5a6',pink:'#ffe1ed',lavender:'#d9ccff'};
const ramps=[[0,'#000000'],[.035,'#030706'],[.18,'#18422d'],[.38,'#4eac7b'],[.54,colors.green],[.70,'#c7edb0'],[.82,colors.gold],[.90,'#ffe5d3'],[1,colors.pink]];
const rgb=c=>[1,3,5].map(i=>parseInt(c.slice(i,i+2),16));
const mix=(a,b,t)=>a.map((v,i)=>v+(b[i]-v)*t);
const hex=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
const f=n=>Number(n.toFixed(4));
const ease=(a,b,x)=>{const t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);};
const gauss=(x,c,w)=>Math.exp(-(((x-c)/w)**2));
function tone(gray){
 const v=Math.max(0,Math.min(1,gray/219));
 for(let i=1;i<ramps.length;i++)if(v<=ramps[i][0])return mix(rgb(ramps[i-1][1]),rgb(ramps[i][1]),(v-ramps[i-1][0])/(ramps[i][0]-ramps[i-1][0]));
 return rgb(colors.pink);
}
// A continuous deformation of the reference-fitted surfaces, shared by all
// contour levels. It lowers the ring and lifts the entire star, including arms.
export function warp(x,y){
 const dx=x-512,ring=(1-ease(220,480,Math.abs(dx)))*(1-ease(430,720,y));
 const flow=gauss(y,280,145)*ring;
 return [512+dx*(1-.10*flow),y+geometry.ringDrop*gauss(dx,0,175)*flow-geometry.starLift*(1-ring)];
}
export function render(size=3072,{singleHalf=false}={}){
 let {defs,body}=artwork('J3','mono');
 body=body.replace(/d="([^"]+)"/g,(_,d)=>`d="${d.replace(/([MC])([^MCZ]*)/g,(_,cmd,coords)=>{
  const a=coords.match(/-?\d*\.?\d+(?:e[-+]?\d+)?/gi).map(Number);
  for(let i=0;i<a.length;i+=2)[a[i],a[i+1]]=warp(a[i],a[i+1]);
  return cmd+a.map(f).join(' ');
 })}"`);
 for(const [i,c]of [...new Set(body.match(/#[0-9a-f]{6}/g))].entries()){
  const gray=parseInt(c.slice(1,3),16),v=gray/219,base=tone(gray);
  const top=mix(base,rgb(colors.lavender),.72*ease(.78,.98,v));
  const green=gauss(v,.45,.25),lower=base.map(ch=>ch+(255-ch)*.12*green);
  defs+=`<linearGradient id="flow-${i}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="1024" color-interpolation="sRGB"><stop stop-color="${hex(top)}"/><stop offset=".10" stop-color="${hex(top)}"/><stop offset=".29" stop-color="${hex(base)}"/><stop offset=".43" stop-color="${hex(base)}"/><stop offset=".59" stop-color="${hex(lower)}"/><stop offset="1" stop-color="${hex(lower)}"/></linearGradient>`;
  body=body.replaceAll(`fill="${c}"`,`fill="url(#flow-${i})"`);
 }
 defs+=`<filter id="soft" x="-3%" y="-3%" width="106%" height="106%"><feGaussianBlur stdDeviation="2.6"/></filter><clipPath id="icon"><rect width="1024" height="1024" rx="${geometry.cornerRadius}"/></clipPath><clipPath id="left"><rect width="512" height="1024"/></clipPath><g id="half" clip-path="url(#left)"><g filter="url(#soft)">${body}</g></g>`;
 const left=singleHalf?`<g clip-path="url(#left)"><g filter="url(#soft)">${body}</g></g>`:'<use href="#half"/>';
 return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024" role="img" aria-label="Aether rounded axial light icon"><defs>${defs}</defs><g clip-path="url(#icon)"><rect width="1024" height="1024" fill="#000000"/>${left}${singleHalf?'':'<use href="#half" transform="translate(1024 0) scale(-1 1)"/>'}</g></svg>`;
}
