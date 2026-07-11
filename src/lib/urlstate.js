// Shareable URL state: persist location + view in the hash so a link reproduces
// exactly what the user is looking at. Kept in the hash (not query) so GitHub
// Pages serves the same static file regardless.

/** Parse a hash string (defaults to the live URL) into a partial state, or null. */
export function parseHash(hash = typeof location !== 'undefined' ? location.hash : '') {
  const raw = (hash || '').replace(/^#/, '');
  if (!raw) return null;
  const p = new URLSearchParams(raw);
  const lat = parseFloat(p.get('lat'));
  const lon = parseFloat(p.get('lon'));
  const year = parseInt(p.get('year'), 10);
  const win = parseInt(p.get('win'), 10);
  return {
    lat: Number.isFinite(lat) ? lat : null,
    lon: Number.isFinite(lon) ? lon : null,
    name: p.get('name') || null,
    admin: p.get('admin') || null,
    mode: p.get('mode') === 'period' ? 'period' : p.get('mode') === 'day' ? 'day' : null,
    year: Number.isFinite(year) ? year : null,
    win: [5, 10, 30].includes(win) ? win : null,
    date: /^\d{4}-\d{2}-\d{2}$/.test(p.get('date') || '') ? p.get('date') : null,
  };
}

/** Write the shareable slice of state to the URL hash (no history entry). */
export function writeHash(state) {
  const loc = state.location;
  if (!loc) return;
  const p = new URLSearchParams();
  p.set('lat', loc.lat.toFixed(4));
  p.set('lon', loc.lon.toFixed(4));
  if (loc.name) p.set('name', loc.name);
  if (loc.admin) p.set('admin', loc.admin);
  p.set('mode', state.mode);
  p.set('year', String(state.selectedYear));
  if (state.mode === 'period') p.set('win', String(state.windowLen));
  if (state.dateSelected && state.selectedIso) p.set('date', state.selectedIso);
  history.replaceState(null, '', `#${p.toString()}`);
}
