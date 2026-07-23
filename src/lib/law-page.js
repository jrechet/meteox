// Rendu des pages statiques par loi (permaliens SEO) — fonctions PURES, sans effet de bord,
// consommées par scripts/generate-law-pages.mjs après `vite build` et testées par
// test/law-page.test.js. Objectif : chaque loi devient trouvable (Google), partageable
// (OpenGraph) et citable (bloc citation) — la SPA reste inchangée.
// Golden Rule : rien d'inventé. Toute donnée absente (facette senat, numéro de scrutin,
// date) est omise ou énoncée honnêtement — jamais de placeholder factice.
import { escapeHtml, safeUrl } from './html.js';
import { AN_GROUPS, SENAT_GROUPS } from './vote-groups.js';

const SITE_NAME = 'meteox';
const DESCRIPTION_MAX = 160;

// Libellés de catégories — version texte (sans emoji) des libellés de
// src/components/politics.js (categoryLabel), adaptée aux balises meta et au HTML statique.
const CATEGORY_LABELS = {
  pesticides: 'Pesticides',
  eau: "Gestion de l'eau",
  canicule: 'Canicule & chaleur',
  agriculture: 'Agriculture & sols',
  biodiversite: 'Biodiversité',
};

function categoryLabel(cat) {
  return CATEGORY_LABELS[cat] || String(cat ?? '');
}

/** Date FR déterministe (JJ/MM/AAAA) depuis un préfixe ISO — null si non exploitable. */
function frDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}/.test(value)) return null;
  return `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}`;
}

/** Tronque proprement (limite mot) pour les meta descriptions. */
function truncate(text, max = DESCRIPTION_MAX) {
  const t = String(text ?? '').trim();
  if (t.length <= max) return t;
  return `${t.slice(0, max - 1).replace(/\s+\S*$/, '')}…`;
}

/** URL canonique d'une page-loi. `baseUrl` se termine toujours par `/` (normalisé ici). */
export function lawUrl(baseUrl, id) {
  const base = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
  return `${base}loi/${encodeURIComponent(id)}/`;
}

/** N° de scrutin AN extrait de l'URL officielle (…/scrutins/3643) — null si absent. */
function scrutinANNumero(sourceUrl) {
  const m = typeof sourceUrl === 'string' ? sourceUrl.match(/\/scrutins\/(\d+)$/) : null;
  return m ? m[1] : null;
}

/** Étape officielle d'une loi « à venir » — même repli neutre que la SPA (politics.js). */
function upcomingStageLabel(law) {
  return typeof law.stage === 'string' && law.stage.trim() ? law.stage.trim() : "En cours d'examen";
}

/** URLs officielles réellement citables (https validé, dédupliquées, ordre stable). */
function officialUrls(law) {
  const candidates =
    law.status === 'passed'
      ? [law.sourceUrl, law.textUrl, law.senat?.scrutinUrl]
      : [law.textUrl]; // upcoming : seul le dossier officiel est montré (comme la carte SPA)
  return [...new Set(candidates.map((u) => safeUrl(u)).filter((u) => u !== '#'))];
}

/** Fragment « scrutin Sénat … » de la citation — uniquement à partir des champs présents. */
function senatCitationPart(senat) {
  if (!senat || typeof senat !== 'object') return '';
  if (senat.hasPublicScrutin === false) {
    return ' ; voté à main levée au Sénat (pas de scrutin public)';
  }
  const numero = senat.numero ? ` n°${senat.numero}` : '';
  const date = frDate(senat.scrutinDate);
  const when = date ? ` du ${date}` : '';
  return numero || when ? ` ; scrutin Sénat${numero}${when}` : ' ; scrutin public au Sénat';
}

/**
 * Citation prête à copier — format :
 * « {Titre}, scrutin AN n°… du {date} ; scrutin Sénat n°… du {date}. Sources officielles :
 * {urls}. Consulté le {date de build} sur meteox — {url de la page} ».
 * Chaque segment n'apparaît que si la donnée existe (Golden Rule).
 */
