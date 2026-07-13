// HTML view builders for each app state. Pure string functions + small derive helpers.
import {
  dayMonthLabel, fmtTemp, fmtSigned, fmtMm, fmtWind, describeWeather,
} from '../lib/format.js';
import { baselineMean, baselineMedian, linearFit } from '../lib/stats.js';
import { heatColor, heatGradient } from '../lib/color.js';
import { renderChart } from './chart.js';
import { periodHTML } from './period.js';
import { heatmapContainerHTML } from './heatmap.js';
import { politicsHTML } from './politics.js';

export function viewLoading(msg = 'Localisation en cours…') {
  return `<div class="state" role="status">
    <div class="spinner" aria-hidden="true"></div>
    <p class="state__msg">${msg}</p>
  </div>`;
}

export function viewError(msg, { retry = true } = {}) {
  return `<div class="state" role="alert">
    <h2 class="state__title">Un contretemps météo</h2>
    <p class="state__msg">${msg}</p>
    ${retry ? '<button class="btn" data-action="retry">Réessayer</button>' : ''}
  </div>`;
}

// ---- derived model shared by hero + focus ----
export function derive(state) {
  const { series, today } = state;
  const hasHistory = series && series.length > 0;

  const temps = hasHistory ? series.filter((d) => d.tmax != null).map((d) => d.tmax) : [];
  const loT = temps.length > 0 ? Math.min(...temps, today.tmax ?? Infinity) : today.tmax;
  const hiT = temps.length > 0 ? Math.max(...temps, today.tmax ?? -Infinity) : today.tmax;
  const baseline = hasHistory ? baselineMean(series, 30) : null;
  const baselineMed = hasHistory ? baselineMedian(series, 30) : null;
  const fit = hasHistory ? linearFit(series.map((d) => ({ x: d.year, y: d.tmax }))) : null;
  const perDecade = fit ? fit.slope * 10 : null;
  const firstYear = hasHistory ? series[0]?.year : 1940;
  const lastYear = hasHistory ? series[series.length - 1]?.year : new Date().getFullYear();
  // climate signal = trend-line rise across the whole record (not today's weather noise)
  const climateRise = fit ? fit.predict(lastYear) - fit.predict(firstYear) : null;
  // daily anomaly = today's max vs the 30-yr normal for this calendar day (weather, not climate)
  const vsNormal = baseline != null && today.tmax != null ? today.tmax - baseline : null;
  return { loT, hiT, baseline, baselineMed, fit, perDecade, climateRise, vsNormal, firstYear, lastYear };
}

function metric(k, v) {
  return `<div class="metric"><div class="metric__k">${k}</div><div class="metric__v tabular">${v}</div></div>`;
}

// ---- hero (today + 10 years ago + warming trend) ----
function heroHTML(state, d) {
  const { today, dayLabel } = state;
  const w = describeWeather(today.code);
  const glow = heatColor(today.tmax);
  const dir = d.climateRise == null ? 'flat' : d.climateRise > 0.3 ? 'warm' : d.climateRise < -0.3 ? 'cold' : 'flat';

  const climateTxt =
    d.climateRise == null
      ? 'Historique en cours de lecture.'
      : `Depuis ${d.firstYear}, le ${dayLabel} s’est ${d.climateRise >= 0 ? 'réchauffé' : 'refroidi'} de ${fmtSigned(d.climateRise)}° selon la tendance de fond.`;
  const decadeTxt = d.perDecade != null ? `Soit ${fmtSigned(d.perDecade)}° par décennie.` : '';

  // Get data for exactly 10 years ago (e.g. 2016)
  const targetYear = state.currentYear - 10;
  const tenYearsAgoData = state.series
    ? state.series.find((s) => s.year === targetYear)
    : null;

  let deltaPastStr = '';
  let wPast = null;
  if (tenYearsAgoData && tenYearsAgoData.tmax != null && today.tmax != null) {
    wPast = describeWeather(tenYearsAgoData.code);
    const diff = today.tmax - tenYearsAgoData.tmax;
    deltaPastStr = `${fmtSigned(diff, 1)}° vs aujourd'hui`;
  }

  return `
  <section class="hero-vignettes" aria-label="Aperçu météo et réchauffement">
    <!-- Vignette 1: Aujourd'hui -->
    <article class="vignette vignette--today reveal" style="--today-glow:${glow}">
      <div class="vignette__glow" aria-hidden="true"></div>
      <p class="vignette__label">Aujourd’hui · ${dayLabel}</p>
      <p class="vignette__date">${w.glyph} ${w.label}</p>
      <div class="vignette__temp tabular">${fmtTemp(today.tmax, false)}<sup>°C</sup></div>
      <div class="vignette__meta">
        ${metric('Min', fmtTemp(today.tmin))}
        ${metric('Max', fmtTemp(today.tmax))}
        ${metric('Pluie', fmtMm(today.precip))}
        ${metric('Vent', fmtWind(today.wind))}
      </div>
    </article>

    <!-- Vignette 2: Il y a 10 ans -->
    <article class="vignette vignette--past reveal">
      <p class="vignette__label">Il y a 10 ans · ${targetYear}</p>
      ${tenYearsAgoData && wPast
        ? `
          <p class="vignette__date">${wPast.glyph} ${wPast.label}</p>
          <div class="vignette__temp tabular">${fmtTemp(tenYearsAgoData.tmax, false)}<sup>°C</sup></div>
          <p class="vignette__cond" data-dir="${today.tmax - tenYearsAgoData.tmax > 0.3 ? 'warm' : today.tmax - tenYearsAgoData.tmax < -0.3 ? 'cold' : 'flat'}">${deltaPastStr}</p>
          <div class="vignette__meta">
            ${metric('Min', fmtTemp(tenYearsAgoData.tmin))}
            ${metric('Max', fmtTemp(tenYearsAgoData.tmax))}
            ${metric('Pluie', fmtMm(tenYearsAgoData.precip))}
            ${metric('Vent', fmtWind(tenYearsAgoData.wind))}
          </div>
        `
        : `
          <div class="vignette__loading">
            <div class="spinner spinner--sm"></div>
            <p class="muted">Chargement de l'historique...</p>
          </div>
        `
      }
    </article>

    <!-- Vignette 3: Réchauffement de ce jour -->
    <article class="vignette vignette--verdict reveal">
      <p class="vignette__label">Réchauffement de ce jour</p>
      <p class="vignette__big" data-dir="${dir}">${d.climateRise == null ? '—' : fmtSigned(d.climateRise) + '°'}</p>
      <p class="vignette__sub">${climateTxt} ${decadeTxt}</p>
      ${
        d.baseline == null
          ? ''
          : `<p class="vignette__norm">Normale ${d.firstYear}–${d.firstYear + 29} · moyenne <b>${fmtTemp(d.baseline)}</b></p>`
      }
    </article>
  </section>`;
}

