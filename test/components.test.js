import { describe, test, expect, beforeAll } from 'vitest';
import { renderChart } from '../src/components/chart.js';
import { periodHTML } from '../src/components/period.js';
import { project, getHeatColor, renderMapSVG, heatmapContainerHTML, preloadFrancePaths } from '../src/components/heatmap.js';

// maps render a placeholder until the lazy France-outline chunk is loaded
beforeAll(async () => {
  await preloadFrancePaths();
});
import { derive, focusHTML, machineContentHTML, viewApp } from '../src/components/views.js';
import { heatColor } from '../src/lib/color.js';

// ---------- fixtures ----------
const day = (date, tmax) => ({ date, tmax, tmin: tmax - 8, precip: 0, wind: 12, code: 1 });

function makeSeries() {
  const s = [];
  for (let y = 1990; y <= 2026; y++) s.push({ year: y, ...day(`${y}-07-10`, 20 + (y - 1990) * 0.05 + (y % 3)) });
  return s;
}

function windowOf(year, n = 12) {
  const rows = [];
  for (let d = n; d >= 1; d--) rows.push(day(`${year}-07-${String(11 - d).padStart(2, '0')}`, 22 + (d % 4)));
  return rows;
}

function makeState(overrides = {}) {
  const series = makeSeries();
  return {
    todayIso: '2026-07-10',
    selectedIso: '2026-07-10',
    dayLabel: '10 juillet',
    currentYear: 2026,
    selectedYear: 1976,
    mode: 'day',
    windowLen: 10,
    dateSelected: false,
    today: day('2026-07-10', 34),
    series,
    windows: { 1976: windowOf(1976), 2026: windowOf(2026) },
    recent: windowOf(2026, 30),
    heatmaps: {},
    location: { name: 'Paris', admin: 'Île-de-France', lat: 48.85, lon: 2.35 },
    ...overrides,
  };
}

// ---------- chart ----------
describe('renderChart', () => {
  test('needs at least two points', () => {
    expect(renderChart([{ year: 2000, tmax: 25 }], 2000)).toContain('Données insuffisantes');
  });

  test('renders an svg with a median line and the selected year', () => {
    const svg = renderChart(makeSeries(), 2001);
    expect(svg).toContain('<svg');
    expect(svg).toContain('médiane');
    expect(svg).toContain('>2001<'); // selected-year guide label
  });

  test('colors dots by absolute temperature (shared scale)', () => {
    const svg = renderChart([{ year: 2000, tmax: 40 }, { year: 2001, tmax: 8 }], 2000);
    expect(svg).toContain(heatColor(40));
    expect(svg).toContain(heatColor(8));
  });
});

// ---------- period ----------
describe('periodHTML', () => {
  test('shows mean and median for both years', () => {
    const html = periodHTML(makeState({ mode: 'period' }));
    expect(html).toContain('moy');
    expect(html).toContain('méd');
    expect(html).toContain('2026'); // current year label
    expect(html).toContain('1976'); // selected year label
  });

  test('renders the dual-line chart and the day strip', () => {
    const html = periodHTML(makeState({ mode: 'period' }));
    expect(html).toContain('<svg');
    expect(html).toContain('pstrip');
    expect(html).toContain('pcol');
  });

  test('when the selected year is the current year, deltas collapse to ~0', () => {
    const html = periodHTML(makeState({ mode: 'period', selectedYear: 2026 }));
    expect(html).not.toContain('-0.0°'); // fmtSigned must not emit a signed zero
  });

  test('metric toggle switches the plotted measure (temp/pluie/vent)', () => {
    const temp = periodHTML(makeState({ mode: 'period', periodMetric: 'tmax' }));
    expect(temp).toContain('data-metric="precip"');
    expect(temp).toContain('data-metric="wind"');
    expect(temp).toMatch(/data-metric="tmax" aria-pressed="true"/);

    const wind = periodHTML(makeState({ mode: 'period', periodMetric: 'wind' }));
    expect(wind).toMatch(/data-metric="wind" aria-pressed="true"/);
    expect(wind).toContain('km/h'); // summary uses the wind formatter
  });
});

