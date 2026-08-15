import './styles/app.css';
import { isoToday, dayMonthLabel, monthDay, isoToDate } from './lib/format.js';
import { currentPosition, reverseName, searchPlaces } from './lib/geo.js';
import {
  fetchToday, fetchRecent, fetchSeries, fetchYearWindow, fetchHeatmap,
  MAX_WINDOW, ARCHIVE_START_YEAR,
} from './lib/weather.js';
import { viewLoading, viewError, viewApp, derive, machineContentHTML, heroHTML } from './components/views.js';
import { renderChart } from './components/chart.js';
import { heatmapContainerHTML, preloadFrancePaths, showsDualMaps } from './components/heatmap.js';
import { parseHash, writeHash } from './lib/urlstate.js';
import { loadLaws, getLoadedLaws } from './lib/laws-data.js';
import { escapeHtml } from './lib/html.js';
import { loadIndicators } from './lib/indicators-data.js';
import { indicatorsWhyBodyHTML } from './components/politics.js';

const syncUrl = () => writeHash(state);
let pendingRestore = null;
// Bumped on every load(). Requests in flight for the previous location resolve
// against a stale token and drop their results instead of writing them into the
// new place's state — the history passes and the per-year windows all outlive a
// quick "Paris → Nice" switch otherwise.
let loadToken = 0;

const PARIS = { name: 'Paris', admin: 'Île-de-France', lat: 48.8566, lon: 2.3522 };
const root = document.getElementById('app');

// The history arrives in two passes so the chart is usable long before the whole
// record is down: the recent decades first (where the slider starts and where
// the "il y a 10 ans" comparison lives), then everything back to 1940.
const RECENT_SPAN = 30;

// Map animation. One archive request per year (20 cities, one day) costs ~0.4s
// when four run at once, so the prefetcher outruns a 600ms frame and playback
// stays smooth after a short warm-up. Open-Meteo answers "Too many concurrent
// requests" well before a dozen parallel calls — do not raise this blindly.
const PLAY_FRAME_MS = 600;
const PLAY_CONCURRENCY = 4;
// Open-Meteo rate-limits per minute. Racing through the remaining years on error
// would look like a broken animation and hammer the API on the way — stop and
// say so instead.
const PLAY_MAX_FAILURES = 3;
// How far the prefetcher may run ahead of the playhead. Bounded so that stopping
// an animation early does not leave a whole span already requested.
const PLAY_LOOKAHEAD = 6;
let playToken = 0;

const now = new Date();
const state = {
  todayIso: isoToday(),
  mmdd: monthDay(now),
  dayLabel: dayMonthLabel(now),
  currentYear: now.getFullYear(),
  location: null,
  today: null,
  series: null,
  windows: {}, // { [year]: rows[] } — per-year N-day look-back windows, fetched on demand
  pendingWindows: new Set(), // years whose window request is in flight
  recent: null, // real last MAX_WINDOW days of the current year (forecast)
  selectedYear: now.getFullYear(),
  mode: 'day', // 'day' | 'period'
  windowLen: 10,
  periodMetric: 'tmax', // 'tmax' | 'precip' | 'wind' — Période chart metric
  mapMode: 'abs', // 'abs' | 'anom' — dual-map coloring
  selectedIso: isoToday(), // Active date for the heatmap
  historyLoaded: false, // pass 1 (recent decades) in — chart + slider usable
  deepLoaded: false, // pass 2 (back to 1940) in — full record plotted
  dateSelected: false, // Flag indicating user explicitly selected a date in the strip
  isPlaying: false, // map animation running, year by year up to today
  playFrom: null, // year the running animation started on
  playWaiting: false, // playhead is waiting on a frame the prefetcher hasn't got yet
};

// ---------- boot: geoloc first, Paris fallback ----------
async function boot() {
  render(viewLoading('Localisation en cours…'));

  // A shared link fully specifies location + view — restore it, skip geolocation.
  const u = parseHash();
  if (u && u.lat != null && u.lon != null) {
    pendingRestore = u;
    await load({
      name: u.name || `${u.lat.toFixed(2)}, ${u.lon.toFixed(2)}`,
      admin: u.admin || 'France',
      lat: u.lat,
      lon: u.lon,
    });
    return;
  }

  let loc;
  try {
    const pos = await currentPosition();
    loc = await reverseName(pos);
  } catch {
    loc = PARIS;
  }
  await load(loc);
}

