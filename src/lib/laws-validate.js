// Validation d'une loi à la frontière (issue #5) — source unique partagée par le client
// runtime (src/lib/laws-data.js) ET la génération de snapshot au build
// (scripts/generate-snapshot.mjs). Une loi n'est acceptée que si elle porte TOUT ce que la
// couche de rendu suppose : sinon on préfère le fallback plutôt qu'un crash ou un affichage faux.
const BLOCS = ['gauche', 'milieu', 'droite', 'extremeDroite'];

/** URL absolue https (les sources officielles AN le sont toutes ; bloque aussi javascript:/data:). */
export function isHttpsUrl(value) {
  if (typeof value !== 'string') return false;
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}

function isBlocVotes(v) {
  return (
    v != null &&
    typeof v === 'object' &&
    ['for', 'against', 'abstained'].every((k) => Number.isFinite(v[k]) && v[k] >= 0)
  );
}

/**
 * Vrai si `l` est une loi exploitable par le rendu. Deux formes valides :
 *  - `passed` : DOIT porter une vraie date de scrutin (YYYY-MM-DD) et ses 4 blocs de votes (la
 *    carte les déréférence) — c'est le bug que cette validation ferme.
 *  - `upcoming` : PAS de date (l'open data n'en source aucune) mais une `stage` non vide (l'étape
 *    officielle du dossier, affichée à la place de la date).
 * Plutôt basculer sur le snapshot que rendre une carte qui plante.
 */
export function isValidLaw(l) {
  if (!l || typeof l !== 'object') return false;
  if (typeof l.id !== 'string' || l.id === '') return false;
  if (typeof l.title !== 'string' || l.title === '') return false;
  if (typeof l.summary !== 'string') return false;
  if (typeof l.category !== 'string') return false;
  if (l.status !== 'passed' && l.status !== 'upcoming') return false;
  if (!isHttpsUrl(l.sourceUrl) || !isHttpsUrl(l.textUrl)) return false;
  if (l.indicators == null || typeof l.indicators !== 'object') return false;
  if (l.status === 'passed') {
    if (typeof l.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(l.date)) return false;
    if (l.votes == null || typeof l.votes !== 'object') return false;
    if (!BLOCS.every((b) => isBlocVotes(l.votes[b]))) return false;
  } else {
    // upcoming : l'étape officielle remplace la date (jamais de date fabriquée).
    if (typeof l.stage !== 'string' || l.stage.trim() === '') return false;
  }
  return true;
}
