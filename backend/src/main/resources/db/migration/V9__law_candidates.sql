-- Candidats « lois votées » détectés dans le jeu Scrutins de l'open data AN (issue #3, tâche 3).
-- Le job sync-corpus remplit cette table de STAGING : votes sur l'ensemble d'un texte de loi
-- (scrutins solennels et ordinaires), à thème environnemental, adoptés depuis 2023, hors lois
-- déjà au corpus. RIEN ici n'est public : un humain promeut un candidat en loi `passed` publiée
-- (Golden Rule : aucune carte publiée sans validation humaine + scrutin ET dossier vérifiés).
CREATE TABLE law_candidates (
    uid             TEXT PRIMARY KEY,             -- identifiant scrutin AN (ex. VTANR5L17V2460)
    legislature     INTEGER NOT NULL,
    numero          INTEGER NOT NULL,
    titre           TEXT    NOT NULL,             -- titre officiel du scrutin
    date_scrutin    TEXT    NOT NULL,             -- ISO-8601 (YYYY-MM-DD)
    theme           TEXT    NOT NULL,             -- mot-clé thématique ayant déclenché la détection
    scrutin_url     TEXT    NOT NULL,             -- URL officielle /dyn/{lég}/scrutins/{n}
    votes_json      TEXT    NOT NULL,             -- votes agrégés par bloc, capturés au scan
    status          TEXT    NOT NULL DEFAULT 'candidate'
                    CHECK (status IN ('candidate', 'promoted', 'rejected')),
    promoted_law_id TEXT REFERENCES laws (id) ON DELETE SET NULL,
    first_seen      TEXT    NOT NULL DEFAULT (datetime('now')),
    last_seen       TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_law_candidates_status ON law_candidates (status);
