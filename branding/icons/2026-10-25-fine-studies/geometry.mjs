// Deterministic geometry plus reference-fitted cubic contours; no raster is embedded.
import {readFileSync} from 'node:fs';
const fitted = JSON.parse(readFileSync(new URL('./contours.json',import.meta.url),'utf8'));
export const palette = {black:'#080808',paper:'#f1f1f1',ink:'#080808',silver:'#cccccc'};
// Median 7×7 patches from the user's specific CD crop, not a rainbow palette.
export const colorAnchors = {green:'#4e7759',gold:'#d5bb67',coral:'#c26364'};
export const variants = [
  {id:'mono',name:'黑白',en:'MONOCHROME',colors:[]},
  {id:'green-gold',name:'绿／金',en:'GREEN / GOLD',colors:[colorAnchors.green,colorAnchors.gold]},
  {id:'coral-gold',name:'珊瑚／金',en:'CORAL / GOLD',colors:[colorAnchors.coral,colorAnchors.gold]}
];
export const studies = [
  {id:'J1',slug:'negative-echo',name:'负空间回声',en:'NEGATIVE ECHO',reference:'R0-negative-space.png',note:'黑白主结构保留，配色集中于圆心。',detail:[248,300,528,450]},
  {id:'J3',slug:'axial-caustic',name:'轴向折光',en:'AXIAL CAUSTIC',reference:'R2-axial-light.png',note:'保留上方光环、两层弧面和向下收束的细轴。',detail:[330,480,364,364]},
  {id:'J5',slug:'phase-fold',name:'相位折叠',en:'PHASE FOLD',reference:'R4-phase-fold.png',note:'按原图折面与遮挡重建，灰阶和颗粒细度保持一致。',detail:[330,300,370,370]},
  {id:'J6',slug:'radial-phase',name:'放射相位',en:'RADIAL PHASE',reference:'R5-radial-phase.png',note:'224 枚等角收尖射线，球体亮缘与细颗粒保持独立。',detail:[500,340,360,360]}
];
const f=n=>Number(n.toFixed(4));
const pt=(x,y)=>`${f(x)} ${f(y)}`;
const polar=(r,a,cx=512,cy=512)=>[cx+r*Math.cos(a),cy+r*Math.sin(a)];
const line=(points,close=false)=>points.map((p,i)=>`${i?'L':'M'}${pt(...p)}`).join(' ')+(close?'Z':'');
function random(seed=25025){return()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};}

function radial(){
  const R=462,r=148.5,N=224;
  let rays='';
  for(let i=0;i<N;i++){
    const a=2*Math.PI*i/N-Math.PI/2;
    const innerHalf=Math.PI/N*.63,tipHalf=.00036;
    const coords=[polar(r,a-innerHalf),polar(R,a-tipHalf),polar(R,a+tipHalf),polar(r,a+innerHalf)];
    rays+=`<path data-ray="${i}" d="${line(coords,true)}" fill="${palette.ink}"/>`;
  }
  const rnd=random(26025);let dots='';
  // Micro stipple follows a fixed crescent density field, not arbitrary paper noise.
  for(let i=0;i<58000;i++){
    const x=512+(rnd()*2-1)*149,y=512+(rnd()*2-1)*149;
    const dist=Math.hypot(x-512,y-512);if(dist>148)continue;
    const edge=Math.hypot(x-470,y-493)-171;
    const chance=Math.max(0,Math.min(1,(edge+29)/48));
    if(rnd()>chance*.83)continue;
    const radius=.11+rnd()*.28;
    dots+=`<circle cx="${f(x)}" cy="${f(y)}" r="${f(radius)}" opacity="${f(.22+rnd()*.6)}"/>`;
  }
  return {defs:`<linearGradient id="orb-tone" x1="0" y1="0" x2="1" y2=".1"><stop stop-color="#080808"/><stop offset=".54" stop-color="#101010"/><stop offset=".72" stop-color="#1f1f1f"/><stop offset=".9" stop-color="#777777"/><stop offset="1" stop-color="#d5d5d5"/></linearGradient>`,body:`
    <g data-part="rays">${rays}</g>
    <circle data-part="orb" cx="512" cy="512" r="148" fill="url(#orb-tone)"/>
    <g data-part="micro-stipple" fill="${palette.paper}">${dots}</g>`};
}

