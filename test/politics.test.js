import { describe, test, expect } from 'vitest';
import { LAWS_DATA, departementLabel, interpellationLetter } from '../src/lib/laws.js';
import { politicsHTML, citizenActionIcon } from '../src/components/politics.js';

const CATEGORIES = ['eau', 'agriculture', 'canicule', 'biodiversite', 'pesticides'];

describe('LAWS_DATA integrity', () => {
  test('every law has the required shape', () => {
    expect(LAWS_DATA.length).toBeGreaterThan(0);
    for (const l of LAWS_DATA) {
      expect(typeof l.id).toBe('string');
      expect(l.title).toBeTruthy();
      expect(CATEGORIES).toContain(l.category);
      expect(['passed', 'upcoming']).toContain(l.status);
      expect(l.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(l.sourceUrl).toMatch(/^https?:\/\//);
    }
  });

  test('indicators stay within the -2..+2 scale', () => {
    for (const l of LAWS_DATA) {
      for (const v of Object.values(l.indicators)) {
        expect(v).toBeGreaterThanOrEqual(-2);
        expect(v).toBeLessThanOrEqual(2);
      }
    }
  });

  test('passed laws carry non-negative vote counts for all four groups', () => {
    for (const l of LAWS_DATA.filter((x) => x.status === 'passed')) {
      expect(l.votes).toBeTruthy();
      for (const group of ['gauche', 'milieu', 'droite', 'extremeDroite']) {
        const v = l.votes[group];
        expect(v).toBeTruthy();
        expect(v.for + v.against + v.abstained).toBeGreaterThan(0);
        [v.for, v.against, v.abstained].forEach((n) => expect(n).toBeGreaterThanOrEqual(0));
      }
    }
  });

  test('has at least one upcoming and one passed law', () => {
    expect(LAWS_DATA.some((l) => l.status === 'upcoming')).toBe(true);
    expect(LAWS_DATA.some((l) => l.status === 'passed')).toBe(true);
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
  const law = LAWS_DATA[0];
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
  test('renders upcoming + passed cards, filters, and the citizen action button', () => {
    const html = politicsHTML({ lawFilter: 'all' });
    expect(html).toContain('pcard--upcoming');
    expect(html).toContain('vote-group'); // vote-by-group matrix on passed laws
    expect(html).toContain('indicator-meter'); // impact gauges
    expect(html).toContain('data-action="interpellate"');
    expect(html).toContain('data-lawfilter="pesticides"');
  });

  test('category filter narrows the passed list', () => {
    const eau = politicsHTML({ lawFilter: 'eau' });
    const passedCount = (eau.match(/pcard__body-layout/g) || []).length;
    const eauLaws = LAWS_DATA.filter((l) => l.status === 'passed' && l.category === 'eau').length;
    expect(passedCount).toBe(eauLaws);
  });

  test('citizen action icon is a self-contained svg', () => {
    expect(citizenActionIcon).toContain('<svg');
    expect(citizenActionIcon).toContain('citizen-icon');
  });
});
