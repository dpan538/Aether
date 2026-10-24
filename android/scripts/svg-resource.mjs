// AndroidSVG 1.4 needs CSS Color 3 literals. Normalize immutable resources at
// build time instead of allocating thousands of formatters on the UI thread.
export function nativeSvg(source) {
  const hex=(parts,unit)=>{
    const values=parts.trim().split(/[\s,/]+/).map(Number);
    if(values[3]===0)return 'none';
    return '#'+values.slice(0,3).map(v=>Math.max(0,Math.min(255,Math.round(v*unit))).toString(16).padStart(2,'0')).join('');
  };
  return source.replace(/color\(srgb ([^)]+)\)/g,(_,v)=>hex(v,255)).replace(/rgba?\(([^)]+)\)/g,(_,v)=>hex(v,1));
}