// ---- machine section content (swaps between Jour même / Période) ----
export function machineContentHTML(state, d) {
  if (state.mode === 'politics') {
    return politicsHTML(state);
  }

  const heatmap = heatmapContainerHTML(state);
  const showDualMaps =
    (state.mode === 'period' && state.dateSelected) ||
    (state.mode === 'day' && state.selectedYear !== state.currentYear);

  const content = state.mode === 'period'
    ? periodHTML(state)
    : `<article class="focus" data-role="focus">${focusHTML(state, d, state.selectedYear)}</article>`;

  if (showDualMaps) {
    return `
      <div class="machine-layout machine-layout--stacked">
        <div class="machine-layout__top">${heatmap}</div>
        <div class="machine-layout__main">${content}</div>
      </div>
    `;
  }

  return `
    <div class="machine-layout">
      <div class="machine-layout__main">${content}</div>
      <div class="machine-layout__side">${heatmap}</div>
    </div>
  `;
}

// ---- focus card for a given year ----
export function focusHTML(state, d, year) {
  const rec = (state.series && state.series.find((s) => s.year === year)) ?? { year };
  const w = describeWeather(rec.code);
  const ago = state.currentYear - year;
  const delta = rec.tmax != null && state.today.tmax != null ? rec.tmax - state.today.tmax : null;
  const dir = delta == null ? 'flat' : delta > 0.3 ? 'warm' : delta < -0.3 ? 'cold' : 'flat';
  const agoTxt = ago === 0 ? 'cette année' : ago === 1 ? 'il y a 1 an' : `il y a ${ago} ans`;

  return `
    <div class="focus__year tabular">${year}</div>
    <div class="focus__ago">${agoTxt} · ${state.dayLabel}</div>
    <div class="focus__temp tabular">${w.glyph} ${fmtTemp(rec.tmax)}</div>
    ${
      ago === 0
        ? ''
        : delta == null
          ? '<div class="focus__delta" data-dir="flat">donnée manquante</div>'
          : `<div class="focus__delta" data-dir="${dir}">
               <span class="tabular">${fmtSigned(delta)}°</span>
               <span>vs aujourd’hui</span>
             </div>`
    }
    <div class="focus__grid">
      ${metric('Min', fmtTemp(rec.tmin))}
      ${metric('Max', fmtTemp(rec.tmax))}
      ${metric('Pluie', fmtMm(rec.precip))}
      ${metric('Vent', fmtWind(rec.wind))}
    </div>`;
}

