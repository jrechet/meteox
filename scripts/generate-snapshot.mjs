// Génération du snapshot de lois au build (issue #5). Interroge l'API backend et remplace
// src/data/laws-snapshot.json ; si l'API est injoignable OU renvoie des données invalides,
// on garde le dernier snapshot committé À CONDITION qu'il soit non vide, valide et assez frais.
// Le build ÉCHOUE s'il ne reste aucun snapshot exploitable (Golden Rule : ne jamais publier de
// données invalides ou périmées en silence). Validation partagée avec le client runtime.
// Run : npm run generate:snapshot
import { readFileSync, writeFileSync } from 'node:fs';
import { isValidLaw } from '../src/lib/laws-validate.js';

const API_BASE = process.env.LAWS_API_BASE || 'https://jrec.fr/meteox-laws-int';
const SNAPSHOT_PATH = new URL('../src/data/laws-snapshot.json', import.meta.url);
const MAX_AGE_DAYS = Number(process.env.SNAPSHOT_MAX_AGE_DAYS || 30);
const TIMEOUT_MS = 15000;

function fail(message) {
  console.error(`✗ ${message} — build refusé.`);
  process.exit(1);
}

/** Refuse un snapshot vide, de forme invalide, ou plus vieux que le seuil. */
function assertUsable(snapshot, origin) {
  if (!snapshot || !Array.isArray(snapshot.laws) || snapshot.laws.length === 0) {
    fail(`Snapshot ${origin} vide`);
  }
  if (!snapshot.laws.every(isValidLaw)) {
    fail(`Snapshot ${origin} : loi de forme invalide`);
  }
  const ageDays = (Date.now() - new Date(snapshot.generatedAt).getTime()) / 864e5;
  if (!snapshot.generatedAt || Number.isNaN(ageDays) || ageDays > MAX_AGE_DAYS) {
    fail(`Snapshot ${origin} périmé (généré : ${snapshot.generatedAt || 'inconnu'}, seuil ${MAX_AGE_DAYS} j)`);
  }
}

async function fromApi() {
  const res = await fetch(`${API_BASE}/api/laws`, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: { Accept: 'application/json' },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const laws = await res.json();
  if (!Array.isArray(laws) || laws.length === 0 || !laws.every(isValidLaw)) {
    throw new Error('réponse API invalide');
  }
  return { generatedAt: new Date().toISOString().replace(/\.\d+Z$/, 'Z'), laws };
}

try {
  const snapshot = await fromApi();
  assertUsable(snapshot, 'API');
  writeFileSync(SNAPSHOT_PATH, JSON.stringify(snapshot, null, 2) + '\n');
  console.log(`✓ Snapshot régénéré depuis ${API_BASE} — ${snapshot.laws.length} loi(s).`);
} catch (e) {
  // API injoignable ou données douteuses : on retombe sur le dernier snapshot committé,
  // qui doit lui-même rester exploitable — sinon le build échoue (jamais de donnée pourrie).
  console.warn(`⚠ Snapshot API indisponible (${e.message}) — repli sur le snapshot committé.`);
  const snapshot = JSON.parse(readFileSync(SNAPSHOT_PATH, 'utf8'));
  assertUsable(snapshot, 'committé');
  console.log(`✓ Snapshot committé conservé (généré : ${snapshot.generatedAt}, ${snapshot.laws.length} loi(s)).`);
}
