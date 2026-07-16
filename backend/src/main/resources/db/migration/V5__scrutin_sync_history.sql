-- Historique des synchronisations de scrutins depuis l'open data AN (issue #3, tâche 1).
-- Politique actée : l'open data officiel fait foi — en cas de divergence le job écrase les
-- votes en base, journalise l'ancien/nouveau ici, et ouvre une issue GitHub pour relecture.
CREATE TABLE scrutin_syncs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    law_id      TEXT    NOT NULL REFERENCES laws (id) ON DELETE CASCADE,
    legislature INTEGER NOT NULL,
    numero      INTEGER NOT NULL,
    scrutin_url TEXT    NOT NULL,              -- URL officielle /dyn/{lég}/scrutins/{n}
    changed     INTEGER NOT NULL,              -- 1 = votes remplacés par l'open data
    old_votes   TEXT,                          -- JSON des votes remplacés (NULL si inchangés)
    new_votes   TEXT    NOT NULL,              -- JSON des votes agrégés depuis l'open data
    synced_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_scrutin_syncs_law ON scrutin_syncs (law_id, synced_at);
