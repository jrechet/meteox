// Sécurité d'injection (issue #5) : les données de lois sont désormais externes (API) et
// rendues via innerHTML — tout doit être échappé, et une loi malformée ne doit pas planter.
import { describe, test, expect } from 'vitest';
import { escapeHtml, safeUrl } from '../src/lib/html.js';
import { isValidLaw } from '../src/lib/laws-validate.js';
import { politicsHTML } from '../src/components/politics.js';

describe('escapeHtml', () => {
  test('neutralise les caractères HTML sensibles', () => {
    expect(escapeHtml('<img src=x onerror=alert(1)>')).toBe(
      '&lt;img src=x onerror=alert(1)&gt;',
    );
    expect(escapeHtml(`"'&<>`)).toBe('&quot;&#39;&amp;&lt;&gt;');
    expect(escapeHtml(null)).toBe('');
  });
});

describe('safeUrl', () => {
  test('laisse passer http(s), bloque les schémas dangereux', () => {
    expect(safeUrl('https://www.assemblee-nationale.fr/dyn/17/scrutins/844')).toContain('https://');
    expect(safeUrl('javascript:alert(1)')).toBe('#');
    expect(safeUrl('data:text/html,<script>alert(1)</script>')).toBe('#');
    expect(safeUrl('not a url')).toBe('#');
    expect(safeUrl(undefined)).toBe('#');
  });
});

describe('isValidLaw', () => {
  const good = {
    id: 'x', title: 'Loi', summary: 'résumé', category: 'pesticides', status: 'passed',
    date: '2024-04-04', sourceUrl: 'https://www.assemblee-nationale.fr/a',
    textUrl: 'https://www.assemblee-nationale.fr/b', indicators: { pesticides: 1 },
    votes: {
      gauche: { for: 1, against: 2, abstained: 0 }, milieu: { for: 1, against: 0, abstained: 0 },
      droite: { for: 0, against: 1, abstained: 0 }, extremeDroite: { for: 0, against: 1, abstained: 0 },
    },
  };
  test('accepte une loi passed complète', () => {
    expect(isValidLaw(good)).toBe(true);
  });
  test('rejette une loi passed sans les 4 blocs de votes (bug de crash fermé)', () => {
    const { extremeDroite, ...partial } = good.votes;
    expect(isValidLaw({ ...good, votes: partial })).toBe(false);
    expect(isValidLaw({ ...good, votes: undefined })).toBe(false);
  });
  test('rejette une URL non https (anti javascript:)', () => {
    expect(isValidLaw({ ...good, sourceUrl: 'javascript:alert(1)' })).toBe(false);
  });
  test('rejette une date malformée et un statut inconnu', () => {
    expect(isValidLaw({ ...good, date: '04/04/2024' })).toBe(false);
    expect(isValidLaw({ ...good, status: 'draft' })).toBe(false);
  });
  test('rejette une loi passed sans date (vraie date de scrutin obligatoire)', () => {
    const { date, ...sansDate } = good;
    expect(isValidLaw(sansDate)).toBe(false);
  });
  test("accepte une loi upcoming sans date ni votes mais avec une étape (stage)", () => {
    const { date, votes, ...base } = good;
    expect(isValidLaw({ ...base, status: 'upcoming', stage: 'En commission' })).toBe(true);
  });
  test('rejette une loi upcoming sans étape (stage manquante ou vide)', () => {
    const { date, votes, ...base } = good;
    expect(isValidLaw({ ...base, status: 'upcoming' })).toBe(false);
    expect(isValidLaw({ ...base, status: 'upcoming', stage: '   ' })).toBe(false);
  });

  // Facette Sénat (2ᵉ facette) : optionnelle — mais si présente, elle doit être bien formée.
  const senatScrutin = {
    hasPublicScrutin: true,
    scrutinUrl: 'https://www.senat.fr/scrutin-public/2022/scr2022-125.html',
    scrutinDate: '2023-02-07',
    votes: {
      gauche: { for: 64, against: 0, abstained: 27 }, milieu: { for: 38, against: 0, abstained: 0 },
      droite: { for: 184, against: 13, abstained: 3 }, extremeDroite: { for: 0, against: 0, abstained: 0 },
    },
  };
  test('accepte une loi sans facette senat (senat optionnel)', () => {
    expect(isValidLaw(good)).toBe(true);
    expect(good.senat).toBeUndefined();
  });
  test('accepte un senat forme 1 (scrutin public : 4 blocs + scrutinUrl https)', () => {
    expect(isValidLaw({ ...good, senat: senatScrutin })).toBe(true);
  });
  test('accepte un senat forme 2 (voté à main levée : reason non vide)', () => {
    const mainLevee = { hasPublicScrutin: false, reason: 'Voté à main levée — pas de scrutin public au Sénat' };
    expect(isValidLaw({ ...good, senat: mainLevee })).toBe(true);
  });
  test('rejette un senat forme 2 sans reason (ou reason vide)', () => {
    expect(isValidLaw({ ...good, senat: { hasPublicScrutin: false } })).toBe(false);
    expect(isValidLaw({ ...good, senat: { hasPublicScrutin: false, reason: '   ' } })).toBe(false);
  });
  test('rejette un senat forme 1 mal formé (blocs manquants ou URL non https)', () => {
    const { extremeDroite, ...partialVotes } = senatScrutin.votes;
    expect(isValidLaw({ ...good, senat: { ...senatScrutin, votes: partialVotes } })).toBe(false);
    expect(isValidLaw({ ...good, senat: { ...senatScrutin, scrutinUrl: 'javascript:alert(1)' } })).toBe(false);
  });
  test('rejette un senat sans hasPublicScrutin booléen', () => {
    expect(isValidLaw({ ...good, senat: { reason: 'x' } })).toBe(false);
    expect(isValidLaw({ ...good, senat: {} })).toBe(false);
  });
});

describe('politicsHTML — robustesse au rendu', () => {
  const base = (over) => ({
    lawFilter: 'all',
    lawsMeta: { source: 'api', dataDate: '2026-07-17T06:00:00Z' },
    laws: [{
      id: 'evil', title: '<img src=x onerror=alert(1)>', summary: '</p><script>alert(2)</script>',
      category: '"><script>alert(3)</script>', status: 'passed', date: '2024-01-01',
      sourceUrl: 'https://www.assemblee-nationale.fr/s', textUrl: 'javascript:alert(4)',
      indicators: { pesticides: 1 },
      votes: {
        gauche: { for: 1, against: 0, abstained: 0 }, milieu: { for: 1, against: 0, abstained: 0 },
        droite: { for: 1, against: 0, abstained: 0 }, extremeDroite: { for: 1, against: 0, abstained: 0 },
      },
    }],
    ...over,
  });

  test('échappe tout texte et neutralise une URL dangereuse (XSS)', () => {
    const html = politicsHTML(base());
    expect(html).not.toContain('<img src=x');
    expect(html).not.toContain('<script>alert');
    expect(html).toContain('&lt;img src=x');
    expect(html).toContain('href="#"'); // javascript: textUrl neutralisé
  });

  test('ne plante pas si une loi passed manque des blocs de votes (rendu défensif)', () => {
    const l = base().laws[0];
    const html = politicsHTML(base({ laws: [{ ...l, votes: { gauche: l.votes.gauche } }] }));
    expect(html).toContain('pcard'); // rendu produit, pas d'exception
  });
});
