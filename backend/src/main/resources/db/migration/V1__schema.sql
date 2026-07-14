-- Socle des données législatives (issue #2) — SQLite.
-- Golden Rule (AGENTS.md) : aucune loi publiée sans source officielle valide et concordante.

CREATE TABLE laws (
    id             TEXT PRIMARY KEY,
    title          TEXT    NOT NULL,
    category       TEXT    NOT NULL,
    status         TEXT    NOT NULL CHECK (status IN ('passed', 'upcoming')),
    date           TEXT    NOT NULL,             -- ISO-8601 (YYYY-MM-DD)
    summary        TEXT    NOT NULL,
    source_url     TEXT    NOT NULL,
    source_expect  TEXT    NOT NULL,             -- fragment de titre officiel attendu sur source_url
    text_url       TEXT    NOT NULL,
    text_expect    TEXT    NOT NULL,             -- fragment de titre officiel attendu sur text_url
    published      INTEGER NOT NULL DEFAULT 1,   -- 0 = dépubliée (source invalide) : absente de GET /api/laws
    created_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at     TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Votes par bloc politique pour le scrutin public associé à une loi.
CREATE TABLE scrutins (
    law_id          TEXT    NOT NULL REFERENCES laws (id) ON DELETE CASCADE,
    bloc            TEXT    NOT NULL CHECK (bloc IN ('gauche', 'milieu', 'droite', 'extremeDroite')),
    votes_for       INTEGER NOT NULL,
    votes_against   INTEGER NOT NULL,
    votes_abstained INTEGER NOT NULL,
    PRIMARY KEY (law_id, bloc)
);

-- Scores d'indicateurs (workflow draft -> published, cf. plan § 3.2 / issue #4).
CREATE TABLE indicator_scores (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    law_id     TEXT    NOT NULL REFERENCES laws (id) ON DELETE CASCADE,
    indicator  TEXT    NOT NULL CHECK (indicator IN ('pesticides', 'pognonPuissants', 'peupleSante', 'partageEau')),
    score      REAL    NOT NULL,
    status     TEXT    NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    model      TEXT,                              -- backend IA à l'origine du score (NULL = éditorial vérifié)
    created_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_indicator_scores_law ON indicator_scores (law_id, indicator, status);

-- Historique des vérifications de sources (job check-sources).
CREATE TABLE source_checks (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    law_id      TEXT    NOT NULL REFERENCES laws (id) ON DELETE CASCADE,
    field       TEXT    NOT NULL CHECK (field IN ('sourceUrl', 'textUrl')),
    url         TEXT    NOT NULL,
    http_status INTEGER,                          -- NULL si erreur réseau/timeout
    ok          INTEGER NOT NULL,                 -- 1 = statut 2xx ET fragment attendu présent
    reason      TEXT,                             -- ex. "HTTP 404", "200 mais fragment attendu absent"
    checked_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_source_checks_law ON source_checks (law_id, checked_at);
