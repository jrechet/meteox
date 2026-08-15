// Deterministic end-to-end suite.
// Boots a real `vite preview` server, drives it with headless Chromium, and mocks
// every network call so the run never depends on a live API.
//
// Scope: every user-facing feature of the site gets at least one check here, so a
// feature that stops working fails CI. The sections below mirror the feature map
// in docs/STATUS.md; FEATURES lists them and the run prints any section that
// produced no check, which is how a new feature shipping without a test is caught.
//
// Run: `npm run test:e2e`
import { readFileSync } from 'node:fs';
import { preview } from 'vite';
import { chromium } from 'playwright';

const DAILY_FIELDS = ['temperature_2m_max', 'temperature_2m_min', 'precipitation_sum', 'wind_speed_10m_max', 'weather_code'];

const FEATURES = [
  'boot & hero',
  'decade chart & slider',
  'jour même',
  'période — strip & chips',
  'période — mesures & écart',
  'cartes de France',
  'animation des cartes',
  'choix du lieu',
  'état dans l’URL',
  'lois — API up',
  'lois — filtres & justifications',
  'lois — API down',
  'pages de loi statiques',
  'responsive & console',
];

const fails = [];
const covered = new Set();
let section = '(hors section)';
const start = (name) => {
  section = name;
  console.log(`\n[${name}]`);
};
const check = (cond, msg) => {
  covered.add(section);
  if (cond) console.log(`  ✓ ${msg}`);
  else {
    console.error(`  ✗ ${msg}`);
    fails.push(`${section} — ${msg}`);
  }
};

// ---- dates: the app reads the real clock, so the fixtures must too ----
const isoOf = (d) => d.toISOString().slice(0, 10);
const TODAY_ISO = isoOf(new Date());
const CURRENT_YEAR = Number(TODAY_ISO.slice(0, 4));
const shiftIso = (iso, n) => {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return isoOf(d);
};

// ---- fixture builders ----
/** Daily columns for exactly the requested span — range assertions depend on it. */
function dailyRange(startIso, endIso) {
  const time = [];
  const cols = Object.fromEntries(DAILY_FIELDS.map((f) => [f, []]));
  const end = new Date(`${endIso}T12:00:00Z`);
  for (let d = new Date(`${startIso}T12:00:00Z`); d <= end; d.setUTCDate(d.getUTCDate() + 1)) {
    const doy = Math.floor((d - new Date(Date.UTC(d.getUTCFullYear(), 0, 0))) / 864e5);
    const seasonal = 18 - 12 * Math.cos((doy / 365) * 2 * Math.PI);
    const warming = (d.getUTCFullYear() - 1940) * 0.03; // a visible trend to plot
    time.push(isoOf(d));
    cols.temperature_2m_max.push(Math.round((seasonal + warming + (doy % 5)) * 10) / 10);
    cols.temperature_2m_min.push(Math.round((seasonal + warming - 8) * 10) / 10);
    cols.precipitation_sum.push(doy % 7); // varies, so the Pluie measure has a real écart
    cols.wind_speed_10m_max.push(10 + (doy % 11));
    cols.weather_code.push(1);
  }
  return { daily: { time, ...cols } };
}

function cityArray(url) {
  const n = url.match(/latitude=([^&]+)/)?.[1].split(',').length ?? 20;
  return Array.from({ length: n }, (_, i) => ({
    daily: { time: ['x'], temperature_2m_max: [20 + (i % 15)], weather_code: [1] },
  }));
}

