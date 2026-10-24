// Aether UI-derived brand studies. Source: design/immersive/disc.js lunar geometry
// and design/modern/hardware.js dial calibration. No external visual motifs.
export const studies=[
  {id:'D1',slug:'lunar-disc',name:'月相原盘',rule:'月相边界 · 盘芯 · 颗粒',note:'保留主 CD 的偏心比例，将材质收成平面色域。'},
  {id:'D2',slug:'phase-echo',name:'月相回声',rule:'月相负形 · 双回声弧',note:'放大盘芯负形、减少颗粒，强调两道连续回声。'},
  {id:'D3',slug:'tuning-phase',name:'调频相位',rule:'月相 · 旋钮校准刻度',note:'以现代页的印刷刻度连接月相，形成仪器感。'},
];
export const colors={background:'#080a0e',face:'#0c131b',paper:'#e5e5db',gold:'#e9dda3',ice:'#a6c5d1',blue:'#278bbb',silver:'#a8bdc6',quiet:'#475c67'};
const n=x=>Number(x.toFixed(3));
const C=(x,y,r,fill,extra='')=>`<circle cx="${n(x)}" cy="${n(y)}" r="${n(r)}" fill="${fill}" ${extra}/>`;
const L=(x1,y1,x2,y2,stroke,w=2)=>`<path d="M${n(x1)} ${n(y1)}L${n(x2)} ${n(y2)}" fill="none" stroke="${stroke}" stroke-width="${w}"/>`;
const at=(r,a)=>[256+r*Math.cos(a),256+r*Math.sin(a)];
const rng=seed=>()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};

export function render(id,{mono=false}={}){
 const study=studies.find(s=>s.id===id);if(!study)throw new Error('Unknown study '+id);
 const k=id+'-'+(mono?'mono':'color')+'-',R=id==='D3'?184:202,face=R*.95;
 // Identical two-circle relationship to the approved moon: cutRadius=1.04,
 // centre shift=.31 and tilt=.23 radians; the physical hole ratio is 15:120.
 const cx=256-.31*R*Math.cos(.23),cy=256+.31*R*Math.sin(.23),cut=1.04*R;
 const bg=colors.background,fg=mono?colors.paper:`url(#${k}pigment)`,line=mono?colors.paper:colors.silver;
 let defs=`<linearGradient id="${k}pigment" x1="0" y1="0" x2=".2" y2="1"><stop offset="0" stop-color="${colors.gold}"/><stop offset=".45" stop-color="${colors.paper}"/><stop offset="1" stop-color="${colors.ice}"/></linearGradient>`;
 defs+=`<clipPath id="${k}face">${C(256,256,face,'white')}</clipPath><mask id="${k}phase" maskUnits="userSpaceOnUse" x="0" y="0" width="512" height="512"><rect width="512" height="512" fill="black"/>${C(256,256,face,'white')}${C(cx,cy,cut,'black')}</mask>`;
 defs+=`<clipPath id="${k}echo"><path d="M278 ${n(256-face*.82)}H${n(256+face)}V${n(256+face*.82)}H278Z"/></clipPath>`;
 let body=`<rect width="512" height="512" fill="${bg}"/>`;
 if(!mono)body+=C(256,256,R,colors.face);
 body+=`<g mask="url(#${k}phase)">${C(256,256,face,fg)}</g>`;
 const random=rng(23041);
 // Edge particles follow the eccentric phase boundary, rather than filling
 // the background uniformly. The seed is fixed; geometry remains editable.
 if(!mono){
  let dots='';const count=id==='D1'?9200:id==='D2'?2000:3600;
  for(let i=0;i<count;i++){
   const x=256+(random()*2-1)*face,y=256+(random()*2-1)*face;
   if(Math.hypot(x-256,y-256)>face-1)continue;
   const d=Math.hypot(x-cx,y-cy)/R-1.04;
   if(Math.abs(d)>.16||random()>Math.exp(-Math.abs(d)/.029)*.86)continue;
   const radius=.38+random()*.78;
   dots+=C(x,y,radius,d>0?bg:colors.ice,`opacity="${n(d>0?.60+random()*.30:.10+random()*.55)}"`);
  }
  if(id==='D1')for(let i=0;i<145;i++){
   const a=random()*Math.PI*2,r=Math.sqrt(random())*face,x=256+Math.cos(a)*r,y=256+Math.sin(a)*r;
   if(Math.hypot(x-cx,y-cy)<cut-14)dots+=C(x,y,.32+random()*.42,colors.silver,`opacity="${n(.09+random()*.19)}"`);
  }
  body+=dots;
 }
 const echoWidth=id==='D2'?3.2:id==='D1'?1.35:1.7;
 body+=`<g clip-path="url(#${k}face)"><g clip-path="url(#${k}echo)" fill="none" stroke="${mono?colors.paper:colors.blue}">`;
 for(const [i,factor] of [.987,.929].entries())body+=C(cx,cy,R*factor,'none',`stroke-width="${echoWidth}" opacity="${mono?.8:id==='D2'?.7-i*.20:.42-i*.16}"`);
 body+='</g></g>';
 // The rim is dry printed linework: no bevel, highlight or drop shadow.
 body+=C(256,256,R,'none',`stroke="${line}" stroke-width="${id==='D2'?2:2.6}" opacity="${mono?1:.80}"`);
 if(id==='D1')body+=C(256,256,R-3.5,'none',`stroke="${mono?colors.paper:colors.quiet}" stroke-width="1.2" opacity=".75"`);
 const hole=id==='D2'?47:R*(15/120);
 body+=C(256,256,hole+3.1,'none',`stroke="${line}" stroke-width="${id==='D2'?3.4:2.6}"`)+C(256,256,hole,bg);
 if(id==='D3'){
  // A proposed 30-mark reduction of the modern panel's printed calibration.
  // Tick marks stay outside the lunar face and never become a waveform logo.
  for(let i=0;i<30;i++){
   const a=(-230+i*10)*Math.PI/180,major=i%5===0,r1=198,r2=major?217:207;
   body+=L(...at(r1,a),...at(r2,a),line,major?2.7:1.8);
  }
  const a=-38*Math.PI/180;
  body+=L(...at(148,a),...at(170,a),mono?colors.paper:colors.ice,5.5);
 }
 return `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 512 512" role="img" aria-label="漫频 Aether ${study.name}${mono?' 单色':''}"><title>漫频 Aether / ${study.name}</title><defs>${defs}</defs>${body}</svg>\n`;
}
