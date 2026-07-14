// Deterministic end-to-end smoke test.
// Boots a real `vite preview` server, drives it with headless Chromium, and
// mocks every Open-Meteo call so the run never depends on the live API.
// Guards the regressions from this session: mobile overflow, tab/chip/date
// interactions, and dual-map stacking. Run: `npm run test:e2e`.
import { preview } from 'vite';
import { chromium } from 'playwright';

const DAILY_FIELDS = ['temperature_2m_max', 'temperature_2m_min', 'precipitation_sum', 'wind_speed_10m_max', 'weather_code'];
const fails = [];
const check = (cond, msg) => {
  if (cond) console.log(`  ✓ ${msg}`);
  else {
    console.error(`  ✗ ${msg}`);
    fails.push(msg);
  }
};

// ---- fixture builders ----
function dailyRange(start, end) {
  const time = [];
  const cols = Object.fromEntries(DAILY_FIELDS.map((f) => [f, []]));
  for (let d = new Date(start + 'T12:00:00'), e = new Date(end + 'T12:00:00'); d <= e; d.setDate(d.getDate() + 1)) {
    const iso = d.toISOString().slice(0, 10);
    const doy = Math.floor((d - new Date(d.getFullYear(), 0, 0)) / 864e5);
    const seasonal = 18 - 12 * Math.cos((doy / 365) * 2 * Math.PI);
    const warming = (d.getFullYear() - 1990) * 0.05;
    time.push(iso);
    cols.temperature_2m_max.push(Math.round((seasonal + warming + (doy % 5)) * 10) / 10);
    cols.temperature_2m_min.push(Math.round((seasonal + warming - 8) * 10) / 10);
    cols.precipitation_sum.push(0);
    cols.wind_speed_10m_max.push(12);
    cols.weather_code.push(1);
  }
  return { daily: { time, ...cols } };
}

function cityArray(url) {
  const n = (url.match(/latitude=([^&]+)/)?.[1].split(',').length) ?? 20;
  return Array.from({ length: n }, (_, i) => ({
    daily: { time: ['x'], temperature_2m_max: [20 + (i % 15)], weather_code: [1] },
  }));
}

function mockResponse(url) {
  const isMulti = /latitude=[\d.-]+,/.test(url);
  if (url.includes('/v1/forecast')) {
    if (isMulti) return cityArray(url); // heatmap current-year
    if (url.includes('past_days=')) return dailyRange('2026-06-11', '2026-07-10'); // fetchRecent (30d)
    return dailyRange('2026-07-10', '2026-07-10'); // fetchToday
  }
  if (url.includes('/v1/archive')) {
    if (isMulti) return cityArray(url); // heatmap past-year
    return dailyRange('1990-01-01', '2026-07-05'); // fetchHistory
  }
  return {}; // geocoding etc.
}

async function run() {
  const server = await preview({ preview: { port: 5199 } });
  const base = `http://localhost:5199${server.config.base}`;
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 375, height: 2600 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));

  await page.route(/open-meteo\.com|bigdatacloud\.net/, (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockResponse(route.request().url())) }),
  );

  try {
    await page.goto(base, { waitUntil: 'networkidle' });
    await page.locator('.heatmap-card').first().waitFor({ timeout: 15000 });

    const overflow = () => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);

    console.log('[375] day mode');
    check(await overflow(), 'no horizontal overflow at 375 (day mode)');
    // guard the off-by-one: the map's day must equal today's LOCAL date, not day-1
    const dateOk = await page.evaluate(() => {
      const title = document.querySelector('.heatmap-card__title')?.textContent || '';
      return title.includes(`${new Date().getDate()} `);
    });
    check(dateOk, "heatmap shows today's local calendar day (no UTC off-by-one)");

    console.log('[375] période + chips + date select');
    await page.locator('.tab[data-tab="period"]').click();
    check(await page.locator('.pstrip').count() > 0, 'switching to Période renders the day strip');
    await page.locator('.chip[data-win="30"]').click();
    check(await page.locator('.pcol').count() >= 30, '30-day chip renders 30 day columns');
    // Move slider to 1976 so a past year is selected for dual maps comparison
    await page.evaluate(() => {
      const slider = document.querySelector('[data-role="slider"]');
      slider.value = 1976;
      slider.dispatchEvent(new Event('input'));
      slider.dispatchEvent(new Event('change'));
    });
    await page.waitForTimeout(500);
    await page.locator('.pstrip .pcol').nth(2).click();
    await page.waitForTimeout(500);
    check(await page.locator('.france-map-col').count() === 2, 'selecting a day shows dual France maps');
    check(await overflow(), 'no horizontal overflow at 375 (dual maps stacked)');

    console.log('[1280] wide');
    await page.setViewportSize({ width: 1280, height: 1600 });
    check(await overflow(), 'no horizontal overflow at 1280');

    console.log('[politics] Lois & Climat tab');
    await page.locator('[data-nav="politics"]').click();
    check(await page.locator('.pcard').count() > 0, 'politics tab renders passed law cards');
    check(await page.locator('.rail--bar').first().isHidden(), 'year slider is hidden in politics mode');
    check(await overflow(), 'no horizontal overflow in politics mode');

    // Golden Rule: upcoming cards only exist with a verified source; otherwise an honest empty state.
    const upcomingCount = await page.locator('.pcard--upcoming').count();
    if (upcomingCount === 0) {
      check(
        (await page.locator('.politics-upcoming-grid .empty-state').count()) === 1,
        'upcoming section shows honest empty state when no verified scrutin',
      );
    } else {
      await page.locator('button[data-action="interpellate"]').first().click();
      check(await page.locator('.cmodal #zipcode-input').count() === 1, 'interpellation modal opens');

      // Check that search-deputy-btn opens the official Assemblée Nationale card search url
      const [popup] = await Promise.all([
        ctx.waitForEvent('page'),
        page.locator('.cmodal #search-deputy-btn').click()
      ]);
      await popup.waitForLoadState('commit');
      check(popup.url() === 'https://www.assemblee-nationale.fr/dyn/vos-deputes/recherche-carte', 'search button opens official AN map locator page');
      await popup.close();

      await page.locator('.cmodal [data-action="close-modal"]').click();
      check(await page.locator('.cmodal').count() === 0, 'interpellation modal closes');
    }

    check(errors.length === 0, `no console errors (saw ${errors.length})`);
    if (errors.length) errors.forEach((e) => console.error('    console:', e));
  } finally {
    await browser.close();
    await server.httpServer.close();
  }

  if (fails.length) {
    console.error(`\nE2E FAILED — ${fails.length} check(s) failed`);
    process.exit(1);
  }
  console.log('\nE2E PASSED');
}

run().catch((e) => {
  console.error(e);
  process.exit(1);
});
