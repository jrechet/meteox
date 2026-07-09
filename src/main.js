import './styles/app.css';
import { isoToday, dayMonthLabel, monthDay } from './lib/format.js';
import { currentPosition, reverseName, searchPlaces } from './lib/geo.js';
import { fetchToday, fetchTrend } from './lib/weather.js';
import { viewLoading, viewError, viewApp, derive, focusHTML } from './components/views.js';
import { renderChart } from './components/chart.js';

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
  selectedYear: now.getFullYear(),
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
  render(viewLoading(`Lecture de 85 ans de météo à ${location.name}…`));
  try {
    const [today, series] = await Promise.all([
      fetchToday(location.lat, location.lon),
      fetchTrend(location.lat, location.lon, state.mmdd, state.todayIso),
    ]);
    // fill this year's point from live forecast if the archive lags
    const cur = series.find((s) => s.year === state.currentYear);
    if (!cur) series.push({ year: state.currentYear, ...today });
    else if (cur.tmax == null) Object.assign(cur, today);

    state.today = today;
    state.series = series;
    state.selectedYear = state.currentYear;
    render(viewApp(state));
    bindApp();
    revealOnScroll();
  } catch (err) {
    render(viewError('Impossible de charger les données météo. Vérifiez la connexion.', { retry: true }));
    root.querySelector('[data-action="retry"]')?.addEventListener('click', () => load(location));
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

  // slider — cheap partial updates only
  const slider = root.querySelector('[data-role="slider"]');
  const focusEl = root.querySelector('[data-role="focus"]');
  const chartEl = root.querySelector('[data-role="chart"]');
  const railYear = root.querySelector('[data-role="rail-year"]');

  slider?.addEventListener('input', () => {
    const yr = Number(slider.value);
    if (yr === state.selectedYear) return;
    state.selectedYear = yr;
    railYear.textContent = yr;
    slider.setAttribute('aria-valuenow', yr);
    focusEl.innerHTML = focusHTML(state, d, yr);
    chartEl.innerHTML = renderChart(state.series, yr);
  });

  bindPlace();
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
