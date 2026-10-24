import { session, stationCatalog } from '../shared/session.js';
import { mountHardware } from './hardware.js';
import { getStatusLights } from './status.js';
import { mountFilterMenus } from './filters.js';

const $ = selector => document.querySelector(selector);
const details = [
  {mark:'802',city:'OSAKA',region:'JAPAN',language:'Japanese',genre:'Alternative'},
  {mark:'BBC 1',city:'LONDON',region:'UK',language:'English',genre:'New Music'},
  {mark:'NTS',city:'LONDON',region:'UK',language:'English',genre:'Experimental'},
  {mark:'NOVA',city:'PARIS',region:'FRANCE',language:'French',genre:'Soul'}
];
let currentView = 'directory';
let motion = false;
let animationFrame;
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
const normal = value => value.normalize('NFKD').replace(/\p{Diacritic}/gu,'').toLowerCase().replace(/[^\p{L}\p{N}]/gu,'');
const pad = value => String(value).padStart(2,'0');
let uiLanguage='en';
const words={
  en:{settings:'SETTINGS',uiLanguage:'Language',checkUpdates:'Check updates',about:'About',previewVersion:'Design preview · 0.0.0',region:'REGION',genre:'GENRE',language:'LANGUAGE',directory:'DIRECTORY',favorites:'FAVORITES',random:'RANDOM',area:'RADIO AREA',audioFormat:'AUDIO FORMAT',stereo:'STEREO',favoriteStations:'Favorite stations',automaticSearch:'Automatic Search',global:'Global (All)',reset:'RESET ↗',allRegions:'ALL REGIONS',allGenres:'ALL GENRES',allLanguages:'ALL LANGUAGES',search:'Station name, city or callsign',globalFrequencies:'GLOBAL FREQUENCIES',yourFrequencies:'YOUR FREQUENCIES',updateUnavailable:'Update service is currently unavailable.',noResults:'No matching stations. Reset filters to try again.'},
  zh:{settings:'设置',uiLanguage:'界面语言',checkUpdates:'检查更新',about:'关于',previewVersion:'设计预览 · 0.0.0',region:'地区',genre:'类型',language:'语言',directory:'目录',favorites:'收藏',random:'随机',area:'电台地区',audioFormat:'音频规格',stereo:'立体声',favoriteStations:'收藏电台',automaticSearch:'直达搜索',global:'全球（全部）',reset:'重置 ↗',allRegions:'全部地区',allGenres:'全部类型',allLanguages:'全部语言',search:'电台名称、城市或呼号',globalFrequencies:'全球频率',yourFrequencies:'我的频率',updateUnavailable:'更新服务暂不可用。',noResults:'没有匹配的电台，清除筛选后再试。'}
};
const tr=key=>words[uiLanguage][key]||key;
function renderStatusLights(){
  const states=getStatusLights(session.snapshot,motion);
  for(const [name,status] of Object.entries(states)){
    const lamp=$(`#${name==='visual'?'visual':name==='link'?'link':'audio'}-lamp`);
    lamp.dataset.status=status;
    const message={connected:'正常 / 已连接',disconnected:'未连接 / 未启用',warning:'需要检查连接或权限',off:'关机'}[status];
    lamp.title=message;lamp.setAttribute('aria-label',`${name}: ${message}`);
  }
}
$('.volume-segments').innerHTML=Array.from({length:30},(_,index)=>`<i data-level="${index+1}"></i>`).join('');
function applyLanguage(){
  document.documentElement.lang=uiLanguage==='zh'?'zh-CN':'en';
  document.querySelectorAll('[data-i18n]').forEach(el=>el.textContent=tr(el.dataset.i18n));
  $('#station-search').placeholder=tr('search');
  $('#clear-filters').textContent=tr('reset');
  $('#region-filter').options[0].textContent=tr('allRegions');
  $('#genre-filter').options[0].textContent=tr('allGenres');
  $('#language-filter').options[0].textContent=tr('allLanguages');
  $('#empty-results').textContent=tr('noResults');
  if(!$('#update-result').hidden)$('#update-result').textContent=tr('updateUnavailable');
  renderSession(session.snapshot);
}


