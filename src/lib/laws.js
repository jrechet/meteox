export const LAWS_DATA = [
  {
    id: 'eco-agri-1',
    title: 'Recul du Plan Écophyto (Indicateurs pesticides)',
    category: 'pesticides',
    status: 'passed',
    date: '2024-04-12',
    summary: 'Mise en pause du plan de réduction des pesticides et remplacement de l\'indicateur historique NODU par l\'indicateur européen HRI-1, accusé de masquer l\'usage de molécules toxiques sous prétexte de pondération par dangerosité.',
    sourceUrl: 'https://www.legifrance.gouv.fr/dossierlegislatif/JORFDOLE000049539075/',
    indicators: {
      pesticides: -2,       // Augmentation du risque/usage autorisé
      pognonPuissants: -2,  // Favorable à l'agro-industrie et lobbies chimiques
      peupleSante: -1.5,    // Augmentation de l'exposition sanitaire
      partageEau: 0
    },
    votes: {
      gauche: { for: 4, against: 145, abstained: 1 },
      milieu: { for: 128, against: 8, abstained: 12 },
      droite: { for: 58, against: 2, abstained: 4 },
      extremeDroite: { for: 82, against: 6, abstained: 2 }
    }
  },
  {
    id: 'eco-eau-1',
    title: 'Simplification des forages et Méga-bassines',
    category: 'eau',
    status: 'passed',
    date: '2024-06-18',
    summary: 'Décret facilitant les autorisations de travaux et les prélèvements d\'eau pour le remplissage des retenues de substitution artificielles (méga-bassines), au profit des irrigants intensifs.',
    sourceUrl: 'https://www.assemblee-nationale.fr/dyn/16/dossiers/retrait-loi_eau_preservation',
    indicators: {
      pesticides: 0,
      pognonPuissants: -2,  // Monopolisation des subventions et infrastructures
      peupleSante: -1,      // Baisse des nappes phréatiques de consommation commune
      partageEau: -2        // Privatisation de la ressource commune
    },
    votes: {
      gauche: { for: 0, against: 152, abstained: 0 },
      milieu: { for: 115, against: 12, abstained: 18 },
      droite: { for: 62, against: 0, abstained: 3 },
      extremeDroite: { for: 79, against: 5, abstained: 4 }
    }
  },
  {
    id: 'eco-canicule-1',
    title: 'Obligation d\'aménagements thermiques des écoles',
    category: 'canicule',
    status: 'passed',
    date: '2025-02-10',
    summary: 'Loi imposant un plan pluriannuel de végétalisation des cours d\'école et d\'isolation thermique des salles de classe dans toutes les communes pour faire face aux pics de chaleur accrus.',
    sourceUrl: 'https://www.legifrance.gouv.fr',
    indicators: {
      pesticides: 0,
      pognonPuissants: 1,   // Investissement public imposé aux communes
      peupleSante: 2,       // Protection immédiate des enfants et du personnel
      partageEau: 1         // Récupération de l'eau de pluie pour les cours végétalisées
    },
    votes: {
      gauche: { for: 148, against: 0, abstained: 2 },
      milieu: { for: 135, against: 2, abstained: 8 },
      droite: { for: 45, against: 12, abstained: 8 },
      extremeDroite: { for: 60, against: 25, abstained: 5 }
    }
  },
  {
    id: 'eco-agri-future',
    title: 'Loi d\'Orientation Agricole (L.O.A) - Cas d\'intérêt supérieur',
    category: 'agriculture',
    status: 'upcoming',
    date: '2026-09-15',
    summary: 'Consécration de l\'agriculture et de la souveraineté alimentaire comme "intérêt général majeur". Cette disposition permettra de contourner des réglementations de protection de la biodiversité (haies, zones humides) en cas de projets d\'aménagements contestés.',
    sourceUrl: 'https://www.assemblee-nationale.fr',
    indicators: {
      pesticides: -1,
      pognonPuissants: -2,  // Protection juridique des grands exploitants
      peupleSante: -1,
      partageEau: -1.5      // Facilitation de retenues d'eau dérogatoires
    }
  },
  {
    id: 'eco-eau-future',
    title: 'Réglementation européenne sur la restauration de la nature (transposition)',
    category: 'eau',
    status: 'upcoming',
    date: '2026-11-20',
    summary: 'Scrutin à venir sur la transposition française de la directive européenne visant à restaurer 20% des zones terrestres et marines dégradées d\'ici 2030, notamment par la réouverture de cours d\'eau et la protection des zones humides.',
    sourceUrl: 'https://www.assemblee-nationale.fr',
    indicators: {
      pesticides: 1,        // Zones tampons sans intrants chimiques
      pognonPuissants: 1,   // Obligation de mise en conformité des industriels
      peupleSante: 2,       // Réduction des polluants et résilience inondation
      partageEau: 2         // Restauration du cycle de l'eau naturel
    }
  }
];
