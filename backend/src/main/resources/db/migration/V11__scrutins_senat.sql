-- Facette « scrutin public au Sénat » d'une loi (extension Sénat — issue #3, tâche 4).
-- On NE touche PAS aux tables AN (laws, scrutins). Deux tables, comme le pipeline AN sépare
-- l'index (laws) du détail (scrutins) :
--
--   * senat_lois     : STATUT de résolution PAR LOI (une ligne par loi résolue). Distingue
--                      « scrutin public trouvé » (has_public_scrutin=1) de « voté à main levée /
--                      pas de scrutin public au Sénat » (=0, reason renseignée, cas réel : PFAS).
--                      L'ABSENCE de ligne = loi non résolue → l'API rend senat:null. C'est ce
--                      marqueur qui permet à l'API de dire explicitement « pas de scrutin public »
--                      au lieu d'un zéro (Golden Rule : ne jamais confondre 0 vote et pas de vote).
--   * scrutins_senat : votes agrégés PAR BLOC du scrutin publié (DDL de la note § 5). session =
--                      année d'ouverture de session (ex. 2022 = session 2022-2023).
--
-- foreign_keys est OFF sur la connexion xerial (cf. V10) : les FK sont documentaires, la
-- synchronisation gère elle-même la cohérence (remplacement atomique par loi).

CREATE TABLE senat_lois (
    law_id             TEXT    NOT NULL PRIMARY KEY REFERENCES laws (id) ON DELETE CASCADE,
    has_public_scrutin INTEGER NOT NULL CHECK (has_public_scrutin IN (0, 1)),
    reason             TEXT,                          -- motif si has_public_scrutin = 0
    updated_at         TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE scrutins_senat (
    law_id          TEXT    NOT NULL REFERENCES laws (id) ON DELETE CASCADE,
    session         INTEGER NOT NULL,                -- ex. 2022 (session 2022-2023)
    numero          INTEGER NOT NULL,
    scrutin_url     TEXT    NOT NULL,                -- page officielle senat.fr (traçabilité)
    scrutin_date    TEXT    NOT NULL,                -- ISO-8601 (YYYY-MM-DD)
    bloc            TEXT    NOT NULL CHECK (bloc IN ('gauche', 'milieu', 'droite', 'extremeDroite')),
    votes_for       INTEGER NOT NULL,
    votes_against   INTEGER NOT NULL,
    votes_abstained INTEGER NOT NULL,
    PRIMARY KEY (law_id, session, numero, bloc)
);
