-- Issue #4 — module indicateurs IA : justification citée, confiance, relecture humaine et audit.
-- V4 (et non V3) : V3 est réservée à la branche feat/opendata-pipeline (issue #3).

ALTER TABLE indicator_scores ADD COLUMN justification TEXT;   -- lecture éditoriale expliquant le score
ALTER TABLE indicator_scores ADD COLUMN citation      TEXT;   -- extrait EXACT du texte source
ALTER TABLE indicator_scores ADD COLUMN confidence    TEXT;   -- faible | moyenne | haute
ALTER TABLE indicator_scores ADD COLUMN reviewed_by   TEXT;   -- relecteur humain (obligatoire pour publier)
ALTER TABLE indicator_scores ADD COLUMN reviewed_at   TEXT;   -- horodatage de la validation humaine

-- Piste d'audit consultable (acceptance issue #4) : qui a fait quoi, sur quel score, quand.
CREATE TABLE indicator_audit (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    action     TEXT    NOT NULL CHECK (action IN ('draft-created', 'published')),
    score_id   INTEGER NOT NULL,
    law_id     TEXT    NOT NULL,
    actor      TEXT    NOT NULL,
    detail     TEXT,
    created_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_indicator_audit_score ON indicator_audit (score_id, created_at);