export function citationText(law, { baseUrl, buildDate }) {
  const parts = [law.title];
  if (law.status === 'passed') {
    const numero = scrutinANNumero(law.sourceUrl);
    const date = frDate(law.date);
    parts.push(`, scrutin AN${numero ? ` n°${numero}` : ''}${date ? ` du ${date}` : ''}`);
    parts.push(senatCitationPart(law.senat));
  } else {
    parts.push(`, dossier législatif en cours (${upcomingStageLabel(law)})`);
  }
  const urls = officialUrls(law);
  if (urls.length > 0) parts.push(`. Sources officielles : ${urls.join(' ; ')}`);
  const consulted = frDate(buildDate) ?? String(buildDate).slice(0, 10);
  parts.push(`. Consulté le ${consulted} sur ${SITE_NAME} — ${lawUrl(baseUrl, law.id)}`);
  return parts.join('');
}

/** Table des votes d'une chambre (Pour / Contre / Abstention par bloc). */
function votesTableHTML(votes, groups, chamberLabel) {
  const rows = groups
    .map(([key, label]) => {
      const v = votes?.[key];
      if (!v) return '';
      return `<tr><th scope="row">${escapeHtml(label)}</th><td>${escapeHtml(v.for)}</td><td>${escapeHtml(v.against)}</td><td>${escapeHtml(v.abstained)}</td></tr>`;
    })
    .join('');
  if (!rows) return '';
  return `
        <table>
          <caption class="sr-only">Votes par bloc politique — ${escapeHtml(chamberLabel)}</caption>
          <thead>
            <tr><th scope="col">Bloc</th><th scope="col">Pour</th><th scope="col">Contre</th><th scope="col">Abstention</th></tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>`;
}

/**
 * Facette « Au Sénat » — mêmes 3 formes exclusives que la SPA (politics.js, senatFacetHTML) :
 *  1. scrutin public : table de votes + n°/session/date + lien officiel (traçabilité) ;
 *  2. voté à main levée : mention honnête, pas de scrutin public ;
 *  3. facette absente : rien (on n'affirme rien qu'on ne sait pas).
 */
function senatSectionHTML(senat) {
  if (!senat || typeof senat !== 'object') return '';
  if (senat.hasPublicScrutin === false) {
    const reason =
      typeof senat.reason === 'string' && senat.reason.trim()
        ? senat.reason
        : 'Voté à main levée — pas de scrutin public au Sénat';
    return `
      <section class="chamber" aria-labelledby="senat-title">
        <h3 id="senat-title">Au Sénat</h3>
        <p class="note">${escapeHtml(reason)}</p>
      </section>`;
  }
  const metaParts = [];
  const numero = senat.numero ? `Scrutin public n°${senat.numero}` : 'Scrutin public';
  metaParts.push(senat.session ? `${numero} (session ${senat.session})` : numero);
  const date = frDate(senat.scrutinDate);
  if (date) metaParts.push(`du ${date}`);
  const url = safeUrl(senat.scrutinUrl);
  const link =
    url !== '#'
      ? ` — <a href="${escapeHtml(url)}" rel="noopener">scrutin officiel ↗</a>`
      : '';
  return `
      <section class="chamber" aria-labelledby="senat-title">
        <h3 id="senat-title">Au Sénat</h3>${votesTableHTML(senat.votes, SENAT_GROUPS, 'Sénat')}
        <p class="scrutin-meta">${escapeHtml(metaParts.join(' '))}${link}</p>
      </section>`;
}

/** Section « votes des deux chambres » — omise entièrement si aucune donnée de vote. */
function votesSectionHTML(law) {
  const anTable = law.votes ? votesTableHTML(law.votes, AN_GROUPS, 'Assemblée nationale') : '';
  const an = anTable
    ? `
      <section class="chamber" aria-labelledby="an-title">
        <h3 id="an-title">À l'Assemblée nationale</h3>${anTable}
      </section>`
    : '';
  const senat = senatSectionHTML(law.senat);
  if (!an && !senat) return '';
  return `
    <section aria-labelledby="votes-title">
      <h2 id="votes-title">Votes par bloc politique</h2>${an}${senat}
    </section>`;
}

/** Liens officiels (scrutin AN, dossier, scrutin Sénat) — seuls ceux qui existent. */
function sourcesSectionHTML(law) {
  const items = [];
  const push = (url, label) => {
    const safe = safeUrl(url);
    if (safe !== '#') items.push(`<li><a href="${escapeHtml(safe)}" rel="noopener">${label} ↗</a></li>`);
  };
  if (law.status === 'passed') push(law.sourceUrl, "Scrutin officiel — Assemblée nationale");
  push(law.textUrl, 'Dossier législatif — texte officiel');
  if (law.senat?.hasPublicScrutin) push(law.senat.scrutinUrl, 'Scrutin officiel — Sénat');
  if (items.length === 0) return '';
  return `
    <section aria-labelledby="sources-title">
      <h2 id="sources-title">Sources officielles</h2>
      <ul class="sources">
        ${items.join('\n        ')}
      </ul>
    </section>`;
}

