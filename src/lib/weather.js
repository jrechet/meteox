// Open-Meteo data access:
//  - today / recent window (forecast, incl. past_days for the real last N days)
//  - same-day history + per-year N-day windows back to 1940 (ERA5 archive).

const FORECAST = 'https://api.open-meteo.com/v1/forecast';
const ARCHIVE = 'https://archive-api.open-meteo.com/v1/archive';
const ARCHIVE_START_YEAR = 1940;
const DAILY = 'temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max,weather_code';
const CACHE_TTL = 1000 * 60 * 60 * 12; // 12h
export const MAX_WINDOW = 30; // longest look-back the "Période" tab supports

function cacheKey(lat, lon, mmdd) {
  return `mx:v2:${lat.toFixed(3)}:${lon.toFixed(3)}:${mmdd}`;
}

function readCache(key) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    const { t, data } = JSON.parse(raw);
    if (Date.now() - t > CACHE_TTL) return null;
    return data;
  } catch {
    return null;
  }
}

function writeCache(key, data) {
  try {
    localStorage.setItem(key, JSON.stringify({ t: Date.now(), data }));
  } catch {
    /* quota — ignore */
  }
}

function rowAt(day, i) {
  return {
    date: day.time[i],
    tmax: day.temperature_2m_max[i],
    tmin: day.temperature_2m_min[i],
    precip: day.precipitation_sum[i],
    wind: day.wind_speed_10m_max[i],
    code: day.weather_code[i],
  };
}

/** Today's conditions (forecast covers the current day reliably). */
export async function fetchToday(lat, lon) {
  const url =
    `${FORECAST}?latitude=${lat}&longitude=${lon}` +
    `&daily=${DAILY}&timezone=Europe%2FParis&forecast_days=1`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('forecast unavailable');
  const day = (await res.json()).daily;
  return rowAt(day, 0);
}

/**
 * The real last `days` days ending today (forecast API `past_days`).
 * Fills the current-year window even where the ERA5 archive still lags.
 * Returns ascending array of daily rows.
 */
export async function fetchRecent(lat, lon, days = MAX_WINDOW) {
  const url =
    `${FORECAST}?latitude=${lat}&longitude=${lon}` +
    `&daily=${DAILY}&timezone=Europe%2FParis&past_days=${days - 1}&forecast_days=1`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('recent unavailable');
  const day = (await res.json()).daily;
  return day.time.map((_, i) => rowAt(day, i));
}

/**
 * One archive request for 1940 -> today, reduced to two views (cached together):
 *  - series:  { year, tmax, tmin, precip, wind, code } for the target MM-DD, ascending
 *  - windows: { [year]: rows[] }  the up-to-MAX_WINDOW days ending on that year's MM-DD
 */
