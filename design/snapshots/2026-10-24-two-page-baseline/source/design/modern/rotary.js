// A detent consumes fresh travel; fast input cannot queue later station changes.
export function createDetentGate({ threshold, interval = 170, maxDelta = threshold }) {
  let travel = 0, lastStep = -Infinity, lastInput = -Infinity;
  return {
    push(delta, now) {
      if (!Number.isFinite(delta) || !Number.isFinite(now)) return 0;
      if (now - lastInput > 240 || Math.sign(delta) !== Math.sign(travel)) travel = 0;
      lastInput = now;
      travel += Math.max(-maxDelta, Math.min(maxDelta, delta));
      if (Math.abs(travel) < threshold) return 0;
      const direction = Math.sign(travel);
      travel = 0;
      if (now - lastStep < interval) return 0;
      lastStep = now;
      return direction;
    },
    reset() { travel = 0; lastInput = -Infinity; }
  };
}
