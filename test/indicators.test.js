// Transparence des indicateurs (issue #4) : dépliant « Pourquoi ces scores ? » — client
// mémoïsé, rendu des justifications citées (IA relue ou éditorial), repli honnête hors ligne.
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { politicsHTML, indicatorsWhyBodyHTML } from '../src/components/politics.js';
import snapshot from '../src/data/laws-snapshot.json';

beforeEach(() => {
  vi.unstubAllGlobals();
});

const API_PAYLOAD = {
  lawId: 'eco-agri-1',
  methodology: 'https://github.com/jrechet/meteox/blob/main/docs/methodologie-indicateurs.md',
  indicators: [
    {
      indicator: 'pesticides',
      score: 1,
      status: 'published',
      model: 'claude-api:claude-sonnet-5',
      justification: 'Le texte restreint la fabrication des produits contenant des PFAS.',
      citation: 'restreindre la fabrication et la vente de produits contenant des PFAS',
      confidence: 'haute',
      reviewedBy: 'jrechet',
    },
    { indicator: 'peupleSante', score: 1.5, status: 'published' },
  ],
};

describe('politicsHTML — dépliant par carte', () => {
  test('every passed card carries a « Pourquoi ces scores ? » details bound to its law', () => {
    const html = politicsHTML({
      lawFilter: 'all',
      laws: snapshot.laws,
      lawsMeta: { source: 'api', dataDate: '2026-07-17T06:00:00Z' },
    });
    const passed = snapshot.laws.filter((l) => l.status === 'passed');
    expect((html.match(/indicators-why__summary/g) || []).length).toBe(passed.length);
    for (const l of passed) {
      expect(html).toContain(`data-law-id="${l.id}"`);
    }
  });
});

describe('indicatorsWhyBodyHTML', () => {
  test('renders justification, exact citation, confidence and review provenance', () => {
    const html = indicatorsWhyBodyHTML(API_PAYLOAD);
    expect(html).toContain('Pesticides');
    expect(html).toContain('restreint la fabrication');
    expect(html).toContain('« restreindre la fabrication');
    expect(html).toContain('confiance haute');
    expect(html).toContain('relue et validée par jrechet');
    expect(html).toContain('Méthodologie complète');
  });

  test('labels editorial scores honestly when no model/justification exists', () => {
    const html = indicatorsWhyBodyHTML(API_PAYLOAD);
    expect(html).toContain('Score éditorial vérifié (relecture humaine)');
  });

  test('escapes API-provided text before innerHTML injection (XSS)', () => {
    const html = indicatorsWhyBodyHTML({
      methodology: 'https://example.org/"><script>alert(1)</script>',
      indicators: [
        {
          indicator: 'pesticides',
          justification: '<img src=x onerror=alert(1)>',
          citation: '</blockquote><script>alert(2)</script>',
        },
      ],
    });
    expect(html).not.toContain('<script>');
    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;img');
  });

  test('falls back honestly with a methodology link when the API is unreachable', () => {
    const html = indicatorsWhyBodyHTML(null);
    expect(html).toContain('indisponibles pour le moment');
    expect(html).toContain('methodologie-indicateurs.md');
  });
});

describe('loadIndicators', () => {
  async function freshModule() {
    vi.resetModules();
    return import('../src/lib/indicators-data.js');
  }

  test('returns the payload and memoizes per law', async () => {
    const spy = vi.fn().mockResolvedValue({ ok: true, json: async () => API_PAYLOAD });
    vi.stubGlobal('fetch', spy);
    const { loadIndicators } = await freshModule();

    const a = await loadIndicators('eco-agri-1');
    const b = await loadIndicators('eco-agri-1');
    await loadIndicators('eco-eau-1');

    expect(a.indicators.length).toBe(2);
    expect(b).toBe(a);
    expect(spy).toHaveBeenCalledTimes(2); // une fois par loi
  });

  test('resolves null on HTTP error or network failure (never rejects)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }));
    let mod = await freshModule();
    expect(await mod.loadIndicators('eco-agri-1')).toBeNull();

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    mod = await freshModule();
    expect(await mod.loadIndicators('eco-agri-1')).toBeNull();
  });
});