function mockResponse(url) {
  if (url.includes('geocoding-api')) {
    return { results: [{ name: 'Lyon', admin1: 'Auvergne-Rhône-Alpes', latitude: 45.764, longitude: 4.8357 }] };
  }
  if (url.includes('bigdatacloud')) {
    return { city: 'Bordeaux', principalSubdivision: 'Nouvelle-Aquitaine' };
  }
  if (/latitude=[\d.-]+,/.test(url)) return cityArray(url); // multi-city heatmap

  const range = url.match(/start_date=([\d-]+)&end_date=([\d-]+)/);
  if (url.includes('/v1/forecast')) {
    const past = url.match(/past_days=(\d+)/);
    if (past) return dailyRange(shiftIso(TODAY_ISO, -Number(past[1])), TODAY_ISO);
    return dailyRange(TODAY_ISO, TODAY_ISO);
  }
  if (url.includes('/v1/archive')) {
    return range ? dailyRange(range[1], range[2]) : dailyRange('1940-01-01', TODAY_ISO);
  }
  return {};
}

const PARIS_HASH = '#lat=48.8566&lon=2.3522&name=Paris&admin=Ile-de-France';
// Long enough for the animation to have advanced at least one frame (600ms in
// main.js), short enough that it cannot have reached the end of a 3-year span.
const PLAY_STOP_AFTER_MS = 900;

