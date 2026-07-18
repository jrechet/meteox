-- Signataires (auteur + cosignataires) des dossiers de loi candidats (issue #33, sous-issues C/D).
-- Alimentée par le job sync-dossiers : pour chaque candidat de loi doté d'un document de dépôt
-- (acte AN1-DEPOT), on résout l'auteur et les cosignataires via le référentiel des acteurs (AMO30)
-- et on les stocke ici. Deux usages : (1) signal d'importance = NOMBRE de cosignataires, agrégé par
-- groupe politique pour la relecture ; (2) matière première réutilisable pour l'analyse réseau
-- ultérieure (« qui cosigne quoi/avec qui ») — d'où l'index sur `acteur_ref`.
CREATE TABLE dossier_signataires (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    dossier_uid  TEXT    NOT NULL,                 -- dossier AN (ex. DLR5L17N52201)
    role         TEXT    NOT NULL
                 CHECK (role IN ('auteur', 'cosignataire')),
    acteur_ref   TEXT    NOT NULL,                 -- PA… (personne) ou PO… (texte de groupe)
    nom          TEXT,                             -- « Prénom Nom » si résolu (nul pour un texte de groupe)
    groupe_sigle TEXT,                             -- sigle du groupe politique si résolu (ex. LFI-NFP)
    bloc         TEXT,                             -- bloc du mapping organe-blocs.json si connu
    synced_at    TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Lecture par dossier (agrégation cosignataires par groupe pour la page admin).
CREATE INDEX idx_dossier_signataires_dossier ON dossier_signataires (dossier_uid);
-- Lecture par acteur (analyse réseau ultérieure : tous les textes cosignés par un acteur donné).
CREATE INDEX idx_dossier_signataires_acteur ON dossier_signataires (acteur_ref);
