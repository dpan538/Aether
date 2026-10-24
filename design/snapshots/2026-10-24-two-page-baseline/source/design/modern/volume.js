import { createDetentGate } from './rotary.js';

// Relative encoder input. Pressing the knob never seeks to an absolute volume.
export function mountVolumeKnob({ session, element }) {
  const events = new AbortController();
  const pointerGate = createDetentGate({ threshold: .3, interval: 140, maxDelta: .12 });
  const verticalGate = createDetentGate({ threshold: 18, interval: 140, maxDelta: 9 });
  const wheelGate = createDetentGate({ threshold: 120, interval: 160, maxDelta: 120 });
  const keyGate = createDetentGate({ threshold: 1, interval: 140 });
  let drag;
  const enabled = () => session.snapshot.powered && !session.snapshot.controlsLocked && element.getAttribute('aria-disabled') !== 'true';
  function setStep(step) {
    if (!enabled()) return;
    const value = Math.round(Math.max(0, Math.min(30, step)) / 30 * 100);
    if (value !== session.snapshot.volume) session.update({ volume: value });
  }
  const advance = direction => { if (direction) setStep(Math.round(session.snapshot.volume * .3) + direction); };
  function point(event) {
    const box = element.getBoundingClientRect();
    const x = event.clientX - box.left - box.width / 2, y = event.clientY - box.top - box.height / 2;
    return { angle: Math.atan2(y, x), radius: Math.hypot(x, y), width: box.width };
  }
  function reset() {
    drag = undefined;
    pointerGate.reset();verticalGate.reset();
    element.dataset.dragging = 'false';
  }
  element.addEventListener('pointerdown', event => {
    if (!enabled() || event.button !== 0) return;
    const start = point(event);
    pointerGate.reset();verticalGate.reset();
    drag = { id: event.pointerId, mode: start.radius > start.width * .27 ? 'rotate' : 'vertical', angle: start.angle, y: event.clientY };
    element.dataset.dragging = 'true';
    element.focus({ preventScroll: true });
    element.setPointerCapture(event.pointerId);
    event.preventDefault();
  }, { signal: events.signal });
  element.addEventListener('pointermove', event => {
    if (!drag || drag.id !== event.pointerId || !enabled()) return;
    const now = performance.now();
    if (drag.mode === 'vertical') {
      const delta = drag.y - event.clientY;
      drag.y = event.clientY;
      advance(verticalGate.push(delta, now));
    } else {
      const next = point(event);
      let delta = next.angle - drag.angle;
      drag.angle = next.angle;
      if (next.radius < next.width * .2) { pointerGate.reset(); return; }
      if (delta > Math.PI) delta -= Math.PI * 2;
      if (delta < -Math.PI) delta += Math.PI * 2;
      advance(pointerGate.push(delta * .75, now));
    }
  }, { signal: events.signal });
  for (const event of ['pointerup','pointercancel','lostpointercapture']) element.addEventListener(event, reset, { signal: events.signal });
  element.addEventListener('wheel', event => {
    event.preventDefault();
    if (!enabled()) return;
    // A coarse mouse-wheel notch is one level; fine trackpad travel accumulates.
    const coarse = event.deltaMode !== 0 || Math.abs(event.deltaY) >= 80;
    const travel = coarse ? Math.sign(event.deltaY) * 120 : event.deltaY;
    advance(-wheelGate.push(travel, performance.now()));
  }, { passive: false, signal: events.signal });
  element.addEventListener('keydown', event => {
    if (!enabled() || !['ArrowUp','ArrowRight','ArrowDown','ArrowLeft','Home','End'].includes(event.key)) return;
    event.preventDefault();
    const direction = ['ArrowUp','ArrowRight','End'].includes(event.key) ? 1 : -1;
    const gated = keyGate.push(direction, performance.now());
    if (event.repeat && !gated) return;
    if (event.key === 'Home') setStep(0);
    else if (event.key === 'End') setStep(30);
    else advance(direction);
  }, { signal: events.signal });
  return () => { events.abort();reset(); };
}
