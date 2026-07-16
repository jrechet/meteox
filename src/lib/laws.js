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

/**
 * Golden Rule (AGENTS.md) : chaque URL est vérifiée (statut HTTP + fragment de titre
 * officiel attendu, champs sourceExpect/textExpect) par scripts/check-sources.mjs.
 * Aucune carte ne doit exister sans source officielle valide et concordante.
 * Audit complet des sources : docs/plans/lois-sources-fiabilite.md (2026-07-14).
 */
export const LAWS_DATA = [
  {
    id: 'eco-agri-1',
    title: 'Protection de la population contre les PFAS (polluants éternels)',
    category: 'pesticides',
    status: 'passed',
    date: '2024-04-04',
    summary: 'Adoption en première lecture de la proposition de loi visant à restreindre la fabrication et la vente de produits contenant des PFAS, avec l\'exclusion controversée des ustensiles de cuisine lors des débats parlementaires.',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/16/scrutins/3643',
    sourceExpect: 'polyfluoroalkylées',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N49455',
    textExpect: 'polyfluoroalkylées',
    indicators: {
      pesticides: 1,        // Limitation des polluants chimiques
      pognonPuissants: 1,   // Régulation (partielle) des lobbies de la chimie
      peupleSante: 1.5,     // Réduction de l'exposition aux perturbateurs endocriniens
      partageEau: 1.5       // Réduction de la contamination éternelle des nappes phréatiques
    },
    // Votes corrigés le 2026-07-16 depuis l'open data AN (scrutin n°3643) — les chiffres
    // précédents (hérités de l'ancien plan) ne concordaient pas avec le décompte officiel.
    votes: {
      gauche: { for: 98, against: 0, abstained: 0 },
      milieu: { for: 86, against: 0, abstained: 0 },
      droite: { for: 0, against: 0, abstained: 4 },
      extremeDroite: { for: 0, against: 0, abstained: 22 }
    }
  },
  {
    id: 'eco-eau-1',
    title: 'Loi d\'accélération de la production d\'énergies renouvelables (APER)',
    category: 'canicule',
    status: 'passed',
    date: '2023-01-10',
    summary: 'Adoption en première lecture du projet de loi visant à planifier et accélérer le déploiement de l\'éolien, du solaire et de l\'hydroélectricité pour réduire les émissions de gaz à effet de serre et lutter contre le réchauffement climatique.',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/16/scrutins/823',
    sourceExpect: 'énergies renouvelables',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N46539',
    textExpect: 'énergies renouvelables',
    indicators: {
      pesticides: 0,
      pognonPuissants: -1,  // Favorise les fonds privés de développement énergétique
      peupleSante: 1.5,     // Limitation de la pollution de l'air et atténuation des canicules
      partageEau: 0
    },
    // Votes corrigés le 2026-07-16 depuis l'open data AN (scrutin n°823).
    votes: {
      gauche: { for: 28, against: 91, abstained: 24 },
      milieu: { for: 257, against: 2, abstained: 6 },
      droite: { for: 1, against: 56, abstained: 4 },
      extremeDroite: { for: 0, against: 87, abstained: 0 }
    }
  },
  {
    id: 'eco-canicule-1',
    title: 'Loi relative à l\'industrie verte',
    category: 'agriculture',
    status: 'passed',
    date: '2023-10-10',
    summary: 'Adoption définitive du texte facilitant l\'implantation de sites industriels décarbonés (solaire, batteries) et réorientant l\'épargne privée vers la transition écologique, tout en simplifiant certaines procédures environnementales.',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/16/scrutins/2721',
    sourceExpect: 'industrie verte',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N47917',
    textExpect: 'industrie verte',
    indicators: {
      pesticides: 0,
      pognonPuissants: -1.5, // Avantages fiscaux et simplifications administratives pour les fonds et grands groupes industriels
      peupleSante: 1,       // Réduction de l'empreinte carbone industrielle nationale
      partageEau: 0.5       // Soutien à l'économie circulaire et recyclage des ressources
    },
    // Votes corrigés le 2026-07-16 depuis l'open data AN (scrutin n°2721).
    votes: {
      gauche: { for: 0, against: 61, abstained: 16 },
      milieu: { for: 154, against: 0, abstained: 2 },
      droite: { for: 29, against: 1, abstained: 1 },
      extremeDroite: { for: 47, against: 0, abstained: 0 }
    }
  },
  {
    id: 'eco-agri-loa',
    title: 'Loi d\'orientation pour la souveraineté alimentaire et le renouvellement des générations en agriculture (LOA)',
    category: 'agriculture',
    status: 'passed',
    date: '2025-02-19',
    summary: 'Adoption définitive (texte de la commission mixte paritaire, scrutin n°844 du 19 février 2025 — loi n°2025-268 promulguée le 24 mars 2025). Qualifie l\'agriculture d\'"intérêt général majeur", assouplit des règles environnementales (haies, atteintes à la biodiversité dépénalisées) et sécurise les projets de stockage d\'eau.',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/17/scrutins/844',
    sourceExpect: 'souveraineté alimentaire et agricole',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/souverainete_agricole_renouvellement_generations',
    textExpect: 'Souveraineté en matière agricole',
    indicators: {
      pesticides: -1,       // Allègement des contraintes sur l'usage des intrants
      pognonPuissants: -2,  // Protection juridique et subventions fléchées vers le grand agro-business
      peupleSante: -1,      // Risques sanitaires accrus liés aux pesticides et à l'appauvrissement des sols
      partageEau: -1.5      // Facilitation de forages dérogatoires et d'irrigation intensive
    },
    // Scrutin 17e lég. n°844 (369 pour / 160 contre / 2 abst.) — mapping des groupes :
    // gauche = LFI + SOC + Écologiste et Social + GDR · milieu = EPR + Dem + HOR + LIOT
    // droite = DR · extrême droite = RN + UDR · (9 « pour » de non-inscrits hors blocs)
    votes: {
      gauche: { for: 1, against: 160, abstained: 0 },
      milieu: { for: 178, against: 0, abstained: 2 },
      droite: { for: 44, against: 0, abstained: 0 },
      extremeDroite: { for: 137, against: 0, abstained: 0 }
    }
  }
];
