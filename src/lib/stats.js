// Small numeric helpers for the trend analysis.

export function mean(values) {
  const nums = values.filter((v) => v != null && !Number.isNaN(v));
  if (!nums.length) return null;
  return nums.reduce((a, b) => a + b, 0) / nums.length;
}

/**
 * Ordinary least-squares regression over {x, y} points.
 * Returns { slope, intercept, predict } or null when not enough data.
 */
export function linearFit(points) {
  const pts = points.filter((p) => p.y != null && !Number.isNaN(p.y));
  const n = pts.length;
  if (n < 2) return null;
  let sx = 0, sy = 0, sxx = 0, sxy = 0;
  for (const { x, y } of pts) {
    sx += x;
    sy += y;
    sxx += x * x;
    sxy += x * y;
  }
  const denom = n * sxx - sx * sx;
  if (denom === 0) return null;
  const slope = (n * sxy - sx * sy) / denom;
  const intercept = (sy - slope * sx) / n;
  return { slope, intercept, predict: (x) => slope * x + intercept };
}

/** Mean of the earliest `count` (default 30) non-null years — the climate baseline. */
export function baselineMean(series, count = 30) {
  const withData = series.filter((d) => d.tmax != null);
  const slice = withData.slice(0, count);
  return mean(slice.map((d) => d.tmax));
}
