// Sécurité d'injection HTML (issue #5) : les données des lois viennent désormais de l'API
// au runtime (titres/résumés potentiellement issus d'une extraction IA côté backend). Tout
// texte externe DOIT passer par escapeHtml avant d'être interpolé dans un innerHTML, et
// toute URL externe par safeUrl avant d'alimenter un href.
const ENTITIES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

/** Échappe les caractères sensibles pour une injection sûre dans du HTML (texte ou attribut). */
export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ENTITIES[c]);
}

/**
 * N'autorise que les URL absolues http(s). Bloque `javascript:`, `data:`, etc. — renvoie '#'
 * pour toute valeur non conforme, afin qu'un href ne devienne jamais un vecteur d'exécution.
 */
export function safeUrl(value) {
  if (typeof value !== 'string') return '#';
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:' ? value : '#';
  } catch {
    return '#';
  }
}
