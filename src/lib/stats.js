// Small numeric helpers for the trend analysis.

export function mean(values) {
  const nums = values.filter((v) => v != null && !Number.isNaN(v));
  if (!nums.length) return null;
  return nums.reduce((a, b) => a + b, 0) / nums.length;
}

/** Median — robust central tendency, resistant to hot/cold outlier days. */
export function median(values) {
  const nums = values.filter((v) => v != null && !Number.isNaN(v)).sort((a, b) => a - b);
  if (!nums.length) return null;
  const mid = Math.floor(nums.length / 2);
  return nums.length % 2 ? nums[mid] : (nums[mid - 1] + nums[mid]) / 2;
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

/** Earliest `count` (default 30) non-null years — the climate baseline slice. */
function baselineSlice(series, count = 30) {
  return series.filter((d) => d.tmax != null).slice(0, count).map((d) => d.tmax);
}

/** Mean of the baseline period — the classic "normal". */
export function baselineMean(series, count = 30) {
  return mean(baselineSlice(series, count));
}

/** Median of the baseline period — robust "normal". */
export function baselineMedian(series, count = 30) {
  return median(baselineSlice(series, count));
}
