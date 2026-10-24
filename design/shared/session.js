export const stationCatalog = Object.freeze([
  Object.freeze({ stationName: 'FM 802', subtitle: 'OSAKA / JAPAN', programme: 'NIGHT\nWINDOWS', description: '夜间漫游 · Alternative / Electronic', track: 'Haru Aoki — Last Train Home', quality: '128 kbps · AAC', localTime: '22:14 JST' }),
  Object.freeze({ stationName: 'BBC RADIO 1', subtitle: 'LONDON / UK', programme: 'AFTER\nTHE CITY', description: '城市余响 · Indie / New Music', track: 'Maya North — Slow Motion', quality: '192 kbps · AAC', localTime: '14:14 BST' }),
  Object.freeze({ stationName: 'NTS RADIO', subtitle: 'LONDON / UK', programme: 'OPEN\nCIRCUIT', description: '开放回路 · Ambient / Experimental', track: 'Mono Field — Soft Systems', quality: '256 kbps · MP3', localTime: '14:14 BST' }),
  Object.freeze({ stationName: 'RADIO NOVA', subtitle: 'PARIS / FRANCE', programme: 'LATE\nRENDEZVOUS', description: '午夜相遇 · Soul / Global Sounds', track: 'Lucie Morel — À demi-mot', quality: '128 kbps · AAC', localTime: '15:14 CEST' })
]);

// A single presentation state shared by the prototype's views. No stream opens.
// Programme, artist, track, quality and clock values are design mocks.
// Real station names anchor the layout; this is not a live directory or schedule.
function createSession() {
  const listeners = new Set();
  let state = Object.freeze({ ...stationCatalog[0], stationIndex: 0, stationCount: stationCatalog.length, mock: true, connection: 'not-connected', stationId: null, streamUrl: null, presentationPlayback: 'playing', powered: true, backlight: false, controlsLocked: false, volume: 66, favorites: Object.freeze([0, 2, 1, 3]) });
  return Object.freeze({
    get snapshot() { return state; },
    subscribe(listener) { listeners.add(listener); listener(state); return () => listeners.delete(listener); },
    update(patch) { state = Object.freeze({ ...state, ...patch }); for (const listener of listeners) listener(state); },
    select(stationIndex) {
      if (!Number.isInteger(stationIndex) || stationIndex < 0 || stationIndex >= stationCatalog.length) return;
      this.update({ ...stationCatalog[stationIndex], stationIndex });
    },
    toggleFavorite(stationIndex = state.stationIndex) {
      const favorites = state.favorites.includes(stationIndex) ? state.favorites.filter(index => index !== stationIndex) : [...state.favorites, stationIndex];
      this.update({ favorites: Object.freeze(favorites) });
    },
    step(direction) {
      const stationIndex = (state.stationIndex + direction + stationCatalog.length) % stationCatalog.length;
      this.update({ ...stationCatalog[stationIndex], stationIndex });
    }
  });

}
// Views in the same local preview host reuse the exact same object and connection state.
// Separate browser tabs are separate mock app instances. No persistence or audio stream.
let host = window;
try { if (window.parent.location.origin === location.origin) host = window.parent; } catch { /* cross-origin embed keeps its own session */ }
const key = Symbol.for('aether.local.presentation.session');
if (!host[key]) Object.defineProperty(host, key, { value: createSession() });
export const session = host[key];
