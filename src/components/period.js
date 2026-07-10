// "Période" tab — the last N days this year vs the same N days X years ago.
// A 10-day-forecast-style strip, but backwards through the decades.
import { fmtTemp, fmtSigned, shortDate, weekdayShort, describeWeather } from '../lib/format.js';
import { mean, median } from '../lib/stats.js';

const W = 960;
const H = 280;
const PAD = { top: 24, right: 18, bottom: 30, left: 40 };
const plotW = W - PAD.left - PAD.right;
const plotH = H - PAD.top - PAD.bottom;

/** Align a window to exactly `len` columns, ending on the target day. */
function alignWindow(arr, len) {
  const out = new Array(len).fill(null);
  if (!arr || !arr.length) return out;
  for (let k = 0; k < len; k++) {
    const idx = arr.length - 1 - (len - 1 - k); // last element = target day
    out[k] = idx >= 0 ? arr[idx] : null;
  }
  return out;
}

function niceBounds(min, max) {
  const pad = Math.max(1.5, (max - min) * 0.12);
  return [Math.floor((min - pad) / 5) * 5, Math.ceil((max + pad) / 5) * 5];
}

function dualChart(recent, past, selectedYear, currentYear) {
  const all = [...recent, ...past].filter((r) => r && r.tmax != null).map((r) => r.tmax);
  if (all.length < 2) return '';
  const len = recent.length;
  const [lo, hi] = niceBounds(Math.min(...all), Math.max(...all));
  const xOf = (k) => PAD.left + (len === 1 ? plotW / 2 : (k / (len - 1)) * plotW);
  const yOf = (t) => PAD.top + (1 - (t - lo) / (hi - lo || 1)) * plotH;

  const line = (rows, cls) => {
    const pts = rows
      .map((r, k) => (r && r.tmax != null ? `${xOf(k).toFixed(1)},${yOf(r.tmax).toFixed(1)}` : null))
      .filter(Boolean)
      .join(' ');
    return `<polyline points="${pts}" class="${cls}"/>`;
  };

  const dots = (rows, cls, isPast) =>
    rows
      .map((r, k) => {
        if (!r || r.tmax == null) return '';
        const dStr = recent[k]?.date ?? past[k]?.date ?? '';
        const dateLabel = dStr ? shortDate(dStr) : '—';
        const tNow = recent[k]?.tmax != null ? Math.round(recent[k].tmax) + '°' : '—';
        const tPast = past[k]?.tmax != null ? Math.round(past[k].tmax) + '°' : '—';
        const titleText = isPast
          ? `${dateLabel} ${selectedYear} · ${Math.round(r.tmax)}° (vs ${tNow} cette année)`
          : `${dateLabel} ${currentYear} · ${Math.round(r.tmax)}° (vs ${tPast} en ${selectedYear})`;
        return `<circle cx="${xOf(k).toFixed(1)}" cy="${yOf(r.tmax).toFixed(1)}" r="3" class="${cls}"><title>${titleText}</title></circle>`;
      })
      .join('');

  // shaded band between the two series
  const top = [];
  const bot = [];
  for (let k = 0; k < len; k++) {
    if (recent[k]?.tmax == null || past[k]?.tmax == null) continue;
    top.push(`${xOf(k).toFixed(1)},${yOf(recent[k].tmax).toFixed(1)}`);
    bot.unshift(`${xOf(k).toFixed(1)},${yOf(past[k].tmax).toFixed(1)}`);
  }
  const band = top.length ? `<polygon points="${top.join(' ')} ${bot.join(' ')}" class="pband"/>` : '';

  // y gridlines
  let grid = '';
  for (let t = lo; t <= hi + 0.01; t += Math.max(5, Math.round((hi - lo) / 4 / 5) * 5) || 5) {
    const y = yOf(t);
    grid +=
      `<line x1="${PAD.left}" y1="${y.toFixed(1)}" x2="${W - PAD.right}" y2="${y.toFixed(1)}" class="pgrid"/>` +
      `<text x="${PAD.left - 8}" y="${(y + 4).toFixed(1)}" class="pax pax--y">${t}°</text>`;
  }
  // a few x labels
  let xlab = '';
  const step = Math.max(1, Math.round(len / 5));
  for (let k = 0; k < len; k += step) {
    xlab += `<text x="${xOf(k).toFixed(1)}" y="${H - 10}" class="pax pax--x">${shortDate(recent[k]?.date ?? '')}</text>`;
  }

  return `
    <svg class="chart" viewBox="0 0 ${W} ${H}" role="img"
         aria-label="Températures des ${len} derniers jours, cette année contre ${selectedYear}"
         preserveAspectRatio="xMidYMid meet">
      <style>
        .pgrid { stroke: var(--color-line); stroke-width: 1; }
        .pax { fill: var(--color-ink-faint); font: 500 12px var(--font-body); }
        .pax--y { text-anchor: end; }
        .pax--x { text-anchor: middle; }
        .pband { fill: var(--color-accent); opacity: 0.1; }
        .pl-now { fill: none; stroke: var(--color-ink); stroke-width: 2.6; stroke-linejoin: round; }
        .pl-past { fill: none; stroke: var(--color-accent); stroke-width: 2.6; stroke-linejoin: round; stroke-dasharray: 1 6; stroke-linecap: round; }
        .pd-now { fill: var(--color-ink); opacity: 0.8; transition: r var(--dur-fast) var(--ease), opacity var(--dur-fast) var(--ease); }
        .pd-past { fill: var(--color-accent); opacity: 0.8; transition: r var(--dur-fast) var(--ease), opacity var(--dur-fast) var(--ease); }
        .pd-now:hover, .pd-past:hover { r: 6.5; cursor: pointer; opacity: 1; }
      </style>
      ${grid}
      ${xlab}
      ${band}
      ${line(past, 'pl-past')}
      ${line(recent, 'pl-now')}
      ${dots(past, 'pd-past', true)}
      ${dots(recent, 'pd-now', false)}
    </svg>`;
}

