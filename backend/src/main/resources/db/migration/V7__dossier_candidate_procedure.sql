-- Type de procédure du dossier (issue #3, tâche 2 — affinage). On ne retient que les projets
-- et propositions de LOI (les résolutions, rapports d'information, pétitions… ne sont pas des
-- lois et n'ont pas leur place en « prochains scrutins »). `projet_de_loi` = origine
-- gouvernementale (indicateur d'importance : soutien de l'exécutif).
ALTER TABLE dossier_candidates ADD COLUMN procedure TEXT NOT NULL DEFAULT '';
ALTER TABLE dossier_candidates ADD COLUMN projet_de_loi INTEGER NOT NULL DEFAULT 0;