async function load(location) {
  const token = ++loadToken;
  stopPlayback(); // an animation belongs to the place it was started on
  state.location = location;
  render(viewLoading(`Chargement de la météo à ${location.name}…`));
  try {
    // 1. Fetch Today and Recent weather first (very fast, <200ms)
    const [today, recent] = await Promise.all([
      fetchToday(location.lat, location.lon),
      fetchRecent(location.lat, location.lon, MAX_WINDOW),
    ]);
    if (token !== loadToken) return; // a newer place won the race

    state.today = today;
    state.recent = recent;
    state.historyLoaded = false;
    state.deepLoaded = false;
    state.selectedYear = state.currentYear;
    state.selectedIso = state.todayIso;
    state.heatmaps = {};
    // Everything below is location-scoped — a new place must not inherit it.
    state.series = null;
    state.windows = {};
    state.pendingWindows.clear();

    // 2. Render initial dashboard instantly
    render(viewApp(state));
    bindApp();
    revealOnScroll();

    // 3. Load the lazy France-outline chunk, then re-draw the map once it lands
    preloadFrancePaths().then(refreshHeatmapUI);

    // 4. Pre-fetch heatmap for the current date/year
    const dayMmdd = monthDay(isoToDate(state.selectedIso));
    loadHeatmap(state.currentYear, dayMmdd);

    syncUrl();

    // 5. Load the record back to 1940 in the background, in two passes
    loadHistoryInBackground(location, token);
  } catch (err) {
    if (token !== loadToken) return;
    console.error('Failed to load weather data:', err);
    render(viewError('Impossible de charger les données météo. Vérifiez la connexion.', { retry: true }));
    root.querySelector('[data-action="retry"]')?.addEventListener('click', () => load(location));
  }
}

/** Merge a pass into state.series, keeping it ascending and free of duplicates. */
function mergeSeries(rows) {
  const byYear = new Map((state.series ?? []).map((s) => [s.year, s]));
  for (const row of rows) {
    const prev = byYear.get(row.year);
    byYear.set(row.year, prev ? { ...prev, ...row } : row);
  }
  // this year's point comes from the live forecast — the archive lags behind it
  const cur = byYear.get(state.currentYear);
  byYear.set(state.currentYear, { ...cur, year: state.currentYear, ...state.today });
  state.series = [...byYear.values()].sort((a, b) => a.year - b.year);
}

/**
 * History in two passes. Pass 1 covers the recent decades and unlocks the UI;
 * pass 2 back-fills to 1940 and runs concurrently, so the deep record never
 * gates first paint. Both are temperature-only — the remaining variables for
 * whichever year is on screen come from ensureYearDetail().
 */
async function loadHistoryInBackground(location, token) {
  const { lat, lon } = location;
  const { mmdd, todayIso, currentYear } = state;
  const recentFrom = Math.max(ARCHIVE_START_YEAR, currentYear - (RECENT_SPAN - 1));

  // Each pass stands on its own. Open-Meteo rate-limits by request weight, so one
  // of the two can 429 while the other lands — whichever years arrive get drawn
  // rather than thrown away with an "indisponible" on a half-successful load.
  let landed = 0;
  const apply = (rows) => {
    if (token !== loadToken || !rows.length) return;
    mergeSeries(rows);
    const isFirst = landed++ === 0;
    state.historyLoaded = true;
    applyPendingRestore();
    redrawApp();
    if (isFirst) {
      refreshSelectedYearData();
      ensureYearDetail(state.currentYear - 10); // "il y a 10 ans" hero vignette
    }
  };
  const pass = (from, to, label) =>
    fetchSeries(lat, lon, mmdd, todayIso, from, to).then(apply, (err) => {
      console.warn(`Failed to load ${label} history:`, err);
    });

  await Promise.all([
    pass(recentFrom, currentYear, 'recent'),
    ...(recentFrom > ARCHIVE_START_YEAR ? [pass(ARCHIVE_START_YEAR, recentFrom - 1, 'deep')] : []),
  ]);

  if (token !== loadToken) return;
  state.deepLoaded = true;
  if (!landed) {
    const chartEl = root.querySelector('[data-role="chart"]');
    if (chartEl) chartEl.innerHTML = `<div class="chart-error">Historique météo indisponible</div>`;
    return;
  }
  redrawApp();
}

