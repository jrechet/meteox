// Open-Meteo data access: today (forecast) + same-day history back to 1940 (ERA5 archive).

const FORECAST = 'https://api.open-meteo.com/v1/forecast';
const ARCHIVE = 'https://archive-api.open-meteo.com/v1/archive';
const ARCHIVE_START_YEAR = 1940;
const DAILY = 'temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max,weather_code';
const CACHE_TTL = 1000 * 60 * 60 * 12; // 12h

function cacheKey(lat, lon, mmdd) {
  return `mx:v1:${lat.toFixed(3)}:${lon.toFixed(3)}:${mmdd}`;
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

/** Today's conditions for a location (forecast covers the current day reliably). */
export async function fetchToday(lat, lon) {
  const url =
    `${FORECAST}?latitude=${lat}&longitude=${lon}` +
    `&daily=${DAILY}&timezone=Europe%2FParis&forecast_days=1`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('forecast unavailable');
  const d = await res.json();
  const day = d.daily;
  return {
    date: day.time[0],
    tmax: day.temperature_2m_max[0],
    tmin: day.temperature_2m_min[0],
    precip: day.precipitation_sum[0],
    wind: day.wind_speed_10m_max[0],
    code: day.weather_code[0],
  };
}

/**
 * Same calendar day (MM-DD), every year 1940 -> now.
 * One archive request for the full span, filtered client-side. Cached.
 * Returns ascending array of { year, tmax, tmin, precip, wind, code }.
 */
export async function fetchTrend(lat, lon, mmdd, todayIso) {
  const key = cacheKey(lat, lon, mmdd);
  const cached = readCache(key);
  if (cached) return cached;

  const end = todayIso;
  const start = `${ARCHIVE_START_YEAR}-01-01`;
  const url =
    `${ARCHIVE}?latitude=${lat}&longitude=${lon}` +
    `&start_date=${start}&end_date=${end}&daily=${DAILY}&timezone=Europe%2FParis`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('archive unavailable');
  const d = await res.json();
  const day = d.daily;
  const suffix = `-${mmdd}`;

  const series = [];
  for (let i = 0; i < day.time.length; i++) {
    if (!day.time[i].endsWith(suffix)) continue;
    series.push({
      year: Number(day.time[i].slice(0, 4)),
      tmax: day.temperature_2m_max[i],
      tmin: day.temperature_2m_min[i],
      precip: day.precipitation_sum[i],
      wind: day.wind_speed_10m_max[i],
      code: day.weather_code[i],
    });
  }
  series.sort((a, b) => a.year - b.year);
  writeCache(key, series);
  return series;
}
