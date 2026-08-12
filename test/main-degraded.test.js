// @vitest-environment jsdom
// The history loads as two independent archive passes. Open-Meteo rate-limits by
// request weight, so one pass can 429 while the other lands — the years that did
// arrive must still be drawn. Booting main.js is a module side effect, so this
// failure mode needs its own file with its own mocked network.
import { describe, test, expect, beforeAll, vi } from 'vitest';

const CURRENT_YEAR = new Date().getFullYear();
const RECENT_FROM = CURRENT_YEAR - 29;
const DAILY = ['temperature_2m_max', 'temperature_2m_min', 'precipitation_sum', 'wind_speed_10m_max', 'weather_code'];

const RATE_LIMITED = JSON.stringify({
  error: true,
  reason: 'Minutely API request limit exceeded. Please try again in one minute.',
});

/** Daily columns for exactly the requested span, so range assertions mean something. */
function dailyRange(start, end) {
  const time = [];
  const cols = Object.fromEntries(DAILY.map((f) => [f, []]));
  for (let d = new Date(`${start}T12:00:00`), e = new Date(`${end}T12:00:00`); d <= e; d.setDate(d.getDate() + 1)) {
    time.push(d.toISOString().slice(0, 10));
    cols.temperature_2m_max.push(20);
    cols.temperature_2m_min.push(10);
    cols.precipitation_sum.push(0);
    cols.wind_speed_10m_max.push(12);
    cols.weather_code.push(1);
  }
  return { daily: { time, ...cols } };
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
  global.fetch = vi.fn((input) => {
    const url = String(input);
    const multi = /latitude=[\d.-]+,/.test(url);
    if (multi) {
      const n = url.match(/latitude=([^&]+)/)[1].split(',').length;
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(Array.from({ length: n }, () => ({
          daily: { time: ['x'], temperature_2m_max: [25], weather_code: [1] },
        }))),
        text: () => Promise.resolve(''),
      });
    }

    // The recent pass is the one we starve.
    if (url.includes('/v1/archive') && url.includes(`start_date=${RECENT_FROM}-01-01`)) {
      return Promise.resolve({ ok: false, text: () => Promise.resolve(RATE_LIMITED) });
    }

    const range = url.match(/start_date=([\d-]+)&end_date=([\d-]+)/);
    const today = new Date().toISOString().slice(0, 10);
    const body = url.includes('past_days=')
      ? dailyRange(new Date(Date.now() - 29 * 864e5).toISOString().slice(0, 10), today)
      : range
        ? dailyRange(range[1], range[2])
        : dailyRange(today, today);

    return Promise.resolve({ ok: true, json: () => Promise.resolve(body), text: () => Promise.resolve('') });
  });

  await import('../src/main.js'); // triggers boot()
  await waitFor(() => document.querySelector('[data-role="chart"] svg.chart'));
});

describe('main.js — one history pass rate-limited', () => {
  test('still draws the years that did arrive instead of an error state', () => {
    const svg = document.querySelector('[data-role="chart"] svg.chart');
    expect(svg).toBeTruthy();
    expect(document.querySelector('.chart-error')).toBeNull();

    const years = [...svg.querySelectorAll('circle title')].map((t) => Number(t.textContent.split(' · ')[0]));
    expect(years.length).toBeGreaterThan(10);
    expect(Math.min(...years)).toBe(1940); // the deep pass landed
    // The starved recent pass left a gap; this year still comes from the forecast.
    expect(years).toContain(CURRENT_YEAR);
    expect(years).not.toContain(RECENT_FROM);
  });

  test('the slider still spans the full archive range', () => {
    const slider = document.querySelector('[data-role="slider"]');
    expect(slider.min).toBe('1940');
    expect(slider.max).toBe(String(CURRENT_YEAR));
    expect(slider.disabled).toBe(false);
  });
});
