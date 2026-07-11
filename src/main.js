import './styles/app.css';
import { isoToday, dayMonthLabel, monthDay, isoToDate } from './lib/format.js';
import { currentPosition, reverseName, searchPlaces } from './lib/geo.js';
import { fetchToday, fetchRecent, fetchHistory, fetchHeatmap, MAX_WINDOW } from './lib/weather.js';
import { viewLoading, viewError, viewApp, derive, machineContentHTML } from './components/views.js';
import { renderChart } from './components/chart.js';
import { heatmapContainerHTML, preloadFrancePaths } from './components/heatmap.js';

const PARIS = { name: 'Paris', admin: 'Île-de-France', lat: 48.8566, lon: 2.3522 };
const root = document.getElementById('app');

const now = new Date();
const state = {
  todayIso: isoToday(),
  mmdd: monthDay(now),
  dayLabel: dayMonthLabel(now),
  currentYear: now.getFullYear(),
  location: null,
  today: null,
  series: null,
  windows: null, // { [year]: rows[] } — per-year N-day look-back windows
  recent: null, // real last MAX_WINDOW days of the current year (forecast)
  selectedYear: now.getFullYear(),
  mode: 'day', // 'day' | 'period'
  windowLen: 10,
  selectedIso: isoToday(), // Active date for the heatmap
  historyLoaded: false, // Flag for progressive loading
  dateSelected: false, // Flag indicating user explicitly selected a date in the strip
};

// ---------- boot: geoloc first, Paris fallback ----------
async function boot() {
  render(viewLoading('Localisation en cours…'));
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
  state.location = location;
  render(viewLoading(`Chargement de la météo à ${location.name}…`));
  try {
    // 1. Fetch Today and Recent weather first (very fast, <200ms)
    const [today, recent] = await Promise.all([
      fetchToday(location.lat, location.lon),
      fetchRecent(location.lat, location.lon, MAX_WINDOW),
    ]);

    state.today = today;
    state.recent = recent;
    state.historyLoaded = false;
    state.selectedYear = state.currentYear;
    state.selectedIso = state.todayIso;
    state.heatmaps = {};

    // 2. Render initial dashboard instantly
    render(viewApp(state));
    bindApp();
    revealOnScroll();

    // 3. Load the lazy France-outline chunk, then re-draw the map once it lands
    preloadFrancePaths().then(refreshHeatmapUI);

    // 4. Pre-fetch heatmap for the current date/year
    const dayMmdd = monthDay(isoToDate(state.selectedIso));
    loadHeatmap(state.currentYear, dayMmdd);

    // 4. Load 85-year history in the background
    loadHistoryInBackground(location);
  } catch (err) {
    console.error('Failed to load weather data:', err);
    render(viewError('Impossible de charger les données météo. Vérifiez la connexion.', { retry: true }));
    root.querySelector('[data-action="retry"]')?.addEventListener('click', () => load(location));
  }
}

async function loadHistoryInBackground(location) {
  try {
    const history = await fetchHistory(location.lat, location.lon, state.mmdd, state.todayIso);
    const { series, windows } = history;

    // fill this year's point from live forecast if the archive lags
    const cur = series.find((s) => s.year === state.currentYear);
    if (!cur) series.push({ year: state.currentYear, ...state.today });
    else if (cur.tmax == null) Object.assign(cur, state.today);

    state.windows = windows;
    state.series = series;
    state.historyLoaded = true;

    // Re-render full app dashboard with enabled history elements
    render(viewApp(state));
    bindApp();
    revealOnScroll();

    // Fetch heatmap for selected past year
    const dayMmdd = monthDay(isoToDate(state.selectedIso));
    loadHeatmap(state.selectedYear, dayMmdd);
  } catch (err) {
    console.warn('Failed to load background history:', err);
    const chartEl = root.querySelector('[data-role="chart"]');
    if (chartEl) {
      chartEl.innerHTML = `<div class="chart-error">Historique météo indisponible</div>`;
    }
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

  // Load only the maps the current view needs. The currentYear map is constant;
  // the selectedYear map is what changes while dragging — so debounce map loads
  // and never fire one per intermediate slider tick.
  const dualMaps = () =>
    (state.mode === 'period' && state.dateSelected) ||
    (state.mode === 'day' && state.selectedYear !== state.currentYear);
  const refreshMaps = () => {
    const dayMmdd = monthDay(isoToDate(state.selectedIso));
    if (dualMaps()) loadHeatmap(state.currentYear, dayMmdd);
    loadHeatmap(state.selectedYear, dayMmdd);
  };
  let mapTimer;
  const refreshMapsDebounced = () => {
    clearTimeout(mapTimer);
    mapTimer = setTimeout(refreshMaps, 250);
  };

  slider?.addEventListener('input', () => {
    const yr = Number(slider.value);
    if (yr === state.selectedYear) return;
    state.selectedYear = yr;
    railYear.textContent = yr;
    slider.setAttribute('aria-valuenow', yr);
    renderContent(); // focus card or period strip (shows map placeholders)
    if (state.historyLoaded) {
      chartEl.innerHTML = renderChart(state.series, yr); // decade chart highlight
    }
    refreshMapsDebounced(); // a single map fetch once the drag settles
  });

  // tab switch: Jour même / Période
  root.querySelectorAll('.tab[data-tab]').forEach((tab) => {
    tab.addEventListener('click', () => {
      const mode = tab.dataset.tab;
      if (mode === state.mode) return;
      state.mode = mode;
      root.querySelectorAll('.tab[data-tab]').forEach((t) =>
        t.setAttribute('aria-selected', String(t.dataset.tab === mode)),
      );
      if (chips) chips.hidden = mode !== 'period';

      // Reset selectedIso and dateSelected when leaving period mode
      if (mode === 'day') {
        state.selectedIso = state.todayIso;
        state.dateSelected = false;
      }
      renderContent();
      refreshMaps();
    });
  });

  // window-length chips (period mode)
  chips?.querySelectorAll('.chip[data-win]').forEach((chip) => {
    chip.addEventListener('click', () => {
      const n = Number(chip.dataset.win);
      if (n === state.windowLen) return;
      state.windowLen = n;
      chips.querySelectorAll('.chip[data-win]').forEach((c) =>
        c.setAttribute('aria-pressed', String(Number(c.dataset.win) === n)),
      );
      renderContent();
      refreshMaps();
    });
  });

  // Event delegation to capture clicks on horizontal day strip columns (.pcol)
  contentEl?.addEventListener('click', (e) => {
    const pcol = e.target.closest('.pcol');
    if (pcol) {
      const date = pcol.dataset.date;
      if (date) {
        state.selectedIso = date;
        state.dateSelected = true;
        renderContent();
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

async function loadHeatmap(year, mmdd) {
  if (!mmdd) mmdd = monthDay(isoToDate(state.selectedIso));
  const cacheKey = `${year}:${mmdd}`;
  if (state.heatmaps?.[cacheKey]) {
    refreshHeatmapUI();
    return;
  }

  const mapsContainer = root.querySelector('[data-role="france-maps-container"]') || root.querySelector('[data-role="france-map-container"]');
  if (mapsContainer) mapsContainer.classList.add('map-loading');

  try {
    const data = await fetchHeatmap(mmdd, year);
    if (!state.heatmaps) state.heatmaps = {};
    state.heatmaps[cacheKey] = data;

    refreshHeatmapUI();
  } catch (err) {
    console.warn(`Failed to load heatmap for ${year}:${mmdd}`, err);
    refreshHeatmapUI();
  }
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

boot();
