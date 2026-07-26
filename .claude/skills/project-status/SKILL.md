---
name: project-status
description: |
  Tenue à jour du suivi projet meteox. Synchronise le tableau de bord
  docs/PROJECT_STATUS.md avec la réalité (git, prod, PR) ET les issues GitHub
  (ferme ce qui est fait, ouvre ce qui se profile), consigne les nouveaux pièges.
  À exécuter en fin de CHAQUE tâche livrée (Definition of Done) et impérativement
  AVANT de vider le contexte, pour qu'aucune information ne soit perdue.
  Se déclenche sur « mets à jour le suivi », « à jour les issues », « status »,
  « on va clear le contexte », « sauvegarde l'état projet ».
allowed-tools:
  - Bash
  - Read
  - Write
  - Edit
  - Grep
  - Glob
---

# Skill : project-status (suivi meteox)

But : garantir qu'à tout instant **une personne (ou un agent) qui débarque comprend l'état du
projet sans relire tout l'historique**, et qu'aucune information importante n'est perdue quand on
vide le contexte.

Deux niveaux, complémentaires :
- **`docs/PROJECT_STATUS.md`** = tableau de bord vivant (le « lis-moi en premier »).
- **Issues + PR GitHub** = historique détaillé. Le board pointe vers elles.

## Quand l'exécuter

- En **Definition of Done** de chaque tâche livrée (après le déploiement vérifié en prod).
- **Avant tout clear de contexte** (« on va vider le chat »), même si aucune tâche n'est « finie ».

## Procédure

### 1. Constater la réalité (ne rien deviner)

```bash
git -C /Users/jre/dev/meteox log --oneline -8
git -C /Users/jre/dev/meteox branch --show-current
rtk proxy gh issue list --state open  --limit 50 --json number,title,labels
rtk proxy gh issue list --state closed --limit 15 --json number,title,closedAt
rtk proxy gh pr list --state merged --limit 8 --json number,title,mergedAt
```
- Rappels rtk : lire un **corps** d'issue en `curl` + token (jamais round-trip `gh issue view`) ;
  ne pas faire `tok=$(rtk proxy gh ...)` en sous-shell (parse error) → écrire le token dans un fichier.

### 2. Mettre à jour `docs/PROJECT_STATUS.md`

Garder les sections à jour et **datées** :
- **Snapshot production** (URLs, `main` SHA, état CI/CD, volumétrie data si utile).
- **✅ Livré récemment** — déplacer ici le travail terminé, avec **PR + SHA**.
- **🚧 En cours / partiel / dette** — ce qui n'est pas vraiment fini.
- **⏭️ Prochaines étapes** — sans jamais qualifier de « prioritaire » de soi-même (décision commune).
- **💤 Backlog / parké**.
- **⚠️ Pièges & incidents** — ajouter tout nouveau piège appris à la dure (c'est le plus précieux).

### 3. Synchroniser les issues GitHub

- **Fermer** une issue dont la DoD est satisfaite, avec un commentaire de clôture qui cite la/les PR
  et les critères démontrés.
- **Ouvrir** une issue pour tout travail qui se profile (les « prochaines étapes » du board),
  courte et actionnable, pour que le tracker reflète le futur proche.
- Cocher les macro-tâches dans les issues en cours.
- Laisser les issues de bots (ex. Renovate Dashboard `#11`) telles quelles.

### 4. Cohérence croisée & mémoire

- Vérifier que board ↔ issues ↔ PR racontent la même histoire (pas de « fait » ici et « ouvert » là-bas).
- Mettre à jour la mémoire agent perso si elle existe :
  `~/.claude/projects/-Users-jre-dev-meteox/memory/` (fichier `meteox-prod-state.md` + `MEMORY.md`).

### 5. Commit

Committer les docs mis à jour (stager **explicitement**, jamais `git add -A`) :
```bash
git -C /Users/jre/dev/meteox add docs/PROJECT_STATUS.md docs/BACKLOG.md AGENTS.md
git -C /Users/jre/dev/meteox commit -m "docs: mise à jour du suivi projet (PROJECT_STATUS + issues)"
```

## Definition of Done du skill

- [ ] `docs/PROJECT_STATUS.md` reflète le HEAD réel, la prod réelle, les PR réelles, daté du jour.
- [ ] Issues GitHub synchronisées (fermées/ouvertes/cochées) et cohérentes avec le board.
- [ ] Nouveaux pièges consignés dans « ⚠️ Pièges & incidents ».
- [ ] Un agent qui ne lirait QUE `PROJECT_STATUS.md` saurait quoi faire ensuite et quoi éviter.