function drawGauge(svg) {
  const tick = 'var(--gauge-tick)';
  const ink = 'var(--gauge-ink)';
  const needle = 'var(--gauge-needle)';
  const battery = svg.dataset.gauge === 'battery';
  const id = battery ? 'bat' : 'temp';
  const value = battery ? 66 : 22;
  const end = (battery ? value/100 : value/50)*Math.PI - Math.PI;
  let marks = '';
  for(let i=0;i<=50;i++) {
    const angle=-Math.PI+i*Math.PI/50;
    const radius=i%5===0?50:55;
    marks+=`<line x1="${140+radius*Math.cos(angle)}" y1="${90+radius*Math.sin(angle)}" x2="${140+61*Math.cos(angle)}" y2="${90+61*Math.sin(angle)}" stroke="${i%5===0?tick:'var(--gauge-minor)'}" stroke-width="${i%5===0?1.5:.75}"/>`;
  }
  svg.innerHTML=`<defs><radialGradient id="rim-${id}" cx="35%" cy="25%"><stop stop-color="#63696d"/><stop offset=".65" stop-color="#1e2328"/><stop offset=".9" stop-color="#090c0e"/><stop offset="1" stop-color="#444b50"/></radialGradient><radialGradient id="glass-${id}" cx="35%" cy="25%"><stop stop-color="#20232a"/><stop offset=".65" stop-color="#090b0d"/><stop offset="1" stop-color="#030606"/></radialGradient><filter id="mint-${id}" x="-30%" y="-50%" width="160%" height="200%"><feGaussianBlur stdDeviation="1.3"/></filter></defs><circle cx="140" cy="90" r="81" fill="#050708"/><circle cx="140" cy="90" r="77" fill="url(#rim-${id})"/><circle class="gauge-surface" cx="140" cy="90" r="71" fill="var(--gauge-paper)" stroke="#10151a" stroke-width="2"/><path d="M73 92a67 67 0 0 1 134 0" fill="none" stroke="#7e858a" stroke-width=".5" opacity=".45"/>${marks}<g fill="${ink}" font-family="monospace" font-size="8" text-anchor="middle"><text x="93" y="88">0</text><text x="140" y="47">${battery?'50':'25'}</text><text x="187" y="88">${battery?'100':'50'}</text></g><path d="M100 30q31-17 60-5L140 90Z" fill="#a4adb4" opacity=".035"/><line x1="${140-18*Math.cos(end)}" y1="${90-18*Math.sin(end)}" x2="${140+60*Math.cos(end)}" y2="${90+60*Math.sin(end)}" stroke="#070a0d" stroke-width="6"/><line x1="${140-18*Math.cos(end)}" y1="${90-18*Math.sin(end)}" x2="${140+60*Math.cos(end)}" y2="${90+60*Math.sin(end)}" stroke="${needle}" stroke-width="3"/><circle cx="140" cy="90" r="7" fill="var(--gauge-hub)"/><rect x="108" y="122" width="64" height="17" rx="8" fill="#9dffd1" style="opacity:calc(var(--frontlight)*.5)" filter="url(#mint-${id})"/><rect x="108" y="122" width="64" height="17" rx="8" fill="var(--gauge-label-paper)"/><text class="gauge-label" x="140" y="134" text-anchor="middle" fill="#315b48" font-family="monospace" font-size="11" font-weight="700">${battery?'BAT　66':'TEMP 22°'}</text>`;
}
$$('.gauge').forEach(drawGauge);
function $$(selector) { return [...document.querySelectorAll(selector)]; }

