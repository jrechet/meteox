import { describe, test, expect } from 'vitest';
import { lawPageHTML, citationText, sitemapXML, lawUrl } from '../src/lib/law-page.js';

// Options de rendu volontairement différentes de la prod pour prouver que baseUrl et
// buildDate sont bien honorés (aucune URL ni date codée en dur dans le rendu).
const OPTS = { baseUrl: 'https://example.org/meteox/', buildDate: '2026-07-23T10:00:00Z' };

const VOTES = {
  gauche: { for: 98, against: 0, abstained: 0 },
  milieu: { for: 86, against: 1, abstained: 0 },
  droite: { for: 0, against: 12, abstained: 4 },
  extremeDroite: { for: 0, against: 0, abstained: 22 },
};

const SENAT_VOTES = {
  gauche: { for: 10, against: 20, abstained: 3 },
  milieu: { for: 40, against: 2, abstained: 1 },
  droite: { for: 120, against: 5, abstained: 0 },
  extremeDroite: { for: 0, against: 3, abstained: 0 },
};

/** Loi votée de référence, avec facette Sénat « scrutin public » (forme 1). */
function passedLaw(overrides = {}) {
  return {
    id: 'pfas-1',
    title: 'Protection de la population contre les PFAS',
    category: 'pesticides',
    status: 'passed',
    date: '2024-04-04',
    summary:
      'Adoption en première lecture de la proposition de loi visant à restreindre la fabrication ' +
      'et la vente de produits contenant des PFAS, avec exclusion des ustensiles de cuisine.',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/16/scrutins/3643',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N49455',
    indicators: { pesticides: 1 },
    votes: VOTES,
    senat: {
      hasPublicScrutin: true,
      session: '2023-2024',
      numero: 187,
      scrutinUrl: 'https://www.senat.fr/scrutin-public/2023/scr2023-187.html',
      scrutinDate: '2024-05-30',
      votes: SENAT_VOTES,
    },
    ...overrides,
  };
}

function upcomingLaw(overrides = {}) {
  return {
    id: 'eau-2',
    title: 'Proposition de loi sur la gestion durable de l\'eau',
    category: 'eau',
    status: 'upcoming',
    stage: 'Examen en commission',
    summary: 'Texte en cours d\'examen sur le partage de la ressource en eau.',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/17/dossiers/exemple',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/17/dossiers/exemple',
    indicators: { partageEau: 1 },
    ...overrides,
  };
}