function summaryStat(label, meanV, medV, dir) {
  return `<div class="psum__row"${dir ? ` data-dir="${dir}"` : ''}>
    <span class="psum__lab">${label}</span>
    <span class="psum__nums tabular">moy ${fmtTemp(meanV)} · méd ${fmtTemp(medV)}</span>
  </div>`;
}

function strip(recent, past, currentYear, selectedYear, selectedIso) {
  return recent
    .map((r, k) => {
      const p = past[k];
      const w = describeWeather(r?.code);
      const wPast = describeWeather(p?.code);
      const delta = r?.tmax != null && p?.tmax != null ? r.tmax - p.tmax : null;
      const dir = delta == null ? 'flat' : delta > 0.3 ? 'warm' : delta < -0.3 ? 'cold' : 'flat';
      const wDay = r?.date ? weekdayShort(r.date) : '—';
      const sDate = r?.date ? shortDate(r.date).split(' ')[0] : '—';
      const isActive = r?.date === selectedIso;
      return `<div class="pcol ${isActive ? 'pcol--active' : ''}" data-date="${r?.date || ''}" role="button" tabindex="0">
        <div class="pcol__day">${wDay}<br><b>${sDate}</b></div>
        
        <div class="pcol__group">
          <div class="pcol__year-lbl">${currentYear}</div>
          <div class="pcol__row">
            <span class="pcol__glyph" aria-hidden="true">${w.glyph}</span>
            <span class="pcol__now tabular">${fmtTemp(r?.tmax)}</span>
          </div>
          <div class="pcol__lo tabular">min ${fmtTemp(r?.tmin)}</div>
        </div>

        <div class="pcol__group">
          <div class="pcol__year-lbl">${selectedYear}</div>
          <div class="pcol__row">
            <span class="pcol__glyph pcol__glyph--past" aria-hidden="true">${wPast.glyph}</span>
            <span class="pcol__past tabular">${fmtTemp(p?.tmax)}</span>
          </div>
        </div>

        <div class="pcol__delta tabular" data-dir="${dir}">${delta == null ? '—' : fmtSigned(delta, 0) + '°'}</div>
      </div>`;
    })
    .join('');
}

/** Full "Période" panel for the current selectedYear + windowLen. */
export function periodHTML(state) {
  const len = state.windowLen;
  const recent = alignWindow(state.recent, len);
  const isCurrent = state.selectedYear === state.currentYear;
  const past = isCurrent ? recent : alignWindow(state.windows[state.selectedYear], len);

  const rMax = recent.map((r) => r?.tmax);
  const pMax = past.map((r) => r?.tmax);
  const rMean = mean(rMax);
  const pMean = mean(pMax);
  const dMean = rMean != null && pMean != null ? rMean - pMean : null;
  const dir = dMean == null ? 'flat' : dMean > 0.3 ? 'warm' : dMean < -0.3 ? 'cold' : 'flat';

  return `
    <div class="period">
      <div class="period__summary">
        <div class="psum">
          ${summaryStat(`Cette année (${state.currentYear})`, rMean, median(rMax), null)}
          ${summaryStat(`Même période ${state.selectedYear}`, pMean, median(pMax), null)}
        </div>
        <div class="psum__delta" data-dir="${dir}">
          <span class="tabular">${dMean == null ? '—' : fmtSigned(dMean) + '°'}</span>
          <span>d’écart moyen sur ${len} j</span>
        </div>
      </div>
      <div class="chart-wrap" data-role="period-chart">${dualChart(recent, past, state.selectedYear, state.currentYear)}</div>
      <div class="chart-legend">
        <span><i class="swatch" style="background:var(--color-ink)"></i> Ligne continue : ${state.currentYear} (actuelle)</span>
        <span><i class="swatch swatch--dashed" style="background:var(--color-accent)"></i> Ligne en pointillés : ${state.selectedYear} (passée)</span>
      </div>
      <div class="pstrip" role="list" aria-label="Jour par jour">${strip(recent, past, state.currentYear, state.selectedYear, state.selectedIso)}</div>
    </div>`;
}
