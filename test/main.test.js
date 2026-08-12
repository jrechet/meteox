// @vitest-environment jsdom
// Boots the real main.js entrypoint in jsdom with a mocked network, then drives
// the actual DOM interactions (tabs, slider, chips, URL hash). Covers the
// orchestration layer that pure component tests can't reach.
import { describe, test, expect, beforeAll, vi } from 'vitest';

const DAILY = ['temperature_2m_max', 'temperature_2m_min', 'precipitation_sum', 'wind_speed_10m_max', 'weather_code'];

function dailyRange(start, end) {
  const time = [];
  const cols = Object.fromEntries(DAILY.map((f) => [f, []]));
  for (let d = new Date(start + 'T12:00:00'), e = new Date(end + 'T12:00:00'); d <= e; d.setDate(d.getDate() + 1)) {
    const doy = Math.floor((d - new Date(d.getFullYear(), 0, 0)) / 864e5);
    const seasonal = 18 - 12 * Math.cos((doy / 365) * 2 * Math.PI);
    time.push(d.toISOString().slice(0, 10));
    cols.temperature_2m_max.push(Math.round((seasonal + (d.getFullYear() - 1995) * 0.05) * 10) / 10);
    cols.temperature_2m_min.push(Math.round((seasonal - 8) * 10) / 10);
    cols.precipitation_sum.push(0);
    cols.wind_speed_10m_max.push(12);
    cols.weather_code.push(1);
  }
  return { daily: { time, ...cols } };
}

function mockResponse(url) {
  const multi = /latitude=[\d.-]+,/.test(url);
  const cities = (u) => {
    const n = u.match(/latitude=([^&]+)/)[1].split(',').length;
    return Array.from({ length: n }, () => ({ daily: { time: ['x'], temperature_2m_max: [25], weather_code: [1] } }));
  };
  if (url.includes('/v1/forecast')) {
    if (multi) return cities(url);
    if (url.includes('past_days=')) {
      const today = new Date();
      const start = new Date(today.getTime() - 29 * 864e5);
      return dailyRange(start.toISOString().slice(0, 10), today.toISOString().slice(0, 10));
    }
    const t = new Date().toISOString().slice(0, 10);
    return dailyRange(t, t);
  }
  if (url.includes('/v1/archive')) {
    if (multi) return cities(url);
    return dailyRange('1995-01-01', new Date().toISOString().slice(0, 10));
  }
  return {};
}

const tick = () => new Promise((r) => setTimeout(r, 0));
async function waitFor(fn, timeout = 4000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeout) {
    const v = fn();
    if (v) return v;
    await tick();
  }
  throw new Error('waitFor timed out');
}

beforeAll(async () => {
  document.body.innerHTML = '<div id="app"></div>';
  global.fetch = vi.fn((url) =>
    Promise.resolve({ ok: true, json: () => Promise.resolve(mockResponse(String(url))), text: () => Promise.resolve('') }),
  );
  await import('../src/main.js'); // triggers boot()
  // wait until the background history load enables the Période tab
  await waitFor(() => document.querySelector('.tab[data-tab="period"]:not([disabled])'));
});

describe('main.js (jsdom integration)', () => {
  test('boots to a rendered dashboard with today card and slider', () => {
    expect(document.querySelector('.vignette--today')).toBeTruthy();
    expect(document.querySelector('[data-role="slider"]')).toBeTruthy();
  });

  test('tab switch follows the ARIA tab pattern', () => {
    const periodTab = document.querySelector('.tab[data-tab="period"]');
    periodTab.click();
    expect(periodTab.getAttribute('aria-selected')).toBe('true');
    expect(document.querySelector('[data-role="machine-content"]').getAttribute('aria-labelledby')).toBe('tab-period');
    expect(document.querySelector('.pstrip')).toBeTruthy();
  });

  test('slider updates the displayed year', () => {
    const slider = document.querySelector('[data-role="slider"]');
    slider.value = String(Number(slider.min) + 3);
    slider.dispatchEvent(new Event('input', { bubbles: true }));
    expect(document.querySelector('[data-role="rail-year"]').textContent).toBe(slider.value);
  });

  test('metric toggle in Période updates aria-pressed', () => {
    const wind = document.querySelector('.chip[data-metric="wind"]');
    wind.click();
    expect(document.querySelector('.chip[data-metric="wind"]').getAttribute('aria-pressed')).toBe('true');
  });

  // Guards the load strategy: asking the archive for all five daily variables
  // across the whole record was the 8s first paint this replaced.
  test('history loads in two temperature-only passes', () => {
    const archive = global.fetch.mock.calls
      .map((c) => String(c[0]))
      .filter((u) => u.includes('/v1/archive') && !/latitude=[\d.-]+,/.test(u));

    const bulk = archive.filter((u) => u.includes('daily=temperature_2m_max,temperature_2m_min&'));
    const starts = bulk.map((u) => u.match(/start_date=(\d{4}-\d{2}-\d{2})/)[1]);
    const recentFrom = `${new Date().getFullYear() - 29}-01-01`;

    expect(starts).toContain(recentFrom); // pass 1 — unlocks the UI
    expect(starts).toContain('1940-01-01'); // pass 2 — deep record, in background
    // Nothing pulls precipitation/wind/weather-code across the decades.
    expect(archive.filter((u) => u.includes('weather_code') && u.includes('1940-01-01'))).toEqual([]);
  });

  test('a past year fetches its own 30-day window on demand', () => {
    const windows = global.fetch.mock.calls
      .map((c) => String(c[0]))
      .filter((u) => u.includes('/v1/archive') && u.includes('weather_code') && !/latitude=[\d.-]+,/.test(u));

    expect(windows.length).toBeGreaterThan(0);
    for (const url of windows) {
      const start = new Date(url.match(/start_date=(\d{4}-\d{2}-\d{2})/)[1]);
      const end = new Date(url.match(/end_date=(\d{4}-\d{2}-\d{2})/)[1]);
      expect(Math.round((end - start) / 864e5)).toBe(29); // MAX_WINDOW days inclusive
    }
  });

  test('interactions are persisted to the URL hash', () => {
    expect(location.hash).toContain('mode=period');
    expect(location.hash).toMatch(/lat=/);
    expect(location.hash).toMatch(/year=/);
  });
});