describe('lawPageHTML — head SEO / partage', () => {
  const html = lawPageHTML(passedLaw(), OPTS);

  test('titre distinct, description et canonical vers l\'URL de la page', () => {
    expect(html).toContain('<html lang="fr">');
    expect(html).toContain('<title>Protection de la population contre les PFAS — meteox</title>');
    expect(html).toMatch(/<meta name="description" content="Adoption en première lecture/);
    expect(html).toContain('<link rel="canonical" href="https://example.org/meteox/loi/pfas-1/">');
  });

  test('OpenGraph complet + twitter card summary', () => {
    expect(html).toContain('property="og:title" content="Protection de la population contre les PFAS — meteox"');
    expect(html).toContain('property="og:url" content="https://example.org/meteox/loi/pfas-1/"');
    expect(html).toContain('property="og:type" content="article"');
    expect(html).toContain('property="og:site_name" content="meteox"');
    expect(html).toContain('property="og:locale" content="fr_FR"');
    expect(html).toMatch(/property="og:description" content="Adoption en première lecture/);
    expect(html).toContain('name="twitter:card" content="summary"');
  });

  test('la meta description est tronquée à ~160 caractères sans couper un mot', () => {
    const long = lawPageHTML(passedLaw({ summary: 'mot '.repeat(100).trim() }), OPTS);
    const desc = long.match(/<meta name="description" content="([^"]*)"/)[1];
    expect(desc.length).toBeLessThanOrEqual(160);
    expect(desc.endsWith('…')).toBe(true);
  });
});

describe('lawPageHTML — contenu (loi votée)', () => {
  const html = lawPageHTML(passedLaw(), OPTS);

  test('titre, catégorie, statut daté et résumé en vrai HTML', () => {
    expect(html).toContain('<h1>Protection de la population contre les PFAS</h1>');
    expect(html).toContain('Pesticides');
    expect(html).toContain('Votée le 04/04/2024');
    expect(html).toContain('restreindre la fabrication');
  });

  test('votes des DEUX chambres, par bloc', () => {
    expect(html).toContain('À l\'Assemblée nationale');
    expect(html).toContain('Au Sénat');
    // Blocs AN (libellés détaillés) et Sénat (blocs génériques)
    expect(html).toContain('Gauche (NFP/LFI/PS/EELV)');
    expect(html).toContain('Extrême droite');
    // Compte réels issus des fixtures (98 pour à gauche AN, 120 pour à droite Sénat)
    expect(html).toMatch(/<td>98<\/td>/);
    expect(html).toMatch(/<td>120<\/td>/);
  });

  test('liens officiels : scrutin AN, dossier, scrutin Sénat', () => {
    expect(html).toContain('https://www.assemblee-nationale.fr/dyn/16/scrutins/3643');
    expect(html).toContain('https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N49455');
    expect(html).toContain('https://www.senat.fr/scrutin-public/2023/scr2023-187.html');
    expect(html).toContain('Scrutin public n°187 (session 2023-2024) du 30/05/2024');
  });

  test('lien retour vers la racine du site', () => {
    expect(html).toContain('href="../../"');
    expect(html).toContain('Toutes les lois sur meteox');
  });

  test('bloc citation + bouton copier', () => {
    expect(html).toContain('id="citation-text"');
    expect(html).toContain('id="copy-citation"');
    expect(html).toContain('Copier la citation');
    expect(html).toContain('navigator.clipboard');
  });
});

describe('citationText', () => {
  test('loi votée avec scrutin Sénat : n° AN + n° Sénat + sources + date de consultation', () => {
    const citation = citationText(passedLaw(), OPTS);
    expect(citation).toContain('Protection de la population contre les PFAS');
    expect(citation).toContain('scrutin AN n°3643 du 04/04/2024');
    expect(citation).toContain('scrutin Sénat n°187 du 30/05/2024');
    expect(citation).toContain('Sources officielles : https://www.assemblee-nationale.fr/dyn/16/scrutins/3643');
    expect(citation).toContain('https://www.senat.fr/scrutin-public/2023/scr2023-187.html');
    expect(citation).toContain('Consulté le 23/07/2026 sur meteox — https://example.org/meteox/loi/pfas-1/');
  });

  test('main levée au Sénat : la citation l\'énonce, sans inventer de n° de scrutin', () => {
    const citation = citationText(
      passedLaw({ senat: { hasPublicScrutin: false, reason: 'Voté à main levée' } }),
      OPTS,
    );
    expect(citation).toContain('voté à main levée au Sénat (pas de scrutin public)');
    expect(citation).not.toContain('scrutin Sénat n°');
  });

  test('loi à venir : dossier en cours, étape officielle, pas de date fabriquée', () => {
    const citation = citationText(upcomingLaw(), OPTS);
    expect(citation).toContain('dossier législatif en cours (Examen en commission)');
    expect(citation).not.toContain('scrutin AN');
    expect(citation).toContain('Consulté le 23/07/2026');
  });
});

describe('lawPageHTML — les 3 formes de la facette Sénat', () => {
  test('forme 1 (scrutin public) : table de votes + lien officiel', () => {
    const html = lawPageHTML(passedLaw(), OPTS);
    expect((html.match(/<table>/g) || []).length).toBe(2); // AN + Sénat
    expect(html).toContain('scrutin officiel ↗');
  });

  test('forme 2 (main levée) : mention honnête, pas de table Sénat', () => {
    const html = lawPageHTML(
      passedLaw({ senat: { hasPublicScrutin: false, reason: 'Voté à main levée — pas de scrutin public au Sénat' } }),
      OPTS,
    );
    expect(html).toContain('Voté à main levée — pas de scrutin public au Sénat');
    expect((html.match(/<table>/g) || []).length).toBe(1); // AN uniquement
  });

  test('forme 3 (facette absente) : aucune section Sénat', () => {
    const html = lawPageHTML(passedLaw({ senat: undefined }), OPTS);
    expect(html).not.toContain('Au Sénat');
    expect((html.match(/<table>/g) || []).length).toBe(1);
  });
});

describe('lawPageHTML — loi à venir (upcoming)', () => {
  const html = lawPageHTML(upcomingLaw(), OPTS);

  test('étape officielle affichée à la place de la date, jamais de date fabriquée', () => {
    expect(html).toContain('Vote à venir · Examen en commission');
    expect(html).not.toContain('Votée le');
  });

  test('pas de section votes (aucun scrutin n\'a eu lieu)', () => {
    expect(html).not.toContain('<table>');
    expect(html).not.toContain('À l\'Assemblée nationale');
  });

  test('repli neutre si l\'étape manque', () => {
    const noStage = lawPageHTML(upcomingLaw({ stage: '   ' }), OPTS);
    // L'apostrophe passe par escapeHtml (donnée interpolée) → &#39; dans le HTML émis.
    expect(noStage).toContain('Vote à venir · En cours d&#39;examen');
  });
});

describe('lawPageHTML — échappement (XSS)', () => {
  const evil = passedLaw({
    title: '<script>alert(1)</script>Loi piégée',
    summary: 'Résumé "piégé" <img src=x onerror=alert(2)>',
    textUrl: 'javascript:alert(3)',
  });
  const html = lawPageHTML(evil, OPTS);

  test('titre et résumé piégés neutralisés partout (head + corps)', () => {
    expect(html).not.toContain('<script>alert(1)');
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;Loi piégée');
    expect(html).not.toContain('<img src=x');
  });

  test('URL non https jamais émise comme lien', () => {
    expect(html).not.toContain('javascript:alert');
  });
});

describe('sitemapXML', () => {
  const laws = [passedLaw(), upcomingLaw()];
  const xml = sitemapXML(laws, OPTS);

  test('contient la racine et chaque loi, lastmod = date de build', () => {
    expect(xml).toContain('<loc>https://example.org/meteox/</loc>');
    expect(xml).toContain('<loc>https://example.org/meteox/loi/pfas-1/</loc>');
    expect(xml).toContain('<loc>https://example.org/meteox/loi/eau-2/</loc>');
    expect((xml.match(/<lastmod>2026-07-23<\/lastmod>/g) || []).length).toBe(3);
  });

  test('XML sitemap valide (déclaration + namespace officiel)', () => {
    expect(xml.startsWith('<?xml version="1.0" encoding="UTF-8"?>')).toBe(true);
    expect(xml).toContain('xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"');
  });
});

describe('lawUrl', () => {
  test('normalise la base et encode l\'id', () => {
    expect(lawUrl('https://example.org/meteox', 'abc-1')).toBe('https://example.org/meteox/loi/abc-1/');
    expect(lawUrl('https://example.org/meteox/', 'a b')).toBe('https://example.org/meteox/loi/a%20b/');
  });
});
