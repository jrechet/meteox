import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  fetchSeries, fetchYearWindow, fetchToday, fetchRecent, fetchHeatmap,
  HEATMAP_CITIES, resetMapRateLimit,
} from '../src/lib/weather.js';

// Mock localStorage
const localStorageMock = (() => {
  let store = {};
  return {
    getItem(key) {
      return store[key] || null;
    },
    setItem(key, value) {
      store[key] = value.toString();
    },
    clear() {
      store = {};
    }
  };
})();
Object.defineProperty(global, 'localStorage', { value: localStorageMock });

const SERIES_KEY = 'mx:v3:48.850:2.350:07-09:s1996-2026';

describe('weather API: fetchSeries', () => {
  beforeEach(() => {
    localStorage.clear();
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  test('keeps only the target calendar day, ascending', async () => {
    const mockDaily = {
      time: ['2020-07-08', '2020-07-09', '2021-07-09'],
      temperature_2m_max: [20, 25, 26],
      temperature_2m_min: [10, 15, 16],
    };

    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ daily: mockDaily }),
    });

    const series = await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026);

    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect(series).toEqual([
      { year: 2020, tmax: 25, tmin: 15 },
      { year: 2021, tmax: 26, tmin: 16 },
    ]);
  });

  test('asks the archive for temperature only — the costly variables are on demand', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ daily: { time: [], temperature_2m_max: [], temperature_2m_min: [] } }),
    });

    await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026);

    const url = global.fetch.mock.calls[0][0];
    expect(url).toContain('daily=temperature_2m_max,temperature_2m_min');
    expect(url).not.toContain('weather_code');
    expect(url).not.toContain('precipitation_sum');
    expect(url).not.toContain('wind_speed_10m_max');
  });

  test('requests only the asked-for range, and clamps the open end to the ERA5 lag', async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({ daily: { time: [], temperature_2m_max: [], temperature_2m_min: [] } }),
    });

    await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1940, 1995);
    expect(global.fetch.mock.calls[0][0]).toContain('start_date=1940-01-01');
    expect(global.fetch.mock.calls[0][0]).toContain('end_date=1995-12-31');

    await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026);
    expect(global.fetch.mock.calls[1][0]).toContain('start_date=1996-01-01');
    expect(global.fetch.mock.calls[1][0]).toContain('end_date=2026-07-04'); // today − 5j
  });

  test('an empty range resolves without touching the network', async () => {
    const series = await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 1995);
    expect(series).toEqual([]);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  test('retries with a safe end_date if Open-Meteo throws an out-of-range error', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: false,
      text: async () => JSON.stringify({ error: true, reason: "End date is out of range. Allowed range is 1940-01-01 to 2024-05-15." }),
    });
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        daily: { time: ['2020-07-09', '2021-07-09'], temperature_2m_max: [25, 26], temperature_2m_min: [15, 16] },
      }),
    });

    const series = await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026);

    expect(global.fetch).toHaveBeenCalledTimes(2);
    expect(global.fetch.mock.calls[1][0]).toContain('end_date=2024-05-15');
    expect(series.length).toBe(2);
  });

  test('throws an error if retry fails or error is unrelated to date bounds', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: false,
      text: async () => "Internal Server Error",
    });

    await expect(fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026)).rejects.toThrow('archive unavailable');
  });
});

