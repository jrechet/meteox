-- Étape officielle du dossier (issue #3) : les cartes « à venir » n'ont PAS de date sourçable
-- (l'open data ne fournit aucune date de scrutin à venir). On affiche à la place l'ÉTAPE
-- courante du dossier, extraite de `actesLegislatifs` (sourcée, jamais fabriquée). Conséquences :
--   1. `dossier_candidates.stage` : l'étape détectée au scan, réutilisée à la promotion.
--   2. `laws.stage` : l'étape publiée sur la carte « à venir ».
--   3. `laws.date` devient NULLABLE (les cartes à venir n'en portent plus ; les lois `passed`
--      gardent leur vraie date de scrutin).

ALTER TABLE dossier_candidates ADD COLUMN stage TEXT;

-- SQLite ne sait pas retirer une contrainte NOT NULL via ALTER : on reconstruit la table `laws`.
-- On réplique EXACTEMENT le schéma de V1 (colonnes, CHECK status, published/timestamps par défaut),
-- on ajoute `stage`, et on retire le NOT NULL sur `date`. Les données sont préservées à l'identique.
-- Les tables enfant (scrutins, indicator_scores, source_checks) référencent laws(id) par NOM : le
-- DROP + RENAME conserve ces références (foreign_keys est OFF sur la connexion xerial → pas de
-- suppression en cascade des lignes enfant pendant la reconstruction).
CREATE TABLE laws_new (
    id             TEXT PRIMARY KEY,
    title          TEXT    NOT NULL,
    category       TEXT    NOT NULL,
    status         TEXT    NOT NULL CHECK (status IN ('passed', 'upcoming')),
    date           TEXT,                          -- nullable : cartes « à venir » sans date fabriquée
    summary        TEXT    NOT NULL,
    source_url     TEXT    NOT NULL,
    source_expect  TEXT    NOT NULL,
    text_url       TEXT    NOT NULL,
    text_expect    TEXT    NOT NULL,
    stage          TEXT,                          -- étape officielle sourcée (cartes « à venir »)
    published      INTEGER NOT NULL DEFAULT 1,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at     TEXT    NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO laws_new
    (id, title, category, status, date, summary, source_url, source_expect,
     text_url, text_expect, published, created_at, updated_at)
SELECT
    id, title, category, status, date, summary, source_url, source_expect,
    text_url, text_expect, published, created_at, updated_at
FROM laws;

DROP TABLE laws;
ALTER TABLE laws_new RENAME TO laws;
