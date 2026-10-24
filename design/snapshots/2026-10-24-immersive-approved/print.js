export function renderPrintPlot(element) {
  const width = 600;
  const height = 190;
  const curves = [
    x => 90 + 18 * Math.sin(x / 52) - 21 * Math.cos(x / 83) + 7 * Math.sin(x / 12),
    x => 94 - 20 * Math.sin(x / 70 + 1.3) + 12 * Math.cos(x / 22),
    x => 92 + 12 * Math.cos(x / 62 + 2) + 8 * Math.sin(x / 17)
  ];
  const paths = curves.map((f, i) => `<path d="${Array.from({ length: 201 }, (_, j) => { const x = j * 3; return `${j ? 'L' : 'M'}${x},${f(x).toFixed(2)}`; }).join(' ')}" fill="none" stroke="#26333f" stroke-width="${i === 0 ? 1.4 : .8}"/>`).join('');
  const labels = Array.from({ length: 31 }, (_, i) => `<text x="${i * 19.4 + 3}" y="12" font-size="8.6" font-weight="700" fill="#25323b">${String(i + 1).padStart(2, '0')}</text>`).join('');
  element.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" aria-hidden="true"><defs><pattern id="minor" width="6" height="6" patternUnits="userSpaceOnUse"><path d="M6 0H0V6" fill="none" stroke="#3b4850" stroke-width=".4"/></pattern><pattern id="major" width="30" height="30" patternUnits="userSpaceOnUse"><rect width="30" height="30" fill="url(#minor)"/><path d="M30 0H0V30" fill="none" stroke="#3b4850" stroke-width=".8"/></pattern></defs>${labels}<rect x=".5" y="20" width="599" height="169.5" fill="url(#major)" stroke="#2b3943" stroke-width="1"/>${paths}<path d="M85 20V190M181 20V190M330 20V190M509 20V190" fill="none" stroke="#35444e" stroke-width="1.2"/><path d="M561 20V190" stroke="#263641" stroke-width="3" stroke-dasharray="2 2"/></svg>`;
}