describe('weather API: fetchYearWindow', () => {
  beforeEach(() => {
    localStorage.clear();
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const windowDaily = {
    time: ['1976-07-08', '1976-07-09'],
    temperature_2m_max: [30, 33],
    temperature_2m_min: [18, 19],
    precipitation_sum: [0, 0],
    wind_speed_10m_max: [9, 11],
    weather_code: [0, 1],
  };

  test('requests the MAX_WINDOW days ending on that year’s calendar day, all five variables', async () => {
    global.fetch.mockResolvedValueOnce({ ok: true, json: async () => ({ daily: windowDaily }) });

    const rows = await fetchYearWindow(48.85, 2.35, '07-09', 1976);

    const url = global.fetch.mock.calls[0][0];
    expect(url).toContain('start_date=1976-06-10'); // 30 jours glissants
    expect(url).toContain('end_date=1976-07-09');
    expect(url).toContain('weather_code');
    expect(rows[rows.length - 1]).toEqual({
      date: '1976-07-09', tmax: 33, tmin: 19, precip: 0, wind: 11, code: 1,
    });
  });

  test('falls back to Feb 28 when the target day is Feb 29 of a non-leap year', async () => {
    global.fetch.mockResolvedValue({ ok: true, json: async () => ({ daily: windowDaily }) });

    await fetchYearWindow(48.85, 2.35, '02-29', 2023);
    expect(global.fetch.mock.calls[0][0]).toContain('end_date=2023-02-28');

    await fetchYearWindow(48.85, 2.35, '02-29', 2024);
    expect(global.fetch.mock.calls[1][0]).toContain('end_date=2024-02-29');
  });

  test('serves a second call for the same year from the cache', async () => {
    global.fetch.mockResolvedValueOnce({ ok: true, json: async () => ({ daily: windowDaily }) });

    const first = await fetchYearWindow(48.85, 2.35, '07-09', 1976);
    const second = await fetchYearWindow(48.85, 2.35, '07-09', 1976);

    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect(second).toEqual(first);
  });
});

describe('weather API: forecast + cache', () => {
  beforeEach(() => {
    localStorage.clear();
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('fetchToday', () => {
    test('successfully fetches today weather', async () => {
      const mockDaily = {
        time: ['2026-07-09'],
        temperature_2m_max: [25],
        temperature_2m_min: [15],
        precipitation_sum: [0],
        wind_speed_10m_max: [10],
        weather_code: [0],
      };

      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ daily: mockDaily }),
      });

      const data = await fetchToday(48.85, 2.35);
      expect(data).toEqual({
        date: '2026-07-09',
        tmax: 25,
        tmin: 15,
        precip: 0,
        wind: 10,
        code: 0,
      });
    });

    test('throws error if forecast is unavailable', async () => {
      global.fetch.mockResolvedValueOnce({ ok: false });
      await expect(fetchToday(48.85, 2.35)).rejects.toThrow('forecast unavailable');
    });
  });

  describe('fetchRecent', () => {
    test('successfully fetches recent weather window', async () => {
      const mockDaily = {
        time: ['2026-07-08', '2026-07-09'],
        temperature_2m_max: [24, 25],
        temperature_2m_min: [14, 15],
        precipitation_sum: [1, 0],
        wind_speed_10m_max: [11, 10],
        weather_code: [1, 0],
      };

      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ daily: mockDaily }),
      });

      const data = await fetchRecent(48.85, 2.35, 2);
      expect(data.length).toBe(2);
      expect(data[0].date).toBe('2026-07-08');
      expect(data[1].date).toBe('2026-07-09');
    });

    test('throws error if recent is unavailable', async () => {
      global.fetch.mockResolvedValueOnce({ ok: false });
      await expect(fetchRecent(48.85, 2.35, 2)).rejects.toThrow('recent unavailable');
    });
  });

  describe('cache exceptions & edge cases', () => {
    const seriesDaily = {
      time: ['2020-07-09'],
      temperature_2m_max: [25],
      temperature_2m_min: [15],
    };
    const mockSeriesFetch = () =>
      global.fetch.mockResolvedValueOnce({ ok: true, json: async () => ({ daily: seriesDaily }) });

    test('handles JSON parse error in localStorage gracefully', async () => {
      localStorage.setItem(SERIES_KEY, '{invalid-json');
      mockSeriesFetch();

      const series = await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026);
      expect(global.fetch).toHaveBeenCalledTimes(1);
      expect(series[0].year).toBe(2020);
    });

    test('handles expired TTL in localStorage gracefully', async () => {
      localStorage.setItem(
        SERIES_KEY,
        JSON.stringify({ t: Date.now() - 1000 * 60 * 60 * 24, data: [[1999, 20, 10]] })
      );
      mockSeriesFetch();

      const series = await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026);
      expect(global.fetch).toHaveBeenCalledTimes(1);
      expect(series[0].year).toBe(2020);
    });

    test('handles write cache exceptions (quota limit) gracefully', async () => {
      const originalSetItem = localStorage.setItem;
      localStorage.setItem = vi.fn().mockImplementation(() => {
        throw new Error('QuotaExceededError');
      });
      mockSeriesFetch();

      try {
        const series = await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026);
        expect(series[0].year).toBe(2020);
      } finally {
        localStorage.setItem = originalSetItem;
      }
    });

    test('returns cached data on cache hit without fetching', async () => {
      // Cached as compact tuples — rehydrated into the same shape a fetch returns.
      localStorage.setItem(SERIES_KEY, JSON.stringify({ t: Date.now(), data: [[2020, 22, 12]] }));

      const series = await fetchSeries(48.85, 2.35, '07-09', '2026-07-09', 1996, 2026);
      expect(global.fetch).not.toHaveBeenCalled();
      expect(series).toEqual([{ year: 2020, tmax: 22, tmin: 12 }]);
    });
  });

  describe('fetchHeatmap', () => {
    // Une année passée consulte d'abord l'archive pré-générée. Ces cas testent le
    // repli réseau : `apiOnly` fait répondre 404 à l'archive et sert les réponses
    // données, dans l'ordre, aux seuls appels météo.
    const apiOnly = (...responses) => {
      const queue = [...responses];
      global.fetch = vi.fn(async (url) => {
        if (String(url).includes('/data/heatmap/')) return { ok: false, json: async () => null };
        return queue.length > 1 ? queue.shift() : queue[0];
      });
      return global.fetch;
    };
    /** Appels météo uniquement (l'archive n'en fait pas partie). */
    const apiCalls = () =>
      global.fetch.mock.calls.map((c) => String(c[0])).filter((u) => !u.includes('/data/heatmap/'));

    beforeEach(() => {
      resetMapRateLimit(); // sinon chaque cas paie l'espacement réel entre requêtes
    });

    test('successfully fetches and maps heatmap for past year using Archive API', async () => {
      const mockResponse = Array.from({ length: 20 }, (_, i) => ({
        daily: {
          temperature_2m_max: [20 + i],
          weather_code: [i % 3],
        },
      }));

      apiOnly({ ok: true, json: async () => mockResponse });

      const data = await fetchHeatmap('07-09', 1976);
      expect(apiCalls()).toHaveLength(1);
      expect(apiCalls()[0]).toContain('archive-api.open-meteo.com');
      expect(data.length).toBe(20);
      expect(data[0].name).toBe('Lille');
      expect(data[0].tmax).toBe(20);
    });

    // weather_code is never rendered on the map, and asking for it cost 1.3–3.1s
    // per request against 0.2–0.8s without — paid once per year of the animation.
    test('asks for temperature only — the map never renders a weather code', async () => {
      apiOnly({ ok: true, json: async () => [{ daily: { temperature_2m_max: [20] } }] });

      const data = await fetchHeatmap('07-09', 1976);

      expect(apiCalls()[0]).toContain('daily=temperature_2m_max&');
      expect(apiCalls()[0]).not.toContain('weather_code');
      expect(data[0]).not.toHaveProperty('code');
    });

    test('a past year is cached without expiry — settled reanalysis never moves', async () => {
      apiOnly({ ok: true, json: async () => [{ daily: { temperature_2m_max: [20] } }] });
      await fetchHeatmap('07-09', 1976);

      // stamp the entry as very old: an immutable year must still be served
      const key = 'mx:heatmap:v2:1976:07-09';
      const entry = JSON.parse(localStorage.getItem(key));
      localStorage.setItem(key, JSON.stringify({ ...entry, t: 0 }));

      const again = await fetchHeatmap('07-09', 1976);
      expect(apiCalls()).toHaveLength(1);
      expect(again[0].tmax).toBe(20);
    });

    test('the current year expires — today’s map has to be able to move', async () => {
      const currentYear = new Date().getFullYear();
      apiOnly({ ok: true, json: async () => [{ daily: { temperature_2m_max: [21] } }] });
      await fetchHeatmap('07-09', currentYear);

      const key = `mx:heatmap:v2:${currentYear}:07-09`;
      const entry = JSON.parse(localStorage.getItem(key));
      localStorage.setItem(key, JSON.stringify({ ...entry, t: Date.now() - 1000 * 60 * 60 * 24 }));

      await fetchHeatmap('07-09', currentYear);
      expect(apiCalls()).toHaveLength(2); // re-fetched, not served stale
    });

    test('successfully fetches and maps heatmap for current year using Forecast API', async () => {
      const mockResponse = Array.from({ length: 20 }, (_, i) => ({
        daily: {
          temperature_2m_max: [22 + i],
          weather_code: [i % 4],
        },
      }));

      apiOnly({ ok: true, json: async () => mockResponse });

      const currentYear = new Date().getFullYear();
      const data = await fetchHeatmap('07-09', currentYear);
      expect(apiCalls()).toHaveLength(1);
      expect(apiCalls()[0]).toContain('api.open-meteo.com/v1/forecast');
      expect(data.length).toBe(20);
      expect(data[0].tmax).toBe(22);
    });

    // L'archive pré-générée (public/data/heatmap/MM-DD.json) est ce qui rend
    // l'animation longue possible : sans elle, rejouer 1940→aujourd'hui demande
    // 87 requêtes pondérées et Open-Meteo la refuse en cours de route.
    describe('archive pré-générée', () => {
      const archiveFor = (mmdd, from, rows) => ({
        url: `/data/heatmap/${mmdd}.json`,
        body: { from, cities: HEATMAP_CITIES.map((c) => c.name), t: rows },
      });

      /** Sert le fichier d'archive, et compte les appels à l'API météo. */
      function mockArchive(mmdd, from, rows) {
        const api = [];
        global.fetch = vi.fn(async (url) => {
          if (String(url).includes('/data/heatmap/')) {
            const a = archiveFor(mmdd, from, rows);
            return String(url).endsWith(a.url)
              ? { ok: true, json: async () => a.body }
              : { ok: false, json: async () => null };
          }
          api.push(String(url));
          return { ok: true, json: async () => [{ daily: { temperature_2m_max: [99] } }] };
        });
        return api;
      }

      test('une année passée est servie par l’archive, sans toucher à l’API', async () => {
        const row = HEATMAP_CITIES.map((_, i) => 20 + i);
        const api = mockArchive('03-01', 1940, [row]);

        const data = await fetchHeatmap('03-01', 1940);

        expect(api).toEqual([]); // aucun appel météo
        expect(data).toHaveLength(HEATMAP_CITIES.length);
        expect(data[0]).toEqual({
          name: HEATMAP_CITIES[0].name,
          lat: HEATMAP_CITIES[0].lat,
          lon: HEATMAP_CITIES[0].lon,
          tmax: 20,
        });
      });

      test('une année absente de l’archive retombe sur l’API', async () => {
        // l'archive ne couvre que 1940 : 1999 doit passer par le réseau
        const api = mockArchive('03-02', 1940, [HEATMAP_CITIES.map(() => 12)]);

        const data = await fetchHeatmap('03-02', 1999);

        expect(api).toHaveLength(1);
        expect(api[0]).toContain('archive-api.open-meteo.com');
        expect(data[0].tmax).toBe(99);
      });

      test('l’année en cours ne vient jamais de l’archive — elle est incomplète', async () => {
        const currentYear = new Date().getFullYear();
        const rows = Array.from({ length: currentYear - 1940 + 1 }, () => HEATMAP_CITIES.map(() => 5));
        const api = mockArchive('03-03', 1940, rows);

        const data = await fetchHeatmap('03-03', currentYear);

        expect(api).toHaveLength(1);
        expect(api[0]).toContain('/v1/forecast'); // prévision, pas archive
        expect(data[0].tmax).toBe(99);
      });

      test('une archive absente ne casse rien — l’API prend le relais', async () => {
        global.fetch = vi.fn(async (url) => {
          if (String(url).includes('/data/heatmap/')) return { ok: false, json: async () => null };
          return { ok: true, json: async () => [{ daily: { temperature_2m_max: [7] } }] };
        });

        const data = await fetchHeatmap('03-04', 1980);
        expect(data[0].tmax).toBe(7);
      });
    });

    test('returns cached data on cache hit', async () => {
      const cachedData = [{ name: 'Lille', lat: 50.6292, lon: 3.0573, tmax: 25 }];
      localStorage.setItem(
        'mx:heatmap:v2:1976:07-09',
        JSON.stringify({ t: Date.now(), data: cachedData }),
      );

      const data = await fetchHeatmap('07-09', 1976);
      expect(global.fetch).not.toHaveBeenCalled();
      expect(data).toEqual(cachedData);
    });

    test('throws error on failure', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: false,
        text: async () => 'API Error',
      });

      await expect(fetchHeatmap('07-09', 1976)).rejects.toThrow('heatmap data unavailable');
    });

    test('handles localStorage exceptions gracefully in fetchHeatmap', async () => {
      const originalGetItem = localStorage.getItem;
      const originalSetItem = localStorage.setItem;

      localStorage.getItem = () => { throw new Error('SecurityError'); };
      localStorage.setItem = () => { throw new Error('QuotaExceededError'); };

      const mockResponse = Array.from({ length: 20 }, (_, i) => ({
        daily: {
          temperature_2m_max: [20 + i],
          weather_code: [i % 3],
        },
      }));

      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const data = await fetchHeatmap('07-09', 1976);
      expect(data.length).toBe(20);

      localStorage.getItem = originalGetItem;
      localStorage.setItem = originalSetItem;
    });

    test('handles single object response and missing fields in fetchHeatmap', async () => {
      const mockSingleResponse = {
        daily: {}
      };

      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockSingleResponse,
      });

      const data = await fetchHeatmap('07-09', 1976);
      expect(data.length).toBe(1);
      expect(data[0].tmax).toBeNull();
    });
  });
});