const segmentShapes={a:'10,0 50,0 58,8 50,14 10,14 2,7',b:'52,16 60,8 60,44 52,50 46,44 46,22',c:'52,54 60,60 60,94 52,101 46,93 46,61',d:'10,89 50,89 57,96 50,103 10,103 2,96',e:'0,60 8,53 14,61 14,85 7,93 0,86',f:'0,16 8,8 14,17 14,43 7,50 0,43',g:'10,45 49,45 57,52 49,59 10,59 3,52'};
const digitSegments=['ab cdef'.replace(/ /g,''),'bc','abged','abgcd','fgbc','afgcd','afgecd','abc','abcdefg','abfgcd'];
function drawClock(time) {
  const digits=(time.match(/\d\d:\d\d/)?.[0]||'22:14').replace(':','');
  $('#digital-clock').innerHTML=digits.split('').map((digit,index)=>{
    const x=index*72+(index>1?21:0);
    return `<g transform="translate(${x+10},0)">${Object.entries(segmentShapes).map(([name,points])=>`<polygon points="${points}" opacity="${digitSegments[Number(digit)].includes(name)?1:.035}"/>`).join('')}</g>`;
  }).join('')+'<rect x="151" y="25" width="9" height="10"/><rect x="151" y="68" width="9" height="10"/>';
  $('#digital-clock').setAttribute('aria-label',`模拟当地时间 ${time}`);
}
const bandLabels=['31','62','125','250','500','1K','2K','4K','8K','16K'];
const initialLevels=[21,22,19,12,16,15,17,10,12,7];
const lastSpectrumLevels=[...initialLevels];
function drawSpectrum(phase=0) {
  const ink = 'var(--spectrum-ink)';
  let content='<defs><filter id="vfd-glow" x="-60%" y="-30%" width="220%" height="160%"><feGaussianBlur stdDeviation="3"/></filter></defs>';
  bandLabels.forEach((label,index)=>{
    const x=75+index*65;
    const level=!session.snapshot.powered?lastSpectrumLevels[index]:(motion?Math.max(3,Math.min(23,initialLevels[index]+Math.round(Math.sin(phase*1.9+index*1.7)*4+Math.cos(phase*.7+index)*2))):initialLevels[index]);
    lastSpectrumLevels[index]=level;
    content+=`<rect x="${x-3}" y="${350-level*12.7}" width="44" height="${level*12.7}" fill="#38ff72" style="opacity:calc(var(--frontlight)*.15)" filter="url(#vfd-glow)"/>`;
    for(let j=0;j<24;j++)content+=`<rect x="${x}" y="${340-j*12.7}" width="38" height="8.8" rx=".7" fill="${j<level?`var(--spectrum-${j<9?'low':j<14?'mid':'high'})`:'var(--spectrum-dim)'}" opacity="${j<level?1:.23}"/>`;
    content+=`<text x="${x+19}" y="392" text-anchor="middle" fill="${ink}" font-family="monospace" font-size="23" font-weight="600">${label}</text>`;
  });
  for(let i=0;i<9;i++)content+=`<circle cx="58" cy="${60+i*35}" r="2.4" fill="${ink}"/><circle cx="752" cy="${60+i*35}" r="2.4" fill="${ink}"/>`;
  content+=`<g fill="${ink}" font-family="monospace" font-size="23"><text x="12" y="66">10</text><text x="24" y="207">0</text><text x="12" y="347">10</text><text x="763" y="66">10</text><text x="763" y="207">5</text><text x="763" y="347">0</text><text x="7" y="392">[Fq.]</text><text x="746" y="392">[Hz]</text></g>`;
  $('#spectrum').innerHTML=content;
}
let lastFrame=0;
function animateSpectrum(now) {
  if(!motion||!session.snapshot.powered||reducedMotion.matches||document.hidden)return;
  if(now-lastFrame>100){drawSpectrum(now/1000);lastFrame=now;}
  animationFrame=requestAnimationFrame(animateSpectrum);
}
function setMotion(value) {
  motion=Boolean(value)&&!reducedMotion.matches;
  if($('#spectrum-motion'))$('#spectrum-motion').checked=motion;
  renderStatusLights();
  $('#monitor-toggle').setAttribute('aria-pressed',String(motion));
  $('#equalizer-preview').setAttribute('aria-pressed',String(motion));
  cancelAnimationFrame(animationFrame);
  if(motion&&!document.hidden)animationFrame=requestAnimationFrame(animateSpectrum);else drawSpectrum();
}
document.addEventListener('visibilitychange',()=>setMotion(motion));
reducedMotion.addEventListener('change',()=>setMotion(motion));
drawSpectrum();
function drawTuner(index) {
  const positions=[85,215,345,475];
  let ticks='';
  for(let i=0;i<67;i++){const x=14+i*8;ticks+=`<line x1="${x}" x2="${x}" y1="${i%5===0?29:35}" y2="42" stroke="#737978" stroke-width="${i%5===0?1.4:.8}"/>`;}
  $('#tuner-scale').innerHTML=`<path d="M8 43H552" stroke="#7e8382" stroke-width="1"/>${ticks}${positions.map((x,i)=>`<path d="m${x-5} 13h10v10l-5 4-5-4Z" fill="#878c89"/><text x="${x}" y="70" text-anchor="middle" fill="#171c1b" font-family="Arial" font-size="23" font-weight="${i===index?700:400}">${pad(i+1)}</text>`).join('')}<path d="M${positions[index]} 1V25m0 7v48" stroke="#141917" stroke-width="3"/>`;
}
function filteredStations() {
  const query=normal($('#station-search').value);
  const region=$('#region-filter').value,genre=$('#genre-filter').value,language=$('#language-filter').value;
  return stationCatalog.map((station,index)=>({...station,...details[index],index})).filter(station=>(!query||normal(`${station.stationName} ${station.city} ${station.region}`).includes(query))&&(!region||station.region===region)&&(!genre||station.genre===genre)&&(!language||station.language===language)&&(currentView!=='favorites'||session.snapshot.favorites.includes(station.index)));
}
function renderStations() {
  const visible=filteredStations();
  $('#station-list').innerHTML=visible.map(station=>`<button class="station-option" data-station="${station.index}" aria-label="选择 ${station.stationName}" aria-pressed="${station.index===session.snapshot.stationIndex}"><span class="station-mark">${station.mark}</span><span class="station-identity"><strong>${station.stationName}</strong><small>${station.city} / ${station.region}</small></span><span class="station-genre">${station.genre}</span><span class="station-number">${pad(station.index+1)}</span><i class="station-flag"></i></button>`).join('');
  $('#empty-results').hidden=visible.length!==0;
  $('#screen-title').textContent=currentView==='settings'?'AETHER / SYSTEM':'AETHER / STATION DIRECTORY';
  $('#result-counter').textContent=currentView==='settings'?`03 ${uiLanguage==='zh'?'设置':'OPTIONS'} · MOCK`:`${pad(visible.length)} ${uiLanguage==='zh'?'条目':'ENTRIES'} · MOCK`;
  $('#list-title').textContent=currentView==='favorites'?tr('yourFrequencies'):tr('globalFrequencies');
  const scope = $$('.filters select').filter(select=>select.value).map(select=>select.selectedOptions[0].textContent);
  const query = $('#station-search').value.trim();
  if(query)scope.push(`“${query}”`);
  if(currentView==='favorites')scope.unshift(tr('favorites'));
  const caption = scope.join(' / ') || (uiLanguage==='zh'?'全球 / 全部电台':'GLOBAL / ALL FREQUENCIES');
  $('#scope-filters').textContent=caption;
  $('#scope-count').innerHTML=`${pad(visible.length)}<span>${uiLanguage==='zh'?'电台':'STATIONS'}</span>`;
  $('#tuning-scope').title=caption;
  $('#tuning-scope').setAttribute('aria-label',`${caption}，${visible.length} 个电台`);
  document.dispatchEvent(new Event('aether:results'));
}
function setView(view) {
  currentView=view;
  $('#settings-view').hidden=view!=='settings';
  $('#directory-view').hidden=view==='settings';
  $$('.hardware-key[data-view]').forEach(button=>{button.classList.toggle('active',button.dataset.view===view);button.setAttribute('aria-pressed',String(button.dataset.view===view));});
  renderStations();
  document.dispatchEvent(new Event('aether:view'));
}
let presetKeySignature;
function presetDigit(number) {
  const segments=[
    '4,1 16,1 18,3 16,5 4,5 2,3',
    '18,4 20,6 20,14 18,16 16,14 16,6',
    '18,17 20,19 20,27 18,29 16,27 16,19',
    '4,28 16,28 18,30 16,32 4,32 2,30',
    '2,17 4,19 4,27 2,29 0,27 0,19',
    '2,4 4,6 4,14 2,16 0,14 0,6',
    '4,14 16,14 18,16 16,18 4,18 2,16'
  ];
  const digits={1:[1,2],2:[0,1,3,4,6],3:[0,1,2,3,6],4:[1,2,5,6]};
  return `<strong class="preset-number" data-digit="${number}" aria-hidden="true"><svg viewBox="-2 -1 24 35">${segments.map((points,index)=>`<polygon class="led-segment${digits[number]?.includes(index)?' on':''}" points="${points}"/>`).join('')}</svg></strong>`;
}
function renderPresetKeys(state) {
  const signature = `${uiLanguage}:${state.favorites.join(',')}`;
  if (signature !== presetKeySignature) {
    presetKeySignature = signature;
    const slots = [...state.favorites];
    while (slots.length < 4) slots.push(null);
    $('#favorite-stations').innerHTML = slots.map((index,slot) => index === null
      ? `<button class="favorite-station preset-empty" disabled aria-label="${uiLanguage==='zh'?'空预设位，收藏电台后填入':'Empty preset; add a favorite to fill'}"><span class="preset-cap"><span class="preset-title">—</span>${presetDigit(slot+1)}</span></button>`
      : `<button class="favorite-station" data-station="${index}" aria-label="预设 ${slot+1}，选择 ${stationCatalog[index].stationName}" aria-pressed="false"><span class="preset-cap"><span class="preset-title">${stationCatalog[index].stationName}</span>${presetDigit(slot+1)}</span></button>`).join('');
  }
  $('#favorite-stations').querySelectorAll('[data-station]').forEach(key => key.setAttribute('aria-pressed',String(Number(key.dataset.station) === state.stationIndex)));
}
function renderSession(state) {
  const detail=details[state.stationIndex];
  document.body.dataset.stationIndex=String(state.stationIndex);
  document.body.dataset.connection=state.connection;
  document.body.dataset.presentationPlayback=state.presentationPlayback;
  // Bistable ink retains its last composition while powered down.
  renderStatusLights();
  if(!state.powered)return;
  $('#preset-number').textContent=String(state.stationIndex+1).padStart(3,'0');
  $('#preset-badge').textContent=String(state.stationIndex+1);
  $('#now-title').textContent=state.programme.replace('\n',' ');
  $('#now-station').textContent=state.stationName;
  $('#local-time').textContent=state.localTime.split(' ')[0];
  $('#clock-location').textContent=`${detail.city} · ${detail.region}`;
  $('#selected-readout').textContent=`SELECTED ${String(state.stationIndex+1).padStart(3,'0')} / ${state.stationName}`;
  $('#favorite-count').textContent=` · ${state.favorites.length}`;
  const favorited=state.favorites.includes(state.stationIndex);
  $('#favorite-toggle').setAttribute('aria-pressed',String(favorited));
  $('#favorite-toggle').setAttribute('aria-label',favorited?'取消收藏当前电台':'收藏当前电台');
  renderPresetKeys(state);
  const volumeStep=Math.round(state.volume*30/100);
  $('#volume').setAttribute('aria-valuenow',String(volumeStep));
  $('#volume').setAttribute('aria-valuetext',`${volumeStep} / 30`);
  $('#stream-format').textContent=state.quality;
  const connection=state.connection==='connected'?(uiLanguage==='zh'?'已连接':'CONNECTED'):(uiLanguage==='zh'?'未连接':'NOT CONNECTED');
  $('#stream-connection').textContent=`${connection} · MOCK`;
  $('#stream-profile').textContent=`${detail.language.toUpperCase()} / ${detail.genre.toUpperCase()}`;
  $('#stream-output').textContent=state.volume===0?'OUTPUT / MUTE':`VOL / ${pad(volumeStep)} · 30`;
  $('#segmented-volume').value=String(volumeStep);
  $('#volume-readout').textContent=`${String(volumeStep).padStart(2,'0')} / 30`;
  $$('.volume-segments i').forEach((bar,index)=>bar.classList.toggle('active',index<volumeStep));
  $('.volume-knob').style.setProperty('--knob-angle',`${-135+volumeStep*9}deg`);
  const muted=state.volume===0;
  $('#stereo-button').setAttribute('aria-pressed',String(muted));
  $('#stereo-button').setAttribute('aria-label',muted?'取消静音，恢复音量':'静音');
  $('#output-glyph').setAttribute('href',muted?'#i-muted':'#i-speaker');
  const paused=state.presentationPlayback==='paused';
  $('#play-toggle').dataset.paused=String(paused);
  $('#play-toggle').setAttribute('aria-pressed',String(!paused));
  $('#play-toggle').setAttribute('aria-label',paused?'开始模拟播放':'暂停模拟播放');
  $('.playing-kicker').textContent=uiLanguage==='zh'?(paused?'已暂停 · MOCK':'播放中 · MOCK'):(paused?'PAUSED · MOCK':'NOW PLAYING · MOCK');
  drawClock(state.localTime);drawTuner(state.stationIndex);renderStations();
}
const unsubscribe=session.subscribe(renderSession);
document.addEventListener('click',event=>{
  const stationButton=event.target.closest('[data-station]');
  if(stationButton)session.select(Number(stationButton.dataset.station));
  const step=event.target.closest('[data-step]');
  if(step)session.step(Number(step.dataset.step));
  const favoriteStep=event.target.closest('[data-favorite-step]');
  if(favoriteStep){const {favorites,stationIndex}=session.snapshot;if(favorites.length){const index=favorites.indexOf(stationIndex);session.select(favorites[(index+Number(favoriteStep.dataset.favoriteStep)+favorites.length)%favorites.length]);}}
  const view=event.target.closest('[data-view]');
  if(view)setView(view.dataset.view);
});
$('#favorite-toggle').addEventListener('click',()=>session.toggleFavorite());
$('#play-toggle').addEventListener('click',()=>session.update({presentationPlayback:session.snapshot.presentationPlayback==='playing'?'paused':'playing'}));
const setVolumeStep=event=>session.update({volume:Math.round(Number(event.target.value)/30*100)});
$('#segmented-volume').addEventListener('input',setVolumeStep);
let previousVolume=66;
$('#stereo-button').addEventListener('click',()=>{const value=session.snapshot.volume;if(value){previousVolume=value;session.update({volume:0});}else session.update({volume:previousVolume});});
$('#station-search').addEventListener('input',renderStations);
$$('.filters select').forEach(select=>select.addEventListener('change',renderStations));
$('#clear-filters').addEventListener('click',()=>{$('#station-search').value='';$$('.filters select').forEach(select=>select.value='');renderStations();});
$('#automatic-search').addEventListener('click',()=>{setView('directory');$('#station-search').focus();});
$('#random-station').addEventListener('click',()=>{const options=filteredStations();if(options.length)session.select(options[Math.floor(Math.random()*options.length)].index);});
$('#monitor-toggle').addEventListener('click',()=>setMotion(!motion));
$('#equalizer-preview').addEventListener('click',()=>setMotion(!motion));
$('#equalizer-preview').title='预览模拟频谱；没有均衡器音频处理';
const tempo=$('#tempo');
function renderTempo(){
  const value=Number(tempo.value);
  tempo.setAttribute('aria-valuetext',`${value}%，视觉样机，未接入音频变速`);
  $('#tempo-value').textContent=`${value}%`;
}
tempo.addEventListener('input',renderTempo);
renderTempo();
$('#ui-language').addEventListener('change',event=>{uiLanguage=event.target.value;applyLanguage();});
$('#check-updates').addEventListener('click',()=>{$('#update-result').hidden=false;$('#update-result').textContent=tr('updateUnavailable');});
$('#about-toggle').addEventListener('click',()=>{const expanded=$('#about-toggle').getAttribute('aria-expanded')!=='true';$('#about-toggle').setAttribute('aria-expanded',String(expanded));$('#about-content').hidden=!expanded;});
let pageSwipe;
const modernScene=$('.scene');
modernScene.addEventListener('pointerdown',event=>{
  if(!session.snapshot.powered||session.snapshot.controlsLocked||document.body.dataset.refreshing==='true'||event.target.closest('button,input,select,a,[role="slider"]'))return;
  pageSwipe={id:event.pointerId,x:event.clientX,y:event.clientY};modernScene.setPointerCapture(event.pointerId);
});
modernScene.addEventListener('pointerup',event=>{
  if(!pageSwipe||pageSwipe.id!==event.pointerId)return;
  const dx=event.clientX-pageSwipe.x,dy=event.clientY-pageSwipe.y;pageSwipe=undefined;
  if(Math.abs(dx)>=72&&Math.abs(dx)>Math.abs(dy)*1.3){if(parent!==window)parent.postMessage({type:'aether-preview-page',page:'immersive'},location.origin);else location.href='/preview.html?page=immersive';}
});
modernScene.addEventListener('pointercancel',()=>{pageSwipe=undefined;});
applyLanguage();
const disposeFilters=mountFilterMenus();
const disposeHardware = mountHardware({session,filteredStations,setView,getView:()=>currentView,renderDisplays:state=>{cancelAnimationFrame(animationFrame);if(state.powered)setMotion(motion);$$('.gauge').forEach(drawGauge);drawSpectrum();}});
window.addEventListener('pagehide',()=>{unsubscribe();disposeHardware();disposeFilters();cancelAnimationFrame(animationFrame);},{once:true});
document.body.dataset.ready='true';
