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

export const LAWS_DATA = [
  {
    id: 'eco-agri-1',
    title: 'Protection de la population contre les PFAS (polluants éternels)',
    category: 'pesticides',
    status: 'passed',
    date: '2024-04-04',
    summary: 'Adoption en première lecture de la proposition de loi visant à restreindre la fabrication et la vente de produits contenant des PFAS, avec l\'exclusion controversée des ustensiles de cuisine lors des débats parlementaires.',
    sourceUrl: 'https://www2.assemblee-nationale.fr/scrutins/detail/(legislature)/16/(num)/3643',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/PFAS_substances_per_polyfluoroalkylees',
    indicators: {
      pesticides: 1,        // Limitation des polluants chimiques
      pognonPuissants: 1,   // Régulation (partielle) des lobbies de la chimie
      peupleSante: 1.5,     // Réduction de l'exposition aux perturbateurs endocriniens
      partageEau: 1.5       // Réduction de la contamination éternelle des nappes phréatiques
    },
    votes: {
      gauche: { for: 95, against: 0, abstained: 0 },
      milieu: { for: 91, against: 0, abstained: 0 },
      droite: { for: 0, against: 0, abstained: 15 },
      extremeDroite: { for: 0, against: 0, abstained: 12 }
    }
  },
  {
    id: 'eco-eau-1',
    title: 'Loi d\'accélération de la production d\'énergies renouvelables (APER)',
    category: 'canicule',
    status: 'passed',
    date: '2023-01-10',
    summary: 'Adoption en première lecture du projet de loi visant à planifier et accélérer le déploiement de l\'éolien, du solaire et de l\'hydroélectricité pour réduire les émissions de gaz à effet de serre et lutter contre le réchauffement climatique.',
    sourceUrl: 'https://www2.assemblee-nationale.fr/scrutins/detail/(legislature)/16/(num)/823',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/production_energies_renouvelables',
    indicators: {
      pesticides: 0,
      pognonPuissants: -1,  // Favorise les fonds privés de développement énergétique
      peupleSante: 1.5,     // Limitation de la pollution de l'air et atténuation des canicules
      partageEau: 0
    },
    votes: {
      gauche: { for: 71, against: 62, abstained: 2 },
      milieu: { for: 215, against: 1, abstained: 0 },
      droite: { for: 0, against: 55, abstained: 2 },
      extremeDroite: { for: 0, against: 88, abstained: 0 }
    }
  },
  {
    id: 'eco-canicule-1',
    title: 'Loi relative à l\'industrie verte',
    category: 'agriculture',
    status: 'passed',
    date: '2023-10-10',
    summary: 'Adoption définitive du texte facilitant l\'implantation de sites industriels décarbonés (solaire, batteries) et réorientant l\'épargne privée vers la transition écologique, tout en simplifiant certaines procédures environnementales.',
    sourceUrl: 'https://www2.assemblee-nationale.fr/scrutins/detail/(legislature)/16/(num)/2721',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/industrie_verte',
    indicators: {
      pesticides: 0,
      pognonPuissants: -1.5, // Avantages fiscaux et simplifications administratives pour les fonds et grands groupes industriels
      peupleSante: 1,       // Réduction de l'empreinte carbone industrielle nationale
      partageEau: 0.5       // Soutien à l'économie circulaire et recyclage des ressources
    },
    votes: {
      gauche: { for: 0, against: 62, abstained: 5 },
      milieu: { for: 176, against: 0, abstained: 0 },
      droite: { for: 55, against: 0, abstained: 2 },
      extremeDroite: { for: 0, against: 0, abstained: 12 }
    }
  },
  {
    id: 'eco-agri-future',
    title: 'Loi d\'orientation pour la souveraineté agricole et le renouvellement des générations (L.O.A)',
    category: 'agriculture',
    status: 'upcoming',
    date: '2026-05-15',
    summary: 'Projet de loi qualifiant l\'agriculture d\'"intérêt général majeur" pour assouplir les règles environnementales (haies, zones humides) et accélérer les recours contre les projets de stockages d\'eau (mégabassines).',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/souverainete_agricole_renouvellement_generations',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/souverainete_agricole_renouvellement_generations',
    indicators: {
      pesticides: -1,       // Allègement des contraintes sur l'usage des intrants
      pognonPuissants: -2,  // Protection juridique et subventions fléchées vers le grand agro-business
      peupleSante: -1,      // Risques sanitaires accrus liés aux pesticides et à l'appauvrissement des sols
      partageEau: -1.5      // Facilitation de forages dérogatoires et d'irrigation intensive
    }
  },
  {
    id: 'eco-eau-future',
    title: 'Transposition du Règlement Européen sur la Restauration de la Nature',
    category: 'eau',
    status: 'upcoming',
    date: '2026-10-15',
    summary: 'Projet de loi transposant les objectifs européens visant à restaurer 20% des zones terrestres et marines dégradées d\'ici 2030, notamment les zones humides, forêts et sols agricoles.',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/restauration_nature_europe',
    textUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/restauration_nature_europe',
    indicators: {
      pesticides: 1,        // Création de zones tampons naturelles sans pesticides
      pognonPuissants: 1,   // Obligations réglementaires renforcées pour les grands propriétaires
      peupleSante: 2,       // Services écosystémiques renforcés (sols vivants, captage de carbone)
      partageEau: 2         // Restauration des cycles naturels de l'eau, zones humides et tourbières
    }
  }
];
