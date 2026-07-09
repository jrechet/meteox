// Semantic cold -> hot color ramp, driven by a normalized 0..1 value.

// Anchor stops in OKLCH (L, C, H). Cold blue -> neutral -> hot red.
const STOPS = [
  [0.0, [0.6, 0.13, 245]],
  [0.35, [0.7, 0.08, 235]],
  [0.5, [0.74, 0.03, 120]],
  [0.65, [0.72, 0.13, 60]],
  [0.85, [0.64, 0.19, 40]],
  [1.0, [0.58, 0.21, 28]],
];

function lerp(a, b, t) {
  return a + (b - a) * t;
}

/** t in [0,1] -> "oklch(L% C H)" string along the ramp. */
export function rampColor(t) {
  const x = Math.max(0, Math.min(1, t));
  let lo = STOPS[0];
  let hi = STOPS[STOPS.length - 1];
  for (let i = 0; i < STOPS.length - 1; i++) {
    if (x >= STOPS[i][0] && x <= STOPS[i + 1][0]) {
      lo = STOPS[i];
      hi = STOPS[i + 1];
      break;
    }
  }
  const span = hi[0] - lo[0] || 1;
  const k = (x - lo[0]) / span;
  const L = lerp(lo[1][0], hi[1][0], k);
  const C = lerp(lo[1][1], hi[1][1], k);
  const H = lerp(lo[1][2], hi[1][2], k);
  return `oklch(${(L * 100).toFixed(1)}% ${C.toFixed(3)} ${H.toFixed(1)})`;
}

/** Normalize value against a domain into [0,1]. */
export function normalize(v, min, max) {
  if (v == null || max === min) return 0.5;
  return (v - min) / (max - min);
}
