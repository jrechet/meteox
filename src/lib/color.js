// Single source of truth for temperature color across the whole app:
// maps, hero glow, and the decade chart all key off ABSOLUTE °C, so the same
// temperature is always the same color. Cold blue -> hot red, no violet
// (violet read as "off-scale" on the cream editorial theme).

// Absolute temperature (°C) -> OKLCH [L, C, H]. Monotonic, no hue wrap.
const HEAT_STOPS = [
  [8, [0.62, 0.13, 250]], // cold blue
  [16, [0.7, 0.12, 155]], // cool green
  [24, [0.8, 0.13, 90]], // amber
  [32, [0.7, 0.17, 55]], // orange
  [40, [0.57, 0.21, 30]], // hot red
];

const NEUTRAL = 'oklch(72.0% 0.020 120.0)'; // missing / no data

function lerp(a, b, t) {
  return a + (b - a) * t;
}

function clamp(v, lo, hi) {
  return Math.max(lo, Math.min(hi, v));
}

/** Absolute temperature (°C) -> "oklch(...)" on the shared thermal ramp. */
export function heatColor(tempC) {
  if (tempC == null || Number.isNaN(tempC)) return NEUTRAL;
  const t = clamp(tempC, HEAT_STOPS[0][0], HEAT_STOPS[HEAT_STOPS.length - 1][0]);

  let lo = HEAT_STOPS[0];
  let hi = HEAT_STOPS[HEAT_STOPS.length - 1];
  for (let i = 0; i < HEAT_STOPS.length - 1; i++) {
    if (t >= HEAT_STOPS[i][0] && t <= HEAT_STOPS[i + 1][0]) {
      lo = HEAT_STOPS[i];
      hi = HEAT_STOPS[i + 1];
      break;
    }
  }
  const span = hi[0] - lo[0] || 1;
  const k = (t - lo[0]) / span;
  const L = lerp(lo[1][0], hi[1][0], k);
  const C = lerp(lo[1][1], hi[1][1], k);
  const H = lerp(lo[1][2], hi[1][2], k);
  return `oklch(${(L * 100).toFixed(1)}% ${C.toFixed(3)} ${H.toFixed(1)})`;
}

/** Legend buckets for the shared scale — { label, temp } pairs. */
export const HEAT_LEGEND = [
  { label: '≤10°', temp: 8 },
  { label: '15°', temp: 15 },
  { label: '22°', temp: 22 },
  { label: '30°', temp: 31 },
  { label: '≥38°', temp: 40 },
];
