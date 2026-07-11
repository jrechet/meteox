// Hand-rolled SVG trend chart — tmax of one calendar day across the decades.
import { heatColor } from '../lib/color.js';
import { linearFit, median } from '../lib/stats.js';

const W = 960;
const H = 380;
const PAD = { top: 28, right: 24, bottom: 40, left: 44 };

const plotW = W - PAD.left - PAD.right;
const plotH = H - PAD.top - PAD.bottom;

function niceBounds(min, max) {
  const pad = Math.max(1.5, (max - min) * 0.12);
  const lo = Math.floor((min - pad) / 5) * 5;
  const hi = Math.ceil((max + pad) / 5) * 5;
  return [lo, hi];
}

/**
 * @param {{year:number,tmax:number}[]} series ascending
 * @param {number} selectedYear
 * @returns {string} svg markup
 */
export function renderChart(series, selectedYear) {
  const pts = series.filter((d) => d.tmax != null);
  if (pts.length < 2) return '<p class="section__note">Données insuffisantes pour tracer la tendance.</p>';

  const years = pts.map((d) => d.year);
  const temps = pts.map((d) => d.tmax);
  const minYear = years[0];
  const maxYear = years[years.length - 1];
  const [loT, hiT] = niceBounds(Math.min(...temps), Math.max(...temps));

  const xOf = (yr) => PAD.left + ((yr - minYear) / (maxYear - minYear || 1)) * plotW;
  const yOf = (t) => PAD.top + (1 - (t - loT) / (hiT - loT || 1)) * plotH;

  const fit = linearFit(pts.map((d) => ({ x: d.year, y: d.tmax })));

  // --- gridlines + y labels ---
  const yTicks = [];
  const stepT = Math.max(5, Math.round((hiT - loT) / 5 / 5) * 5) || 5;
  for (let t = loT; t <= hiT + 0.01; t += stepT) {
    const y = yOf(t);
    yTicks.push(
      `<line x1="${PAD.left}" y1="${y.toFixed(1)}" x2="${W - PAD.right}" y2="${y.toFixed(1)}" class="grid"/>` +
        `<text x="${PAD.left - 10}" y="${(y + 4).toFixed(1)}" class="ax ax--y">${t}°</text>`,
    );
  }

  // --- x labels (decades) ---
  const xTicks = [];
  for (let yr = Math.ceil(minYear / 10) * 10; yr <= maxYear; yr += 10) {
    const x = xOf(yr);
    xTicks.push(`<text x="${x.toFixed(1)}" y="${H - 12}" class="ax ax--x">${yr}</text>`);
  }

  // --- median reference (robust "normal") ---
  const med = median(temps);
  let medLine = '';
  if (med != null) {
    const y = yOf(med);
    medLine =
      `<line x1="${PAD.left}" y1="${y.toFixed(1)}" x2="${W - PAD.right}" y2="${y.toFixed(1)}" class="median"/>` +
      `<text x="${W - PAD.right}" y="${(y - 6).toFixed(1)}" class="ax ax--med">médiane ${Math.round(med)}°</text>`;
  }

  // --- regression line ---
  let trendLine = '';
  if (fit) {
    const x1 = xOf(minYear);
    const y1 = yOf(fit.predict(minYear));
    const x2 = xOf(maxYear);
    const y2 = yOf(fit.predict(maxYear));
    trendLine = `<line x1="${x1.toFixed(1)}" y1="${y1.toFixed(1)}" x2="${x2.toFixed(1)}" y2="${y2.toFixed(1)}" class="trend"/>`;
  }

  // --- connecting soft line ---
  const path = pts
    .map((d, i) => `${i ? 'L' : 'M'}${xOf(d.year).toFixed(1)},${yOf(d.tmax).toFixed(1)}`)
    .join(' ');

  // --- dots colored by temperature ---
  const dots = pts
    .map((d) => {
      const cx = xOf(d.year);
      const cy = yOf(d.tmax);
      const col = heatColor(d.tmax);
      const sel = d.year === selectedYear;
      return (
        `<circle cx="${cx.toFixed(1)}" cy="${cy.toFixed(1)}" r="${sel ? 7 : 3.2}" ` +
        `fill="${col}" ${sel ? 'stroke="var(--color-ink)" stroke-width="2.5"' : 'opacity="0.85"'}>` +
        `<title>${d.year} · ${Math.round(d.tmax)}°</title></circle>`
      );
    })
    .join('');

  // selected vertical guide
  const selPt = pts.find((d) => d.year === selectedYear);
  let guide = '';
  if (selPt) {
    const x = xOf(selectedYear);
    guide =
      `<line x1="${x.toFixed(1)}" y1="${PAD.top}" x2="${x.toFixed(1)}" y2="${H - PAD.bottom}" class="guide"/>` +
      `<text x="${x.toFixed(1)}" y="${PAD.top - 12}" class="ax ax--sel">${selectedYear}</text>`;
  }

  return `
    <svg class="chart" viewBox="0 0 ${W} ${H}" role="img"
         aria-label="Température maximale de ce jour, ${minYear} à ${maxYear}"
         preserveAspectRatio="xMidYMid meet">
      <style>
        .grid { stroke: var(--color-line); stroke-width: 1; }
        .ax { fill: var(--color-ink-faint); font: 500 13px var(--font-body); }
        .ax--y { text-anchor: end; }
        .ax--x { text-anchor: middle; }
        .ax--sel { text-anchor: middle; fill: var(--color-ink); font-weight: 600; }
        .trend { stroke: var(--color-accent); stroke-width: 2.5; stroke-dasharray: 2 7; stroke-linecap: round; }
        .median { stroke: var(--color-ink-soft); stroke-width: 1.5; opacity: 0.5; }
        .ax--med { text-anchor: end; fill: var(--color-ink-soft); font-weight: 600; }
        .guide { stroke: var(--color-line-strong); stroke-width: 1.5; stroke-dasharray: 3 4; }
        .spark { fill: none; stroke: var(--color-ink-soft); stroke-width: 1.4; opacity: 0.35; }
      </style>
      ${yTicks.join('')}
      ${xTicks.join('')}
      ${guide}
      <path d="${path}" class="spark"/>
      ${medLine}
      ${trendLine}
      ${dots}
    </svg>`;
}
