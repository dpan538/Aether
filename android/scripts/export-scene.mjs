// Build-time conversion only. The Android app never runs a browser/DOM/CSS engine.
// Export individual empty material surfaces, native text geometry, and SVG vectors.
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
import { nativeSvg } from './svg-resource.mjs';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const out = path.resolve('app/src/main/assets');
await fs.mkdir(out + '/skins', { recursive: true });
await fs.mkdir(out + '/scenes', { recursive: true });
const browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}) });
const context = await browser.newContext({viewport:{width:1704,height:1136},deviceScaleFactor:2,reducedMotion:'reduce'});
const page = await context.newPage();
const paintPage = await context.newPage();
await paintPage.setViewportSize({width:1800,height:1200});
await paintPage.setContent('<html><body style="margin:0;background:transparent"></body></html>');
const skins = new Map();
const source = 'http://127.0.0.1:4177';
async function exportScene(name, pageName, action) {
  await page.goto(`${source}/${pageName}/index.html`);
  await page.waitForFunction(()=>document.querySelectorAll('svg').length>3 || !!document.querySelector('#record'));
  if(pageName==='modern') await page.waitForFunction(()=>document.body.dataset.ready==='true');
  await page.waitForTimeout(80);
  if(action) await action(page);
  await page.waitForTimeout(100);
  const data = await page.evaluate(() => {
    const scene=document.querySelector('.scene'), origin=scene.getBoundingClientRect();
    const surfaces=[];
    const n=v=>Math.round(v*10000)/10000;
    const paintProps=['backgroundColor','backgroundImage','backgroundSize','backgroundPosition','backgroundRepeat',
      'borderTopWidth','borderRightWidth','borderBottomWidth','borderLeftWidth','borderTopStyle','borderRightStyle','borderBottomStyle','borderLeftStyle',
      'borderTopColor','borderRightColor','borderBottomColor','borderLeftColor','borderTopLeftRadius','borderTopRightRadius','borderBottomLeftRadius','borderBottomRightRadius','boxShadow','clipPath'];
    const rect=r=>[n(r.x-origin.x),n(r.y-origin.y),n(r.width),n(r.height)];
    function skin(s,w,h) {
      if(w<=0||h<=0)return null;
      const p=Object.fromEntries(paintProps.map(k=>[k,s[k]]));
      if(p.backgroundImage==='none' && ['rgba(0, 0, 0, 0)','transparent'].includes(p.backgroundColor) && p.boxShadow==='none' && ['Top','Bottom','Left','Right'].every(v=>parseFloat(p['border'+v+'Width'])===0))return null;
      const index=surfaces.length;surfaces.push({w:n(w),h:n(h),p});return index;
    }
    function pseudo(el,which,r){
      const s=getComputedStyle(el,which);if(s.content==='none'||s.display==='none'||s.visibility==='hidden')return null;
      let w=parseFloat(s.width),h=parseFloat(s.height);if(!Number.isFinite(w)||!Number.isFinite(h))return null;
      if(s.boxSizing!=='border-box'){w+=parseFloat(s.borderLeftWidth)+parseFloat(s.borderRightWidth)+parseFloat(s.paddingLeft)+parseFloat(s.paddingRight);h+=parseFloat(s.borderTopWidth)+parseFloat(s.borderBottomWidth)+parseFloat(s.paddingTop)+parseFloat(s.paddingBottom);}
      let x=parseFloat(s.left),y=parseFloat(s.top);if(!Number.isFinite(x))x=Number.isFinite(parseFloat(s.right))?r[2]-parseFloat(s.right)-w:0;
      if(!Number.isFinite(y))y=Number.isFinite(parseFloat(s.bottom))?r[3]-parseFloat(s.bottom)-h:0;
      if(s.position!=='absolute'){x=(r[2]-w)/2;y=(r[3]-h)/2;}
      else {const parent=getComputedStyle(el);x+=parseFloat(parent.borderLeftWidth)||0;y+=parseFloat(parent.borderTopWidth)||0;}
      let rotation=0;
      if(s.transform!=='none'){const m=new DOMMatrix(s.transform);rotation=Math.atan2(m.b,m.a)*180/Math.PI;x+=m.e;y+=m.f;}
      return {r:[n(r[0]+x),n(r[1]+y),n(w),n(h)],skin:skin(s,w,h),alpha:parseFloat(s.opacity),rotation};
    }
    function svg(el){
      const clone=el.cloneNode(true),orig=[el,...el.querySelectorAll('*')],copy=[clone,...clone.querySelectorAll('*')];
      const props=['fill','stroke','stroke-width','stroke-linecap','stroke-linejoin','stroke-dasharray','opacity','fill-opacity','stroke-opacity','font-family','font-size','font-weight','letter-spacing','text-anchor','color','stop-color','stop-opacity'];
      for(let i=0;i<orig.length;i++){
        const s=getComputedStyle(orig[i]);
        const c=copy[i];
        c.removeAttribute('class');c.removeAttribute('style');
        for(const p of props){let v=s.getPropertyValue(p);if(v){v=v.replace(/url\("?[^#)]*#([^)"]+)"?\)/g,'url(#$1)');c.setAttribute(p,v);}}
        c.removeAttribute('filter');
      }
      const sprite=document.querySelector('.sprite defs');
      if(sprite){const defs=sprite.cloneNode(true);defs.querySelectorAll('filter').forEach(e=>e.remove());clone.prepend(defs);}
      clone.setAttribute('xmlns','http://www.w3.org/2000/svg');
      clone.setAttribute('width',el.clientWidth);clone.setAttribute('height',el.clientHeight);
      return new XMLSerializer().serializeToString(clone);
    }
    const textCanvas=document.createElement('canvas'),ctx=textCanvas.getContext('2d');
    function texts(el,s){
      const ops=[];
      for(const t of el.childNodes){if(t.nodeType!==3||!t.textContent.trim())continue;
        const groups=[];let last;
        for(let i=0;i<t.length;i++){
          const range=document.createRange();range.setStart(t,i);range.setEnd(t,i+1);const r=range.getBoundingClientRect();if(!r.width&&!r.height)continue;
          if(!last||Math.abs(last.y-r.y)>.5){last={x:r.x,y:r.y,right:r.right,h:r.height,text:''};groups.push(last);}last.text+=t.textContent[i];last.right=r.right;
        }
        ctx.font=`${s.fontWeight} ${s.fontSize} ${s.fontFamily}`;const metrics=ctx.measureText('Hg');
        for(const g of groups){if(!g.text.trim())continue;ops.push({text:g.text,x:n(g.x-origin.x),y:n(g.y-origin.y+(metrics.fontBoundingBoxAscent||parseFloat(s.fontSize)*.9)),w:n(g.right-g.x),size:parseFloat(s.fontSize),weight:parseInt(s.fontWeight)||400,mono:!s.fontFamily.includes('PingFang')&&/mono|Consolas|SFMono|Liberation/.test(s.fontFamily),color:s.color,spacing:parseFloat(s.letterSpacing)||0});}
      }
      if(el.tagName==='INPUT' && ['search','text'].includes(el.type)){
        const r=el.getBoundingClientRect(),ps=getComputedStyle(el,'::placeholder');
        ops.push({text:el.value||el.placeholder,x:n(r.x-origin.x+parseFloat(s.paddingLeft)),y:n(r.y-origin.y+r.height/2+parseFloat(s.fontSize)*.35),w:0,size:parseFloat(s.fontSize),weight:400,mono:false,color:el.value?s.color:ps.color,spacing:0});
      }
      return ops;
    }
    let serial=0;
    function walk(el){
      const s=getComputedStyle(el),r=rect(el.getBoundingClientRect());
      if(s.display==='none'||s.visibility==='hidden'||el.hidden||r[2]<=0||r[3]<=0||el.classList.contains('sprite')||el.tagName==='SELECT')return null;
      const node={id:el.id||'',cls:el.getAttribute('class')||'',tag:el.tagName,key:++serial,r,alpha:parseFloat(s.opacity),skin:skin(s,r[2],r[3]),before:null,after:null,text:[],children:[],i18n:el.dataset.i18n||'',control:el.closest('.filter-trigger')?.closest('label')?.querySelector('select')?.id||''};
      const button=el.matches('button,[role="slider"],input');
      if(button) node.action=el.id||el.dataset.view&&'view:'+el.dataset.view||el.dataset.station&&'station:'+el.dataset.station||el.dataset.step&&'step:'+el.dataset.step||el.dataset.favoriteStep&&'favorite-step:'+el.dataset.favoriteStep||el.getAttribute('role')==='option'&&'option:'+el.dataset.option||el.closest('label')?.querySelector('select')?.id&&'menu:'+el.closest('label').querySelector('select').id||el.classList.contains('station-step--previous')&&'previous'||el.classList.contains('station-step--next')&&'next'||'';
      node.label=el.getAttribute('aria-label')||'';
      if(el.namespaceURI==='http://www.w3.org/2000/svg'){
        node.svg=svg(el);node.skin=null;
        const m=new DOMMatrix(s.transform==='none'?undefined:s.transform);node.rotation=Math.atan2(m.b,m.a)*180/Math.PI;
        return node;
      }
      if(el.tagName==='CANVAS'){node.disc=el.id;node.skin=null;return node;}
      node.before=pseudo(el,'::before',r);node.after=pseudo(el,'::after',r);
      node.text=texts(el,s);
      if(['hidden','auto','scroll'].includes(s.overflowX)||['hidden','auto','scroll'].includes(s.overflowY)){
        node.clip=[...r,parseFloat(s.borderTopLeftRadius)||0];
        node.clipRadii=['borderTopLeftRadius','borderTopRightRadius','borderBottomRightRadius','borderBottomLeftRadius'].flatMap(k=>{
          const v=s[k].split(' '),x=v[0],y=v[1]||x;
          return [x.endsWith('%')?parseFloat(x)*r[2]/100:parseFloat(x),y.endsWith('%')?parseFloat(y)*r[3]/100:parseFloat(y)];
        });
      }
      const children=[...el.children].sort((a,b)=>(parseInt(getComputedStyle(a).zIndex)||0)-(parseInt(getComputedStyle(b).zIndex)||0));
      node.children=children.map(walk).filter(Boolean);
      return node;
    }
    return {width:1704,height:1136,root:walk(scene),surfaces};
  });
  for (const s of data.surfaces) {
    const hash=crypto.createHash('sha256').update(JSON.stringify(s)).digest('hex').slice(0,16);
    s.name=hash;
    if(!skins.has(hash))skins.set(hash,s);
  }
  function relink(n){if(n.svg)n.svg=nativeSvg(n.svg);if(n.skin!=null)n.skin=data.surfaces[n.skin].name;for(const p of [n.before,n.after])if(p?.skin!=null)p.skin=data.surfaces[p.skin].name;for(const c of n.children)relink(c);}
  relink(data.root);delete data.surfaces;
  await fs.writeFile(out+`/scenes/${name}.json`,JSON.stringify(data));
  console.log(name, 'nodes', data.root.key, 'unique surfaces',skins.size);
}
const light=async p=>{await p.click('#device-light');};
const settings=async p=>{await p.click('#device-settings');};
for(const lit of [false,true]){
  const suffix=lit?'lit':'ink';
  await exportScene('modern-'+suffix,'modern',lit?light:null);
  await exportScene('settings-'+suffix,'modern',async p=>{if(lit)await light(p);await settings(p);});
  for(const menu of ['region','genre','language','ui-language']){
    await exportScene(menu+'-'+suffix,'modern',async p=>{if(lit)await light(p);if(menu==='ui-language')await settings(p);await p.click(`[aria-controls="${menu==='ui-language'?'ui-language':menu+'-filter'}-menu"]`);});
  }
  await exportScene('mute-'+suffix,'modern',async p=>{if(lit)await light(p);await p.click('#stereo-button');});
  await exportScene('004-'+suffix,'modern',async p=>{if(lit)await light(p);await p.click('.favorite-station[data-station="3"]');});
}
await exportScene('off','modern',async p=>{await p.click('#device-power');await p.waitForTimeout(1900);});
await exportScene('locked','modern',async p=>{await p.click('#device-lock');});
await exportScene('immersive','immersive');
// Only blank surfaces are rasterized. No text, controls, SVGs, or whole UI captures.
let index=0;
for(const [hash,s] of skins){
  const target=out+'/skins/'+hash+'.png';
  if(!process.env.REPAINT)try{await fs.access(target);continue;}catch{}
  const pad=32;
  const w=Math.ceil(s.w+pad*2),h=Math.ceil(s.h+pad*2);
  await paintPage.evaluate(({s,pad})=>{
    document.body.innerHTML='';const e=document.createElement('div');
    Object.assign(e.style,s.p,{position:'absolute',left:pad+'px',top:pad+'px',width:s.w+'px',height:s.h+'px',boxSizing:'border-box'});document.body.append(e);
  },{s,pad});
  await paintPage.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  await paintPage.screenshot({path:target,clip:{x:0,y:0,width:w,height:h},omitBackground:true,animations:'disabled'});
  if(++index%40===0)console.log('material surfaces',index,'/',skins.size);
}
await fs.writeFile(out+'/skins/index.json',JSON.stringify(Object.fromEntries([...skins].map(([k,s])=>[k,{w:s.w,h:s.h,pad:32}]))));
// Preserve the exact approved shader, not a cropped UI image.
const disc=await fs.readFile('../design/snapshots/2026-10-24-two-page-baseline/source/design/immersive/disc.js','utf8');
await fs.writeFile(out+'/disc.frag',disc.split('const fragmentSource = `')[1].split('`;')[0]);
await browser.close();