/** Apply a view restored from a shared link, once the year range can clamp it. */
function applyPendingRestore() {
  if (!pendingRestore) return;
  const r = pendingRestore;
  pendingRestore = null;
  if (r.win) state.windowLen = r.win;
  if (r.year != null) {
    state.selectedYear = Math.min(state.currentYear, Math.max(ARCHIVE_START_YEAR, r.year));
  }
  if (r.date) {
    state.selectedIso = r.date;
    state.dateSelected = true;
  }
  if (r.mode) state.mode = r.mode;
  syncUrl();
}

function redrawApp() {
  render(viewApp(state));
  bindApp();
  revealOnScroll();
}

/** Maps + full-detail window for whatever year is currently selected. */
function refreshSelectedYearData() {
  const dayMmdd = monthDay(isoToDate(state.selectedIso));
  if (showsDualMaps(state)) loadHeatmap(state.currentYear, dayMmdd);
  loadHeatmap(state.selectedYear, dayMmdd);
  ensureYearDetail(state.selectedYear);
}

/**
 * Precipitation, wind and weather code for one year — the variables left out of
 * the bulk series. Fills state.windows[year] (the "Période" strip) and folds the
 * target day back into state.series (the focus card and hero vignette).
 */
async function ensureYearDetail(year) {
  if (year === state.currentYear) return; // covered by fetchRecent + fetchToday
  if (state.windows[year] || state.pendingWindows.has(year)) return;
  const token = loadToken;
  const { lat, lon } = state.location;
  state.pendingWindows.add(year);
  try {
    const rows = await fetchYearWindow(lat, lon, state.mmdd, year);
    if (token !== loadToken) return; // these are another place's days now
    state.windows[year] = rows;
    const day = rows[rows.length - 1];
    const entry = state.series?.find((s) => s.year === year);
    if (entry && day) Object.assign(entry, { ...day, year });
    // Only repaint the swappable panel: a full redraw here would fight the slider.
    const contentEl = root.querySelector('[data-role="machine-content"]');
    if (contentEl?.isConnected && state.mode !== 'politics') {
      contentEl.innerHTML = machineContentHTML(state, derive(state));
    }
    refreshHeroVignette();
  } catch (err) {
    console.warn(`Failed to load detail for ${year}:`, err);
  } finally {
    state.pendingWindows.delete(year);
  }
}

// ---------- render helper ----------
function render(html) {
  root.innerHTML = html;
  root.setAttribute('aria-busy', 'false');
}

