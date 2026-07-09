// HTML view builders for each app state. Pure string functions + small derive helpers.
import {
  dayMonthLabel, fmtTemp, fmtSigned, fmtMm, fmtWind, describeWeather,
} from '../lib/format.js';
import { baselineMean, linearFit } from '../lib/stats.js';
import { rampColor, normalize } from '../lib/color.js';
import { renderChart } from './chart.js';

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
  const temps = series.filter((d) => d.tmax != null).map((d) => d.tmax);
  const loT = Math.min(...temps, today.tmax ?? Infinity);
  const hiT = Math.max(...temps, today.tmax ?? -Infinity);
  const baseline = baselineMean(series, 30);
  const fit = linearFit(series.map((d) => ({ x: d.year, y: d.tmax })));
  const perDecade = fit ? fit.slope * 10 : null;
  const firstYear = series[0]?.year;
  const lastYear = series[series.length - 1]?.year;
  // climate signal = trend-line rise across the whole record (not today's weather noise)
  const climateRise = fit ? fit.predict(lastYear) - fit.predict(firstYear) : null;
  // daily anomaly = today's max vs the 30-yr normal for this calendar day (weather, not climate)
  const vsNormal = baseline != null && today.tmax != null ? today.tmax - baseline : null;
  return { loT, hiT, baseline, fit, perDecade, climateRise, vsNormal, firstYear, lastYear };
}

function metric(k, v) {
  return `<div class="metric"><div class="metric__k">${k}</div><div class="metric__v tabular">${v}</div></div>`;
}

// ---- hero (today + verdict) ----
function heroHTML(state, d) {
  const { today, dayLabel } = state;
  const w = describeWeather(today.code);
  const tNorm = normalize(today.tmax, d.loT, d.hiT);
  const glow = rampColor(tNorm);
  const dir = d.climateRise == null ? 'flat' : d.climateRise > 0.3 ? 'warm' : d.climateRise < -0.3 ? 'cold' : 'flat';

  const climateTxt =
    d.climateRise == null
      ? 'Historique en cours de lecture.'
      : `Depuis ${d.firstYear}, le ${dayLabel} s’est ${d.climateRise >= 0 ? 'réchauffé' : 'refroidi'} de ${fmtSigned(d.climateRise)}° selon la tendance de fond.`;
  const decadeTxt = d.perDecade != null ? `Soit ${fmtSigned(d.perDecade)}° par décennie.` : '';

  const anomTag =
    d.vsNormal == null
      ? ''
      : `<span class="today__anom" data-dir="${d.vsNormal > 0.3 ? 'warm' : d.vsNormal < -0.3 ? 'cold' : 'flat'}">
           ${fmtSigned(d.vsNormal)}° vs la normale du jour</span>`;

  return `
  <section class="hero" aria-label="Conditions du jour">
    <article class="today reveal" style="--today-glow:${glow}">
      <div class="today__glow" aria-hidden="true"></div>
      <p class="today__label">Aujourd’hui · ${dayLabel}</p>
      <p class="today__date">${w.glyph} ${w.label}</p>
      <div class="today__temp tabular">${fmtTemp(today.tmax, false)}<sup>°C</sup></div>
      <p class="today__cond">Mini ${fmtTemp(today.tmin)} ${anomTag}</p>
      <div class="today__meta">
        ${metric('Min', fmtTemp(today.tmin))}
        ${metric('Max', fmtTemp(today.tmax))}
        ${metric('Pluie', fmtMm(today.precip))}
        ${metric('Vent', fmtWind(today.wind))}
      </div>
    </article>
    <aside class="verdict reveal">
      <p class="verdict__ey">Le réchauffement de ce jour</p>
      <p class="verdict__big" data-dir="${dir}">${d.climateRise == null ? '—' : fmtSigned(d.climateRise) + '°'}</p>
      <p class="verdict__sub">${climateTxt} ${decadeTxt}</p>
    </aside>
  </section>`;
}

// ---- focus card for a given year ----
export function focusHTML(state, d, year) {
  const rec = state.series.find((s) => s.year === year) ?? { year };
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
      delta == null
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

    ${heroHTML(state, d)}

    <section class="section" aria-label="Machine à remonter le temps">
      <div class="section__head">
        <h2 class="section__title reveal">Le même jour, remonté année après année</h2>
        <p class="section__note reveal">Glissez pour voyager de ${d.firstYear} à ${d.lastYear}. La météo du ${state.dayLabel} de chaque année, comparée à aujourd’hui.</p>
      </div>
      <div class="machine">
        <article class="focus reveal" data-role="focus">${focusHTML(state, d, sel)}</article>
        <div class="rail reveal">
          <div class="rail__val">
            <span class="rail__now tabular" data-role="rail-year">${sel}</span>
            <span class="rail__hint">glissez le curseur</span>
          </div>
          <input class="slider" type="range" min="${d.firstYear}" max="${d.lastYear}" step="1"
                 value="${sel}" data-role="slider"
                 aria-label="Année" aria-valuemin="${d.firstYear}" aria-valuemax="${d.lastYear}" />
          <div class="rail__scale">
            <span>${d.firstYear}</span><span>${Math.round((d.firstYear + d.lastYear) / 2)}</span><span>${d.lastYear}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="section" aria-label="Tendance sur les décennies">
      <div class="section__head">
        <h2 class="section__title reveal">La courbe du réchauffement, un jour à la fois</h2>
        <p class="section__note reveal">Température maximale du ${state.dayLabel}, chaque année. La ligne pointillée est la tendance de fond.</p>
      </div>
      <div class="chart-card reveal">
        <div class="chart-wrap" data-role="chart">${renderChart(state.series, sel)}</div>
        <div class="chart-legend">
          <span><i class="dot-sample" style="background:${rampColor(0.15)}"></i> jour plus frais</span>
          <span><i class="dot-sample" style="background:${rampColor(0.9)}"></i> jour plus chaud</span>
          <span><i class="swatch" style="background:var(--color-accent)"></i> tendance longue durée</span>
        </div>
      </div>
    </section>

    <footer class="foot">
      <span>Données&nbsp;: <a href="https://open-meteo.com" target="_blank" rel="noopener">Open-Meteo</a> · réanalyse ERA5 &amp; prévision</span>
      <span>${state.location.name}, ${state.location.admin} · ${state.location.lat.toFixed(2)}, ${state.location.lon.toFixed(2)}</span>
    </footer>
  </main>`;
}
