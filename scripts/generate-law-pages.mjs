// Génération des pages statiques par loi (permaliens SEO) — s'exécute APRÈS `vite build`
// (chaîne : npm run build = vite build && generate:pages). Lit le snapshot de lois du build
// (régénéré en CI par generate:snapshot, donc avec les facettes Sénat fraîches) et émet :
//   - dist/loi/{id}/index.html  → une vraie page HTML par loi (contenu, votes, citation, OG)
//   - dist/sitemap.xml          → racine + chaque loi, lastmod = date de build
// Run : npm run generate:pages
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { lawPageHTML, sitemapXML } from '../src/lib/law-page.js';

const BASE_URL = process.env.PAGES_BASE_URL || 'https://jrechet.github.io/meteox/';
const DIST_DIR = fileURLToPath(new URL('../dist/', import.meta.url));
const SNAPSHOT_PATH = new URL('../src/data/laws-snapshot.json', import.meta.url);

function fail(message) {
  console.error(`✗ ${message} — génération refusée.`);
  process.exit(1);
}

if (!existsSync(DIST_DIR)) {
  fail('dist/ absent : lancer `vite build` avant `generate:pages`');
}

const snapshot = JSON.parse(readFileSync(SNAPSHOT_PATH, 'utf8'));
if (!Array.isArray(snapshot.laws) || snapshot.laws.length === 0) {
  fail('Snapshot de lois vide ou invalide');
}

const buildDate = new Date().toISOString();

for (const law of snapshot.laws) {
  // L'id devient un chemin sur disque : on refuse tout id qui pourrait sortir de dist/loi/.
  if (!/^[a-z0-9_-]+$/i.test(law.id)) {
    fail(`Id de loi non sûr pour un chemin de fichier : « ${law.id} »`);
  }
  const dir = `${DIST_DIR}loi/${law.id}`;
  mkdirSync(dir, { recursive: true });
  writeFileSync(`${dir}/index.html`, lawPageHTML(law, { baseUrl: BASE_URL, buildDate }));
}

writeFileSync(`${DIST_DIR}sitemap.xml`, sitemapXML(snapshot.laws, { baseUrl: BASE_URL, buildDate }));

console.log(
  `✓ ${snapshot.laws.length} page(s) de loi générée(s) dans dist/loi/ + sitemap.xml (base : ${BASE_URL}).`,
);
