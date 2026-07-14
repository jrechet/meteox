-- Import initial : les 4 lois vérifiées de src/lib/laws.js (audit sources du 2026-07-14).
-- Golden Rule : données STRICTEMENT identiques à laws.js — aucune donnée inventée.
-- Le test LawsApiTest diffe la réponse de GET /api/laws contre le contenu de laws.js.

INSERT INTO laws (id, title, category, status, date, summary, source_url, source_expect, text_url, text_expect, published) VALUES
('eco-agri-1',
 'Protection de la population contre les PFAS (polluants éternels)',
 'pesticides', 'passed', '2024-04-04',
 'Adoption en première lecture de la proposition de loi visant à restreindre la fabrication et la vente de produits contenant des PFAS, avec l''exclusion controversée des ustensiles de cuisine lors des débats parlementaires.',
 'https://www.assemblee-nationale.fr/dyn/16/scrutins/3643', 'polyfluoroalkylées',
 'https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N49455', 'polyfluoroalkylées',
 1),
('eco-eau-1',
 'Loi d''accélération de la production d''énergies renouvelables (APER)',
 'canicule', 'passed', '2023-01-10',
 'Adoption en première lecture du projet de loi visant à planifier et accélérer le déploiement de l''éolien, du solaire et de l''hydroélectricité pour réduire les émissions de gaz à effet de serre et lutter contre le réchauffement climatique.',
 'https://www.assemblee-nationale.fr/dyn/16/scrutins/823', 'énergies renouvelables',
 'https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N46539', 'énergies renouvelables',
 1),
('eco-canicule-1',
 'Loi relative à l''industrie verte',
 'agriculture', 'passed', '2023-10-10',
 'Adoption définitive du texte facilitant l''implantation de sites industriels décarbonés (solaire, batteries) et réorientant l''épargne privée vers la transition écologique, tout en simplifiant certaines procédures environnementales.',
 'https://www.assemblee-nationale.fr/dyn/16/scrutins/2721', 'industrie verte',
 'https://www.assemblee-nationale.fr/dyn/16/dossiers/DLR5L16N47917', 'industrie verte',
 1),
('eco-agri-loa',
 'Loi d''orientation pour la souveraineté alimentaire et le renouvellement des générations en agriculture (LOA)',
 'agriculture', 'passed', '2025-02-19',
 'Adoption définitive (texte de la commission mixte paritaire, scrutin n°844 du 19 février 2025 — loi n°2025-268 promulguée le 24 mars 2025). Qualifie l''agriculture d''"intérêt général majeur", assouplit des règles environnementales (haies, atteintes à la biodiversité dépénalisées) et sécurise les projets de stockage d''eau.',
 'https://www.assemblee-nationale.fr/dyn/17/scrutins/844', 'souveraineté alimentaire et agricole',
 'https://www.assemblee-nationale.fr/dyn/16/dossiers/souverainete_agricole_renouvellement_generations', 'Souveraineté en matière agricole',
 1);

-- Votes par bloc (répartitions extraites des pages officielles de scrutin, cf. laws.js).
INSERT INTO scrutins (law_id, bloc, votes_for, votes_against, votes_abstained) VALUES
('eco-agri-1', 'gauche', 95, 0, 0),
('eco-agri-1', 'milieu', 91, 0, 0),
('eco-agri-1', 'droite', 0, 0, 15),
('eco-agri-1', 'extremeDroite', 0, 0, 12),
('eco-eau-1', 'gauche', 71, 62, 2),
('eco-eau-1', 'milieu', 215, 1, 0),
('eco-eau-1', 'droite', 0, 55, 2),
('eco-eau-1', 'extremeDroite', 0, 88, 0),
('eco-canicule-1', 'gauche', 0, 62, 5),
('eco-canicule-1', 'milieu', 176, 0, 0),
('eco-canicule-1', 'droite', 55, 0, 2),
('eco-canicule-1', 'extremeDroite', 0, 0, 12),
-- Scrutin 17e lég. n°844 (369 pour / 160 contre / 2 abst.) — mapping des groupes :
-- gauche = LFI + SOC + Écologiste et Social + GDR · milieu = EPR + Dem + HOR + LIOT
-- droite = DR · extrême droite = RN + UDR · (9 « pour » de non-inscrits hors blocs)
('eco-agri-loa', 'gauche', 1, 160, 0),
('eco-agri-loa', 'milieu', 178, 0, 2),
('eco-agri-loa', 'droite', 44, 0, 0),
('eco-agri-loa', 'extremeDroite', 137, 0, 0);

-- Indicateurs éditoriaux vérifiés (model NULL = éditorial, statut published).
INSERT INTO indicator_scores (law_id, indicator, score, status, model) VALUES
('eco-agri-1', 'pesticides', 1, 'published', NULL),
('eco-agri-1', 'pognonPuissants', 1, 'published', NULL),
('eco-agri-1', 'peupleSante', 1.5, 'published', NULL),
('eco-agri-1', 'partageEau', 1.5, 'published', NULL),
('eco-eau-1', 'pesticides', 0, 'published', NULL),
('eco-eau-1', 'pognonPuissants', -1, 'published', NULL),
('eco-eau-1', 'peupleSante', 1.5, 'published', NULL),
('eco-eau-1', 'partageEau', 0, 'published', NULL),
('eco-canicule-1', 'pesticides', 0, 'published', NULL),
('eco-canicule-1', 'pognonPuissants', -1.5, 'published', NULL),
('eco-canicule-1', 'peupleSante', 1, 'published', NULL),
('eco-canicule-1', 'partageEau', 0.5, 'published', NULL),
('eco-agri-loa', 'pesticides', -1, 'published', NULL),
('eco-agri-loa', 'pognonPuissants', -2, 'published', NULL),
('eco-agri-loa', 'peupleSante', -1, 'published', NULL),
('eco-agri-loa', 'partageEau', -1.5, 'published', NULL);