export async function fetchHistory(lat, lon, mmdd, todayIso) {
  const key = cacheKey(lat, lon, mmdd);
  const cached = readCache(key);
  if (cached) return cached;

  // Subtract 5 days to ensure the end date is safely within the ERA5 archive range,
  // preventing 400 Bad Request errors from Open-Meteo for future/too recent dates.
  const now = new Date(todayIso + 'T12:00:00');
  let safeEnd = new Date(now.getTime() - 5 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const start = `${ARCHIVE_START_YEAR}-01-01`;
  let url =
    `${ARCHIVE}?latitude=${lat}&longitude=${lon}` +
    `&start_date=${start}&end_date=${safeEnd}&daily=${DAILY}&timezone=Europe%2FParis`;
  
  let res = await fetch(url);
  if (!res.ok) {
    const errText = await res.text();
    console.warn('Archive API error on first try:', errText);
    // Auto-correct if date is out of range (e.g. app runs in 2026 but API only has up to 2024)
    // Error example: "Reason: End date is out of range. Allowed range is 1940-01-01 to 2024-05-15."
    const match = errText.match(/to (\d{4}-\d{2}-\d{2})/);
    if (match) {
      safeEnd = match[1];
      console.log('Retrying Archive API with max allowed end_date:', safeEnd);
      url =
        `${ARCHIVE}?latitude=${lat}&longitude=${lon}` +
        `&start_date=${start}&end_date=${safeEnd}&daily=${DAILY}&timezone=Europe%2FParis`;
      res = await fetch(url);
    }
    if (!res.ok) {
      const finalErr = await res.text();
      throw new Error(`archive unavailable: ${finalErr}`);
    }
  }
  
  const day = (await res.json()).daily;
  const suffix = `-${mmdd}`;

  const series = [];
  const windows = {};
  for (let i = 0; i < day.time.length; i++) {
    if (!day.time[i].endsWith(suffix)) continue;
    const year = Number(day.time[i].slice(0, 4));
    series.push({ year, ...rowAt(day, i) });
    const win = [];
    for (let j = Math.max(0, i - (MAX_WINDOW - 1)); j <= i; j++) win.push(rowAt(day, j));
    windows[year] = win;
  }
  series.sort((a, b) => a.year - b.year);

  const data = { series, windows };
  writeCache(key, data);
  return data;
}

export const HEATMAP_CITIES = [
  { name: 'Lille', lat: 50.6292, lon: 3.0573 },
  { name: 'Amiens', lat: 49.8941, lon: 2.2957 },
  { name: 'Brest', lat: 48.3903, lon: -4.4860 },
  { name: 'Rennes', lat: 48.1173, lon: -1.6778 },
  { name: 'Rouen', lat: 49.4431, lon: 1.0993 },
  { name: 'Paris', lat: 48.8566, lon: 2.3522 },
  { name: 'Reims', lat: 49.2583, lon: 4.0317 },
  { name: 'Strasbourg', lat: 48.5734, lon: 7.7521 },
  { name: 'Nantes', lat: 47.2184, lon: -1.5536 },
  { name: 'Tours', lat: 47.3941, lon: 0.6848 },
  { name: 'Dijon', lat: 47.3220, lon: 5.0415 },
  { name: 'Limoges', lat: 45.8336, lon: 1.2611 },
  { name: 'Lyon', lat: 45.7640, lon: 4.8357 },
  { name: 'Bordeaux', lat: 44.8378, lon: -0.5792 },
  { name: 'Toulouse', lat: 43.6047, lon: 1.4442 },
  { name: 'Montpellier', lat: 43.6108, lon: 3.8767 },
  { name: 'Marseille', lat: 43.2964, lon: 5.3698 },
  { name: 'Nice', lat: 43.7102, lon: 7.2620 },
  { name: 'Perpignan', lat: 42.6887, lon: 2.8948 },
  { name: 'Ajaccio', lat: 41.9272, lon: 8.7381 }
];

export async function fetchHeatmap(mmdd, year) {
  const dateStr = `${year}-${mmdd}`;
  const cacheKey = `mx:heatmap:${year}:${mmdd}`;

  try {
    const cached = localStorage.getItem(cacheKey);
    if (cached) return JSON.parse(cached);
  } catch {
    /* ignore */
  }

  const lats = HEATMAP_CITIES.map((c) => c.lat.toFixed(4)).join(',');
  const lons = HEATMAP_CITIES.map((c) => c.lon.toFixed(4)).join(',');

  const isCurrentYear = year === new Date().getFullYear();
  const url = isCurrentYear
    ? `${FORECAST}?latitude=${lats}&longitude=${lons}&start_date=${dateStr}&end_date=${dateStr}&daily=temperature_2m_max,weather_code&timezone=Europe%2FParis`
    : `${ARCHIVE}?latitude=${lats}&longitude=${lons}&start_date=${dateStr}&end_date=${dateStr}&daily=temperature_2m_max,weather_code&timezone=Europe%2FParis`;

  const res = await fetch(url);
  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`heatmap data unavailable: ${errText}`);
  }

  const data = await res.json();
  const results = Array.isArray(data) ? data : [data];

  const mapped = results.map((item, idx) => {
    const tmax = item.daily?.temperature_2m_max?.[0] ?? null;
    const code = item.daily?.weather_code?.[0] ?? null;
    return {
      name: HEATMAP_CITIES[idx].name,
      lat: HEATMAP_CITIES[idx].lat,
      lon: HEATMAP_CITIES[idx].lon,
      tmax,
      code,
    };
  });

  try {
    localStorage.setItem(cacheKey, JSON.stringify(mapped));
  } catch {
    /* ignore */
  }

  return mapped;
}
