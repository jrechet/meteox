// Semantic cold -> hot color ramp, driven by a normalized 0..1 value.

// Anchor stops in OKLCH (L, C, H). Cold blue -> neutral -> hot red.
const STOPS = [
  [0.0, [0.6, 0.15, 250]],   // Blue
  [0.25, [0.68, 0.16, 142]], // Green
  [0.5, [0.72, 0.16, 72]],   // Orange
  [0.75, [0.58, 0.22, 28]],  // Red
  [1.0, [0.52, 0.21, 305]],  // Violet
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
  const span = hi[0] - lo[0];
  const k = (x - lo[0]) / span;
  const L = lerp(lo[1][0], hi[1][0], k);
  const C = lerp(lo[1][1], hi[1][1], k);
  
  let h1 = lo[1][2];
  let h2 = hi[1][2];
  if (h2 - h1 > 180) h1 += 360;
  const H = lerp(h1, h2, k) % 360;

  return `oklch(${(L * 100).toFixed(1)}% ${C.toFixed(3)} ${H.toFixed(1)})`;
}

/** Normalize value against a domain into [0,1]. */
export function normalize(v, min, max) {
  if (v == null || max === min) return 0.5;
  return (v - min) / (max - min);
}
