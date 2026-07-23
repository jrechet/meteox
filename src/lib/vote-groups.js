// Libellés des 4 blocs politiques, partagés entre la SPA (src/components/politics.js)
// et les pages statiques par loi (src/lib/law-page.js) — une seule source pour éviter
// toute divergence de libellé entre la carte et sa page permalien.
// Les libellés diffèrent selon la chambre : l'AN garde ses groupes détaillés, le Sénat
// reste en blocs génériques (les groupes sénatoriaux ne sont pas fabriqués côté front).
export const AN_GROUPS = [
  ['gauche', 'Gauche (NFP/LFI/PS/EELV)'],
  ['milieu', 'Centre (EPR/MoDem/Horizons)'],
  ['droite', 'Droite (DR/LR)'],
  ['extremeDroite', 'Extrême Droite (RN/UDR)'],
];

export const SENAT_GROUPS = [
  ['gauche', 'Gauche'],
  ['milieu', 'Centre'],
  ['droite', 'Droite'],
  ['extremeDroite', 'Extrême droite'],
];
