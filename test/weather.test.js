import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { fetchHistory, fetchToday, fetchRecent, fetchHeatmap } from '../src/lib/weather.js';

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

describe('weather API: fetchHistory', () => {
  beforeEach(() => {
    localStorage.clear();
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  test('successfully fetches and processes history data on first try', async () => {
    const mockDaily = {
      time: ['2020-07-08', '2020-07-09', '2021-07-09'],
      temperature_2m_max: [20, 25, 26],
      temperature_2m_min: [10, 15, 16],
      precipitation_sum: [2, 0, 0],
      wind_speed_10m_max: [8, 10, 12],
      weather_code: [3, 0, 1],
    };

    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ daily: mockDaily }),
    });

    const data = await fetchHistory(48.85, 2.35, '07-09', '2026-07-09');
    
    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect(data.series).toBeDefined();
    expect(data.series.length).toBe(2);
    expect(data.series[0].year).toBe(2020);
    expect(data.series[0].tmax).toBe(25);
  });

  test('retries with a safe end_date if Open-Meteo throws an out-of-range error', async () => {
    const mockDaily = {
      time: ['2020-07-09', '2021-07-09'],
      temperature_2m_max: [25, 26],
      temperature_2m_min: [15, 16],
      precipitation_sum: [0, 0],
      wind_speed_10m_max: [10, 12],
      weather_code: [0, 1],
    };

    // First fetch fails with the typical Open-Meteo out of range error
    global.fetch.mockResolvedValueOnce({
      ok: false,
      text: async () => JSON.stringify({ error: true, reason: "End date is out of range. Allowed range is 1940-01-01 to 2024-05-15." }),
    });

    // Second fetch succeeds
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ daily: mockDaily }),
    });

    const data = await fetchHistory(48.85, 2.35, '07-09', '2026-07-09');
    
    expect(global.fetch).toHaveBeenCalledTimes(2);
    // Ensure the second call used the extracted date "2024-05-15"
    const secondCallUrl = global.fetch.mock.calls[1][0];
    expect(secondCallUrl).toContain('end_date=2024-05-15');
    
    expect(data.series.length).toBe(2);
  });

  test('throws an error if retry fails or error is unrelated to date bounds', async () => {
    // First fetch fails with a generic 500 error
    global.fetch.mockResolvedValueOnce({
      ok: false,
      text: async () => "Internal Server Error",
    });

    await expect(fetchHistory(48.85, 2.35, '07-09', '2026-07-09')).rejects.toThrow('archive unavailable');
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
    test('handles JSON parse error in localStorage gracefully', async () => {
      localStorage.setItem('mx:v2:48.850:2.350:07-09', '{invalid-json');
      const mockDaily = {
        time: ['2020-07-09'],
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

      const data = await fetchHistory(48.85, 2.35, '07-09', '2026-07-09');
      expect(global.fetch).toHaveBeenCalledTimes(1);
      expect(data.series[0].year).toBe(2020);
    });

    test('handles expired TTL in localStorage gracefully', async () => {
      localStorage.setItem(
        'mx:v2:48.850:2.350:07-09',
        JSON.stringify({ t: Date.now() - 1000 * 60 * 60 * 24, data: { series: [] } })
      );
      const mockDaily = {
        time: ['2020-07-09'],
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

      const data = await fetchHistory(48.85, 2.35, '07-09', '2026-07-09');
      expect(global.fetch).toHaveBeenCalledTimes(1);
      expect(data.series[0].year).toBe(2020);
    });

    test('handles write cache exceptions (quota limit) gracefully', async () => {
      const originalSetItem = localStorage.setItem;
      localStorage.setItem = vi.fn().mockImplementation(() => {
        throw new Error('QuotaExceededError');
      });

      const mockDaily = {
        time: ['2020-07-09'],
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

      const data = await fetchHistory(48.85, 2.35, '07-09', '2026-07-09');
      expect(data.series[0].year).toBe(2020);

      localStorage.setItem = originalSetItem;
    });

    test('returns cached data on cache hit without fetching', async () => {
      const cachedData = { series: [{ year: 2020, tmax: 22 }], windows: {} };
      localStorage.setItem('mx:v2:48.850:2.350:07-09', JSON.stringify({ t: Date.now(), data: cachedData }));

      const data = await fetchHistory(48.85, 2.35, '07-09', '2026-07-09');
      expect(global.fetch).not.toHaveBeenCalled();
      expect(data).toEqual(cachedData);
    });
  });

  describe('fetchHeatmap', () => {
    test('successfully fetches and maps heatmap for past year using Archive API', async () => {
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
      expect(global.fetch).toHaveBeenCalledTimes(1);
      const calledUrl = global.fetch.mock.calls[0][0];
      expect(calledUrl).toContain('archive-api.open-meteo.com');
      expect(data.length).toBe(20);
      expect(data[0].name).toBe('Lille');
      expect(data[0].tmax).toBe(20);
      expect(data[0].code).toBe(0);
    });

    test('successfully fetches and maps heatmap for current year using Forecast API', async () => {
      const mockResponse = Array.from({ length: 20 }, (_, i) => ({
        daily: {
          temperature_2m_max: [22 + i],
          weather_code: [i % 4],
        },
      }));

      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const currentYear = new Date().getFullYear();
      const data = await fetchHeatmap('07-09', currentYear);
      expect(global.fetch).toHaveBeenCalledTimes(1);
      const calledUrl = global.fetch.mock.calls[0][0];
      expect(calledUrl).toContain('api.open-meteo.com/v1/forecast');
      expect(data.length).toBe(20);
      expect(data[0].tmax).toBe(22);
    });

    test('returns cached data on cache hit', async () => {
      const cachedData = [{ name: 'Lille', lat: 50.6292, lon: 3.0573, tmax: 25, code: 0 }];
      localStorage.setItem('mx:heatmap:1976:07-09', JSON.stringify(cachedData));

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
      expect(data[0].code).toBeNull();
    });
  });
});
