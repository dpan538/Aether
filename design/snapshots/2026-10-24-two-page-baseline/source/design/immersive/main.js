import { createDisc } from './disc.js';
import { renderPrintPlot } from './print.js';
import { session } from './session.js';

const query = new URLSearchParams(location.search);
const animate = query.get('motion') === 'on' && !matchMedia('(prefers-reduced-motion: reduce)').matches;
const pixelRatio = query.get('render') === 'high' ? 2 : undefined;
const canvas = document.getElementById('record');
const disc = createDisc(canvas, { animate: false, material: 'print', pixelRatio });
canvas.parentElement.dataset.renderer = canvas.dataset.renderer;
const selectorCanvas = document.getElementById('selector-record');
const selectorDisc = createDisc(selectorCanvas, { animate, material: 'spectral', pixelRatio });
renderPrintPlot(document.getElementById('print-plot'));
const unsubscribe = session.subscribe(state => {
  document.getElementById('station-name').textContent = state.stationName;
  document.getElementById('station-subtitle').textContent = state.subtitle;
  document.getElementById('programme').textContent = state.programme;
  document.getElementById('programme-description').textContent = state.description;
  document.getElementById('now-playing').textContent = state.track;
  document.getElementById('stream-quality').textContent = state.quality;
  document.getElementById('station-time').textContent = state.localTime;
  document.getElementById('station-serial').textContent = String(state.stationIndex + 1).padStart(3, '0');
  document.getElementById('disc-edition').textContent = `PHASE ${String(state.stationIndex + 1).padStart(2, '0')} · FREQUENCY`;
  document.getElementById('station-position').textContent = `${String(state.stationIndex + 1).padStart(2, '0')} / ${String(state.stationCount).padStart(2, '0')}`;
});
document.querySelector('.station-step--previous').addEventListener('click', () => session.step(-1));
document.querySelector('.station-step--next').addEventListener('click', () => session.step(1));
let pointerStart;
const scene = document.querySelector('.scene');
scene.addEventListener('pointerdown', event => {
  if (event.target.closest('button')) return;
  pointerStart = { id: event.pointerId, x: event.clientX, y: event.clientY };
  scene.setPointerCapture(event.pointerId);
});
scene.addEventListener('pointerup', event => {
  if (!pointerStart || pointerStart.id !== event.pointerId) return;
  const dx = event.clientX - pointerStart.x;
  const dy = event.clientY - pointerStart.y;
  pointerStart = null;
  if (Math.abs(dx) >= 72 && Math.abs(dx) > Math.abs(dy) * 1.3) {
    if(parent !== window) parent.postMessage({type:'aether-preview-page',page:'modern'},location.origin);
    else location.href='/preview.html?page=modern';
    return;
  }
  if (Math.abs(dy) >= 40 && Math.abs(dy) > Math.abs(dx)) session.step(dy < 0 ? 1 : -1);
});
scene.addEventListener('pointercancel', () => { pointerStart = null; });
window.addEventListener('keydown', event => {
  if (['ArrowLeft', 'ArrowUp', 'ArrowRight', 'ArrowDown'].includes(event.key)) {
    event.preventDefault();
    session.step(['ArrowLeft', 'ArrowUp'].includes(event.key) ? 1 : -1);
  }
});
const observer = new ResizeObserver(() => { disc.resize(); selectorDisc.resize(); });
observer.observe(canvas);
observer.observe(selectorCanvas);
window.addEventListener('pagehide', () => { unsubscribe(); observer.disconnect(); disc.destroy(); selectorDisc.destroy(); }, { once: true });
window.aetherDesign = Object.freeze({
  reference: { width: 3408, height: 2272, cssWidth: 1704, cssHeight: 1136 },
  getGeometry() {
    const stage = document.querySelector('.scene').getBoundingClientRect();
    const record = document.querySelector('.record').getBoundingClientRect();
    const title = document.querySelector('.station-selector').getBoundingClientRect();
    const note = document.querySelector('.signal-note').getBoundingClientRect();
    const box = rect => ({ x: rect.x, y: rect.y, width: rect.width, height: rect.height });
    return { stage: box(stage), record: box(record), title: box(title), note: box(note), diameterRatio: record.width / Math.min(stage.width, stage.height), leftMargin: record.left - stage.left, topMargin: record.top - stage.top, bottomMargin: stage.bottom - record.bottom, renderer: canvas.dataset.renderer, interactiveElements: document.querySelectorAll('button, input, select, a, [role="button"]').length, connection: session.snapshot.connection };
  }
});
document.body.dataset.ready = 'true';
