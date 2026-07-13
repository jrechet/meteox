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

  // tab switch: Jour même / Période / Lois (two-level navigation)
  const tabs = [...root.querySelectorAll('.tab[data-tab]')];
  const panel = root.querySelector('[data-role="machine-content"]');
  const switchMode = (mode, focusTab = false) => {
    if (mode === state.mode) return;
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
      showInterpellationModal(interpellateBtn.dataset.lawId, interpellateBtn);
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

function showInterpellationModal(lawId, triggerEl) {
  import('./lib/laws.js').then(({ LAWS_DATA, departementLabel, interpellationLetter }) => {
    const law = LAWS_DATA.find((l) => l.id === lawId);
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
        <p class="cmodal__desc">Rédigez une interpellation à votre député concernant : <strong>${law.title}</strong>.</p>

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
          <div class="cmodal__letter" data-role="letter">${letter()}</div>
        </div>

        <div class="cmodal__actions">
          <button class="btn btn--outline btn--sm" data-action="copy-letter">Copier le message</button>
          <a href="${mailto()}" class="btn btn--citoyen btn--sm" data-action="send-email" data-role="send">Envoyer par e-mail ✉️</a>
        </div>
      </div>
    `;
    document.body.appendChild(modal);

    const letterEl = modal.querySelector('[data-role="letter"]');
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
      const val = cpInput.value.trim();
      if (/^\d{5}$/.test(val)) {
        window.open(`https://www.nosdeputes.fr/${val}`, '_blank');
      } else {
        window.open('https://habitants.assemblee-nationale.fr/', '_blank');
      }
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