// Petit CSS inline dérivé des tokens du site (src/styles/tokens.css) — la page reste
// autonome (aucun asset du bundle Vite) tout en gardant le même langage visuel.
const PAGE_CSS = `
*,*::before,*::after{box-sizing:border-box;margin:0}
:root{--bg:oklch(97.5% 0.012 85);--bg-grain:oklch(96% 0.014 82);--surface:oklch(99% 0.006 90);--line:oklch(87% 0.015 80);--ink:oklch(24% 0.02 60);--ink-soft:oklch(44% 0.02 65);--accent-ink:oklch(44% 0.2 34);--font-display:'Fraunces','Iowan Old Style',Georgia,serif;--font-body:'Inter',system-ui,-apple-system,sans-serif}
body{font-family:var(--font-body);font-size:1rem;line-height:1.55;color:var(--ink);background-color:var(--bg);background-image:radial-gradient(120% 80% at 50% -10%,oklch(99% 0.02 85 / 0.9),transparent 60%),repeating-linear-gradient(0deg,var(--bg-grain) 0,var(--bg-grain) 1px,transparent 1px,transparent 3px);min-height:100dvh;padding:clamp(1rem,4vw,2.5rem) clamp(1rem,4vw,2rem);-webkit-font-smoothing:antialiased}
header,main,footer{max-width:720px;margin-inline:auto}
a{color:var(--accent-ink)}
.back{display:inline-block;font-size:.875rem;font-weight:600;text-decoration:none}
.back:hover{text-decoration:underline}
main{margin-top:2rem}
.law-meta{display:flex;flex-wrap:wrap;gap:.35rem .75rem;font-size:.8125rem;color:var(--ink-soft);margin-bottom:.75rem}
.law-cat{font-weight:600;color:var(--accent-ink)}
h1{font-family:var(--font-display);font-weight:600;line-height:1.05;letter-spacing:-0.02em;font-size:clamp(1.6rem,1.2rem + 2.5vw,2.6rem);margin-bottom:1rem}
.law-summary{color:var(--ink-soft);max-width:62ch}
h2{font-family:var(--font-display);font-weight:600;font-size:1.35rem;margin:2.25rem 0 .75rem}
h3{font-size:1rem;margin:0 0 .5rem}
.chamber{background:var(--surface);border:1px solid var(--line);border-radius:16px;padding:1rem 1.25rem;margin-bottom:1rem;overflow-x:auto}
table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums;font-size:.9rem}
th,td{padding:.45rem .5rem;text-align:right;border-bottom:1px solid var(--line)}
tbody tr:last-child th,tbody tr:last-child td{border-bottom:none}
th[scope=row],thead th:first-child{text-align:left;font-weight:500;overflow-wrap:anywhere}
thead th{font-size:.75rem;text-transform:uppercase;letter-spacing:.06em;color:var(--ink-soft)}
.note{color:var(--ink-soft);font-style:italic}
.scrutin-meta{font-size:.875rem;color:var(--ink-soft);margin-top:.75rem}
.sources{list-style:none;padding:0;display:grid;gap:.5rem}
.sources a{font-weight:600;font-size:.9rem}
.citation{background:var(--surface);border:1px solid var(--line);border-left:4px solid var(--accent-ink);border-radius:8px;padding:1rem 1.25rem;font-size:.9rem;color:var(--ink-soft);overflow-wrap:anywhere}
.copy-btn{font:inherit;font-weight:600;font-size:.875rem;color:var(--accent-ink);background:var(--surface);border:1px solid var(--line);border-radius:999px;padding:.5rem 1.1rem;margin-top:.75rem;cursor:pointer}
.copy-btn:hover{border-color:var(--accent-ink)}
footer{margin-top:3rem;padding-top:1.25rem;border-top:1px solid var(--line);font-size:.8125rem;color:var(--ink-soft)}
.sr-only{position:absolute;width:1px;height:1px;padding:0;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap;border:0}
:focus-visible{outline:2px solid var(--accent-ink);outline-offset:3px;border-radius:4px}
`;

