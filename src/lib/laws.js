/** Best-effort human label for a French postal code's area (offline, no API). */
export function departementLabel(cp) {
  if (!/^\d{5}$/.test(cp)) return null;
  const d = cp.slice(0, 2);
  if (d === '20') return 'Corse';
  if (d === '97' || d === '98') return `Outre-mer (${cp.slice(0, 3)})`;
  return `département ${d}`;
}

/** Pre-filled interpellation letter for a law, optionally localized by postal code. */
export function interpellationLetter(law, cp = '') {
  const dep = departementLabel(cp);
  const here = dep ? ` (${dep})` : '';
  
  const pesticides =
    law.indicators.pesticides != null
      ? (law.indicators.pesticides < 0
          ? 'Recul environnemental et hausse des risques pour la santé'
          : law.indicators.pesticides > 0
            ? 'Amélioration ou préservation'
            : 'Aucun impact direct')
      : '';
  const eau =
    law.indicators.partageEau != null
      ? (law.indicators.partageEau < 0
          ? "Accaparement accru et déséquilibre d'usage"
          : law.indicators.partageEau > 0
            ? 'Préservation de la ressource commune'
            : 'Aucun impact direct')
      : '';

  return `Madame, Monsieur le Député,

En tant que citoyen(ne) de votre circonscription${here}, je tiens à vous exprimer ma préoccupation concernant le projet de loi suivant : "${law.title}".

Cette réforme aura un impact significatif sur notre environnement :
${pesticides ? `- Pesticides : ${pesticides}\n` : ''}${eau ? `- Partage de l'eau : ${eau}\n` : ''}
Je vous demande solennellement de prendre position en faveur de l'intérêt général et de la protection du climat, de la santé publique et du partage équitable de l'eau.

Veuillez agréer, Madame, Monsieur le Député, l'assurance de mes salutations citoyennes.`;
}
