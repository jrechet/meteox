// Golden Rule enforcement (AGENTS.md): every law card must point to a live official
// page whose content matches the announced text. HTTP 200 alone is NOT enough — this
// audit caught 200-but-wrong-dossier links, so each URL is also checked against an
// expected title fragment (sourceExpect/textExpect in laws.js).
// Run: npm run check:sources  (exit code 1 if any source is invalid)
import { setDefaultResultOrder } from 'node:dns';
import { readFileSync } from 'node:fs';

// Le check opère sur le snapshot embarqué au build (issue #5) — la donnée qui part
// réellement dans le bundle — régénéré depuis l'API par scripts/generate-snapshot.mjs.
const LAWS_DATA = JSON.parse(
  readFileSync(new URL('../src/data/laws-snapshot.json', import.meta.url), 'utf8'),
).laws;

setDefaultResultOrder('ipv4first');

const TIMEOUT_MS = 20000;
const UA = 'Mozilla/5.0 (compatible; meteox-source-check; +https://jrechet.github.io/meteox/)';

function normalize(html) {
  return html
    .replace(/&#0?39;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/[’‘]/g, "'")
    .toLowerCase();
}

async function checkUrl(url, expectFragment) {
  try {
    const res = await fetch(url, {
      redirect: 'follow',
      headers: { 'User-Agent': UA },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!res.ok) return { ok: false, reason: `HTTP ${res.status}` };
    const body = normalize(await res.text());
    if (!body.includes(normalize(expectFragment))) {
      return { ok: false, reason: `200 mais fragment attendu absent : "${expectFragment}"` };
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, reason: e.name === 'TimeoutError' ? 'timeout' : e.message };
  }
}

let failures = 0;
for (const law of LAWS_DATA) {
  console.log(`\n${law.id} — ${law.title}`);
  const targets = [
    ['sourceUrl', law.sourceUrl, law.sourceExpect],
    ['textUrl', law.textUrl, law.textExpect],
  ];
  for (const [field, url, expect] of targets) {
    if (!url || !expect) {
      failures++;
      console.error(`  ✗ ${field} : URL ou fragment attendu manquant`);
      continue;
    }
    const { ok, reason } = await checkUrl(url, expect);
    if (ok) console.log(`  ✓ ${field} ${url}`);
    else {
      failures++;
      console.error(`  ✗ ${field} ${url} → ${reason}`);
    }
  }
}

if (failures > 0) {
  console.error(`\nCHECK SOURCES FAILED — ${failures} source(s) invalide(s).`);
  console.error('Golden Rule : aucune carte ne doit être publiée avec une source invalide (AGENTS.md).');
  process.exit(1);
}
console.log('\nCHECK SOURCES PASSED — toutes les sources sont vivantes et concordantes.');