// ---- full loaded app ----
export function viewApp(state) {
  const d = derive(state);
  const sel = state.selectedYear;

  return `
  <main class="shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand__mark">Météo<em>Évolution</em></span>
        <span class="brand__tag">France · depuis 1940</span>
      </div>
      <nav class="top-nav" role="navigation" aria-label="Menu principal">
        <button class="top-nav__btn ${state.mode !== 'politics' ? 'top-nav__btn--active' : ''}" data-nav="climat">Climat 🌍</button>
        <button class="top-nav__btn ${state.mode === 'politics' ? 'top-nav__btn--active' : ''}" data-nav="politics">Loi 📜</button>
      </nav>
      <div class="place">
        <button class="place__btn" data-action="toggle-search" aria-haspopup="true" aria-expanded="false">
          <span class="place__pin" aria-hidden="true"></span>
          <span data-role="place-name">${state.location.name}</span>
        </button>
        <div class="place__search" data-role="search-panel" role="dialog" aria-label="Choisir un lieu">
          <input class="place__input" type="search" placeholder="Chercher une commune…"
                 data-role="search-input" autocomplete="off" />
          <ul class="place__results" data-role="search-results"></ul>
          <button class="place__geo" data-action="use-geo">↳ Utiliser ma position</button>
        </div>
      </div>
    </header>

    ${state.mode === 'politics' ? '' : heroHTML(state, d)}

    <section class="section" aria-label="Machine à remonter le temps">
      <div class="section__head" ${state.mode === 'politics' ? 'hidden style="display:none;"' : ''}>
        <h2 class="section__title reveal">Remontez le temps, un curseur à la main</h2>
        <p class="section__note reveal">Comparez à aujourd’hui&nbsp;: soit le <b>jour même</b> ${state.dayLabel}, soit une <b>période</b> — les derniers jours contre les mêmes jours d’une année passée.</p>
      </div>

      <div class="tabs reveal" role="tablist" aria-label="Mode de comparaison" ${state.mode === 'politics' ? 'hidden style="display:none;"' : ''}>
        <button class="tab" role="tab" id="tab-day" aria-controls="machine-panel" data-tab="day"
                aria-selected="${state.mode === 'day'}" tabindex="${state.mode === 'day' ? '0' : '-1'}">Jour même</button>
        <button class="tab" role="tab" id="tab-period" aria-controls="machine-panel" data-tab="period"
                aria-selected="${state.mode === 'period'}" tabindex="${state.mode === 'period' ? '0' : '-1'}"
                ${state.historyLoaded ? '' : 'disabled style="opacity: 0.6; cursor: not-allowed;"'}>Période ${state.historyLoaded ? '' : '(Chargement...)'}</button>
      </div>

      <div class="rail rail--bar reveal" ${state.mode === 'politics' ? 'hidden' : ''}>
        <div class="rail__val">
          <span class="rail__now tabular" data-role="rail-year">${sel}</span>
          <span class="rail__hint">glissez pour changer d’année</span>
        </div>
        <input class="slider" type="range" min="${d.firstYear}" max="${d.lastYear}" step="1"
               value="${sel}" data-role="slider" ${state.historyLoaded ? '' : 'disabled'}
               aria-label="Année" aria-valuemin="${d.firstYear}" aria-valuemax="${d.lastYear}" aria-valuenow="${sel}" />
        <div class="rail__scale">
          <span>${d.firstYear}</span><span>${Math.round((d.firstYear + d.lastYear) / 2)}</span><span>${d.lastYear}</span>
        </div>
        <div class="chips" data-role="window-chips" role="group" aria-label="Longueur de la période"${state.mode === 'period' ? '' : ' hidden'}>
          <span class="chips__lab">Période&nbsp;:</span>
          ${[5, 10, 30]
            .map(
              (n) =>
                `<button class="chip" data-win="${n}" aria-pressed="${state.windowLen === n}">${n} jours</button>`,
            )
            .join('')}
        </div>
      </div>

      <div class="machine__content reveal" data-role="machine-content" id="machine-panel"
           role="tabpanel" aria-labelledby="tab-${state.mode}">${machineContentHTML(state, d)}</div>
    </section>

    <section class="section" aria-label="Tendance sur les décennies" ${state.mode === 'politics' ? 'hidden' : ''}>
      <div class="section__head">
        <h2 class="section__title reveal">La courbe du réchauffement, un jour à la fois</h2>
        <p class="section__note reveal">Température maximale du ${state.dayLabel}, chaque année. La ligne pointillée est la tendance de fond.</p>
      </div>
      <div class="chart-card reveal">
        <div class="chart-wrap" data-role="chart">
          ${state.historyLoaded
            ? renderChart(state.series, sel)
            : `<div class="chart-loading"><div class="spinner"></div><p>Chargement des 85 ans d'historique...</p></div>`}
        </div>
        <div class="chart-legend">
          <span><i class="scale-bar" style="background:${heatGradient()}"></i> froid → chaud (°C)</span>
          <span><i class="swatch" style="background:var(--color-accent)"></i> tendance longue durée</span>
          <span><i class="swatch" style="background:var(--color-ink-soft);opacity:.6"></i> médiane</span>
        </div>
      </div>
    </section>

    <footer class="foot">
      <span>Données&nbsp;: <a href="https://open-meteo.com" target="_blank" rel="noopener">Open-Meteo</a> · réanalyse ERA5 &amp; prévision</span>
      <span>${state.location.name}, ${state.location.admin} · ${state.location.lat.toFixed(2)}, ${state.location.lon.toFixed(2)}</span>
    </footer>
  </main>`;
}