// ---------- interactions ----------
function bindApp() {
  const d = derive(state);

  const slider = root.querySelector('[data-role="slider"]');
  const contentEl = root.querySelector('[data-role="machine-content"]');
  const chartEl = root.querySelector('[data-role="chart"]');
  const railYear = root.querySelector('[data-role="rail-year"]');
  const chips = root.querySelector('[data-role="window-chips"]');

  // Redraw the swappable content (day focus card OR period panel). Pure — no fetches.
  const renderContent = () => {
    contentEl.innerHTML = machineContentHTML(state, d);
  };

  // Onglet Lois : données via l'API (snapshot en secours) — squelette pendant le fetch,
  // puis re-rendu une fois chargé. loadLaws est mémoïsé : un seul fetch par session.
  const ensureLawsLoaded = () => {
    if (state.laws) return;
    loadLaws().then(({ laws, meta }) => {
      state.laws = laws;
      state.lawsMeta = meta;
      // N'écris que si ce conteneur est toujours dans le DOM (un re-render a pu le
      // remplacer entre-temps) et qu'on est encore sur l'onglet Lois.
      if (state.mode === 'politics' && contentEl.isConnected) renderContent();
    });
  };
  if (state.mode === 'politics') ensureLawsLoaded(); // deep-link #politics

  // Load only the maps the current view needs. The currentYear map is constant;
  // the selectedYear map is what changes while dragging — so debounce map loads
  // and never fire one per intermediate slider tick.
  const refreshMaps = () => {
    const dayMmdd = monthDay(isoToDate(state.selectedIso));
    if (showsDualMaps(state)) loadHeatmap(state.currentYear, dayMmdd);
    loadHeatmap(state.selectedYear, dayMmdd);
    ensureYearDetail(state.selectedYear); // precip/wind/code + the period window
  };
  let mapTimer;
  const refreshMapsDebounced = () => {
    clearTimeout(mapTimer);
    mapTimer = setTimeout(refreshMaps, 250);
  };

  slider?.addEventListener('input', () => {
    stopPlayback(); // taking the slider means taking over from the animation
    const yr = Number(slider.value);
    if (yr === state.selectedYear) return;
    state.selectedYear = yr;
    railYear.textContent = yr;
    slider.setAttribute('aria-valuenow', yr);
    renderContent(); // focus card or period strip (shows map placeholders)
    if (state.historyLoaded) {
      chartEl.innerHTML = renderChart(state.series, yr, d.sliderMin, d.sliderMax); // decade chart highlight
    }
    refreshMapsDebounced(); // a single map fetch once the drag settles
    syncUrl();
  });

  // tab switch: Jour même / Période / Lois (two-level navigation)
  const tabs = [...root.querySelectorAll('.tab[data-tab]')];
  const panel = root.querySelector('[data-role="machine-content"]');
  const switchMode = (mode, focusTab = false) => {
    if (mode === state.mode) return;
    stopPlayback();
    state.mode = mode;
    if (mode !== 'politics') {
      state.lastClimatMode = mode;
    }

    // Update primary navigation buttons
    const navButtons = [...root.querySelectorAll('[data-nav]')];
    navButtons.forEach((btn) => {
      const active =
        (mode === 'politics' && btn.dataset.nav === 'politics') ||
        (mode !== 'politics' && btn.dataset.nav === 'climat');
      btn.classList.toggle('top-nav__btn--active', active);
    });

    // Update secondary tabs active state
    tabs.forEach((t) => {
      const on = t.dataset.tab === mode;
      t.setAttribute('aria-selected', String(on));
      t.tabIndex = on ? 0 : -1;
    });

    if (panel) panel.setAttribute('aria-labelledby', `tab-${mode}`);
    if (chips) chips.hidden = mode !== 'period';

    // Toggle visibility of elements outside machine-content
    const sliderRail = root.querySelector('.rail--bar');
    const chartSec = root.querySelector('section[aria-label="Tendance sur les décennies"]');
    const noteText = root.querySelector('.section__note');
    const heroSec = root.querySelector('.hero-vignettes');
    const secHead = root.querySelector('.section__head');
    const subTabs = root.querySelector('.tabs');

    if (sliderRail) sliderRail.hidden = mode === 'politics';
    if (chartSec) chartSec.hidden = mode === 'politics';
    if (heroSec) {
      heroSec.hidden = mode === 'politics';
      heroSec.style.display = mode === 'politics' ? 'none' : '';
    }
    if (secHead) {
      secHead.hidden = mode === 'politics';
      secHead.style.display = mode === 'politics' ? 'none' : '';
    }
    if (subTabs) {
      subTabs.hidden = mode === 'politics';
      subTabs.style.display = mode === 'politics' ? 'none' : '';
    }

    if (noteText) {
      if (mode === 'politics') {
        noteText.innerHTML = "Consultez les récentes réformes écologiques en France, l'impact des lobbies et les actions citoyennes possibles.";
      } else {
        noteText.innerHTML = `Comparez à aujourd’hui&nbsp;: soit le <b>jour même</b> ${state.dayLabel}, soit une <b>période</b> — les derniers jours contre les mêmes jours d’une année passée.`;
      }
    }

    if (mode === 'day') {
      state.selectedIso = state.todayIso;
      state.dateSelected = false;
    }
    if (mode === 'politics') ensureLawsLoaded();
    renderContent();
    refreshMaps();
    syncUrl();
    
    if (focusTab && mode !== 'politics') {
      const activeTab = tabs.find((t) => t.dataset.tab === mode);
      if (activeTab) activeTab.focus();
    }
  };

  // Bind primary menu buttons
  root.querySelectorAll('[data-nav]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const targetNav = btn.dataset.nav;
      if (targetNav === 'politics') {
        switchMode('politics');
      } else {
        switchMode(state.lastClimatMode || 'day');
      }
    });
  });

  // Bind secondary tab buttons
  tabs.forEach((tab) => {
    tab.addEventListener('click', () => switchMode(tab.dataset.tab));
    tab.addEventListener('keydown', (e) => {
      if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
      e.preventDefault();
      const enabled = tabs.filter((t) => !t.disabled);
      const i = enabled.indexOf(tab);
      const next = enabled[(i + (e.key === 'ArrowRight' ? 1 : enabled.length - 1)) % enabled.length];
      switchMode(next.dataset.tab, true);
    });
  });

  // window-length chips (period mode)
  chips?.querySelectorAll('.chip[data-win]').forEach((chip) => {
    chip.addEventListener('click', () => {
      stopPlayback();
      const n = Number(chip.dataset.win);
      if (n === state.windowLen) return;
      state.windowLen = n;
      chips.querySelectorAll('.chip[data-win]').forEach((c) =>
        c.setAttribute('aria-pressed', String(Number(c.dataset.win) === n)),
      );
      renderContent();
      refreshMaps();
      syncUrl();
    });
  });

  // Event delegation (content is re-rendered, so listeners live on the container)
  contentEl?.addEventListener('click', (e) => {
    // Map animation: replay the chosen day year by year up to today.
    if (e.target.closest('[data-action="toggle-play"]')) {
      state.isPlaying ? stopPlayback() : startPlayback();
      return;
    }

    // Law category filters
    const filterChip = e.target.closest('[data-lawfilter]');
    if (filterChip) {
      state.lawFilter = filterChip.dataset.lawfilter;
      renderContent();
      return;
    }

    // Interpellate modal trigger
    const interpellateBtn = e.target.closest('[data-action="interpellate"]');
    if (interpellateBtn) {
      showInterpellationModal(interpellateBtn.dataset.lawId, interpellateBtn);
      return;
    }

    // Dépliant « Pourquoi ces scores ? » (issue #4) : charge la justification au premier
    // dépliage. Le <details> s'ouvre nativement ; on ne fait que remplir son corps.
    const whySummary = e.target.closest('.indicators-why__summary');
    if (whySummary) {
      const details = whySummary.closest('.indicators-why');
      if (details.dataset.loaded) return;
      details.dataset.loaded = '1';
      const body = details.querySelector('[data-role="why-body"]');
      loadIndicators(details.dataset.lawId).then((payload) => {
        body.innerHTML = indicatorsWhyBodyHTML(payload);
      });
      return;
    }

    // metric toggle (Température / Pluie / Vent) in the Période panel
    const metricBtn = e.target.closest('.chip[data-metric]');
    if (metricBtn) {
      if (metricBtn.dataset.metric !== state.periodMetric) {
        stopPlayback();
        state.periodMetric = metricBtn.dataset.metric;
        renderContent();
        refreshMaps();
      }
      return;
    }

    // map coloring toggle (Absolu / Écart) on the dual maps
    const mapModeBtn = e.target.closest('.chip[data-mapmode]');
    if (mapModeBtn) {
      if (mapModeBtn.dataset.mapmode !== state.mapMode) {
        state.mapMode = mapModeBtn.dataset.mapmode;
        refreshHeatmapUI();
      }
      return;
    }

    const pcol = e.target.closest('.pcol');
    if (pcol) {
      const date = pcol.dataset.date;
      if (date) {
        stopPlayback(); // the animation is bound to one day — a new day restarts it
        state.selectedIso = date;
        state.dateSelected = true;
        renderContent();
        refreshMaps(); // load both maps for the newly selected date
        syncUrl();
      }
    }
  });

  bindPlace();
}