// ---------- heatmap ----------
describe('heatmap', () => {
  test('project uses known city coords, else the linear formula', () => {
    expect(project(48.85, 2.35, 'Paris')).toEqual({ x: 274.5, y: 130.0 });
    const p = project(45, 5, 'Nowhere');
    expect(p.x).toBeCloseTo(37.03 * 5 + 2.36 * 45 + 73.4, 1);
  });

  test('getHeatColor delegates to the shared scale', () => {
    expect(getHeatColor(30)).toBe(heatColor(30));
  });

  test('renderMapSVG draws city dots and skips null temps', () => {
    const data = [
      { name: 'Paris', lat: 48.85, lon: 2.35, tmax: 30, code: 1 },
      { name: 'Brest', lat: 48.39, lon: -4.48, tmax: null, code: 1 },
    ];
    const svg = renderMapSVG(data, 2026);
    expect(svg).toContain('<svg');
    expect(svg).toContain('Paris (2026)');
    expect(svg).not.toContain('Brest (2026)'); // null temp -> no marker
  });

  test('renderMapSVG anomaly mode shows the signed delta vs a reference year', () => {
    const data = [{ name: 'Paris', lat: 48.85, lon: 2.35, tmax: 30, code: 1 }];
    const svg = renderMapSVG(data, 2026, { ref: { Paris: 22 }, refYear: 1976 });
    expect(svg).toContain('+8'); // 30 - 22 = +8 shown on the dot
    expect(svg).toContain('vs 1976');
  });

  test('dual maps expose an Absolu/Écart toggle', () => {
    const dual = heatmapContainerHTML(
      makeState({
        mode: 'period',
        dateSelected: true,
        selectedYear: 1976,
        heatmaps: {
          '2026:07-10': [{ name: 'Paris', lat: 48.85, lon: 2.35, tmax: 30, code: 1 }],
          '1976:07-10': [{ name: 'Paris', lat: 48.85, lon: 2.35, tmax: 22, code: 1 }],
        },
        selectedIso: '2026-07-10',
      }),
    );
    expect(dual).toContain('data-mapmode="abs"');
    expect(dual).toContain('data-mapmode="anom"');
  });

  test('single map in day mode, dual maps when a date is selected in période', () => {
    const single = heatmapContainerHTML(makeState({ mode: 'day', selectedYear: 2026 }));
    expect(single).not.toContain('heatmap-card--wide');

    const dual = heatmapContainerHTML(makeState({ mode: 'period', dateSelected: true }));
    expect(dual).toContain('heatmap-card--wide');
    expect(dual).toContain('(Actuelle)');
  });
});

// ---------- views ----------
describe('views', () => {
  test('derive exposes baseline mean+median and the climate signal', () => {
    const d = derive(makeState());
    expect(typeof d.baseline).toBe('number');
    expect(typeof d.baselineMed).toBe('number');
    expect(typeof d.climateRise).toBe('number');
    expect(d.firstYear).toBe(1990);
    expect(d.lastYear).toBe(2026);
  });

  test('focusHTML shows the year and its delta vs today', () => {
    const state = makeState();
    const html = focusHTML(state, derive(state), 1976);
    expect(html).toContain('focus__year');
    expect(html).toContain('1976');
    expect(html).toContain('focus__delta');
  });

  test('machineContentHTML swaps day focus vs période panel', () => {
    const dayState = makeState({ mode: 'day', selectedYear: 2026 });
    expect(machineContentHTML(dayState, derive(dayState))).toContain('data-role="focus"');

    const periodState = makeState({ mode: 'period', dateSelected: true });
    expect(machineContentHTML(periodState, derive(periodState))).toContain('pstrip');
  });

  test('viewApp renders the full shell without throwing', () => {
    const html = viewApp(makeState());
    expect(html).toContain('brand__mark'); // brand present
    expect(html).toContain('data-role="slider"');
  });
});
