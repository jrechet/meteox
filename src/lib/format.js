// Formatting + WMO weather-code helpers.

const MONTHS_FR = [
  'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre',
];

export function isoToday() {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Parse a "YYYY-MM-DD" string to a Date at local noon.
 * Avoids the UTC-midnight off-by-one that shifts the day in behind-UTC timezones.
 */
export function isoToDate(iso) {
  return new Date(iso + 'T12:00:00');
}

/** Human date "9 juillet" from a Date or ISO string. */
export function dayMonthLabel(date) {
  if (!date) return '—';
  const d = typeof date === 'string' ? new Date(date + 'T12:00:00') : date;
  if (Number.isNaN(d?.getTime())) return '—';
  return `${d.getDate()} ${MONTHS_FR[d.getMonth()]}`;
}

/** MM-DD from a Date. */
export function monthDay(date) {
  if (!date || Number.isNaN(date.getTime())) return '—';
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

const WEEKDAYS_FR = ['dim', 'lun', 'mar', 'mer', 'jeu', 'ven', 'sam'];
const MONTHS_ABBR_FR = [
  'janv', 'févr', 'mars', 'avr', 'mai', 'juin',
  'juil', 'août', 'sept', 'oct', 'nov', 'déc',
];

/** "mer" — short weekday from an ISO date. */
export function weekdayShort(iso) {
  if (!iso) return '—';
  const d = new Date(iso + 'T12:00:00');
  if (Number.isNaN(d.getTime())) return '—';
  return WEEKDAYS_FR[d.getDay()];
}

/** "8 juil" — compact day + abbreviated month. */
export function shortDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso + 'T12:00:00');
  if (Number.isNaN(d.getTime())) return '—';
  return `${d.getDate()} ${MONTHS_ABBR_FR[d.getMonth()]}`;
}

export function fmtTemp(v, withUnit = true) {
  if (v == null || Number.isNaN(v)) return '—';
  return `${Math.round(v)}${withUnit ? '°' : ''}`;
}

export function fmtSigned(v, digits = 1) {
  if (v == null || Number.isNaN(v)) return '—';
  const s = v.toFixed(digits);
  return v > 0 ? `+${s}` : s;
}

export function fmtMm(v) {
  if (v == null || Number.isNaN(v)) return '—';
  return `${v.toFixed(v < 10 ? 1 : 0)} mm`;
}

export function fmtWind(v) {
  if (v == null || Number.isNaN(v)) return '—';
  return `${Math.round(v)} km/h`;
}

// WMO code -> { label, glyph, tint (oklch), glow }
const WMO = {
  0: ['Ciel dégagé', '☀︎'],
  1: ['Peu nuageux', '🌤'],
  2: ['Partiellement nuageux', '⛅'],
  3: ['Couvert', '☁︎'],
  45: ['Brouillard', '🌫'],
  48: ['Brouillard givrant', '🌫'],
  51: ['Bruine légère', '🌦'],
  53: ['Bruine', '🌦'],
  55: ['Bruine dense', '🌧'],
  61: ['Pluie faible', '🌦'],
  63: ['Pluie', '🌧'],
  65: ['Pluie forte', '🌧'],
  66: ['Pluie verglaçante', '🌧'],
  67: ['Pluie verglaçante forte', '🌧'],
  71: ['Neige faible', '🌨'],
  73: ['Neige', '❄︎'],
  75: ['Neige forte', '❄︎'],
  77: ['Grains de neige', '🌨'],
  80: ['Averses', '🌦'],
  81: ['Averses', '🌧'],
  82: ['Averses violentes', '⛈'],
  85: ['Averses de neige', '🌨'],
  86: ['Averses de neige fortes', '❄︎'],
  95: ['Orage', '⛈'],
  96: ['Orage, grêle', '⛈'],
  99: ['Orage violent, grêle', '⛈'],
};

export function describeWeather(code) {
  const [label, glyph] = WMO[code] ?? ['—', '·'];
  return { label, glyph };
}
