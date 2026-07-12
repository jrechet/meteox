import './styles/app.css';
import { isoToday, dayMonthLabel, monthDay, isoToDate } from './lib/format.js';
import { currentPosition, reverseName, searchPlaces } from './lib/geo.js';
import { fetchToday, fetchRecent, fetchHistory, fetchHeatmap, MAX_WINDOW } from './lib/weather.js';
import { viewLoading, viewError, viewApp, derive, machineContentHTML } from './components/views.js';
import { renderChart } from './components/chart.js';
import { heatmapContainerHTML, preloadFrancePaths } from './components/heatmap.js';
import { parseHash, writeHash } from './lib/urlstate.js';

const syncUrl = () => writeHash(state);
let pendingRestore = null;

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
  periodMetric: 'tmax', // 'tmax' | 'precip' | 'wind' — Période chart metric
  mapMode: 'abs', // 'abs' | 'anom' — dual-map coloring
  selectedIso: isoToday(), // Active date for the heatmap
  historyLoaded: false, // Flag for progressive loading
  dateSelected: false, // Flag indicating user explicitly selected a date in the strip
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

    syncUrl();

    // 5. Load 85-year history in the background
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

    // Apply a restored view from a shared link now that the range is known.
    if (pendingRestore) {
      const r = pendingRestore;
      pendingRestore = null;
      const first = series[0]?.year ?? state.currentYear;
      const last = series[series.length - 1]?.year ?? state.currentYear;
      if (r.win) state.windowLen = r.win;
      if (r.year != null) state.selectedYear = Math.min(last, Math.max(first, r.year));
      if (r.date) {
        state.selectedIso = r.date;
        state.dateSelected = true;
      }
      if (r.mode) state.mode = r.mode;
      syncUrl();
    }

    // Re-render full app dashboard with enabled history elements
    render(viewApp(state));
    bindApp();
    revealOnScroll();

    // Fetch heatmap(s) for the selected date/year (current year too if dual maps)
    const dayMmdd = monthDay(isoToDate(state.selectedIso));
    if (state.dateSelected) loadHeatmap(state.currentYear, dayMmdd);
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
    syncUrl();
  });

  // tab switch: Jour même / Période / Lois & Climat (ARIA tab pattern + keyboard)
  const tabs = [...root.querySelectorAll('.tab[data-tab]')];
  const panel = root.querySelector('[data-role="machine-content"]');
  const switchMode = (mode, focusTab = false) => {
    if (mode === state.mode) return;
    const tab = tabs.find((t) => t.dataset.tab === mode);
    if (!tab || tab.disabled) return;
    state.mode = mode;
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

    if (sliderRail) sliderRail.hidden = mode === 'politics';
    if (chartSec) chartSec.hidden = mode === 'politics';
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
    renderContent();
    refreshMaps();
    syncUrl();
    if (focusTab) tab.focus();
  };

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
      const lawId = interpellateBtn.dataset.lawId;
      showInterpellationModal(lawId);
      return;
    }

    // metric toggle (Température / Pluie / Vent) in the Période panel
    const metricBtn = e.target.closest('.chip[data-metric]');
    if (metricBtn) {
      if (metricBtn.dataset.metric !== state.periodMetric) {
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

function showInterpellationModal(lawId) {
  import('./lib/laws.js').then(({ LAWS_DATA }) => {
    const law = LAWS_DATA.find((l) => l.id === lawId);
    if (!law) return;

    // Create modal overlay container
    const modal = document.createElement('div');
    modal.className = 'cmodal';
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    
    const subject = encodeURIComponent(`Interpellation citoyenne : ${law.title}`);
    const bodyText = `Madame, Monsieur le Député,

En tant que citoyen(ne) de votre circonscription, je tiens à vous exprimer ma préoccupation concernant le projet de loi suivant : "${law.title}".

Cette réforme aura un impact significatif sur notre environnement :
- Pesticides : ${law.indicators.pesticides < 0 ? 'Recul environnemental et hausse des risques pour la santé' : 'Amélioration ou préservation'}
- Partage de l'eau : ${law.indicators.partageEau < 0 ? 'Accaparement accru et déséquilibre d\'usage' : 'Préservation de la ressource commune'}

Je vous demande solennellement de voter contre tout recul des normes environnementales et sanitaires, et de privilégier l'intérêt des citoyens face aux lobbies économiques.

Veuillez agréer, Madame, Monsieur le Député, l'assurance de mes salutations citoyennes.`;

    const mailtoUrl = `mailto:?subject=${subject}&body=${encodeURIComponent(bodyText)}`;

    modal.innerHTML = `
      <div class="cmodal-content">
        <button class="cmodal__close" data-action="close-modal" aria-label="Fermer">&times;</button>
        <h3 class="cmodal__title">Interpeller votre représentant</h3>
        <p class="cmodal__desc">Envoyez une interpellation directe à votre député concernant : <strong>${law.title}</strong>.</p>
        
        <div class="cmodal__input-group">
          <label class="cmodal__label" for="zipcode-input">Votre Code Postal (pour cibler l'élu)</label>
          <input class="cmodal__input" type="text" id="zipcode-input" placeholder="Ex: 49000" maxlength="5" />
        </div>

        <div class="cmodal__input-group">
          <label class="cmodal__label">Aperçu du message</label>
          <div class="cmodal__letter" readonly>${bodyText}</div>
        </div>

        <div class="cmodal__actions">
          <button class="btn btn--outline btn--sm" data-action="copy-letter">Copier le message</button>
          <a href="${mailtoUrl}" class="btn btn--citoyen btn--sm" data-action="send-email">
            Envoyer par e-mail ✉️
          </a>
        </div>
      </div>
    `;

    document.body.appendChild(modal);

    const close = () => {
      modal.remove();
      document.removeEventListener('keydown', handleEsc);
    };
    const handleEsc = (e) => {
      if (e.key === 'Escape') close();
    };
    document.addEventListener('keydown', handleEsc);

    modal.querySelector('[data-action="close-modal"]').addEventListener('click', close);
    modal.addEventListener('click', (e) => {
      if (e.target === modal) close();
    });

    const copyBtn = modal.querySelector('[data-action="copy-letter"]');
    copyBtn.addEventListener('click', () => {
      navigator.clipboard.writeText(bodyText).then(() => {
        copyBtn.textContent = 'Copié !';
        setTimeout(() => {
          copyBtn.textContent = 'Copier le message';
        }, 2000);
      });
    });

    modal.querySelector('[data-action="send-email"]').addEventListener('click', () => {
      setTimeout(close, 500);
    });
  });
}

boot();
