// Aether: authored flat geometry. No raster generation, filters or textures.
const TAU = Math.PI * 2;
const n = x => Number(x.toFixed(3));
const pt = (r, a, x=256, y=256) => [x+r*Math.cos(a), y+r*Math.sin(a)];
const points = a => a.map(p=>p.map(n).join(',')).join(' ');
const polygon = (a, fill) => `<polygon points="${points(a)}" fill="${fill}"/>`;
const path = (d, stroke, width=4, fill='none', extra='') => `<path d="${d}" fill="${fill}" stroke="${stroke}" stroke-width="${width}" ${extra}/>`;
const line = (x,y,a,b,stroke,width=4) => `<path d="M${n(x)} ${n(y)}L${n(a)} ${n(b)}" fill="none" stroke="${stroke}" stroke-width="${width}"/>`;
const circle = (x,y,r,fill,stroke='none',width=0) => `<circle cx="${n(x)}" cy="${n(y)}" r="${n(r)}" fill="${fill}" stroke="${stroke}" stroke-width="${width}"/>`;
const ellipse = (x,y,rx,ry,stroke,width=4,rotate=0,fill='none') => `<ellipse cx="${n(x)}" cy="${n(y)}" rx="${n(rx)}" ry="${n(ry)}" fill="${fill}" stroke="${stroke}" stroke-width="${width}" transform="rotate(${n(rotate)} ${n(x)} ${n(y)})"/>`;
const polyline = (a,stroke,width=4,close=false) => path(a.map((p,i)=>(i?'L':'M')+p.map(n).join(' ')).join('')+(close?'Z':''),stroke,width);
const sequence = (count,fn) => Array.from({length:count},(_,i)=>fn(i)).join('');
const rectangle=(x,y,w,h,fill,rotate=0)=>`<rect x="${n(x)}" y="${n(y)}" width="${n(w)}" height="${n(h)}" fill="${fill}" transform="rotate(${n(rotate)} ${n(x+w/2)} ${n(y+h/2)})"/>`;
const swatches={ ink:'#181b1d', paper:'#f0ecdf', blue:'#174bb5', red:'#e3492f' };
export const concepts = [
 ['phase-aperture','相位孔','PHASE APERTURE',0,[1],'同心环 × 相位棋格','径向棋格依次错位，观察相位流动。'],
 ['offset-vortex','偏心涡','ECCENTRIC VORTEX',0,[2],'偏心圆 × 等差线距','圆心沿共同轨道缓慢移动，线距连续改变。'],
 ['radial-pulse','脉冲环','RADIAL PULSE',0,[3,4,5],'径向阵列 × 阶梯频带','条带以不同幅值伸缩，保持内核安静。'],
 ['interference-lens','干涉透镜','INTERFERENCE LENS',0,[1,2,15],'双波源 × 几何叠合','双波源等速错相，形成新的干涉负形。'],
 ['dot-occultation','点阵掩星','DOT OCCULTATION',1,[8,11],'三角点阵 × 圆形遮蔽','点阵遮蔽边界平移，粒子保持有序。'],
 ['null-signal','静噪核','NULL SIGNAL',1,[4,5,8],'极坐标方点 × 静默内核','方点环按层反向转动，不加入随机闪烁。'],
 ['cymatic-node','驻波结','CYMATIC NODE',1,[1,11,15],'四阶曲线 × 嵌套节点','驻波四节点连续起伏，形成稳定的结构。'],
 ['wave-fold','折波','WAVE FOLD',1,[10,11,14],'平行波带 × 相切切口','平行波带横向传递，形状连续而速度恒定。'],
 ['orbital-cage','轨道笼','ORBITAL CAGE',2,[7,9],'三椭圆 × 交织遮挡','三个轨道缓慢进动，平面交织关系保持。'],
 ['horizon-globe','地平穹','HORIZON GLOBE',2,[7,8],'经纬网 × 固定地平环','子午线平移，地平线始终固定。'],
 ['phase-prism','相位棱','PHASE PRISM',2,[11],'三角网 × 缺失面','线性相位沿三角结构传递，轮廓保持平面。'],
 ['field-torus','回环网','TOROIDAL GRID',2,[2,13],'环面参数网 × 偏轴投影','网线沿回环流动，线宽与颜色保持一致。'],
 ['triphasic-field','三相场','TRIPHASIC FIELD',3,[10,14],'三圆旋切 × 负形','三片圆弧同速错相，打开中央负形。'],
 ['echo-horizon','回声地平','ECHO HORIZON',3,[6,12],'圆盘 × 双频带','双频带穿过圆盘，切口以线性速度移动。'],
 ['signal-ghost','幽灵信号','SIGNAL GHOST',3,[15,2],'收束等值线 × 双焦点','等值线向双焦点收束，保持清晰边界。'],
 ['binary-tide','双星潮汐','BINARY TIDE',3,[8,12,14],'双圆相交 × 正负形','两个圆核围绕共同中心移动，交集切换正负形。'],
 ['blue-medium','蓝色介质','BLUE MEDIUM',4,[16,15],'弯曲扫描线 × 蓝色空腔','扫描线连续流过曲面，焦点在下方缓慢移动。'],
 ['convergent-cavity','汇聚空腔','CONVERGENT CAVITY',4,[16,2],'偏轴等高线 × 收束孔口','等高线向下方孔口汇聚，维持清晰的平面色域。']
].map((a,i)=>({id:String(i+1).padStart(2,'0'),slug:a[0],name:a[1],english:a[2],family:a[3],refs:a[4],rule:a[5],motion:a[6],file:`${String(i+1).padStart(2,'0')}-${a[0]}.svg`}));
export const families=['干涉 / 波纹','点阵 / 噪声','天体 / 轨道','相位 / 负形','蓝色介质'];

