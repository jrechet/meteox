# Backlog — travail parallélisable par agents

> **Référence de travail : les GitHub issues.** Chaque issue = une feature couvrant un domaine
> métier cohérent, avec des macro-tâches à cocher **dans l'issue** au fur et à mesure.
> Ce fichier est l'index : il pointe vers les issues et donne les règles communes.
> Suivi d'avancement produit : [political_tab.PRD.md](../political_tab.PRD.md) ·
> Plan de référence : [plans/lois-sources-fiabilite.md](plans/lois-sources-fiabilite.md).

## Règles pour les agents

1. **Prendre une issue entière** (pas une sous-tâche isolée) ; s'assigner l'issue avant de commencer.
2. Cocher les macro-tâches dans l'issue au fil de l'eau ; commits en conventional commits référençant l'issue (`#N`).
3. Respecter les **Golden Rules** d'`AGENTS.md` (aucune donnée non sourcée, aucune carte sans source vérifiée en contenu).
4. Une issue n'est fermée que si la **Definition of Done** (commune, rappelée dans chaque issue) est intégralement satisfaite.
5. Dépendances entre issues indiquées ci-dessous — ne pas démarrer une issue dont les prérequis ne sont pas mergés.

## Definition of Done (commune à toutes les issues)

- `npm test` + `npm run test:e2e` verts en CI ;
- `npm run check:sources` vert (aucune source morte ou non concordante) ;
- si UI touchée : revue Playwright 375/768/1280/1920 sans overflow ni erreur console ;
- déployé et vérifié sur l'environnement cible (GitHub Pages via `main` ; backend : env **int** jrec.fr) ;
- `political_tab.PRD.md` et ce backlog mis à jour ; documentation nouvelle si nécessaire ;
- toutes les cases de l'issue cochées, critères d'acceptance démontrés dans un commentaire de clôture.

## Issues (créées le 2026-07-14)

| Issue | Domaine métier | Dépend de |
|---|---|---|
| [#2 — Backend Quarkus + SQLite (socle données lois, jrec.fr)](https://github.com/jrechet/meteox/issues/2) | Infrastructure données | — |
| [#3 — Pipeline open data AN (scrutins réels & prochains scrutins)](https://github.com/jrechet/meteox/issues/3) | Données législatives | #2 |
| [#4 — Module indicateurs IA multi-backend + méthodologie transparente](https://github.com/jrechet/meteox/issues/4) | Scoring éditorial | #2 |
| [#5 — Front branché sur l'API avec snapshot de fallback](https://github.com/jrechet/meteox/issues/5) | Frontend données | #2 |
| [#6 — Qualité éditoriale & accessibilité de l'onglet Lois](https://github.com/jrechet/meteox/issues/6) | Éditorial / a11y | — |

**Parallélisation immédiate possible** : #2 et #6 en simultané. #3, #4, #5 démarrent quand #2 expose `GET /api/laws` en int.

## Hors backlog (non planifié)

- **V2 FranceConnect / envoi direct** : bloqué par une étude de faisabilité juridique (habilitation,
  conventions, RGPD) — cf. PRD § 4 et § 6. À transformer en issue quand la décision sera prise.
