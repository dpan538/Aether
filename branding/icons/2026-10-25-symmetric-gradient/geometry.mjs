import {artwork as originalArtwork} from '../2026-10-25-fine-studies/geometry.mjs';
export const palette={green:'#4e7759',gold:'#d5bb67',orange:'#f6b98d',pink:'#ffd0df',purple:'#d5c5f4'};
export const samples={method:'Original green/gold hue progression retained with restrained exposure. Pale pink and lavender extend a broad, continuous highlight gradient directly; no color overlays.',reference:'references/original-green-gold.png'};
export const studies=[
 {id:'J3',name:'轴向流光',en:'AXIAL FLOW',detail:[300,470,424,424],note:'圆环沿中轴下垂流向星芒，绿光提亮；03 在浅粉中加入浅紫。'},
 {id:'J5',name:'对称折面球',en:'SYMMETRIC FOLD',detail:[300,290,424,424],note:'由同一半幅折面镜像构成，保留一致的层次。'},
 {id:'J6',name:'对称放射球',en:'RADIAL PHASE',detail:[350,350,424,424],note:'同心细射线与球体明暗、颗粒均左右对称。'}
];
export const variants=[
 {id:'01',name:'绿黄提亮',high:[],note:'保留原绿／黄，适度提亮'},
 {id:'02',name:'浅橙高光',high:[[.89,'#ecd3a1'],[1,palette.orange]],note:'绿／黄自然过渡到浅橙'},
 {id:'03',name:'浅粉高光',high:[[.87,'#ecd1ad'],[1,palette.pink]],note:'宽幅暖色过渡，顶部为浅粉'},
 {id:'04',name:'浅紫高光',high:[[.87,'#ded0b8'],[.95,'#d9c9de'],[1,palette.purple]],note:'绿／黄柔和过渡到淡紫'},
 {id:'05',name:'浅粉紫高光',high:[[.87,'#ecd1b6'],[.95,palette.pink],[1,palette.purple]],note:'浅粉到浅紫的连续高光'}
];
export const variations={
 J3:[{x:1,y:1,dy:0},{x:1.025,y:.99,dy:-2},{x:.985,y:1.02,dy:0},{x:1.015,y:1.025,dy:-2},{x:.97,y:1.01,dy:0}],
 J5:[{x:1,y:1,dy:0},{x:1.02,y:1.01,dy:-2},{x:1.035,y:.99,dy:0},{x:1.015,y:1.025,dy:0},{x:.98,y:1.01,dy:-2}],
 J6:[{x:1,y:1,dy:0,rays:224},{x:1.01,y:1.01,dy:0,rays:232},{x:1.02,y:.99,dy:0,rays:240},{x:1.01,y:1.01,dy:0,rays:248},{x:.985,y:.985,dy:0,rays:256}]
};
const rgb=c=>[1,3,5].map(i=>parseInt(c.slice(i,i+2),16));
const mix=(a,b,t)=>rgb(a).map((v,i)=>v+(rgb(b)[i]-v)*t);
function hsv(c){const a=c.map(v=>v/255),max=Math.max(...a),min=Math.min(...a),d=max-min;let h=0;if(d){if(max===a[0])h=((a[1]-a[2])/d+6)%6;else if(max===a[1])h=(a[2]-a[0])/d+2;else h=(a[0]-a[1])/d+4;}return [h/6,max?d/max:0,max];}
function fromHSV(h,s,v){const k=n=>(n+h*6)%6,f=n=>v-v*s*Math.max(0,Math.min(k(n),4-k(n),1));return [f(5),f(3),f(1)].map(x=>x*255);}
export function colorAt(gray,number,id){
 const max={J3:219,J5:234,J6:213}[id],value=Math.max(0,Math.min(1,gray*255/max));
 const high=variants[number-1].high;
 const stops=[[8/max,'#080808'],[.46,palette.green],[.79,palette.gold],...high,...(high.length?[]:[[1,'#f6fff9']])];
 let c=rgb('#080808');
 for(let i=1;i<stops.length;i++)if(value<=stops[i][0]){c=mix(stops[i-1][1],stops[i][1],Math.max(0,(value-stops[i-1][0])/(stops[i][0]-stops[i-1][0])));break;}
 const [h,s,v]=hsv(c),lift=8/255+237/255*Math.min(1,1.10*Math.pow(Math.max(0,(v*255-8)/233),.76));
 return fromHSV(h,Math.min(.75,s*1.08+.02*s/(s+.10)),Math.min(245/255,lift));
}
const f=n=>Number(n.toFixed(5));
function toneFilter(id,number){
 // SVG renderers support up to 256 table entries; interpolation stays continuous.
 const values=Array.from({length:256},(_,i)=>colorAt(i/255,number,id));
 return `<filter id="tone" x="-1%" y="-1%" width="102%" height="102%" color-interpolation-filters="sRGB"><feGaussianBlur stdDeviation="${id==='J5'?4:id==='J3'?2:.15}"/><feComponentTransfer>${['R','G','B'].map((c,i)=>`<feFunc${c} type="table" tableValues="${values.map(v=>f(v[i]/255)).join(' ')}"/>`).join('')}</feComponentTransfer></filter>`;
}
function grain(strength){return `<filter id="grain" x="-3%" y="-3%" width="106%" height="106%" color-interpolation-filters="sRGB"><feTurbulence type="fractalNoise" baseFrequency="1.25" numOctaves="1" seed="25" result="noise"/><feColorMatrix in="noise" type="saturate" values="0"/><feComponentTransfer result="factor">${['R','G','B'].map(c=>`<feFunc${c} type="linear" slope="${strength}" intercept="${1-strength}"/>`).join('')}<feFuncA type="linear" slope="0" intercept="1"/></feComponentTransfer><feComposite in="SourceGraphic" in2="factor" operator="arithmetic" k1="1" k2="0" k3="0" k4="0"/></filter>`;}
function rays(n){let body='';const pt=(r,a)=>`${f(512+r*Math.cos(a))} ${f(512+r*Math.sin(a))}`;for(let i=0;i<n;i++){const a=i*2*Math.PI/n-Math.PI/2,h=Math.PI/n*.63;body+=`<path data-ray="${i}" d="M${pt(148.5,a-h)}L${pt(462,a-.00036)}L${pt(462,a+.00036)}L${pt(148.5,a+h)}Z" fill="#080808"/>`;}return `<g data-part="rays">${body}</g>`;}
function axialFlow(body,number){
 const amount=[28,32,38,42,46][number-1];
 // Deform the existing contours smoothly; no extra beam is placed over them.
 body=body.replace(/d="([^"]+)"/g,(_,d)=>`d="${d.replace(/([MC])([^MCZ]*)/g,(_,cmd,coords)=>{
  const a=coords.match(/-?\d*\.?\d+(?:e[-+]?\d+)?/gi).map(Number);
  for(let i=0;i<a.length;i+=2){const x=a[i],y=a[i+1];a[i+1]=y+amount*Math.exp(-(((x-512)/170)**2))*Math.exp(-(((y-285)/115)**2));}
  return cmd+a.map(f).join(' ');
 })}"`);
 const hex=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
 const blend=(a,b,t)=>a.map((v,i)=>v+(b[i]-v)*t);
 let defs='';const colors=[...new Set(body.match(/#[0-9a-f]{6}/g))];
 for(const [i,c]of colors.entries()){
  const gray=parseInt(c.slice(1,3),16),base=colorAt(gray/255,number,'J3'),[h,s,v]=hsv(base);
  const green=Math.exp(-(((gray/219-.45)/.28)**2));
  const lower=fromHSV(h,s,Math.min(.96,v+(1-v)*.32*green));
  const top=number===3?blend(base,rgb(palette.purple),.68*Math.max(0,Math.min(1,(gray/219-.78)/.18))):base;
  defs+=`<linearGradient id="flow-${i}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="1024" color-interpolation="sRGB"><stop stop-color="${hex(top)}"/><stop offset=".08" stop-color="${hex(top)}"/><stop offset=".24" stop-color="${hex(base)}"/><stop offset=".37" stop-color="${hex(base)}"/><stop offset=".55" stop-color="${hex(lower)}"/><stop offset="1" stop-color="${hex(lower)}"/></linearGradient>`;
  body=body.replaceAll(`fill="${c}"`,`fill="url(#flow-${i})"`);
 }
 defs+='<filter id="flow-soft" x="-2%" y="-2%" width="104%" height="104%"><feGaussianBlur stdDeviation="2"/></filter>';
 return{defs,body:`<g filter="url(#flow-soft)">${body}</g>`};
}
function radialPaint(defs,body,number){
 const hex=a=>'#'+a.map(v=>Math.max(0,Math.min(255,Math.round(v))).toString(16).padStart(2,'0')).join('');
 const shadow=[[0,8],[.54,16],[.72,31],[.9,119],[1,213]];
 const stops=Array.from({length:256},(_,i)=>{
  const t=i/255;let g=213;
  for(let j=1;j<shadow.length;j++)if(t<=shadow[j][0]){const[a,x]=shadow[j-1],[b,y]=shadow[j];g=x+(y-x)*(t-a)/(b-a);break;}
  return `<stop offset="${f(t)}" stop-color="${hex(colorAt(g/255,number,'J6'))}"/>`;
 }).join('');
 defs=defs.replace(/<linearGradient id="orb-tone"[\s\S]*?<\/linearGradient>/,`<linearGradient id="orb-tone" x1="0" y1="0" x2="1" y2=".1" color-interpolation="sRGB">${stops}</linearGradient>`);
 // Retain each particle's position/radius while batching its opacity to sixteen
 // levels. Thousands of separate opacity surfaces otherwise stall SVG export.
 body=body.replace(/<g data-part="micro-stipple"[^>]*>([\s\S]*?)<\/g>/,(_,dots)=>{
  const buckets=Array.from({length:16},()=>[]);
  for(const m of dots.matchAll(/<circle cx="([^"]+)" cy="([^"]+)" r="([^"]+)" opacity="([^"]+)"\/>/g)){
   const[x,y,r,a]=m.slice(1).map(Number),i=Math.max(0,Math.min(15,Math.round(a*15)));
   buckets[i].push(`M${f(x-r)} ${f(y)}a${f(r)} ${f(r)} 0 1 0 ${f(2*r)} 0a${f(r)} ${f(r)} 0 1 0 ${f(-2*r)} 0Z`);
  }
  return `<g data-part="micro-stipple" fill="${hex(colorAt(1,number,'J6'))}">${buckets.map((d,i)=>d.length?`<path opacity="${f(i/15)}" d="${d.join('')}"/>`:'').join('')}</g>`;
 });
 return{defs,body};
}
export function render(id,number,{width=3072,height=3072,viewBox='0 0 1024 1024'}={}){
 const p=variations[id]?.[number-1];if(!p)throw new Error('Unknown symmetric variation');
 let {defs,body}=originalArtwork(id,'mono');
 if(id==='J3'){const flow=axialFlow(body,number);defs+=flow.defs;body=flow.body;}
 if(id==='J6'){body=body.replace(/<g data-part="rays">[\s\S]*?<\/g>/,rays(p.rays));const paint=radialPaint(defs,body,number);defs=paint.defs;body=paint.body;}
 const right=id==='J6',x=right?512:0;
 const matrix=`translate(512 ${512+p.dy}) scale(${p.x} ${p.y}) translate(-512 -512)`;
 // One half is defined once. Its mirror reuses the same geometry, colors,
 // light and grain. No independent left/right drawing or colored overlays.
 if(id==='J5')body=`<g filter="url(#tone)">${body}</g>`;
 if(id!=='J6')body=`<g filter="url(#grain)">${body}</g>`;
 defs+=(id==='J5'?toneFilter(id,number):'')+(id==='J6'?'':grain(.025))+`<clipPath id="half-clip"><rect x="${x}" y="-1024" width="512" height="3072"/></clipPath><g id="half" clip-path="url(#half-clip)"><g transform="${matrix}">${body}</g></g>`;
 let svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="${viewBox}" role="img" aria-label="Aether ${id} symmetric ${number}"><defs>${defs}</defs><rect x="-1024" y="-1024" width="3072" height="3072" fill="${id==='J6'?'#f7f7f7':'#080808'}"/><use href="#half"/><use href="#half" transform="translate(1024 0) scale(-1 1)"/></svg>`;
 const prefix=`${id}-${number}`;
 return svg.replace(/id="([^"]+)"/g,(_,s)=>`id="${prefix}-${s}"`).replace(/url\(#([^)]+)\)/g,(_,s)=>`url(#${prefix}-${s})`).replace(/href="#([^"]+)"/g,(_,s)=>`href="#${prefix}-${s}"`);
}
