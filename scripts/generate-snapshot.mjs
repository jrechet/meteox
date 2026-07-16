// Génération du snapshot de lois au build (issue #5). Interroge l'API backend et
// remplace src/data/laws-snapshot.json ; si l'API est injoignable, on garde le
// dernier snapshot committé À CONDITION qu'il soit non vide et assez frais.
// Le build ÉCHOUE si le snapshot final est vide ou plus vieux que le seuil
// (Golden Rule : ne jamais publier de données invalides ou périmées en silence).
// Run : npm run generate:snapshot
import { readFileSync, writeFileSync } from 'node:fs';

const API_BASE = process.env.LAWS_API_BASE || 'https://jrec.fr/meteox-laws-int';
const SNAPSHOT_PATH = new URL('../src/data/laws-snapshot.json', import.meta.url);
const MAX_AGE_DAYS = Number(process.env.SNAPSHOT_MAX_AGE_DAYS || 30);
const TIMEOUT_MS = 15000;

function isValidLaw(l) {
  return (
    l &&
    typeof l.id === 'string' &&
    typeof l.title === 'string' &&
    ['passed', 'upcoming'].includes(l.status) &&
    typeof l.sourceUrl === 'string' &&
    l.indicators != null
  );
}

function assertUsable(snapshot, origin) {
  if (!Array.isArray(snapshot.laws) || snapshot.laws.length === 0) {
    console.error(`✗ Snapshot ${origin} vide — build refusé.`);
    process.exit(1);
  }
  if (!snapshot.laws.every(isValidLaw)) {
    console.error(`✗ Snapshot ${origin} : loi de forme invalide — build refusé.`);
    process.exit(1);
  }
  const ageDays = (Date.now() - new Date(snapshot.generatedAt).getTime()) / 864e5;
  if (!snapshot.generatedAt || Number.isNaN(ageDays) || ageDays > MAX_AGE_DAYS) {
    console.error(
      `✗ Snapshot ${origin} périmé (généré : ${snapshot.generatedAt || 'inconnu'}, seuil ${MAX_AGE_DAYS} j) — build refusé.`,
    );
    process.exit(1);
  }
}

let snapshot;
try {
  const res = await fetch(`${API_BASE}/api/laws`, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: { Accept: 'application/json' },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const laws = await res.json();
  snapshot = { generatedAt: new Date().toISOString().replace(/\.\d+Z$/, 'Z'), laws };
  assertUsable(snapshot, 'API');
  writeFileSync(SNAPSHOT_PATH, JSON.stringify(snapshot, null, 2) + '\n');
  console.log(`✓ Snapshot régénéré depuis ${API_BASE} — ${laws.length} loi(s).`);
} catch (e) {
  console.warn(`⚠ API injoignable (${e.message}) — vérification du snapshot committé.`);
  snapshot = JSON.parse(readFileSync(SNAPSHOT_PATH, 'utf8'));
  assertUsable(snapshot, 'committé');
  console.log(
    `✓ Snapshot committé conservé (généré : ${snapshot.generatedAt}, ${snapshot.laws.length} loi(s)).`,
  );
}
