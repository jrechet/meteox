// Justifications des indicateurs (issue #4) : GET /api/laws/{id}/indicators expose, pour
// chaque score PUBLIÉ, la justification citant le texte source, le niveau de confiance et
// la provenance (modèle IA relu, ou score éditorial vérifié). Chargé paresseusement au
// premier dépliage — un échec réseau rend null, l'UI affiche alors un repli honnête
// (la méthodologie, elle, reste toujours accessible : lien statique).
import { LAWS_API_BASE } from './laws-data.js';

export const METHODOLOGY_URL =
  'https://github.com/jrechet/meteox/blob/main/docs/methodologie-indicateurs.md';

const FETCH_TIMEOUT_MS = 5000;
const cache = new Map(); // lawId → Promise<payload|null>

/**
 * Détail des indicateurs publiés d'une loi, mémoïsé par loi.
 * Ne rejette jamais : null quand l'API est injoignable ou la réponse invalide.
 */
export function loadIndicators(lawId) {
  if (!cache.has(lawId)) {
    cache.set(
      lawId,
      fetch(`${LAWS_API_BASE}/api/laws/${encodeURIComponent(lawId)}/indicators`, {
        signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
        headers: { Accept: 'application/json' },
      })
        .then((res) => (res.ok ? res.json() : null))
        .then((payload) => (payload && Array.isArray(payload.indicators) ? payload : null))
        .catch(() => null),
    );
  }
  return cache.get(lawId);
}