export function renderGeometry(id, {phase=0,mono=false,foregroundOnly=false}={}) {
 const c=concepts[Number(id)-1];
 if(!c)throw new Error('Unknown geometric concept '+id);
 const palettes=[
  [swatches.blue,swatches.paper,swatches.paper], [swatches.ink,swatches.paper,swatches.red],
  [swatches.ink,swatches.paper,swatches.red], [swatches.paper,swatches.blue,swatches.red],
  [swatches.paper,swatches.ink,swatches.blue], [swatches.ink,swatches.paper,swatches.blue],
  [swatches.blue,swatches.paper,swatches.paper], [swatches.paper,swatches.blue,swatches.red],
  [swatches.ink,swatches.paper,swatches.blue], [swatches.paper,swatches.ink,swatches.blue],
  [swatches.blue,swatches.paper,swatches.red], [swatches.paper,swatches.blue,swatches.ink],
  [swatches.paper,swatches.blue,swatches.red], [swatches.paper,swatches.red,swatches.blue],
  [swatches.ink,swatches.paper,swatches.blue], [swatches.blue,swatches.paper,swatches.paper],
  [swatches.ink,'#164ebd','#65aade'],[swatches.ink,'#164ebd','#65aade']
 ];
 const [bg,fg,accent]=mono?[swatches.paper,swatches.ink,swatches.ink]:palettes[Number(id)-1];
 const p=phase, key='aether-'+id+'-'; let body='',defs='';
 const clip=(name,shape)=>{defs+=`<clipPath id="${key+name}">${shape}</clipPath>`;return `clip-path="url(#${key+name})"`;};
 const group=(content,extra='')=>`<g ${extra}>${content}</g>`;
 const degree=p*180/Math.PI;
 switch(Number(id)){
 case 1: {
  // Eight annular bands share 32 divisions; opposing shear creates phase tension.
  for(let band=0;band<8;band++){
   const ri=40+band*17,ro=ri+17;
   for(let j=0;j<32;j++)if((j+band)%2===0){
    const make=(r,a)=>pt(r,a+.105*Math.sin(a*2+p)+(band-3.5)*.025*Math.cos(a+p));
    const a=j*TAU/32,b=(j+1)*TAU/32;
    const poly=[];
    for(let k=0;k<=5;k++)poly.push(make(ro,a+(b-a)*k/5));
    for(let k=5;k>=0;k--)poly.push(make(ri,a+(b-a)*k/5));
    body+=polygon(poly,fg);
   }
  }
  break;
 }
 case 2: {
  for(let i=0;i<13;i++){
   const q=i/12,r=26+q*149;
   body+=circle(256-(1-q)*65+Math.sin(p)*12*(1-q),256+(1-q)*43+Math.cos(p)*8*(1-q),r,'none',fg,5.5);
  }
  break;
 }
 case 3: {
  for(let i=0;i<56;i++){
   const a=TAU*i/56-Math.PI/2;
   const length=47+19*Math.sin(Math.floor(i/4)*1.8+p)+13*Math.cos(i*.45-p*.7);
   const a1=pt(80,a),a2=pt(80+length+28,a);
   body+=line(...a1,...a2,i===5?accent:fg,6.5);
  }
  break;
 }
 case 4: {
  const mask=clip('field',circle(256,256,180,'white'));
  let waves='';
  for(const side of [-1,1])for(let i=0;i<12;i++){
   const r=24+i*15+Math.sin(p)*side*4;
   waves+=circle(256+side*65,256,r,'none',side<0?fg:accent,4);
  }
  body=group(waves,mask);
  break;
 }
 case 5: {
  // Equal-size dots and an explicit hard edge: density, never surface shading.
  const mask=clip('disc',circle(256,256,174,'white'));
  let dots='';
  for(let row=0;row<35;row++)for(let col=0;col<35;col++){
   const x=73+col*11+(row%2)*5.5,y=72+row*11;
   const threshold=246+44*Math.sin((y-90)/90+p);
   if(x<threshold||((row+col)%3===0))dots+=circle(x,y,3.6,x<threshold?fg:accent);
  }
  body=group(dots,mask);
  body+=path('M256 82A174 174 0 0 1 430 256',accent,6);
  break;
 }
 case 6: {
  for(let r=0;r<6;r++)for(let j=0;j<56;j++){
   if((j+2*r)%17===0)continue;
   const angle=j*TAU/56+p*(r%2?-.12:.12);
   const xy=pt(79+r*17,angle);
   body+=rectangle(xy[0]-3.6,xy[1]-3.6,7.2,7.2,r===5&&j===5?accent:fg,angle*180/Math.PI+45);
  }
  break;
 }
 case 7: {
  for(let j=0;j<8;j++){
   const scale=.32+j*.095;
   const arr=Array.from({length:241},(_,i)=>{
    const a=TAU*i/240;const r=174*scale*(.8+.2*Math.cos(4*a+p*.25));return pt(r,a+Math.PI/4);
   });
   body+=polyline(arr,fg,5,true);
  }
  break;
 }
 case 8: {
  // Broad parallel cubic waves with uniform solid ink, without folded highlights.
  for(let i=0;i<5;i++){
   const y=157+i*43;const d=Math.sin(p)*30;
   body+=path(`M108 ${y}C${180+d} ${y-87} ${332-d} ${y+87} 404 ${y}`,i===2?accent:fg,17);
  }
  break;
 }
 case 9: {
  // Same ellipse construction in all three planes, interlace indicated by gaps.
  for(let j=0;j<3;j++){
   const a=j*60-25+degree*.08;
   body+=ellipse(256,256,168,70,bg,32,a);
   body+=ellipse(256,256,168,70,j===1?accent:fg,20,a);
  }
  // Return the leading arc to the foreground to complete the braid.
  const arr=Array.from({length:61},(_,i)=>{const a=i*Math.PI/120;return [256+168*Math.cos(a),256+70*Math.sin(a)];});
  body+=group(polyline(arr,fg,20),`transform="rotate(${-25+degree*.08} 256 256)"`);
  break;
 }
 case 10: {
  body=circle(256,256,157,'none',fg,5);
  for(let i=0;i<7;i++){
   const angle=i*Math.PI/7+p*.14;const rx=157*Math.cos(angle);
   body+=ellipse(256,256,Math.abs(rx),157,fg,3.5);
  }
  for(const y of [-89,0,89])body+=ellipse(256,256+y,Math.sqrt(157**2-y**2),27,fg,3.5);
  body+=ellipse(256,266,188,47,bg,23);
  body+=ellipse(256,266,188,47,accent,9);
  break;
 }
 case 11: {
  const a=[[201,89],[345,121],[413,278],[312,419],[140,367],[92,218]];
  body=polygon(a,'none');body+=polyline([...a,a[0]],fg,7);
  const hub=[258,249];
  for(const v of a)body+=line(...v,...hub,fg,5);
  body+=polyline([[201,89],[140,367],[345,121],[312,419],[92,218],[413,278],[201,89]],fg,3.5);
  body+=polygon([[240,214],[285,250],[238,279]],bg);
  const k=(Math.sin(p)+1)/2;
  body+=circle(201+(345-201)*k,89+(121-89)*k,7,accent);
  break;
 }
 case 12: {
  const project=(u,v)=>{
   const a=111+48*Math.cos(v),x=a*Math.cos(u),y=a*Math.sin(u)*.69+48*Math.sin(v)*.74;
   return [256+x*Math.cos(-.35)-y*Math.sin(-.35),256+x*Math.sin(-.35)+y*Math.cos(-.35)];
  };
  for(let j=0;j<24;j++)body+=polyline(Array.from({length:81},(_,i)=>project(j*TAU/24+p*.09,i*TAU/80)),fg,2.7,true);
  for(let j=0;j<10;j++)body+=polyline(Array.from({length:161},(_,i)=>project(i*TAU/160,j*TAU/10+p*.08)),fg,2.7,true);
  break;
 }
 case 13: {
  for(let j=0;j<3;j++){
   const a=-Math.PI/2+j*TAU/3+p*.08,xy=pt(63,a);
   const mask=clip('petal'+j,circle(...xy,116,'white'));
   const notch=pt(92,a+1.2);
   body+=circle(...xy,116,j===1?accent:fg);
   body+=group(circle(...notch,91,bg),mask);
  }
  body+=circle(256,256,29,bg);
  break;
 }
 case 14: {
  const mask=clip('broadcast',circle(256,220,151,'white'));
  body=circle(256,220,151,fg);
  const shift=Math.sin(p*.3)*18;
  let bars=rectangle(174+shift,213,242,36,accent,38)+rectangle(148-shift,258,242,36,accent,38);
  body+=bars;
  if(!mono)body+=group(bars.replaceAll(accent,swatches.ink),mask);
  body+=circle(256,220,18,bg);
  break;
 }
 case 15: {
  for(let j=0;j<12;j++){
   const r=40+j*12;
   const arr=Array.from({length:201},(_,i)=>{
    const a=i*TAU/200;
    const x=r*Math.cos(a)*(.48+.22*Math.cos(2*a+p*.2));
    return [256+x,256+1.03*r*Math.sin(a)];
   });
   body+=polyline(arr,j%4===0?accent:fg,4,true);
  }
  break;
 }
 case 16: {
  const rot=degree*.07-35;
  let mark=circle(194,256,105,fg)+circle(318,256,105,'none',fg,10);
  const mask=clip('cross',circle(194,256,105,'white'));
  mark+=group(circle(318,256,105,bg),mask);
  mark+=circle(194,256,21,bg)+circle(318,256,21,fg);
  mark+=line(122,256,144,256,bg,8)+line(368,256,391,256,fg,8);
  body=group(mark,`transform="rotate(${n(rot)} 256 256)"`);
  break;
 }
 case 17: {
  const mask=clip('medium',circle(256,256,175,'white'));
  const flow=Math.sin(p*.22)*8;
  body=circle(256,256,175,mono?'none':'#10213f',mono?fg:'none',4);
  let contours='';
  for(let i=0;i<16;i++){
   const y=94+i*21;
   const arr=Array.from({length:101},(_,k)=>{
    const x=74+k*3.64;
    const bend=60*Math.sin(Math.PI*(y-72)/368)*Math.pow(Math.max(0,1-((x-256)/183)**2),2);
    return [x,y+bend+flow];
   });
   contours+=polyline(arr,i>11?accent:fg,i>11?4:3.8);
  }
  body+=group(contours,mask);
  body+=ellipse(256,348+flow,34,18,bg,9,0,bg);
  body+=path(`M222 ${n(348+flow)}A34 18 0 0 0 290 ${n(348+flow)}`,accent,5);
  break;
 }
 case 18: {
  const mask=clip('cavity',circle(256,256,175,'white'));
  body=circle(256,256,175,fg);
  const bowl=path('M78 160C120 40 320 10 417 178C466 246 412 375 320 387C212 400 61 280 78 160Z','none',0,mono?bg:'#0b1735');
  let contours=bowl;
  for(let i=0;i<14;i++){
   const q=i/13;
   contours+=ellipse(246+q*62+Math.sin(p*.16)*6,183+q*169,218*(1-q)+30,153*(1-q)+17,i>11?accent:fg,i>11?3.8:3,18);
  }
  body+=group(contours,mask);
  break;
 }
 }
 return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" role="img" aria-label="Aether ${c.name}"><title>Aether ${c.id} · ${c.name}</title>${defs?`<defs>${defs}</defs>`:''}${foregroundOnly?'':`<rect width="512" height="512" fill="${bg}"/>`}${body}</svg>`;
}
