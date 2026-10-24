// Signal lamps describe known state; a mock programme is not a live connection.
export function getStatusLights(state, motion) {
  if (!state.powered) return { link: 'off', audio: 'off', visual: 'off' };
  const fault = ['error','failed','permission-required','permission-denied'].includes(state.connection) || state.permissionState === 'denied';
  const linked = state.connection === 'connected';
  return {
    link: fault ? 'warning' : linked ? 'connected' : 'disconnected',
    audio: fault ? 'warning' : linked && state.presentationPlayback === 'playing' ? 'connected' : 'disconnected',
    visual: motion ? 'connected' : 'disconnected'
  };
}