// Bouton « Copier la citation » : clipboard API + repli textarea/execCommand (vieux
// navigateurs, contextes non sécurisés). Script inline autonome, aucune dépendance.
const COPY_SCRIPT = `
(function () {
  var btn = document.getElementById('copy-citation');
  var source = document.getElementById('citation-text');
  if (!btn || !source) return;
  var text = source.textContent.replace(/\\s+/g, ' ').trim();
  function done() {
    btn.textContent = 'Citation copiée ✓';
    setTimeout(function () { btn.textContent = 'Copier la citation'; }, 2000);
  }
  function fallbackCopy() {
    var area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'absolute';
    area.style.left = '-9999px';
    document.body.appendChild(area);
    area.select();
    try { document.execCommand('copy'); done(); } catch (e) { /* le texte reste sélectionnable */ }
    document.body.removeChild(area);
  }
  btn.addEventListener('click', function () {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, fallbackCopy);
    } else {
      fallbackCopy();
    }
  });
})();
`;

/**
 * Page HTML complète et autonome d'une loi (permalien SEO).
 * @param {object} law — une loi validée du snapshot (voir src/lib/laws-validate.js)
 * @param {{ baseUrl: string, buildDate: string }} options — URL absolue du site
 *   (avec `/` final) et date ISO du build (jamais fabriquée côté rendu).
 */
export function lawPageHTML(law, { baseUrl, buildDate }) {
  const pageUrl = lawUrl(baseUrl, law.id);
  const title = `${law.title} — ${SITE_NAME}`;
  const description = truncate(law.summary);
  const statusLabel =
    law.status === 'passed'
      ? `Votée le ${frDate(law.date) ?? '(date non renseignée)'}`
      : `Vote à venir · ${upcomingStageLabel(law)}`;
  const citation = citationText(law, { baseUrl, buildDate });

  return `<!doctype html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escapeHtml(title)}</title>
  <meta name="description" content="${escapeHtml(description)}">
  <link rel="canonical" href="${escapeHtml(pageUrl)}">
  <meta property="og:title" content="${escapeHtml(title)}">
  <meta property="og:description" content="${escapeHtml(description)}">
  <meta property="og:url" content="${escapeHtml(pageUrl)}">
  <meta property="og:type" content="article">
  <meta property="og:site_name" content="${SITE_NAME}">
  <meta property="og:locale" content="fr_FR">
  <meta name="twitter:card" content="summary">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,600&family=Inter:wght@400;500;600&display=swap">
  <style>${PAGE_CSS}</style>
</head>
<body>
  <header>
    <a class="back" href="../../">← Toutes les lois sur ${SITE_NAME}</a>
  </header>
  <main>
    <article>
      <p class="law-meta">
        <span class="law-cat">${escapeHtml(categoryLabel(law.category))}</span>
        <span>${escapeHtml(statusLabel)}</span>
      </p>
      <h1>${escapeHtml(law.title)}</h1>
      <p class="law-summary">${escapeHtml(law.summary)}</p>
${votesSectionHTML(law)}${sourcesSectionHTML(law)}
    <section aria-labelledby="cite-title">
      <h2 id="cite-title">Citer cette loi</h2>
      <blockquote class="citation" id="citation-text">${escapeHtml(citation)}</blockquote>
      <button type="button" class="copy-btn" id="copy-citation">Copier la citation</button>
    </section>
    </article>
  </main>
  <footer>
    <p>Page générée le ${escapeHtml(frDate(buildDate) ?? buildDate)} — toutes les données proviennent des sources officielles listées ci-dessus.</p>
    <p><a class="back" href="../../">← Toutes les lois sur ${SITE_NAME}</a></p>
  </footer>
  <script>${COPY_SCRIPT}</script>
</body>
</html>
`;
}

/** sitemap.xml : page racine + une entrée par loi, lastmod = date de build (AAAA-MM-JJ). */
export function sitemapXML(laws, { baseUrl, buildDate }) {
  const base = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
  const lastmod = String(buildDate).slice(0, 10);
  const urls = [base, ...laws.map((l) => lawUrl(base, l.id))]
    .map((loc) => `  <url>\n    <loc>${escapeHtml(loc)}</loc>\n    <lastmod>${lastmod}</lastmod>\n  </url>`)
    .join('\n');
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`;
}
