import { createDetentGate } from './rotary.js';
import { mountVolumeKnob } from './volume.js';

// Physical controls share presentation state. No audio or hardware driver opens.
export function mountHardware({ session, filteredStations, setView, getView, renderDisplays }) {
  const $ = selector => document.querySelector(selector);
  const power = $('#device-power'), lock = $('#device-lock');
  const settings = $('#device-settings'), light = $('#device-light');
  const dial = $('#tuning-dial'), modeDial = $('#tuning-mode');
  const dimensions = [
    { key: 'station', label: '电台', caption: 'STATION', icon: 'station' },
    { key: 'region', label: '地区', caption: 'REGION', selector: '#region-filter', icon: 'globe' },
    { key: 'genre', label: '类型', caption: 'GENRE', selector: '#genre-filter', icon: 'genre' },
    { key: 'language', label: '语言', caption: 'LANGUAGE', selector: '#language-filter', icon: 'language' }
  ];
  let mode = 0;
  let previousPlayback = session.snapshot.presentationPlayback;
  let lastDisplayState;
  let refreshTimer;
  let refreshing=false;
  let dialRotation = -125;
  const enabled = () => session.snapshot.powered && !session.snapshot.controlsLocked && !refreshing;

  $('.dial-scale').innerHTML = Array.from({ length: 80 }, (_, index) => {
    const angle = index * Math.PI * 2 / 80;
    const inner = index % 5 === 0 ? 107 : 111;
    return `<path d="M${120 + inner * Math.sin(angle)} ${120 - inner * Math.cos(angle)}L${120 + 116 * Math.sin(angle)} ${120 - 116 * Math.cos(angle)}" stroke-width="${index % 5 === 0 ? 1.6 : 1}"/>`;
  }).join('');
  const arcAt=degrees=>{const angle=degrees*Math.PI/180;return[60+55*Math.sin(angle),60-55*Math.cos(angle)];};
  const arcStart=arcAt(-75),arcEnd=arcAt(80);
  $('#mode-scale-arc').setAttribute('d',`M${arcStart[0]} ${arcStart[1]}A55 55 0 0 1 ${arcEnd[0]} ${arcEnd[1]}`);
  $('#volume-scale').innerHTML = Array.from({length:31},(_,index)=>{
    const angle=(-135+index*9)*Math.PI/180, major=index%5===0;
    const at=radius=>[100+Math.sin(angle)*radius,100-Math.cos(angle)*radius];
    const a=at(major?79:83),b=at(87),label=at(93);
    return `<path class="${major?'major':'minor'}" d="M${a[0]} ${a[1]}L${b[0]} ${b[1]}"/>${major?`<text x="${label[0]}" y="${label[1]+3}" text-anchor="middle">${index}</text>`:''}`;
  }).join('');
  $('.mode-detents').innerHTML = dimensions.map((dimension,index) => `<span data-mode="${index}" title="${dimension.label}"><svg><use href="#i-${dimension.icon}"/></svg></span>`).join('');
  function drawModeLegend() {
    const area=$('.tuning-area').getBoundingClientRect(),knob=modeDial.getBoundingClientRect();
    const scale=$('.mode-scale').getBoundingClientRect();
    const unit=$('.scene').getBoundingClientRect().height/100;
    const column=area.width-.4*unit;
    const cx=knob.left-area.left+knob.width/2,cy=knob.top-area.top+knob.height/2;
    const radius=scale.width*59/120;
    const firstTrunk=dial.getBoundingClientRect().right-area.left+.45*unit;
    $('.mode-leaders').setAttribute('viewBox',`0 0 ${area.width} ${area.height}`);
    $('.mode-leaders').innerHTML=dimensions.map((_,index)=>{
      const angle=(-60+index*40)*Math.PI/180;
      const at=r=>[cx+Math.sin(angle)*r,cy-Math.cos(angle)*r];
      const tip=at(radius);
      const trunk=firstTrunk+index*.75*unit;
      const crown=cy-radius+(.65-index*.7)*unit;
      const y=(.9+index*2.65)*unit;
      const mark=$(`.mode-detents [data-mode="${index}"]`);
      mark.style.left=`${column}px`;mark.style.top=`${y}px`;
      // Identical small-radius bends keep the early, ordered long-line layout.
      const corner=.18*unit, direction=Math.sign(trunk-tip[0]);
      return `<path d="M${tip[0]} ${tip[1]}V${crown+corner}Q${tip[0]} ${crown} ${tip[0]+direction*corner} ${crown}H${trunk-direction*corner}Q${trunk} ${crown} ${trunk} ${crown-corner}V${y+corner}Q${trunk} ${y} ${trunk+corner} ${y}H${column-1.25*unit}"/>`;
    }).join('');
  }
  const legendResize=new ResizeObserver(drawModeLegend);
  legendResize.observe($('.tuning-area'));
  drawModeLegend();

  function refreshDials() {
    const dimension = dimensions[mode];
    let count, index, text;
    if (dimension.selector) {
      const select = $(dimension.selector);
      count = select.options.length;
      index = select.selectedIndex;
      text = `${dimension.label} · ${select.selectedOptions[0].textContent}`;
    } else {
      const results = filteredStations();
      count = results.length;
      index = results.findIndex(station => station.index === session.snapshot.stationIndex);
      text = index >= 0 ? `${index + 1} / ${count} · ${results[index].stationName}` : (count ? '当前电台不在筛选结果中' : '当前筛选没有电台');
    }
    dial.setAttribute('aria-valuemax', String(Math.max(0, count)));
    dial.setAttribute('aria-valuenow', String(index + 1));
    dial.setAttribute('aria-valuetext', text);
    dial.setAttribute('aria-label', `大旋钮：调整${dimension.label}`);
    dial.setAttribute('aria-disabled', String(!enabled() || !count));
    dial.tabIndex = enabled() && count ? 0 : -1;
    // A physical encoder keeps its angle when a station is selected elsewhere.
    dial.style.setProperty('--tuning-angle', `${dialRotation}deg`);
    modeDial.setAttribute('aria-valuenow', String(mode));
    modeDial.setAttribute('aria-valuetext', dimension.label);
    modeDial.setAttribute('aria-disabled', String(!enabled()));
    modeDial.tabIndex = enabled() ? 0 : -1;
    $('.mode-selector').style.setProperty('--mode-angle', `${-60 + mode * 40}deg`);
    document.querySelectorAll('.mode-detents [data-mode]').forEach(mark => { mark.dataset.active = String(Number(mark.dataset.mode) === mode); });
    document.body.dataset.tuningDimension = dimension.key;
    $('.directory-unit').dataset.tuningMode = dimension.key;
    $('#tuning-mode-readout').textContent = `TUNE / ${dimension.caption}`;
    document.querySelectorAll('.filters select').forEach(select => {
      select.closest('label').dataset.tuningActive = String(select.id === `${dimension.key}-filter`);
    });
  }
  function tune(direction, endpoint) {
    if (!enabled()) return;
    if (getView() === 'settings') setView('directory');
    const dimension = dimensions[mode];
    if (dimension.selector) {
      const select = $(dimension.selector), count = select.options.length;
      const next = endpoint === 'first' ? 0 : endpoint === 'last' ? count - 1 : (select.selectedIndex + direction + count) % count;
      select.selectedIndex = next;
      select.dispatchEvent(new Event('change', { bubbles: true }));
      refreshDials();
    } else {
      const results = filteredStations();
      if (!results.length) return;
      const current = results.findIndex(station => station.index === session.snapshot.stationIndex);
      const next = endpoint === 'first' ? 0 : endpoint === 'last' ? results.length - 1 : current < 0 ? (direction > 0 ? 0 : results.length - 1) : (current + direction + results.length) % results.length;
      session.select(results[next].index);
    }
  }
  function changeMode(direction, endpoint) {
    if (!enabled()) return;
    if (getView() === 'settings') setView('directory');
    mode = endpoint === 'first' ? 0 : endpoint === 'last' ? dimensions.length - 1 : (mode + direction + dimensions.length) % dimensions.length;
    refreshDials();
  }
  function render(state) {
    document.body.dataset.powered = String(state.powered);
    document.body.dataset.backlight = String(state.backlight);
    document.body.dataset.locked = String(state.controlsLocked);
    power.setAttribute('aria-pressed', String(state.powered));
    lock.setAttribute('aria-pressed', String(state.controlsLocked));
    light.setAttribute('aria-pressed', String(state.powered && state.backlight));
    settings.setAttribute('aria-pressed', String(getView() === 'settings'));
    power.disabled = state.controlsLocked || refreshing;
    lock.disabled = !state.powered || refreshing;
    light.disabled = !state.powered || refreshing;
    settings.disabled = !enabled();
    document.querySelectorAll('.directory-unit button,.directory-unit input,.directory-unit select,.console button,.console input').forEach(control => { control.disabled = !enabled() || control.classList.contains('preset-empty'); });
    $('#volume').setAttribute('aria-disabled',String(!enabled()));
    $('#volume').tabIndex = enabled() ? 0 : -1;
    const displayState = `${state.powered}:${state.backlight}`;
    if (lastDisplayState !== displayState) {
      lastDisplayState = displayState;
      renderDisplays(state);
    }
    refreshDials();
  }
  function refreshInk() {
    const scene = $('.scene');
    clearTimeout(refreshTimer);
    scene.classList.remove('epaper-refresh');
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    refreshing=true;document.body.dataset.refreshing='true';render(session.snapshot);
    void scene.offsetWidth;
    scene.classList.add('epaper-refresh');
    refreshTimer = setTimeout(() => {
      scene.classList.remove('epaper-refresh');refreshing=false;document.body.dataset.refreshing='false';render(session.snapshot);
    }, 1850);
  }
  power.addEventListener('click', () => {
    if (session.snapshot.controlsLocked) return;
    const powered = !session.snapshot.powered;
    if (!powered) previousPlayback = session.snapshot.presentationPlayback;
    session.update({ powered, presentationPlayback: powered ? previousPlayback : 'paused' });
    refreshInk();
  });
  lock.addEventListener('click', () => { if (session.snapshot.powered) session.update({ controlsLocked: !session.snapshot.controlsLocked }); });
  light.addEventListener('click', () => { if (session.snapshot.powered) session.update({ backlight: !session.snapshot.backlight }); });
  settings.addEventListener('click', () => { if (enabled()) setView(getView() === 'settings' ? 'directory' : 'settings'); });

  // Heavy detents: no burst loop, no inertial queue, and no angle near the hub.
  function bindRotary(element, step) {
    let drag, pulseTimer;
    const isMain = element === dial;
    const pointerGate = createDetentGate({ threshold: isMain ? .57 : .66, interval: 180, maxDelta: .2 });
    const wheelGate = createDetentGate({ threshold: 96, interval: 180, maxDelta: 48 });
    const keyGate = createDetentGate({ threshold: 1, interval: 180 });
    const canOperate = () => enabled() && element.getAttribute('aria-disabled') !== 'true';
    function commit(direction, endpoint) {
      if (!canOperate()) return;
      if (isMain) dialRotation += direction * 24;
      step(direction, endpoint);
      element.dataset.detent = 'true';
      clearTimeout(pulseTimer);
      pulseTimer = setTimeout(() => { element.dataset.detent = 'false'; }, 190);
    }
    element.addEventListener('wheel', event => {
      event.preventDefault();
      if (!canOperate()) return;
      const direction = wheelGate.push(event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? 96 : 1), performance.now());
      if (direction) commit(direction);
    }, { passive: false });
    element.addEventListener('keydown', event => {
      if (!canOperate()) return;
      if (['ArrowRight','ArrowUp','ArrowLeft','ArrowDown','Home','End'].includes(event.key)) {
        event.preventDefault();
        const direction = ['ArrowRight','ArrowUp','End'].includes(event.key) ? 1 : -1;
        const gated = keyGate.push(direction, performance.now());
        if (!event.repeat || gated) commit(direction, event.key === 'Home' ? 'first' : event.key === 'End' ? 'last' : undefined);
      }
    });
    function angle(event) {
      const box = element.getBoundingClientRect();
      // The centre has no useful tangential leverage and used to cause jumps.
      if (Math.hypot(event.clientX - box.left - box.width / 2, event.clientY - box.top - box.height / 2) < box.width * .2) return null;
      return Math.atan2(event.clientY - box.top - box.height / 2, event.clientX - box.left - box.width / 2);
    }
    element.addEventListener('pointerdown', event => {
      if (!canOperate() || event.button !== 0) return;
      element.focus();
      pointerGate.reset();
      drag = { id: event.pointerId, angle: angle(event), x: event.clientX, y: event.clientY, moved: false };
      element.dataset.dragging = 'true';
      element.setPointerCapture(event.pointerId);
      event.preventDefault();
    });
    element.addEventListener('pointermove', event => {
      if (!drag || drag.id !== event.pointerId || !canOperate()) return;
      if (Math.hypot(event.clientX - drag.x, event.clientY - drag.y) > 7) drag.moved = true;
      const next = angle(event);
      if (next === null || drag.angle === null) { drag.angle = next; pointerGate.reset(); return; }
      let delta = next - drag.angle;
      if (delta > Math.PI) delta -= Math.PI * 2;
      if (delta < -Math.PI) delta += Math.PI * 2;
      drag.angle = next;
      const direction = pointerGate.push(delta * .82, performance.now());
      if (direction) commit(direction);
    });
    element.addEventListener('pointerup', event => {
      if (!drag || drag.id !== event.pointerId) return;
      if (!drag.moved && canOperate() && angle(event) !== null) {
        const box = element.getBoundingClientRect();
        commit(event.clientX < box.left + box.width / 2 ? -1 : 1);
      }
      drag = undefined;
      element.dataset.dragging = 'false';
      pointerGate.reset();
    });
    const cancelDrag = () => { drag = undefined; element.dataset.dragging = 'false'; pointerGate.reset(); };
    element.addEventListener('pointercancel', cancelDrag);
    element.addEventListener('lostpointercapture', cancelDrag);
  }
  bindRotary(dial, tune);
  bindRotary(modeDial, changeMode);
  const disposeVolume = mountVolumeKnob({ session, element: $('#volume') });
  const viewChanged = () => render(session.snapshot);
  document.addEventListener('aether:results', refreshDials);
  document.addEventListener('aether:view', viewChanged);
  const unsubscribe = session.subscribe(render);
  if(session.snapshot.powered)refreshInk();
  return () => {
    unsubscribe();
    disposeVolume();
    legendResize.disconnect();
    clearTimeout(refreshTimer);
    document.removeEventListener('aether:results', refreshDials);
    document.removeEventListener('aether:view', viewChanged);
  };
}
