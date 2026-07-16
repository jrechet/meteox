// Source des données législatives (issue #5) : API backend jrec.fr au chargement de
// l'onglet Lois, avec bascule transparente sur le snapshot embarqué au build si l'API
// est injoignable (timeout, 5xx, offline). Golden Rule : on sert la dernière donnée
// vérifiée, jamais une donnée invalide — le snapshot est lui-même issu de l'API
// (lois `published` uniquement) et validé par check:sources au build.
import snapshot from '../data/laws-snapshot.json';

export const LAWS_API_BASE = 'https://jrec.fr/meteox-laws-int';
const FETCH_TIMEOUT_MS = 5000;

let loaded = null; // { laws, meta } après le premier chargement
let inflight = null;

/** Forme minimale attendue d'une loi (validation à la frontière, cf. coding rules). */
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

function fromSnapshot() {
  return {
    laws: snapshot.laws,
    meta: { source: 'snapshot', dataDate: snapshot.generatedAt },
  };
}

async function fetchFromApi() {
  const res = await fetch(`${LAWS_API_BASE}/api/laws`, {
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    headers: { Accept: 'application/json' },
  });
  if (!res.ok) throw new Error(`API lois → HTTP ${res.status}`);
  const laws = await res.json();
  if (!Array.isArray(laws) || laws.length === 0 || !laws.every(isValidLaw)) {
    throw new Error('API lois → réponse invalide');
  }
  return { laws, meta: { source: 'api', dataDate: new Date().toISOString() } };
}

/**
 * Charge les lois : API d'abord, snapshot en secours. Résultat mémoïsé (un seul
 * fetch par session) ; ne rejette jamais — le snapshot est toujours disponible.
 */
export function loadLaws() {
  if (loaded) return Promise.resolve(loaded);
  if (!inflight) {
    inflight = fetchFromApi()
      .catch(() => fromSnapshot())
      .then((result) => {
        loaded = result;
        return result;
      });
  }
  return inflight;
}

/** Accès synchrone aux lois déjà chargées (modale d'interpellation). */
export function getLoadedLaws() {
  return loaded ? loaded.laws : snapshot.laws;
}