function updateHeatmapsForSelectedDate() {
  const dayMmdd = monthDay(isoToDate(state.selectedIso));
  const dayLabel = dayMonthLabel(state.selectedIso);

  // Update heatmap card title immediately for responsiveness
  const cardTitle = root.querySelector('.heatmap-card__title');
  if (cardTitle) {
    cardTitle.textContent = `Cartes de France · le ${dayLabel}`;
  }

  loadHeatmap(state.currentYear, dayMmdd);
  loadHeatmap(state.selectedYear, dayMmdd);
}

/** Repaint the hero in place — used when a year's detail lands after first paint. */
function refreshHeroVignette() {
  const hero = root.querySelector('.hero-vignettes');
  if (!hero) return;
  hero.outerHTML = heroHTML(state, derive(state));
  root.querySelectorAll('.hero-vignettes .reveal').forEach((el) => el.classList.add('in'));
}

function refreshHeatmapUI() {
  const card = root.querySelector('.heatmap-card');
  if (card) {
    const parent = card.parentElement;
    if (parent) {
      parent.innerHTML = heatmapContainerHTML(state);
    }
  }
}

function bindPlace() {
  const btn = root.querySelector('[data-action="toggle-search"]');
  const panel = root.querySelector('[data-role="search-panel"]');
  const input = root.querySelector('[data-role="search-input"]');
  const results = root.querySelector('[data-role="search-results"]');

  const close = () => {
    panel.dataset.open = 'false';
    btn.setAttribute('aria-expanded', 'false');
  };
  const open = () => {
    panel.dataset.open = 'true';
    btn.setAttribute('aria-expanded', 'true');
    input.focus();
  };

  btn?.addEventListener('click', () => {
    panel.dataset.open === 'true' ? close() : open();
  });

  root.querySelector('[data-action="use-geo"]')?.addEventListener('click', async () => {
    close();
    try {
      const pos = await currentPosition();
      const loc = await reverseName(pos);
      await load(loc);
    } catch {
      render(viewError('Position indisponible. Autorisez la géolocalisation ou cherchez une commune.', { retry: false }));
      bootRetryFooter();
    }
  });

  // debounced search
  let timer;
  input?.addEventListener('input', () => {
    clearTimeout(timer);
    const q = input.value;
    timer = setTimeout(async () => {
      const places = await searchPlaces(q);
      results.innerHTML = places.length
        ? places
            .map(
              (p, i) =>
                `<li><button data-idx="${i}">
                   <span>${p.name}</span><span class="muted">${p.admin}</span>
                 </button></li>`,
            )
            .join('')
        : q.trim().length >= 2
          ? '<li><span class="muted" style="padding:.5rem .7rem;display:block">Aucun résultat</span></li>'
          : '';
      results.querySelectorAll('button[data-idx]').forEach((b) => {
        b.addEventListener('click', () => {
          close();
          load(places[Number(b.dataset.idx)]);
        });
      });
    }, 260);
  });

  document.addEventListener('click', (e) => {
    if (panel.dataset.open === 'true' && !e.target.closest('.place')) close();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') close();
  });
}

