import { FRANCE_PATHS } from './france-paths.js';
import { monthDay, dayMonthLabel, isoToDate } from '../lib/format.js';


const lonMin = -5.5;
const lonMax = 10.0;
const latMin = 41.0;
const latMax = 51.5;
const W_MAP = 492;
const H_MAP = 543;

const CITY_COORDS = {
  'Lille': { x: 301.0, y: 37.2 },
  'Amiens': { x: 268.0, y: 60.0 },
  'Brest': { x: 20.0, y: 151.6 },
  'Rennes': { x: 116.8, y: 161.2 },
  'Rouen': { x: 220.4, y: 109.8 },
  'Paris': { x: 274.5, y: 130.0 },
  'Reims': { x: 340.9, y: 117.0 },
  'Strasbourg': { x: 464.5, y: 126.5 },
  'Nantes': { x: 130.9, y: 244.8 },
  'Tours': { x: 208.7, y: 215.8 },
  'Dijon': { x: 364.8, y: 205.7 },
  'Limoges': { x: 228.7, y: 295.5 },
  'Lyon': { x: 344.9, y: 298.2 },
  'Bordeaux': { x: 156.6, y: 357.4 },
  'Toulouse': { x: 232.6, y: 458.7 },
  'Montpellier': { x: 309.8, y: 424.5 },
  'Marseille': { x: 388.9, y: 429.1 },
  'Nice': { x: 432.7, y: 432.0 },
  'Perpignan': { x: 278.0, y: 484.0 },
  'Ajaccio': { x: 448.0, y: 520.0 }
};

export function project(lat, lon, name) {
  if (name && CITY_COORDS[name]) {
    return CITY_COORDS[name];
  }
  const x = 37.03 * lon + 2.36 * lat + 73.4;
  const y = -1.48 * lon - 59.44 * lat + 3043.2;
  return { x, y };
}

function lerp(a, b, t) {
  return a + (b - a) * t;
}

export function getHeatColor(temp) {
  if (temp == null || Number.isNaN(temp)) return 'oklch(0.7 0.02 120)';

  const t = Math.max(5, Math.min(45, temp));

  const STOPS = [
    [10, [0.6, 0.15, 250]],   // Blue (<10)
    [20, [0.68, 0.16, 142]],  // Green (<20)
    [30, [0.72, 0.16, 72]],   // Orange (30)
    [40, [0.58, 0.22, 28]],   // Red (40)
    [45, [0.52, 0.21, 305]]   // Violet (>40)
  ];

  if (t <= STOPS[0][0]) {
    return `oklch(${(STOPS[0][1][0] * 100).toFixed(1)}% ${STOPS[0][1][1].toFixed(3)} ${STOPS[0][1][2].toFixed(1)})`;
  }
  if (t >= STOPS[STOPS.length - 1][0]) {
    return `oklch(${(STOPS[STOPS.length - 1][1][0] * 100).toFixed(1)}% ${STOPS[STOPS.length - 1][1][1].toFixed(3)} ${STOPS[STOPS.length - 1][1][2].toFixed(1)})`;
  }

  let lo = STOPS[0];
  let hi = STOPS[STOPS.length - 1];
  for (let i = 0; i < STOPS.length - 1; i++) {
    if (t >= STOPS[i][0] && t <= STOPS[i + 1][0]) {
      lo = STOPS[i];
      hi = STOPS[i + 1];
      break;
    }
  }

  const k = (t - lo[0]) / (hi[0] - lo[0]);
  const L = lerp(lo[1][0], hi[1][0], k);
  const C = lerp(lo[1][1], hi[1][1], k);

  let h1 = lo[1][2];
  let h2 = hi[1][2];
  if (Math.abs(h2 - h1) > 180) {
    if (h2 > h1) h1 += 360;
    else h2 += 360;
  }
  let H = lerp(h1, h2, k) % 360;
  if (H < 0) H += 360;

  return `oklch(${(L * 100).toFixed(1)}% ${C.toFixed(3)} ${H.toFixed(1)})`;
}

