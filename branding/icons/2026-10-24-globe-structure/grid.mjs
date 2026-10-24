// ONE orthographic sphere. Both color regions reference these same paths.
export const size=1024,center=512,radius=365.2;
const PI=Math.PI,TAU=2*PI,n=x=>Number(x.toFixed(4));
const normalize=v=>{const m=Math.hypot(...v);return v.map(x=>x/m);};
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
export const pole=normalize([-.55,.4,.733]);
export const axisU=normalize([pole[1],-pole[0],0]);
export const axisV=cross(pole,axisU);
export function sphere(latitude,longitude){
 const a=Math.cos(latitude)*Math.cos(longitude),b=Math.cos(latitude)*Math.sin(longitude),c=Math.sin(latitude);
 return pole.map((_,i)=>axisU[i]*a+axisV[i]*b+pole[i]*c);
}
const project=p=>[center+radius*p[0],center-radius*p[1]];
export const definitions=[
 ...[-60,-40,-20,0,20,40,60].map(degree=>({id:'latitude-'+(degree+90),type:'latitude',degree,a:0,b:TAU,fn:t=>sphere(degree*PI/180,t)})),
 ...Array.from({length:18},(_,i)=>({id:'meridian-'+i,type:'meridian',degree:i*20,a:-PI/2,b:PI/2,fn:t=>sphere(t,i*20*PI/180)})),
];
function root(fn,a,b,component){
 let fa=fn(a)[component];
 for(let i=0;i<42;i++){
  const m=(a+b)/2,fm=fn(m)[component];
  if((fa<0)===(fm<0)){a=m;fa=fm;}else b=m;
 }
 return (a+b)/2;
}
export function sampleCurve(curve){
 const steps=720,segments=[],crossings=[];let segment=[];
 let prevT=curve.a,prev=curve.fn(prevT);
 if(prev[2]>=0)segment.push(project(prev));
 for(let i=1;i<=steps;i++){
  const t=curve.a+(curve.b-curve.a)*i/steps,p=curve.fn(t);
  if((prev[2]>=0)!==(p[2]>=0)){
   const boundary=project(curve.fn(root(curve.fn,prevT,t,2)));
   if(prev[2]>=0){segment.push(boundary);if(segment.length>1)segments.push(segment);segment=[];}else segment=[boundary];
  }
  if(p[2]>=0)segment.push(project(p));
  if(prev[1]*p[1]<0){
   const tc=root(curve.fn,prevT,t,1),pc=curve.fn(tc);
   if(pc[2]>1e-6){
    const pa=project(curve.fn(tc-1e-5)),pb=project(curve.fn(tc+1e-5));
    const length=Math.hypot(pb[0]-pa[0],pb[1]-pa[1]);
    crossings.push({curve:curve.id,x:n(project(pc)[0]),y:center,tangent:[n((pb[0]-pa[0])/length),n((pb[1]-pa[1])/length)]});
   }
  }
  prevT=t;prev=p;
 }
 if(segment.length>1)segments.push(segment);
 return {d:segments.map(points=>points.map((p,i)=>(i?'L':'M')+p.map(n).join(' ')).join('')).join(''),crossings};
}
export const curves=definitions.map(curve=>({...curve,...sampleCurve(curve)}));
export const paths=curves.map(c=>`<path id="${c.id}" d="${c.d}"/>`).join('');
const header='<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1024" height="1024" viewBox="0 0 1024 1024">';
export function renderStructure(){
 return `${header}<title>Aether / single continuous spherical grid</title><rect width="1024" height="1024" fill="#080a0e"/><g fill="none" stroke="#e0e7e7" stroke-width="2.3" stroke-linejoin="round" stroke-linecap="round">${paths}<circle cx="512" cy="512" r="${radius}"/></g></svg>\n`;
}
export function renderArtwork(textureURI){
 return `${header}<title>Aether / E2-v2 shared spherical structure</title><defs>
 <g id="shared-spherical-grid" fill="none" stroke-width="2.3" stroke-linejoin="round" stroke-linecap="round">${paths}</g>
 <clipPath id="sphere-boundary"><circle cx="512" cy="512" r="${radius}"/></clipPath>
 <clipPath id="upper-region"><rect x="0" y="0" width="1024" height="512"/></clipPath>
 <clipPath id="lower-region"><rect x="0" y="512" width="1024" height="512"/></clipPath>
 <mask id="lower-colored-lines" maskUnits="userSpaceOnUse" x="0" y="0" width="1024" height="1024"><rect width="1024" height="1024" fill="black"/><use id="lower-grid-use" xlink:href="#shared-spherical-grid" stroke="white"/><circle cx="512" cy="512" r="${radius}" fill="none" stroke="white" stroke-width="2.3"/></mask>
 </defs><rect width="1024" height="1024" fill="#080a0e"/>
 <g clip-path="url(#upper-region)"><g clip-path="url(#sphere-boundary)"><image x="0" y="0" width="1024" height="1024" xlink:href="${textureURI}"/></g><use id="upper-grid-use" xlink:href="#shared-spherical-grid" stroke="#f1f0e8"/><circle cx="512" cy="512" r="${radius}" fill="none" stroke="#f1f0e8" stroke-width="2.3"/></g>
 <g clip-path="url(#lower-region)"><image x="0" y="0" width="1024" height="1024" xlink:href="${textureURI}" mask="url(#lower-colored-lines)"/></g>
 </svg>\n`;
}
