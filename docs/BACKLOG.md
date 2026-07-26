# Backlog — travail parallélisable par agents

> **⚡ Où on en est maintenant : [PROJECT_STATUS.md](PROJECT_STATUS.md)** (tableau de bord vivant,
> à lire en premier). Ce fichier-ci = l'**index des issues** + les **règles communes** (DoD).
>
> **Référence de travail : les GitHub issues.** Chaque issue = une feature couvrant un domaine
> métier cohérent, avec des macro-tâches à cocher **dans l'issue** au fur et à mesure.
> Suivi d'avancement produit : [political_tab.PRD.md](../political_tab.PRD.md) ·
> Plan de référence : [plans/lois-sources-fiabilite.md](plans/lois-sources-fiabilite.md).

## Règles pour les agents

1. **Prendre une issue entière** (pas une sous-tâche isolée) ; s'assigner l'issue avant de commencer.
2. Cocher les macro-tâches dans l'issue au fil de l'eau ; commits en conventional commits référençant l'issue (`#N`).
3. Respecter les **Golden Rules** d'`AGENTS.md` (aucune donnée non sourcée, aucune carte sans source vérifiée en contenu).
4. Une issue n'est fermée que si la **Definition of Done** (commune, rappelée dans chaque issue) est intégralement satisfaite.
5. Dépendances entre issues indiquées ci-dessous — ne pas démarrer une issue dont les prérequis ne sont pas mergés.

## Definition of Done (commune à toutes les issues)

- **Couverture de test : 100 % des actions livrées.** Chaque endpoint, bouton et élément
  interactif livré est couvert par un test automatisé qui exerce le **vrai chemin** — pas une
  variante qui contourne le problème. En particulier une action déclenchée par le navigateur
  est testée **comme le navigateur l'émet** (ex. requête avec en-tête `Origin` pour une action
  admin même origine ; POST avec `Content-Type`). Deux boutons livrés ⇒ deux boutons testés.
  Un livrable non couvert par un test n'est **pas** « done ». _(Leçon 2026-07-18 : un `/sync`
  testé au curl — sans `Origin` — a laissé passer un 403 CORS que seul le navigateur déclenchait.)_
- `npm test` + `npm run test:e2e` verts en CI ; `./mvnw verify` vert (backend) ;
- `npm run check:sources` vert (aucune source morte ou non concordante) ;
- si UI touchée : revue Playwright 375/768/1280/1920 sans overflow ni erreur console ;
- déployé et vérifié sur l'environnement cible (GitHub Pages via `main` ; backend : env **int** jrec.fr) ;
- `political_tab.PRD.md` et ce backlog mis à jour ; documentation nouvelle si nécessaire ;
- **traçabilité (obligatoire)** : [`PROJECT_STATUS.md`](PROJECT_STATUS.md) mis à jour **et** issues
  GitHub synchronisées (fermer ce qui est fait, ouvrir ce qui se profile) — procédure : skill
  [`project-status`](../.claude/skills/project-status/SKILL.md). But : si le contexte est vidé, le
  prochain agent ne perd rien ;
- toutes les cases de l'issue cochées, critères d'acceptance démontrés dans un commentaire de clôture.

## Issues (créées le 2026-07-14)

| Issue | Domaine métier | Dépend de | État |
|---|---|---|---|
| [#2 — Backend Quarkus + SQLite (socle données lois, jrec.fr)](https://github.com/jrechet/meteox/issues/2) | Infrastructure données | — | ✅ Fermée (2026-07-16) — API int : `https://jrec.fr/meteox-laws-int` |
| [#3 — Pipeline open data AN (scrutins réels & prochains scrutins)](https://github.com/jrechet/meteox/issues/3) | Données législatives | #2 | ✅ Tâches 1-4 livrées et déployées (extract-scrutins, sync-dossiers, corpus élargi, **Sénat** : scrutins Dosleg + 2ᵉ facette). Pages statiques par loi (#50) en prod. |
| [#4 — Module indicateurs IA multi-backend + méthodologie transparente](https://github.com/jrechet/meteox/issues/4) | Scoring éditorial | #2 | ✅ Fermée (2026-07-17) — UI « Pourquoi ces scores ? » (PR #22) |
| [#5 — Front branché sur l'API avec snapshot de fallback](https://github.com/jrechet/meteox/issues/5) | Frontend données | #2 | ✅ Fermée (2026-07-17) — API + snapshot (PR #21), plus de données en dur |
| [#6 — Qualité éditoriale & accessibilité de l'onglet Lois](https://github.com/jrechet/meteox/issues/6) | Éditorial / a11y | — | ✅ Fermée |

**Reste à faire** : voir [PROJECT_STATUS.md](PROJECT_STATUS.md) (§ Prochaines étapes) — l'état
détaillé y est tenu à jour à chaque livraison. Carte des dépendances externes : [DEPENDENCIES.md](DEPENDENCIES.md).

## Hors backlog (non planifié)

- **V2 FranceConnect / envoi direct** : bloqué par une étude de faisabilité juridique (habilitation,
  conventions, RGPD) — cf. PRD § 4 et § 6. À transformer en issue quand la décision sera prise.
