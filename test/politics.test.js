import { describe, test, expect } from 'vitest';
import { departementLabel, interpellationLetter } from '../src/lib/laws.js';
import { politicsHTML, citizenActionIcon } from '../src/components/politics.js';
import snapshot from '../src/data/laws-snapshot.json';

// Les données viennent du snapshot embarqué (issue #5) — la même donnée que le fallback
// runtime. Plus AUCUNE donnée législative codée en dur dans src/.
const LAWS = snapshot.laws;
const CATEGORIES = ['eau', 'agriculture', 'canicule', 'biodiversite', 'pesticides'];

describe('snapshot laws integrity', () => {
  test('every law has the required shape', () => {
    expect(LAWS.length).toBeGreaterThan(0);
    for (const l of LAWS) {
      expect(typeof l.id).toBe('string');
      expect(l.title).toBeTruthy();
      expect(CATEGORIES).toContain(l.category);
      expect(['passed', 'upcoming']).toContain(l.status);
      expect(l.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(l.sourceUrl).toMatch(/^https?:\/\//);
    }
  });

  test('indicators stay within the -2..+2 scale', () => {
    for (const l of LAWS) {
      for (const v of Object.values(l.indicators)) {
        expect(v).toBeGreaterThanOrEqual(-2);
        expect(v).toBeLessThanOrEqual(2);
      }
    }
  });

  test('passed laws carry non-negative vote counts for all four groups', () => {
    for (const l of LAWS.filter((x) => x.status === 'passed')) {
      expect(l.votes).toBeTruthy();
      for (const group of ['gauche', 'milieu', 'droite', 'extremeDroite']) {
        const v = l.votes[group];
        expect(v).toBeTruthy();
        expect(v.for + v.against + v.abstained).toBeGreaterThan(0);
        [v.for, v.against, v.abstained].forEach((n) => expect(n).toBeGreaterThanOrEqual(0));
      }
    }
  });

  test('has at least one passed law', () => {
    expect(LAWS.some((l) => l.status === 'passed')).toBe(true);
  });

  test('every law carries verifiable source expectations (Golden Rule)', () => {
    for (const l of LAWS) {
      expect(l.sourceUrl).toMatch(/^https:\/\/www\.assemblee-nationale\.fr\//);
      expect(l.textUrl).toMatch(/^https:\/\/www\.assemblee-nationale\.fr\//);
      expect(l.sourceExpect).toBeTruthy();
      expect(l.textExpect).toBeTruthy();
    }
  });

  test('passed laws point to an official scrutin page', () => {
    for (const l of LAWS.filter((x) => x.status === 'passed')) {
      expect(l.sourceUrl).toMatch(/\/dyn\/\d+\/scrutins\/\d+$/);
    }
  });
});

describe('departementLabel', () => {
  test('maps postal codes to an area label', () => {
    expect(departementLabel('49000')).toBe('département 49');
    expect(departementLabel('75012')).toBe('département 75');
    expect(departementLabel('20000')).toBe('Corse');
    expect(departementLabel('97400')).toContain('Outre-mer');
  });
  test('rejects malformed codes', () => {
    expect(departementLabel('')).toBeNull();
    expect(departementLabel('123')).toBeNull();
    expect(departementLabel('abcde')).toBeNull();
  });
});

describe('interpellationLetter', () => {
  const law = LAWS[0];
  test('includes the law title and closing', () => {
    const letter = interpellationLetter(law);
    expect(letter).toContain(law.title);
    expect(letter).toContain('salutations citoyennes');
  });
  test('localizes the letter when a valid postal code is given', () => {
    expect(interpellationLetter(law, '49000')).toContain('département 49');
    expect(interpellationLetter(law, '')).not.toContain('département');
  });
});

describe('politicsHTML', () => {
  const stateWith = (over = {}) => ({
    lawFilter: 'all',
    laws: LAWS,
    lawsMeta: { source: 'api', dataDate: '2026-07-17T06:00:00Z' },
    ...over,
  });

  test('renders a dimensioned loading skeleton while laws are not loaded yet', () => {
    const html = politicsHTML({ lawFilter: 'all', laws: null });
    expect(html).toContain('pcard--skeleton');
    expect(html).toContain('aria-busy="true"');
    expect(html).toContain('Chargement des données législatives');
  });

  test('renders passed cards, filters, and upcoming section (cards or honest empty state)', () => {
    const html = politicsHTML(stateWith());
    const hasUpcoming = LAWS.some((l) => l.status === 'upcoming');
    if (hasUpcoming) {
      expect(html).toContain('pcard--upcoming');
      expect(html).toContain('data-action="interpellate"');
    } else {
      expect(html).toContain('Aucun scrutin vérifié à venir');
    }
    expect(html).toContain('vote-group'); // vote-by-group matrix on passed laws
    expect(html).toContain('indicator-meter'); // impact gauges
    expect(html).toContain('data-lawfilter="pesticides"');
  });

  test('shows a discreet freshness indicator for API data', () => {
    const html = politicsHTML(stateWith());
    expect(html).toContain('politics-freshness');
    expect(html).toContain('data-source="api"');
    expect(html).toContain('Données à jour du');
  });

  test('labels snapshot data honestly when the API was unreachable', () => {
    const html = politicsHTML(
      stateWith({ lawsMeta: { source: 'snapshot', dataDate: '2026-07-16T20:00:00Z' } }),
    );
    expect(html).toContain('data-source="snapshot"');
    expect(html).toContain('Données archivées du');
    expect(html).toContain('source temporairement injoignable');
  });

  test('category filter narrows the passed list', () => {
    const eau = politicsHTML(stateWith({ lawFilter: 'eau' }));
    const passedCount = (eau.match(/pcard__body-layout/g) || []).length;
    const eauLaws = LAWS.filter((l) => l.status === 'passed' && l.category === 'eau').length;
    expect(passedCount).toBe(eauLaws);
  });

  test('gauges and vote matrix expose screen-reader text equivalents', () => {
    const html = politicsHTML(stateWith());
    // indicator gauges: name + scale description in sr-only text
    expect(html).toMatch(/sr-only">Pesticides : [^<]*sur une échelle de −2/);
    // vote matrix: exact counts, not just percentages (LOA gauche: 1 pour, 160 contre)
    expect(html).toContain('Gauche (NFP/LFI/PS/EELV) : 1 pour, 160 contre, 0 abstention.');
    // decorative visuals hidden from AT
    expect(html).toMatch(/class="vote-group__bar" aria-hidden="true"/);
    expect(html).toMatch(/class="indicator-meter__track" aria-hidden="true"/);
  });

  test('exports the citizen action icon used by the interpellation button', () => {
    expect(citizenActionIcon).toContain('<svg');
  });
});