export function renderMapSVG(data, year) {
  // Generate visual heatmap blur glow circles
  const glowSpots = data.map((city) => {
    const { x, y } = project(city.lat, city.lon, city.name);
    if (city.tmax == null) return '';
    const color = getHeatColor(city.tmax);
    // Large blurred circles that overlap to form continuous heat zones
    return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="70" fill="${color}" opacity="0.32" filter="url(#blur-filter)" />`;
  }).join('');

  // Generate interactive city dots and labels
  const dots = data.map((city) => {
    const { x, y } = project(city.lat, city.lon, city.name);
    if (city.tmax == null) return '';
    const tempColor = getHeatColor(city.tmax);

    // Label position offsets for reference cities (so they don't overlap)
    const showLabel = ['Paris', 'Lille', 'Strasbourg', 'Brest', 'Lyon', 'Bordeaux', 'Marseille', 'Ajaccio'].includes(city.name);
    let labelX = x + 10;
    let labelY = y + 3;
    let textAnchor = 'start';

    if (city.name === 'Brest') {
      labelX = x - 10;
      textAnchor = 'end';
    } else if (city.name === 'Ajaccio') {
      labelX = x - 10;
      textAnchor = 'end';
    } else if (city.name === 'Lille') {
      labelY = y - 8;
      labelX = x;
      textAnchor = 'middle';
    }

    const label = showLabel
      ? `<text x="${labelX.toFixed(1)}" y="${labelY.toFixed(1)}" text-anchor="${textAnchor}" class="map__city-lbl">${city.name}</text>`
      : '';

    return `
      <g class="map__dot-group">
        <circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="7.5" fill="${tempColor}" class="map__dot">
          <title>${city.name} (${year}) : ${Math.round(city.tmax)}°C</title>
        </circle>
        <text x="${x.toFixed(1)}" y="${y.toFixed(1)}" class="map__temp-lbl">${Math.round(city.tmax)}</text>
        ${label}
      </g>
    `;
  }).join('');

  // Map contours and separator line
  const mainlandPaths = FRANCE_PATHS.slice(1);
  const separatorPath = FRANCE_PATHS[0];

  return `
    <svg viewBox="0 0 492 543" class="france-map__svg" role="img" aria-label="Carte des températures de la France en ${year}">
      <defs>
        <filter id="blur-filter" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="24" />
        </filter>
        <clipPath id="france-clip">
          ${mainlandPaths.map((d) => `<path d="${d}" />`).join('')}
        </clipPath>
      </defs>

      <!-- Base map regions under the heatmap -->
      <g class="map__regions-bg">
        ${mainlandPaths.map((d) => `<path d="${d}" fill="var(--color-bg-alt)" stroke="none" />`).join('')}
      </g>

      <!-- Visual Heatmap glow layer (clipped to France outline) -->
      <g clip-path="url(#france-clip)">
        ${glowSpots}
      </g>

      <!-- Regional borders overlaid on top of the heatmap -->
      <g class="map__regions-fg">
        ${mainlandPaths.map((d) => `<path d="${d}" fill="none" stroke="var(--color-line)" stroke-width="0.6" opacity="0.7" />`).join('')}
      </g>

      <!-- Corsica separation box line -->
      <path d="${separatorPath}" fill="none" stroke="var(--color-line-strong)" stroke-width="1.5" />

      <!-- City markers -->
      ${dots}
    </svg>
  `;
}

export function heatmapContainerHTML(state) {
  const yr = state.selectedYear;
  const showDualMaps =
    (state.mode === 'period' && state.dateSelected) ||
    (state.mode === 'day' && yr !== state.currentYear);
  const dayIso = state.selectedIso || state.todayIso;
  const dayMmdd = monthDay(isoToDate(dayIso));
  const dayLabel = dayMonthLabel(dayIso);

  if (!showDualMaps) {
    // Single map mode (Jour même)
    const data = state.heatmaps?.[`${yr}:${dayMmdd}`];
    const inner = data
      ? renderMapSVG(data, yr)
      : `<div class="map-placeholder"><div class="spinner"></div></div>`;

    return `
      <article class="heatmap-card">
        <h3 class="heatmap-card__title">Carte de France · le ${dayLabel}</h3>
        <p class="heatmap-card__sub">Zones thermiques et températures en ${yr}</p>
        <div class="france-map" data-role="france-map-container">
          ${inner}
        </div>
        <div class="map-legend">
          <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.6 0.15 250)"></i> &lt;10°</span>
          <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.68 0.16 142)"></i> &lt;20°</span>
          <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.72 0.16 72)"></i> 30°</span>
          <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.58 0.22 28)"></i> 40°</span>
          <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.52 0.21 305)"></i> &gt;40°</span>
        </div>
      </article>
    `;
  }

  // Dual map mode (Période)
  const currentData = state.heatmaps?.[`${state.currentYear}:${dayMmdd}`];
  const pastData = state.heatmaps?.[`${yr}:${dayMmdd}`];

  // Left map: Current Year (e.g. 2026)
  const leftMap = currentData
    ? renderMapSVG(currentData, state.currentYear)
    : `<div class="map-placeholder"><div class="spinner"></div></div>`;

  // Right map: Past Year (selectedYear)
  const rightMap = pastData
    ? renderMapSVG(pastData, yr)
    : `<div class="map-placeholder"><div class="spinner"></div></div>`;

  return `
    <article class="heatmap-card heatmap-card--wide">
      <h3 class="heatmap-card__title">Cartes de France · le ${dayLabel}</h3>
      <p class="heatmap-card__sub">Comparaison des zones thermiques et températures</p>
      
      <div class="france-maps france-maps--side" data-role="france-maps-container">
        <div class="france-map-col">
          <h4 class="france-map-col__title">${state.currentYear} (Actuelle)</h4>
          <div class="france-map" data-role="france-map-current">
            ${leftMap}
          </div>
        </div>
        <div class="france-map-col">
          <h4 class="france-map-col__title">${yr === state.currentYear ? 'Passée' : yr} (Passée)</h4>
          <div class="france-map" data-role="france-map-past">
            ${rightMap}
          </div>
        </div>
      </div>

      <div class="map-legend">
        <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.6 0.15 250)"></i> &lt;10°</span>
        <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.68 0.16 142)"></i> &lt;20°</span>
        <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.72 0.16 72)"></i> 30°</span>
        <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.58 0.22 28)"></i> 40°</span>
        <span class="map-legend__item"><i class="dot-sample" style="background:oklch(0.52 0.21 305)"></i> &gt;40°</span>
      </div>
    </article>
  `;
}