function bootRetryFooter() {
  root.querySelector('[data-action="retry"]')?.addEventListener('click', () => boot());
}

// A year already being fetched must not be fetched again: during the animation
// the prefetch pool and the playhead ask for the same year as soon as the
// playhead catches up, which used to send the request twice.
const heatmapInFlight = new Map();

/** Fetch one year's map into state.heatmaps without touching the DOM. */
function ensureHeatmapData(year, mmdd) {
  const cacheKey = `${year}:${mmdd}`;
  if (state.heatmaps?.[cacheKey]) return Promise.resolve(state.heatmaps[cacheKey]);
  const pending = heatmapInFlight.get(cacheKey);
  if (pending) return pending;

  const request = fetchHeatmap(mmdd, year)
    .then((data) => {
      if (!state.heatmaps) state.heatmaps = {};
      state.heatmaps[cacheKey] = data;
      return data;
    })
    .finally(() => heatmapInFlight.delete(cacheKey));
  heatmapInFlight.set(cacheKey, request);
  return request;
}

async function loadHeatmap(year, mmdd) {
  if (!mmdd) mmdd = monthDay(isoToDate(state.selectedIso));
  if (state.heatmaps?.[`${year}:${mmdd}`]) {
    refreshHeatmapUI();
    return;
  }

  const mapsContainer = root.querySelector('[data-role="france-maps-container"]') || root.querySelector('[data-role="france-map-container"]');
  if (mapsContainer) mapsContainer.classList.add('map-loading');

  try {
    await ensureHeatmapData(year, mmdd);
  } catch (err) {
    console.warn(`Failed to load heatmap for ${year}:${mmdd}`, err);
  }
  refreshHeatmapUI();
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Move the year controls without re-rendering the whole panel. */
function syncYearControls(year) {
  const slider = root.querySelector('[data-role="slider"]');
  const railYear = root.querySelector('[data-role="rail-year"]');
  if (slider) {
    slider.value = String(year);
    slider.setAttribute('aria-valuenow', String(year));
  }
  if (railYear) railYear.textContent = String(year);
}

/**
 * Replay the selected calendar day year by year, from the selected year to today.
 *
 * Frames are fetched by a small worker pool running ahead of the playhead, so
 * playback is smooth once a couple of years are in and instant on a replay
 * (fetchHeatmap caches each year in localStorage). Only the maps and the year
 * controls move — re-rendering the whole panel every frame would fight the
 * slider and put the "Période" strip into a loading state 50 times over.
 */
async function startPlayback() {
  const from = state.selectedYear;
  const to = state.currentYear;
  if (state.isPlaying || from >= to) return;

  const mmdd = monthDay(isoToDate(state.selectedIso));
  const years = [];
  for (let y = from; y <= to; y++) years.push(y);

  const token = ++playToken;
  state.isPlaying = true;
  state.playFrom = from;
  state.playWaiting = false;
  state.playError = null;
  refreshHeatmapUI();

  // Prefetch pool — pulls years in playback order so the wait is always for the
  // frame about to be shown. Two limits keep it from becoming a burst generator:
  //  - it never runs more than PLAY_LOOKAHEAD frames ahead of the playhead, so
  //    stopping after a few seconds costs a few requests, not the whole span;
  //  - the first failure parks it. Left unbounded and failure-blind, replaying
  //    1940→today fired 90 requests in 6.7s and 81 came back refused — the pool
  //    kept hammering while the API was already saying no.
  let next = 0;
  let playIndex = 0;
  let poolParked = false;
  const prefetch = async () => {
    while (token === playToken && !poolParked && next < years.length) {
      if (next > playIndex + PLAY_LOOKAHEAD) {
        await sleep(PLAY_FRAME_MS); // far enough ahead — let the playhead catch up
        continue;
      }
      const year = years[next++];
      try {
        await ensureHeatmapData(year, mmdd);
      } catch {
        poolParked = true; // the playhead will retry at its own pace and report
      }
    }
  };
  for (let i = 0; i < PLAY_CONCURRENCY; i++) prefetch();

  let misses = 0;
  for (const [index, year] of years.entries()) {
    playIndex = index;
    if (token !== playToken) return;
    if (!state.heatmaps?.[`${year}:${mmdd}`]) {
      state.playWaiting = true;
      refreshHeatmapUI();
      try {
        await ensureHeatmapData(year, mmdd);
        misses = 0;
      } catch (err) {
        if (token !== playToken) return;
        if (++misses >= PLAY_MAX_FAILURES) {
          console.warn('Map animation stopped — heatmap unavailable:', err);
          state.playError = 'Données indisponibles pour le moment. Réessayez dans une minute.';
          break;
        }
        // Skip this year but keep the cadence: racing ahead on failure both
        // looks broken and turns a rate limit into a burst of doomed requests.
        state.playWaiting = false;
        refreshHeatmapUI();
        await sleep(PLAY_FRAME_MS);
        continue;
      }
      if (token !== playToken) return;
      state.playWaiting = false;
    }
    state.selectedYear = year;
    syncYearControls(year);
    refreshHeatmapUI();
    await sleep(PLAY_FRAME_MS);
  }
  if (token === playToken) {
    const message = state.playError;
    stopPlayback();
    state.playError = message; // survives the stop so the failure stays on screen
    refreshHeatmapUI();
  }
}

/** Stop the animation and let the rest of the panel catch up with the year it left on. */
function stopPlayback() {
  if (!state.isPlaying) return;
  playToken++; // invalidates the running loop and its prefetch pool
  state.isPlaying = false;
  state.playWaiting = false;
  state.playError = null;
  syncUrl();
  const contentEl = root.querySelector('[data-role="machine-content"]');
  if (contentEl?.isConnected && state.mode !== 'politics') {
    contentEl.innerHTML = machineContentHTML(state, derive(state));
  }
  ensureYearDetail(state.selectedYear);
}

// ---------- scroll reveal ----------
function revealOnScroll() {
  const items = root.querySelectorAll('.reveal');
  if (!('IntersectionObserver' in window)) {
    items.forEach((el) => el.classList.add('in'));
    return;
  }
  const io = new IntersectionObserver(
    (entries) => {
      for (const en of entries) {
        if (en.isIntersecting) {
          en.target.classList.add('in');
          io.unobserve(en.target);
        }
      }
    },
    { rootMargin: '0px 0px -8% 0px', threshold: 0.08 },
  );
  items.forEach((el) => io.observe(el));
}

function showInterpellationModal(lawId, triggerEl) {
  import('./lib/laws.js').then(({ departementLabel, interpellationLetter }) => {
    const law = getLoadedLaws().find((l) => l.id === lawId);
    if (!law) return;

    let cp = '';
    let email = '';
    const letter = () => interpellationLetter(law, cp);
    const subject = encodeURIComponent(`Interpellation citoyenne : ${law.title}`);
    const mailto = () => {
      const recipient = email.trim();
      return `mailto:${recipient}?subject=${subject}&body=${encodeURIComponent(letter())}`;
    };

    const modal = document.createElement('div');
    modal.className = 'cmodal';
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    modal.setAttribute('aria-labelledby', 'cmodal-title');
    modal.innerHTML = `
      <div class="cmodal-content">
        <button class="cmodal__close" data-action="close-modal" aria-label="Fermer">&times;</button>
        <h3 class="cmodal__title" id="cmodal-title">Interpeller votre représentant</h3>
        <p class="cmodal__desc">Rédigez une interpellation à votre député concernant : <strong>${escapeHtml(law.title)}</strong>.</p>

        <div class="cmodal__input-group">
          <label class="cmodal__label" for="zipcode-input">1. Saisissez votre code postal</label>
          <div style="display: flex; gap: var(--space-2);">
            <input class="cmodal__input" type="text" inputmode="numeric" id="zipcode-input"
                   placeholder="Ex : 49000" maxlength="5" autocomplete="postal-code" style="flex: 1;" />
            <button class="btn btn--outline btn--sm" id="search-deputy-btn" style="white-space: nowrap;">Rechercher mon député 🔍</button>
          </div>
          <p class="cmodal__hint" data-role="cp-hint">Entrez votre code postal puis cliquez sur Rechercher pour ouvrir l'annuaire.</p>
        </div>

        <div class="cmodal__input-group">
          <label class="cmodal__label" for="deputy-email-input">2. Collez l'e-mail officiel de votre député(e)</label>
          <input class="cmodal__input" type="email" id="deputy-email-input"
                 placeholder="Ex : prenom.nom@assemblee-nationale.fr" />
          <p class="cmodal__hint">Trouvez l'e-mail officiel de l'élu sur NosDéputés.fr ou l'Assemblée nationale.</p>
        </div>

        <div class="cmodal__input-group">
          <label class="cmodal__label">Aperçu du message</label>
          <div class="cmodal__letter" data-role="letter"></div>
        </div>

        <div class="cmodal__actions">
          <button class="btn btn--outline btn--sm" data-action="copy-letter">Copier le message</button>
          <a href="${mailto()}" class="btn btn--citoyen btn--sm" data-action="send-email" data-role="send">Envoyer par e-mail ✉️</a>
        </div>
      </div>
    `;
    document.body.appendChild(modal);

    const letterEl = modal.querySelector('[data-role="letter"]');
    // Le corps de la lettre est du texte : injection via textContent (jamais innerHTML),
    // le titre de loi qu'il contient ne peut donc pas exécuter de HTML.
    letterEl.textContent = letter();
    const hintEl = modal.querySelector('[data-role="cp-hint"]');
    const sendEl = modal.querySelector('[data-role="send"]');
    const cpInput = modal.querySelector('#zipcode-input');
    const emailInput = modal.querySelector('#deputy-email-input');
    const searchBtn = modal.querySelector('#search-deputy-btn');

    const updateMailto = () => {
      sendEl.setAttribute('href', mailto());
    };

    cpInput.addEventListener('input', () => {
      cp = cpInput.value.replace(/\D/g, '').slice(0, 5);
      if (cpInput.value !== cp) cpInput.value = cp;
      const dep = departementLabel(cp);
      hintEl.textContent =
        cp.length === 0
          ? 'Entrez votre code postal puis cliquez sur Rechercher pour ouvrir l\'annuaire.'
          : cp.length < 5
            ? 'Code postal incomplet…'
            : dep
              ? `Département identifié : ${dep}. Cliquez sur Rechercher pour identifier l'élu de votre circonscription.`
              : 'Code postal non reconnu.';
      letterEl.textContent = letter();
      updateMailto();
    });

    emailInput.addEventListener('input', () => {
      email = emailInput.value.trim();
      updateMailto();
    });

    searchBtn.addEventListener('click', (e) => {
      e.preventDefault();
      window.open('https://www.assemblee-nationale.fr/dyn/vos-deputes/recherche-carte', '_blank');
    });

    // --- focus management (trap + restore) ---
    const focusables = () =>
      [...modal.querySelectorAll('a[href], button, input, [tabindex]:not([tabindex="-1"])')].filter(
        (el) => !el.disabled && el.offsetParent !== null,
      );
    const close = () => {
      modal.remove();
      document.removeEventListener('keydown', onKey);
      triggerEl?.focus();
    };
    const onKey = (e) => {
      if (e.key === 'Escape') return close();
      if (e.key !== 'Tab') return;
      const f = focusables();
      if (!f.length) return;
      const first = f[0];
      const last = f[f.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    modal.querySelector('[data-action="close-modal"]').addEventListener('click', close);
    modal.addEventListener('click', (e) => e.target === modal && close());

    const copyBtn = modal.querySelector('[data-action="copy-letter"]');
    copyBtn.addEventListener('click', () => {
      navigator.clipboard?.writeText(letter()).then(() => {
        copyBtn.textContent = 'Copié !';
        setTimeout(() => (copyBtn.textContent = 'Copier le message'), 2000);
      });
    });
    sendEl.addEventListener('click', () => setTimeout(close, 500));

    cpInput.focus();
  });
}

boot();