async function run() {
  const server = await preview({ preview: { port: 5199 } });
  const base = `http://localhost:5199${server.config.base}`;
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 375, height: 2600 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));

  const mockWeather = (p) =>
    p.route(/open-meteo\.com|bigdatacloud\.net/, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockResponse(route.request().url())),
      }),
    );
  await mockWeather(page);

  const overflow = () => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
  const setYear = async (year) => {
    await page.evaluate((y) => {
      const s = document.querySelector('[data-role="slider"]');
      s.value = String(y);
      s.dispatchEvent(new Event('input', { bubbles: true }));
    }, year);
    await page.waitForTimeout(450); // map/detail fetches are debounced at 250ms
  };
  /**
   * Full document load at `hash`, then wait until the history has landed.
   * The about:blank hop matters: goto()-ing the same document with a different
   * hash is a same-document navigation, so main.js would never re-run and a
   * "restores from a shared link" check would silently assert the previous view.
   */
  const bootTo = async (hash) => {
    await page.goto('about:blank');
    await page.goto(base + hash, { waitUntil: 'networkidle' });
    await page.locator('[data-role="slider"]:not([disabled])').waitFor({ timeout: 20000 });
    await page.locator('.heatmap-card').first().waitFor({ timeout: 20000 });
  };

  try {
    // ---------------------------------------------------------------- boot & hero
    start('boot & hero');
    // No hash: geolocation is unavailable in headless Chromium, so this exercises
    // the reverse-geocode / Paris fallback that every first-time visitor hits.
    await page.goto(base, { waitUntil: 'networkidle' });
    await page.locator('[data-role="slider"]:not([disabled])').waitFor({ timeout: 20000 });
    check(
      (await page.locator('[data-role="place-name"]').textContent()).trim().length > 0,
      'a visit with no shared link still resolves a place',
    );
    check((await page.locator('.state[role="alert"]').count()) === 0, 'no error state on a cold first visit');
    const adminLink = page.locator('.foot__admin');
    check((await adminLink.count()) === 1, 'the footer offers a way into the admin');
    check(
      (await adminLink.getAttribute('href')) === 'https://jrec.fr/meteox-laws-int/admin.html',
      'the admin link points at the back office',
    );
    check((await adminLink.getAttribute('rel')) === 'noopener', 'the admin link opens safely');

    await bootTo(PARIS_HASH);
    check((await page.locator('.vignette').count()) === 3, 'the hero renders its three vignettes');
    check(
      /\d/.test(await page.locator('.vignette--today .vignette__temp').textContent()),
      "the « Aujourd'hui » vignette shows a temperature",
    );
    await page.locator('.vignette--past .vignette__temp').waitFor({ timeout: 15000 });
    check(
      (await page.locator('.vignette--past .vignette__loading').count()) === 0,
      'the « il y a 10 ans » vignette resolves instead of spinning forever',
    );
    check(
      (await page.locator('.vignette--past .metric').count()) === 4,
      'the past vignette fills min/max/pluie/vent from its on-demand window',
    );
    check(
      /[+-]?\d/.test(await page.locator('.vignette--verdict .vignette__big').textContent()),
      'the warming verdict shows a value once the history is in',
    );

    // -------------------------------------------------- decade chart & slider
    start('decade chart & slider');
    const slider = page.locator('[data-role="slider"]');
    check((await slider.getAttribute('min')) === '1940', 'the slider starts at 1940');
    check(
      (await slider.getAttribute('max')) === String(CURRENT_YEAR),
      'the slider ends at the current year',
    );
    const chartYears = await page.evaluate(() =>
      [...document.querySelectorAll('[data-role="chart"] svg.chart circle title')].map((t) =>
        Number(t.textContent.split(' · ')[0]),
      ),
    );
    check(chartYears.length > 80, `the decade chart plots the whole record (${chartYears.length} points)`);
    check(Math.min(...chartYears) === 1940, 'the chart reaches back to 1940 — the deep pass landed');
    check(Math.max(...chartYears) === CURRENT_YEAR, 'the chart includes the current year');
    check(
      new Set(chartYears).size === chartYears.length,
      'the two history passes merge without duplicating a year',
    );
    check(
      (await page.locator('[data-role="chart"] line.trend').count()) === 1,
      'the trend line is drawn',
    );
    check((await page.locator('.chart-error').count()) === 0, 'no chart error state');

    // ------------------------------------------------------------- jour même
    start('jour même');
    await setYear(1976);
    check((await page.locator('[data-role="rail-year"]').textContent()) === '1976', 'the slider updates the rail year');
    check(
      (await page.locator('.focus__year').textContent()).includes('1976'),
      'the focus card follows the selected year',
    );
    check(
      (await page.locator('.focus__delta').count()) === 1,
      'the focus card states the gap vs today for a past year',
    );
    check(
      (await page.locator('.focus__grid .metric').count()) === 4,
      'the focus card shows min/max/pluie/vent from the on-demand year window',
    );
    check(
      (await page.evaluate(() => document.querySelector('.chart .ax--sel')?.textContent)) === '1976',
      'the chart highlights the selected year',
    );

    // --------------------------------------------------- cartes de France
    // The rule that broke: two maps as soon as a past year is picked, in EITHER
    // tab and without having to click a day in the strip first.
    start('cartes de France');
    check(
      (await page.locator('.france-map-col').count()) === 2,
      'a past year shows both maps in « Jour même », with no day clicked',
    );
    check((await page.locator('.map-placeholder').count()) === 0, 'both maps have real data');
    await setYear(CURRENT_YEAR);
    check(
      (await page.locator('.france-map-col').count()) === 0,
      'the current year collapses to a single map — nothing to compare',
    );
    check((await page.locator('.heatmap-card--wide').count()) === 0, 'the card is not the wide variant');
    await setYear(1976);
    check(
      (await page.locator('.france-map-col').count()) === 2,
      'going back to a past year restores both maps',
    );
    const layoutAgrees = await page.evaluate(
      () =>
        !!document.querySelector('.machine-layout--stacked') === !!document.querySelector('.heatmap-card--wide'),
    );
    check(layoutAgrees, 'the panel layout agrees with the card it wraps');
    const dateOk = await page.evaluate(() => {
      const title = document.querySelector('.heatmap-card__title')?.textContent || '';
      return title.includes(`${new Date().getDate()} `);
    });
    check(dateOk, "the maps use today's local calendar day (no UTC off-by-one)");
    await page.locator('.chip[data-mapmode="anom"]').click();
    await page.waitForTimeout(300);
    check(
      (await page.locator('.chip[data-mapmode="anom"][aria-pressed="true"]').count()) === 1,
      'the « Écart » map colouring can be selected',
    );
    check(
      (await page.locator('.france-map-col__title').first().textContent()).includes('écart'),
      'the left map announces it is showing the écart',
    );
    await page.locator('.chip[data-mapmode="abs"]').click();
    await page.waitForTimeout(300);
    check(
      (await page.locator('.chip[data-mapmode="abs"][aria-pressed="true"]').count()) === 1,
      'the « Absolu » colouring can be selected back',
    );

    // ------------------------------------------------- animation des cartes
    // Replays the chosen day year by year. Started three years out so the run is
    // a few frames rather than half a century.
    start('animation des cartes');
    await setYear(CURRENT_YEAR - 3);
    const playBtn = page.locator('[data-action="toggle-play"]');
    check((await playBtn.count()) === 1, 'a past year offers the animation');
    check(
      (await playBtn.textContent()).includes(`${CURRENT_YEAR - 3} → ${CURRENT_YEAR}`),
      'the button announces the span it will replay',
    );
    await playBtn.click();
    check((await page.locator('.map-play--on').count()) === 1, 'pressing play switches to the running state');
    check(
      (await page.locator('[data-action="toggle-play"][aria-pressed="true"]').count()) === 1,
      'the control reports it is playing',
    );
    // The run advances on its own and ends on the current year.
    await page.locator('.map-play--on').waitFor({ state: 'detached', timeout: 20000 });
    check(
      (await page.locator('[data-role="rail-year"]').textContent()) === String(CURRENT_YEAR),
      'the animation walks the years and lands on the current year',
    );
    check((await page.locator('.map-play__error').count()) === 0, 'the run reports no failure');

    // Cadence: frames must land on a steady beat. A stalling playhead still ends
    // on the right year, so the check above alone would not notice.
    await setYear(CURRENT_YEAR - 8);
    const beats = await page.evaluate(async () => {
      const seen = [];
      const tick = setInterval(() => {
        const y = document.querySelector('[data-role="rail-year"]')?.textContent;
        if (!seen.length || seen[seen.length - 1].y !== y) seen.push({ y, t: performance.now() });
      }, 20);
      document.querySelector('[data-action="toggle-play"]').click();
      await new Promise((r) => setTimeout(r, 4000));
      clearInterval(tick);
      document.querySelector('[data-action="toggle-play"]')?.click(); // stop
      return seen.map((s) => Math.round(s.t));
    });
    const gaps = beats.slice(1).map((t, i) => t - beats[i]);
    const worst = gaps.length ? Math.max(...gaps) : Infinity;
    check(gaps.length >= 4, `the animation advanced ${gaps.length + 1} years in 4s`);
    check(worst < 1500, `frames keep a steady beat (slowest ${worst}ms)`);

    // Stopping mid-run must leave the year it stopped on, not snap back.
    await setYear(CURRENT_YEAR - 3);
    await page.locator('[data-action="toggle-play"]').click();
    await page.waitForTimeout(PLAY_STOP_AFTER_MS);
    await page.locator('[data-action="toggle-play"]').click();
    check((await page.locator('.map-play--on').count()) === 0, 'pressing stop ends the animation');
    const stoppedYear = Number(await page.locator('[data-role="rail-year"]').textContent());
    check(
      stoppedYear >= CURRENT_YEAR - 3 && stoppedYear <= CURRENT_YEAR,
      `stopping keeps the year it reached (${stoppedYear})`,
    );
    await page.waitForTimeout(1200);
    check(
      Number(await page.locator('[data-role="rail-year"]').textContent()) === stoppedYear,
      'the animation really stopped — the year no longer moves',
    );

    // Touching the slider takes over from a running animation.
    await setYear(CURRENT_YEAR - 3);
    await page.locator('[data-action="toggle-play"]').click();
    await page.waitForTimeout(PLAY_STOP_AFTER_MS);
    await setYear(1976);
    check((await page.locator('.map-play--on').count()) === 0, 'moving the slider stops the animation');
    check((await page.locator('[data-role="rail-year"]').textContent()) === '1976', 'the slider wins over the playhead');
    await setYear(CURRENT_YEAR);
    check(
      (await page.locator('[data-action="toggle-play"]').count()) === 0,
      'the current year offers no animation — there is nothing to replay',
    );
    await setYear(1976);

    // --------------------------------------------- période — strip & chips
    start('période — strip & chips');
    await page.locator('.tab[data-tab="period"]').click();
    await page.locator('.pstrip').waitFor({ timeout: 10000 });
    check((await page.locator('.pstrip').count()) > 0, 'switching to Période renders the day strip');
    for (const [win, expected] of [[5, 5], [30, 30], [10, 10]]) {
      await page.locator(`.chip[data-win="${win}"]`).click();
      await page.waitForTimeout(300);
      check(
        (await page.locator('.pcol').count()) === expected,
        `the ${win}-day chip renders ${expected} day columns`,
      );
    }
    check(
      (await page.locator('.france-map-col').count()) === 2,
      'entering Période on a past year already shows both maps',
    );
    const firstDate = await page.locator('.pcol').first().getAttribute('data-date');
    await page.locator('.pcol').first().click();
    await page.waitForTimeout(500);
    check(
      (await page.locator('.pcol--active').count()) === 1,
      'clicking a day column marks it active',
    );
    check(
      (await page.locator('.heatmap-card__title').textContent()).includes(
        String(Number(firstDate.slice(8, 10))),
      ),
      'the maps follow the day picked in the strip',
    );

    // ---------------------------------------- période — mesures & écart
    start('période — mesures & écart');
    const badge = page.locator('.psum__delta');
    check(
      (await badge.textContent()).includes('1976'),
      'the écart badge names the compared year instead of leaving the sign alone',
    );
    check(
      /plus (chaud|frais)|aussi chaud/.test(await badge.textContent()),
      'the écart badge states the direction in words',
    );
    check(
      (await badge.getAttribute('data-dir')) !== 'neutral',
      'temperature keeps its warm/cold hue',
    );
    for (const [metric, unit] of [['precip', 'mm'], ['wind', 'km/h']]) {
      await page.locator(`.chip[data-metric="${metric}"]`).click();
      await page.waitForTimeout(250);
      check(
        (await page.locator(`.chip[data-metric="${metric}"][aria-pressed="true"]`).count()) === 1,
        `the ${metric} measure can be selected`,
      );
      check((await badge.textContent()).includes(unit), `the badge switches to ${unit}`);
      check(
        (await badge.getAttribute('data-dir')) === 'neutral',
        `${metric} uses the neutral badge, not a temperature hue`,
      );
    }
    await page.locator('.chip[data-metric="tmax"]').click();
    await page.waitForTimeout(250);
    check((await page.locator('[data-role="period-chart"] svg').count()) === 1, 'the dual-line chart renders');
    check(
      (await page.locator('[data-role="period-chart"] polyline.pl-now').count()) === 1 &&
        (await page.locator('[data-role="period-chart"] polyline.pl-past').count()) === 1,
      'both series are plotted',
    );
    await setYear(CURRENT_YEAR);
    check(
      (await badge.textContent()).includes('glissez le curseur'),
      'on the current year the badge invites a comparison instead of comparing a year to itself',
    );
    await setYear(1976);

    // -------------------------------------------------------- état dans l'URL
    start('état dans l’URL');
    const hash = await page.evaluate(() => location.hash);
    check(hash.includes('mode=period'), 'the active tab is written to the URL');
    check(hash.includes('year=1976'), 'the selected year is written to the URL');
    check(/lat=|lon=/.test(hash), 'the place is written to the URL');
    await bootTo(`${PARIS_HASH}&mode=period&year=2003&win=30`);
    check((await page.locator('[data-role="rail-year"]').textContent()) === '2003', 'a shared link restores the year');
    check(
      (await page.locator('.tab[data-tab="period"][aria-selected="true"]').count()) === 1,
      'a shared link restores the tab',
    );
    check((await page.locator('.pcol').count()) === 30, 'a shared link restores the period length');

    // ---------------------------------------------------------- choix du lieu
    start('choix du lieu');
    await page.locator('[data-action="toggle-search"]').click();
    check(
      (await page.locator('[data-role="search-panel"][data-open="true"]').count()) === 1,
      'the place search panel opens',
    );
    await page.locator('[data-role="search-input"]').fill('Lyon');
    await page.locator('[data-role="search-results"] button').first().waitFor({ timeout: 8000 });
    check(
      (await page.locator('[data-role="search-results"] button').count()) >= 1,
      'typing a commune lists matches',
    );
    await page.locator('[data-role="search-results"] button').first().click();
    await page.locator('[data-role="slider"]:not([disabled])').waitFor({ timeout: 20000 });
    check(
      (await page.locator('[data-role="place-name"]').textContent()).includes('Lyon'),
      'selecting a result loads that commune',
    );
    check(
      (await page.evaluate(() => location.hash)).includes('Lyon'),
      'the new place is written to the URL',
    );
    check((await page.locator('.chart-error').count()) === 0, 'the new place loads its history without error');

    // --------------------------------------------------------- lois — API up
    start('lois — API up');
    await bootTo(PARIS_HASH);
    const apiLaws = JSON.parse(readFileSync(new URL('../src/data/laws-snapshot.json', import.meta.url), 'utf8')).laws;
    await page.route(/jrec\.fr\/meteox-laws-int\/api\/laws/, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        // CORS : le front tourne sur localhost, l'API mockée doit l'autoriser comme la vraie.
        headers: { 'access-control-allow-origin': '*' },
        body: JSON.stringify(apiLaws),
      }),
    );
    await page.locator('[data-nav="politics"]').click();
    // Le squelette de chargement porte aussi .pcard : attendre le rendu final (fraîcheur).
    await page.locator('.politics-freshness').waitFor({ timeout: 8000 });
    check((await page.locator('.pcard:not(.pcard--skeleton)').count()) > 0, 'politics tab renders passed law cards');
    check(
      (await page.locator('.politics-freshness[data-source="api"]').count()) === 1,
      'freshness indicator shows live API data',
    );
    check(await page.locator('.rail--bar').first().isHidden(), 'year slider is hidden in politics mode');
    check(await page.locator('.hero-vignettes').first().isHidden(), 'the weather hero is hidden in politics mode');
    await page.locator('[data-nav="climat"]').click();
    await page.locator('.hero-vignettes').first().waitFor({ timeout: 8000 });
    check((await page.locator('.pstrip, .focus').count()) > 0, 'going back to Climat restores the weather panel');
    await page.locator('[data-nav="politics"]').click();
    await page.locator('.politics-freshness').waitFor({ timeout: 8000 });

    // ------------------------------------- lois — filtres & justifications
    start('lois — filtres & justifications');
    const filters = page.locator('[data-lawfilter]');
    check((await filters.count()) > 1, 'law category filters are offered');
    const allCards = await page.locator('.pcard:not(.pcard--skeleton)').count();
    const lastFilter = filters.nth((await filters.count()) - 1);
    const lastFilterKey = await lastFilter.getAttribute('data-lawfilter');
    await lastFilter.click();
    await page.waitForTimeout(300);
    check(
      (await page.locator(`[data-lawfilter="${lastFilterKey}"].chip--active`).count()) === 1,
      'the picked category becomes active',
    );
    const filtered = await page.locator('.pcard:not(.pcard--skeleton)').count();
    check(filtered <= allCards, `filtering narrows the cards (${allCards} → ${filtered})`);
    await page.locator('[data-lawfilter="all"]').click();
    await page.waitForTimeout(300);
    check(
      (await page.locator('.pcard:not(.pcard--skeleton)').count()) === allCards,
      'clearing the filter restores every card',
    );

    // Acceptance issue #4 : le dépliant « Pourquoi ces scores ? » donne accès à la
    // justification et à la méthodologie depuis chaque carte.
    await page.route(/jrec\.fr\/meteox-laws-int\/api\/laws\/[^/]+\/indicators/, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'access-control-allow-origin': '*' },
        body: JSON.stringify({
          lawId: 'x',
          methodology: 'https://github.com/jrechet/meteox/blob/main/docs/methodologie-indicateurs.md',
          indicators: [{ indicator: 'pesticides', score: 1, justification: 'Justification de test citée.', citation: 'extrait exact du texte', confidence: 'haute', model: 'claude-api:test' }],
        }),
      }),
    );
    await page.locator('.indicators-why__summary').first().click();
    await page.locator('.indicators-why__citation').first().waitFor({ timeout: 8000 });
    check(
      (await page.locator('.indicators-why__body').first().textContent()).includes('Justification de test citée'),
      'indicators details reveals the cited justification',
    );
    check(
      (await page.locator('.indicators-why__body a[href*="methodologie-indicateurs"]').count()) >= 1,
      'indicators details links to the methodology',
    );

    // Golden Rule: upcoming cards only exist with a verified source; otherwise an honest empty state.
    const upcomingCount = await page.locator('.pcard--upcoming').count();
    if (upcomingCount === 0) {
      check(
        (await page.locator('.politics-upcoming-grid .empty-state').count()) === 1,
        'upcoming section shows honest empty state when no verified scrutin',
      );
    } else {
      await page.locator('button[data-action="interpellate"]').first().click();
      // La modale n'est montée qu'après un import() dynamique (lib/laws.js) : on attend le
      // champ au lieu de le compter tout de suite.
      await page.locator('.cmodal #zipcode-input').waitFor({ timeout: 8000 });
      check((await page.locator('.cmodal #zipcode-input').count()) === 1, 'interpellation modal opens');
      await page.locator('.cmodal #zipcode-input').fill('49000');
      check(
        (await page.locator('.cmodal [data-role="cp-hint"]').textContent()).length > 0,
        'the modal resolves the département from the postcode',
      );
      check(
        (await page.locator('.cmodal [data-role="letter"]').textContent()).length > 50,
        'the modal drafts the interpellation letter',
      );
      const [popup] = await Promise.all([
        ctx.waitForEvent('page'),
        page.locator('.cmodal #search-deputy-btn').click(),
      ]);
      await popup.waitForLoadState('commit');
      check(
        popup.url() === 'https://www.assemblee-nationale.fr/dyn/vos-deputes/recherche-carte',
        'search button opens official AN map locator page',
      );
      await popup.close();
      await page.locator('.cmodal [data-action="close-modal"]').click();
      check((await page.locator('.cmodal').count()) === 0, 'interpellation modal closes');
    }

    // ------------------------------------------------- responsive & console
    start('responsive & console');
    for (const w of [375, 768, 1280, 1920]) {
      await page.setViewportSize({ width: w, height: 1600 });
      check(await overflow(), `no horizontal overflow in politics mode at ${w}`);
    }
    await page.locator('[data-nav="climat"]').click();
    await page.locator('.hero-vignettes').first().waitFor({ timeout: 8000 });
    for (const w of [375, 768, 1280, 1920]) {
      await page.setViewportSize({ width: w, height: 2200 });
      await page.waitForTimeout(150);
      check(await overflow(), `no horizontal overflow in climat mode at ${w}`);
    }
    await page.setViewportSize({ width: 375, height: 2600 });
    await page.locator('.tab[data-tab="period"]').click();
    await page.waitForTimeout(400);
    check(await overflow(), 'no horizontal overflow at 375 with the period strip + dual maps');

    // ------------------------------------------------------- lois — API down
    // Acceptance issue #5 : API down → bascule transparente sur le snapshot embarqué,
    // cartes affichées, indicateur honnête, aucune erreur console.
    start('lois — API down');
    const page2 = await ctx.newPage();
    const errors2 = [];
    page2.on('console', (m) => m.type() === 'error' && errors2.push(m.text()));
    await mockWeather(page2);
    await page2.route(/jrec\.fr\/meteox-laws-int/, (route) => route.abort('connectionfailed'));
    await page2.goto(base + PARIS_HASH, { waitUntil: 'networkidle' });
    await page2.locator('[data-nav="politics"]').click();
    await page2.locator('.politics-freshness').waitFor({ timeout: 10000 });
    check((await page2.locator('.pcard:not(.pcard--skeleton)').count()) > 0, 'snapshot laws render when API is down');
    check(
      (await page2.locator('.politics-freshness[data-source="snapshot"]').count()) === 1,
      'freshness indicator honestly labels archived snapshot data',
    );
    // Le navigateur logge lui-même l'échec réseau ("Failed to load resource") — inévitable
    // et attendu quand l'API est down. On vérifie qu'AUCUNE erreur applicative ne s'ajoute.
    const appErrors2 = errors2.filter((e) => !e.startsWith('Failed to load resource'));
    check(appErrors2.length === 0, `no app console errors with API down (saw ${appErrors2.length})`);
    if (appErrors2.length) appErrors2.forEach((e) => console.error('    console:', e));
    await page2.close();

    // ------------------------------------------------ pages de loi statiques
    // Generated by `npm run generate:pages` at build time and served by Pages —
    // the SEO surface of the site, invisible to any check driving the SPA only.
    start('pages de loi statiques');
    const snapshot = JSON.parse(readFileSync(new URL('../src/data/laws-snapshot.json', import.meta.url), 'utf8'));
    const firstLaw = snapshot.laws[0];
    const lawPage = await ctx.newPage();
    await lawPage.goto(`${base}loi/${firstLaw.id}/`, { waitUntil: 'domcontentloaded' });
    check((await lawPage.locator('h1').textContent()) === firstLaw.title, 'a law page renders its law title');
    check((await lawPage.title()).length > 0, 'a law page has a title tag for sharing and search');
    check((await lawPage.locator('a.back').count()) >= 1, 'a law page links back to the site');
    check((await lawPage.locator('.sources a').count()) >= 1, 'a law page cites its official sources');

    const sitemap = await lawPage.goto(`${base}sitemap.xml`, { waitUntil: 'domcontentloaded' });
    const sitemapXml = await sitemap.text();
    check(sitemap.status() === 200, 'sitemap.xml is served');
    check(
      snapshot.laws.every((l) => sitemapXml.includes(`/loi/${l.id}/`)),
      `sitemap lists all ${snapshot.laws.length} law pages`,
    );
    await lawPage.close();

    start('responsive & console');
    const appErrors = errors.filter((e) => !e.startsWith('Failed to load resource'));
    check(appErrors.length === 0, `no console errors (saw ${appErrors.length})`);
    if (appErrors.length) appErrors.forEach((e) => console.error('    console:', e));
  } finally {
    await browser.close();
    await server.httpServer.close();
  }

  // A feature listed in FEATURES with no check at all is a coverage hole, and a
  // failure — that is the whole point of keeping the list next to the suite.
  const uncovered = FEATURES.filter((f) => !covered.has(f));
  if (uncovered.length) {
    console.error(`\n✗ features with no e2e check: ${uncovered.join(', ')}`);
    fails.push(...uncovered.map((f) => `${f} — no check ran`));
  }

  if (fails.length) {
    console.error(`\nE2E FAILED — ${fails.length} check(s) failed`);
    fails.forEach((f) => console.error(`  · ${f}`));
    process.exit(1);
  }
  console.log(`\nE2E PASSED — ${FEATURES.length} features covered`);
}

run().catch((e) => {
  console.error(e);
  process.exit(1);
});
