-- Candidats « Prochains scrutins » détectés dans l'open data AN (issue #3, tâche 2).
-- Le job sync-dossiers remplit cette table de STAGING : rien ici n'est public. Un humain
-- promeut un candidat en carte `upcoming` publiée (Golden Rule : le flux brut de l'AN est
-- trop bruité — aucune carte publiée sans validation humaine + source vérifiée en contenu).
CREATE TABLE dossier_candidates (
    uid          TEXT PRIMARY KEY,              -- identifiant dossier AN (ex. DLR5L17N53637)
    legislature  INTEGER NOT NULL,
    titre        TEXT    NOT NULL,
    dossier_url  TEXT    NOT NULL,              -- URL officielle /dyn/{lég}/dossiers/{uid}
    theme        TEXT    NOT NULL,              -- mot-clé thématique ayant déclenché la détection
    terminated   INTEGER NOT NULL DEFAULT 0,    -- 1 = loi promulguée / dossier clos (jamais upcoming)
    status       TEXT    NOT NULL DEFAULT 'candidate'
                 CHECK (status IN ('candidate', 'promoted', 'rejected')),
    promoted_law_id TEXT REFERENCES laws (id) ON DELETE SET NULL,
    first_seen   TEXT    NOT NULL DEFAULT (datetime('now')),
    last_seen    TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_dossier_candidates_status ON dossier_candidates (status, terminated);