const makers={J6:radial};
const rgb=hex=>[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16));
const mix=(a,b,t)=>'#'+rgb(a).map((v,i)=>Math.round(v+(rgb(b)[i]-v)*t).toString(16).padStart(2,'0')).join('');
function tone(hex,variant){
  const value=rgb(hex)[0]/241;
  const colors=variants.find(v=>v.id===variant).colors;
  const anchors=[[8/241,palette.black],[.46,colors[0]],[.79,colors[1]],[1,palette.paper]];
  if(value<=anchors[0][0])return palette.black;
  for(let i=1;i<anchors.length;i++)if(value<=anchors[i][0]){
    const [p,a]=anchors[i-1],[q,b]=anchors[i];
    return mix(a,b,(value-p)/(q-p));
  }
  return palette.paper;
}
function inkGradient(variant){
  const [a,b]=variants.find(v=>v.id===variant).colors;
  return `<linearGradient id="limited-ink" gradientUnits="userSpaceOnUse" x1="366" y1="419" x2="658" y2="607"><stop stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient>`;
}
export function artwork(id,variant='mono'){
  const original=fitted[id] || makers[id]?.();
  if(!original)throw new Error(`Unknown study ${id}`);
  if(!variants.some(v=>v.id===variant))throw new Error(`Unknown variant ${variant}`);
  let {defs,body}=original;
  if(variant==='mono')return {defs,body};
  if(id==='J1'){
    if(!original.accentPath)throw new Error('J1 requires the exact recovered core contour');
    defs+=inkGradient(variant);
    body+=`<path data-part="colored-core" d="${original.accentPath}" fill="url(#limited-ink)"/>`;
  }else{
    const recolor=s=>s.replace(/#[0-9a-f]{6}/gi,hex=>tone(hex,variant));
    defs=recolor(defs);body=recolor(body);
  }
  return {defs,body};
}
export function background(id){return ['J3','J5'].includes(id)?palette.black:palette.paper;}
export function render(id,{variant='mono',width=3072,height=3072,viewBox='0 0 1024 1024',prefix=id+'-'+variant}={}){
  let {defs,body}=artwork(id,variant);
  if(['J3','J5'].includes(id)){
    const strength=id==='J5'?.12:.035;
    // Fine deterministic print grain replaces coarse reference noise. Multiplying
    // all RGB channels by the same scalar preserves the restricted color families.
    defs+=`<filter id="fine-print" x="-3%" y="-3%" width="106%" height="106%" color-interpolation-filters="sRGB"><feTurbulence type="fractalNoise" baseFrequency="1.25" numOctaves="1" seed="25" result="grain"/><feColorMatrix in="grain" type="saturate" values="0"/><feComponentTransfer result="factor"><feFuncR type="linear" slope="${strength}" intercept="${1-strength}"/><feFuncG type="linear" slope="${strength}" intercept="${1-strength}"/><feFuncB type="linear" slope="${strength}" intercept="${1-strength}"/><feFuncA type="linear" slope="0" intercept="1"/></feComponentTransfer><feComposite in="SourceGraphic" in2="factor" operator="arithmetic" k1="1" k2="0" k3="0" k4="0"/></filter>`;
    body=`<g filter="url(#fine-print)">${body}</g>`;
  }
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="${viewBox}" role="img" aria-label="Aether ${id} ${variant} study"><defs>${defs}</defs><rect x="-1024" y="-1024" width="3072" height="3072" fill="${background(id)}"/>${body}</svg>`;
  // Unique IDs allow multiple masters to be embedded into one editable overview.
  return svg.replace(/id="([^"]+)"/g,(_,name)=>`id="${prefix}-${name}"`).replace(/url\(#([^)]+)\)/g,(_,name)=>`url(#${prefix}-${name})`);
}
