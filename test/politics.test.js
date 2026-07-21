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

  test("carte à venir : affiche l'étape officielle sourcée, jamais une date fabriquée", () => {
    const upcoming = {
      id: 'DLR5L17N53637', title: 'Texte à venir', summary: 'Résumé vérifié.',
      category: 'eau', status: 'upcoming', stage: 'En navette au Sénat',
      sourceUrl: 'https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17N53637',
      textUrl: 'https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17N53637',
      indicators: { pesticides: 0, partageEau: 1, pognonPuissants: 0, peupleSante: 1 },
    };
    const html = politicsHTML(stateWith({ laws: [...LAWS, upcoming] }));
    expect(html).toContain('pcard--upcoming');
    // Minuscule initiale enchaînée après « Vote à venir · », et AUCUNE date fabriquée.
    expect(html).toContain('Vote à venir · en navette au Sénat');
    expect(html).not.toContain('Invalid Date');
  });

  test("carte à venir sans étape : repli neutre « en cours d'examen »", () => {
    const upcoming = {
      id: 'x', title: 'Texte sans étape', summary: 'Résumé.', category: 'eau', status: 'upcoming',
      sourceUrl: 'https://www.assemblee-nationale.fr/dyn/17/dossiers/x',
      textUrl: 'https://www.assemblee-nationale.fr/dyn/17/dossiers/x',
      indicators: { pesticides: 0, partageEau: 0, pognonPuissants: 0, peupleSante: 0 },
    };
    const html = politicsHTML(stateWith({ laws: [upcoming] }));
    // L'étape est échappée (donnée externe rendue via innerHTML) : l'apostrophe devient &#39;.
    expect(html).toContain('Vote à venir · en cours d&#39;examen');
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
    // decorative visuals hidden from AT (the bar+numbers row carries aria-hidden)
    expect(html).toMatch(/class="vote-group__row" aria-hidden="true"/);
    expect(html).toMatch(/class="indicator-meter__track" aria-hidden="true"/);
  });

  test('vote counts show all three tallies incl. abstention (no hidden 0%/0% groups)', () => {
    const html = politicsHTML(stateWith());
    // Les trois comptes (P/C/A) sont rendus, abstention comprise — un groupe 100 %
    // abstention n'est plus un « 0% P / 0% C » muet.
    expect(html).toContain('vote-num--abstained');
    // PFAS Droite = 0 pour / 0 contre / 4 abstentions : la valeur 4 doit apparaître.
    expect(html).toMatch(/vote-num--abstained[^>]*>4 A</);
    // Le compte de « pour » de la gauche PFAS (98) est affiché tel quel.
    expect(html).toMatch(/vote-num--for[^>]*>98 P</);
  });

  test('exports the citizen action icon used by the interpellation button', () => {
    expect(citizenActionIcon).toContain('<svg');
  });
});

describe('politicsHTML — 2ᵉ facette « Au Sénat »', () => {
  // Loi votée minimale et contrôlée : on maîtrise la facette senat des 3 formes.
  const passedLaw = (senat) => ({
    id: 'sen-test', title: 'Loi test Sénat', summary: 'Résumé vérifié.',
    category: 'eau', status: 'passed', date: '2024-04-04',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/17/scrutins/1',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/17/dossiers/D1',
    indicators: { pesticides: 0, partageEau: 1, pognonPuissants: 0, peupleSante: 1 },
    votes: {
      gauche: { for: 1, against: 160, abstained: 0 }, milieu: { for: 30, against: 0, abstained: 0 },
      droite: { for: 90, against: 0, abstained: 0 }, extremeDroite: { for: 40, against: 0, abstained: 0 },
    },
    ...(senat === undefined ? {} : { senat }),
  });
  const render = (senat) =>
    politicsHTML({
      lawFilter: 'all', laws: [passedLaw(senat)],
      lawsMeta: { source: 'api', dataDate: '2026-07-17T06:00:00Z' },
    });

  const scrutin = {
    hasPublicScrutin: true, session: 2022, numero: 125,
    scrutinUrl: 'https://www.senat.fr/scrutin-public/2022/scr2022-125.html',
    scrutinDate: '2023-02-07',
    votes: {
      gauche: { for: 64, against: 0, abstained: 27 }, milieu: { for: 38, against: 0, abstained: 0 },
      droite: { for: 184, against: 13, abstained: 3 }, extremeDroite: { for: 0, against: 0, abstained: 0 },
    },
  };

  test('sous-titre les deux chambres (une carte = 2 facettes)', () => {
    const html = render(scrutin);
    expect(html).toContain("À l&#39;Assemblée nationale");
    expect(html).toContain('Au Sénat');
  });

  test('forme 1 (scrutin public) : blocs Sénat + date + lien vers la page officielle', () => {
    const html = render(scrutin);
    // Blocs de votes Sénat (mêmes barres) : agrégats officiels exposés en sr-only.
    expect(html).toContain('Gauche : 64 pour, 0 contre, 27 abstentions.');
    expect(html).toContain('Droite : 184 pour, 13 contre, 3 abstentions.');
    // Date du scrutin (jamais fabriquée) + lien officiel senat.fr (traçabilité, Golden Rule).
    expect(html).toContain('Scrutin du 07/02/2023');
    expect(html).toContain('href="https://www.senat.fr/scrutin-public/2022/scr2022-125.html"');
    expect(html).toContain('Scrutin Sénat officiel');
    expect(html).toMatch(/rel="noopener"/);
  });

  test('forme 2 (main levée) : mention claire, pas de scrutin public', () => {
    const html = render({
      hasPublicScrutin: false,
      reason: 'Voté à main levée — pas de scrutin public au Sénat',
    });
    expect(html).toContain('Au Sénat');
    expect(html).toContain('Voté à main levée — pas de scrutin public au Sénat');
    expect(html).toContain('chamber-vote--note');
  });

  test('forme 3 (senat absent) : aucune facette Sénat rendue', () => {
    const html = render(undefined);
    expect(html).not.toContain('Au Sénat');
    expect(html).not.toContain('chamber-vote--note');
    // La facette AN reste, elle, bien présente.
    expect(html).toContain("À l&#39;Assemblée nationale");
  });

  test('échappe/neutralise toute donnée Sénat externe (anti-XSS)', () => {
    const html = render({
      hasPublicScrutin: true,
      scrutinUrl: 'javascript:alert(1)',
      scrutinDate: '2023-02-07',
      votes: scrutin.votes,
    });
    expect(html).not.toContain('javascript:alert(1)');
    expect(html).toContain('href="#"'); // URL dangereuse neutralisée par safeUrl
    const evil = render({
      hasPublicScrutin: false,
      reason: '<img src=x onerror=alert(1)>',
    });
    expect(evil).not.toContain('<img src=x');
    expect(evil).toContain('&lt;img src=x');
  });
});
