// Single source of truth for temperature color across the whole app:
// maps, hero glow, and the decade chart all key off ABSOLUTE °C, so the same
// temperature is always the same color. Cold blue -> hot red, no violet
// (violet read as "off-scale" on the cream editorial theme).

// Absolute temperature (°C) -> OKLCH [L, C, H]. Monotonic, no hue wrap.
const HEAT_STOPS = [
  [8, [0.60, 0.15, 250]],   // bleu (<10°)
  [18, [0.68, 0.16, 142]],  // vert (<20°)
  [28, [0.72, 0.16, 72]],   // orange (<30°)
  [38, [0.58, 0.22, 28]],   // rouge (<40°)
  [45, [0.52, 0.21, 305]],  // violet (>40°)
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

// Diverging scale for anomalies (Δ°C): cooler = blue, ~0 = neutral, warmer = red.
const ANOM_MAX = 12;
const ANOM_STOPS = [
  [-ANOM_MAX, [0.62, 0.13, 250]],
  [0, [0.9, 0.02, 95]],
  [ANOM_MAX, [0.57, 0.21, 30]],
];

/** Temperature anomaly (Δ°C) -> "oklch(...)" on the diverging scale. */
export function divergingColor(delta) {
  if (delta == null || Number.isNaN(delta)) return NEUTRAL;
  const t = clamp(delta, -ANOM_MAX, ANOM_MAX);
  const [lo, hi] = t <= 0 ? [ANOM_STOPS[0], ANOM_STOPS[1]] : [ANOM_STOPS[1], ANOM_STOPS[2]];
  const k = (t - lo[0]) / (hi[0] - lo[0] || 1);
  const L = lerp(lo[1][0], hi[1][0], k);
  const C = lerp(lo[1][1], hi[1][1], k);
  const H = lerp(lo[1][2], hi[1][2], k);
  return `oklch(${(L * 100).toFixed(1)}% ${C.toFixed(3)} ${H.toFixed(1)})`;
}

/** CSS linear-gradient spanning the shared thermal scale (cold -> hot). */
export function heatGradient() {
  const stops = [8, 16, 24, 32, 40].map((t) => heatColor(t)).join(', ');
  return `linear-gradient(90deg, ${stops})`;
}

/** Legend buckets for the shared scale — { label, temp } pairs. */
export const HEAT_LEGEND = [
  { label: '<10°', temp: 8 },
  { label: '<20°', temp: 18 },
  { label: '<30°', temp: 28 },
  { label: '<40°', temp: 38 },
  { label: '>40°', temp: 45 },
];
