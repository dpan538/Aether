// A single presentation state shared by the prototype's views. No stream opens.
const listeners = new Set();
// All programme, artist, track, quality and clock values below are design mocks.
// Real station names anchor the layout; this is not a directory or live schedule.
const stations = Object.freeze([
  Object.freeze({ stationName: 'FM 802', subtitle: 'OSAKA / JAPAN', programme: 'NIGHT\nWINDOWS', description: '夜间漫游 · Alternative / Electronic', track: 'Haru Aoki — Last Train Home', quality: '128 kbps · AAC', localTime: '22:14 JST' }),
  Object.freeze({ stationName: 'BBC RADIO 1', subtitle: 'LONDON / UK', programme: 'AFTER\nTHE CITY', description: '城市余响 · Indie / New Music', track: 'Maya North — Slow Motion', quality: '192 kbps · AAC', localTime: '14:14 BST' }),
  Object.freeze({ stationName: 'NTS RADIO', subtitle: 'LONDON / UK', programme: 'OPEN\nCIRCUIT', description: '开放回路 · Ambient / Experimental', track: 'Mono Field — Soft Systems', quality: '256 kbps · MP3', localTime: '14:14 BST' }),
  Object.freeze({ stationName: 'RADIO NOVA', subtitle: 'PARIS / FRANCE', programme: 'LATE\nRENDEZVOUS', description: '午夜相遇 · Soul / Global Sounds', track: 'Lucie Morel — À demi-mot', quality: '128 kbps · AAC', localTime: '15:14 CEST' })
]);
let state = Object.freeze({ ...stations[0], stationIndex: 0, stationCount: stations.length, mock: true, connection: 'not-connected', stationId: null, streamUrl: null });
export const session = Object.freeze({
  get snapshot() { return state; },
  subscribe(listener) { listeners.add(listener); listener(state); return () => listeners.delete(listener); },
  update(patch) { state = Object.freeze({ ...state, ...patch }); for (const listener of listeners) listener(state); },
  step(direction) {
    const stationIndex = (state.stationIndex + direction + stations.length) % stations.length;
    this.update({ ...stations[stationIndex], stationIndex });
  }
});
