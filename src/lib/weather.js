// Open-Meteo data access:
//  - today / recent window (forecast, incl. past_days for the real last N days)
//  - same-day history + per-year N-day windows back to 1940 (ERA5 archive).

const FORECAST = 'https://api.open-meteo.com/v1/forecast';
const ARCHIVE = 'https://archive-api.open-meteo.com/v1/archive';
export const ARCHIVE_START_YEAR = 1940;
const DAILY = 'temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max,weather_code';
// The decade chart and the slider only ever plot temperature. Asking the archive
// for the other three variables across the whole record multiplied the
// server-side extraction time by ~4.5x (measured 1940→2026: 1.7s for these two
// vs 7.6s for all five) for data that is displayed one single year at a time —
// fetchYearWindow fetches those on demand instead.
const SERIES_DAILY = 'temperature_2m_max,temperature_2m_min';
const CACHE_TTL = 1000 * 60 * 60 * 12; // 12h
const ARCHIVE_LAG_DAYS = 5; // ERA5 trails real time; keep end_date safely inside it
export const MAX_WINDOW = 30; // longest look-back the "Période" tab supports

function seriesKey(lat, lon, mmdd, fromYear, toYear) {
  return `mx:v3:${lat.toFixed(3)}:${lon.toFixed(3)}:${mmdd}:s${fromYear}-${toYear}`;
}

function windowKey(lat, lon, mmdd, year) {
  return `mx:v3:${lat.toFixed(3)}:${lon.toFixed(3)}:${mmdd}:w${year}`;
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

function archiveUrl(lat, lon, daily, start, end) {
  return (
    `${ARCHIVE}?latitude=${lat}&longitude=${lon}` +
    `&start_date=${start}&end_date=${end}&daily=${daily}&timezone=Europe%2FParis`
  );
}

/**
 * Archive request with the ERA5 lag guard: the archive trails real time by a few
 * days, and how far varies. A too-recent end_date 400s with the allowed range in
 * the message ("Allowed range is 1940-01-01 to 2024-05-15") — retry against that.
 */
async function archiveDaily(lat, lon, daily, start, end) {
  let res = await fetch(archiveUrl(lat, lon, daily, start, end));
  if (!res.ok) {
    const errText = await res.text();
    const allowed = errText.match(/to (\d{4}-\d{2}-\d{2})/);
    if (!allowed) throw new Error(`archive unavailable: ${errText}`);
    res = await fetch(archiveUrl(lat, lon, daily, start, allowed[1]));
    if (!res.ok) throw new Error(`archive unavailable: ${await res.text()}`);
  }
  return (await res.json()).daily;
}

function shiftIso(iso, days) {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

function isLeap(year) {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

/** Feb 29 only exists in leap years — fall back to Feb 28 elsewhere. */
function dayInYear(year, mmdd) {
  if (mmdd === '02-29' && !isLeap(year)) return `${year}-02-28`;
  return `${year}-${mmdd}`;
}

/**
 * Same-calendar-day temperatures for every year in [fromYear, toYear], ascending.
 * Two variables only — see SERIES_DAILY. Returns `{ year, tmax, tmin }[]`.
 *
 * Callers request narrow ranges so the record can arrive in passes: the app is
 * usable off the recent decades while the deep record is still downloading.
 */
export async function fetchSeries(lat, lon, mmdd, todayIso, fromYear, toYear) {
  if (toYear < fromYear) return [];
  const key = seriesKey(lat, lon, mmdd, fromYear, toYear);
  const cached = readCache(key);
  if (cached) return cached.map(([year, tmax, tmin]) => ({ year, tmax, tmin }));

  const currentYear = Number(todayIso.slice(0, 4));
  const start = `${fromYear}-01-01`;
  const end =
    toYear >= currentYear ? shiftIso(todayIso, -ARCHIVE_LAG_DAYS) : `${toYear}-12-31`;
  const day = await archiveDaily(lat, lon, SERIES_DAILY, start, end);

  const suffix = `-${mmdd}`;
  const series = [];
  for (let i = 0; i < day.time.length; i++) {
    if (!day.time[i].endsWith(suffix)) continue;
    series.push({
      year: Number(day.time[i].slice(0, 4)),
      tmax: day.temperature_2m_max[i],
      tmin: day.temperature_2m_min[i],
    });
  }
  series.sort((a, b) => a.year - b.year);

  // Cached as tuples: ~95x smaller than the old series+windows blob, which blew
  // the localStorage quota after ~20 lookups and left every visit re-fetching.
  writeCache(key, series.map((s) => [s.year, s.tmax, s.tmin]));
  return series;
}

/**
 * The MAX_WINDOW days ending on `year`-mmdd, all five variables — what the
 * "Période" strip plots and what the focus card needs beyond temperature.
 * One year at a time: ~1.5 kB and ~130 ms, so it is fetched on demand.
 */
export async function fetchYearWindow(lat, lon, mmdd, year) {
  const key = windowKey(lat, lon, mmdd, year);
  const cached = readCache(key);
  if (cached) return cached;

  const end = dayInYear(year, mmdd);
  const day = await archiveDaily(lat, lon, DAILY, shiftIso(end, -(MAX_WINDOW - 1)), end);
  const rows = day.time.map((_, i) => rowAt(day, i));
  writeCache(key, rows);
  return rows;
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
