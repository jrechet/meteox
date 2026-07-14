# Plan — Fiabilité des sources de données « Lois & Climat »

> Référencé depuis [political_tab.PRD.md](../../political_tab.PRD.md).
> Décisions actées le 2026-07-14 : backend **Java/Quarkus + SQLite** déployé sur **jrec.fr** (pattern espace-client),
> module d'indicateurs IA **multi-backend** (Claude subscription / Claude API / Ollama),
> gestion des 404 = **exclusion au build + alerte CI** (« les deux »).

---

## 1. Constat (audit du 2026-07-14)

Audit manuel de chaque URL de `src/lib/laws.js`, page par page (statut HTTP **et** contenu) :

| Carte | Problème constaté | Correction vérifiée |
|---|---|---|
| PFAS | `sourceUrl` et `textUrl` → **404** (format `www2…/scrutins/detail/...` et slug décommissionnés) | Scrutin : `/dyn/16/scrutins/3643` · Dossier : `/dyn/16/dossiers/DLR5L16N49455` |
| APER | Idem **404** | Scrutin : `/dyn/16/scrutins/823` · Dossier : `/dyn/16/dossiers/DLR5L16N46539` |
| Industrie verte | Idem **404** | Scrutin : `/dyn/16/scrutins/2721` · Dossier : `/dyn/16/dossiers/DLR5L16N47917` |
| LOA | Affichée « vote à venir · mai 2026 » alors que la **loi n° 2025-268 est promulguée depuis le 24 mars 2025** | Reclassée `passed`, scrutin CMP `/dyn/17/scrutins/844` (19/02/2025), votes réels par groupe extraits de la page officielle |
| Restauration de la Nature | **Aucun dossier français n'existe** — règlement (UE) 2024/1991 d'application directe, la carte décrivait un « projet de loi de transposition » fictif | **Carte supprimée** |

Leçon critique : deux URLs testées par l'ancien `test-urls.cjs` renvoyaient **200 mais pointaient vers de mauvais dossiers**
(dépenses électorales des Français de l'étranger, retraite des sapeurs-pompiers).
**Un contrôle HTTP 200 ne suffit pas : il faut vérifier le contenu.**

---

## 2. Golden Rules (reprises dans AGENTS.md)

1. **Aucune carte sans source valide** : si `sourceUrl` ou `textUrl` est 404 — ou si la page ne correspond pas au texte annoncé — la carte ne doit pas apparaître.
2. **Vérification de contenu, pas seulement de statut** : chaque URL est associée à un `expectedTitle` (fragment du titre officiel) qui doit apparaître dans la page.
3. **Aucun statut inventé** : `upcoming` exige un dossier en cours vérifié ; `passed` exige un scrutin officiel avec répartition des votes extraite de la page du scrutin.
4. **Toute donnée = une provenance** : votes, dates, statuts et indicateurs doivent être traçables vers une page officielle (assemblee-nationale.fr, senat.fr, legifrance.gouv.fr) ou vers la méthodologie documentée (indicateurs).

---

## 3. Source par source

### 3.1 Votes à venir (« Prochains scrutins »)

- **Source de vérité** : agenda et dossiers législatifs en cours de la **17e législature** — open data AN (`data.assemblee-nationale.fr`, jeux `dossiers_legislatifs` et `agenda`) + pages `/dyn/17/dossiers/...`.
- **Règle** : une carte `upcoming` n'existe que si le dossier est réellement à l'ordre du jour ou en navette, avec `textUrl` vérifié (statut + `expectedTitle`).
- **Problème structurel** : une liste statique devient fausse en quelques semaines (cf. LOA). C'est le premier cas d'usage du backend (§ 5) : rafraîchissement planifié depuis l'open data.
- **Transitoire (fait le 2026-07-14)** : plus aucune carte `upcoming` fabriquée ; la section affiche un état vide honnête tant que l'automatisation n'est pas en place.

### 3.2 Indicateurs d'impact (pesticides, partage de l'eau, lobbies, santé)

Les scores −2..+2 sont un **jugement éditorial** : aucune API ne les fournit. Ils nécessitent une extraction assistée par IA + relecture humaine.

**Module dédié `laws-indicators` (dans le backend Quarkus, § 5)** :

1. **Entrée** : texte de l'exposé des motifs + résumé du dossier législatif (récupérés depuis l'open data AN / Legifrance).
2. **Extraction IA** : prompt structuré produisant, pour chaque axe, un score, une **justification citée** (extrait du texte) et un niveau de confiance.
3. **Abstraction multi-backend** (décision : interface commune, backends interchangeables par config) :
   - `ClaudeCliBackend` — `claude -p` headless (abonnement existant, exécution locale/serveur jrec.fr, pas de coût API) ;
   - `ClaudeApiBackend` — API Anthropic (clé en secret, utilisable en CI cloud) ;
   - `OllamaBackend` — LLM local (gratuit, qualité moindre sur texte législatif : réservé au dev).
   - Interface Java : `IndicatorExtractor#extract(LawText) → IndicatorScores` + `quarkus.laws.ai.backend=claude-cli|claude-api|ollama`.
4. **Relecture humaine obligatoire** : les scores IA arrivent en statut `draft` en base ; publication seulement après validation (flag `reviewed_by`). L'UI n'affiche jamais un score `draft`.
5. **Transparence** : la méthodologie + la justification de chaque score sont exposées par l'API (`GET /api/laws/{id}/indicators`) et affichables dans l'UI (répond à la question ouverte « méthodo des indicateurs » du PRD).

### 3.3 Bilan des lois votées

- **Source de vérité votes** : la page officielle du scrutin (`/dyn/{lég}/scrutins/{n}`) — la section « Votes des groupes » est parsable (HTML stable) ; l'open data AN fournit aussi les scrutins en JSON (`Scrutins.json.zip`).
- **Règle de mapping des groupes** (documentée, appliquée au scrutin 844) :
  - gauche = LFI + SOC + Écologiste et Social + GDR ;
  - milieu = EPR/RE + Dem/MoDem + Horizons + LIOT ;
  - droite = DR/LR ;
  - extrême droite = RN + UDR ;
  - non-inscrits : hors blocs (mentionné en commentaire de la donnée).
- **Pourquoi il y a peu de lois** : la liste est manuelle. L'automatisation (backend § 5 + open data scrutins) permettra d'élargir le corpus (filtre par mots-clés environnement/eau/agriculture + validation humaine).

---

## 4. Contrôles automatisés

### 4.1 Script `scripts/check-sources.mjs` (fait le 2026-07-14)

- Lit `LAWS_DATA`, vérifie pour **chaque** `sourceUrl`/`textUrl` :
  1. statut HTTP ≤ 399 après redirections ;
  2. présence du `expectedTitle` (fragment du titre officiel) dans le HTML — pare le cas « 200 mais mauvaise page » ;
- Vérifie aussi la cohérence interne : somme des votes par groupe vs page de scrutin (contrôle manuel initial, automatisable via open data), bornes des indicateurs.
- Sortie : rapport par carte, exit code ≠ 0 si une source est invalide.
- `npm run check:sources`.

### 4.2 CI / CD (stratégie « les deux »)

1. **Au build (bloquant à terme, filet de sécurité)** : étape de CI qui exécute `check:sources`.
   - Phase 1 (actuelle) : la CI échoue si une source est invalide → correction manuelle.
   - Phase 2 (avec backend) : le build front consomme un snapshot JSON déjà filtré par le backend — une carte non vérifiée n'entre jamais dans le bundle (exclusion au build).
2. **Cron quotidien (GitHub Action `schedule`)** : re-exécute `check:sources` contre la donnée publiée ; en cas d'échec, **ouvre automatiquement une issue GitHub** (alerte) au lieu de casser silencieusement la prod.
3. **E2E** : l'e2e existant garde une assertion « tout lien affiché répond 200 + titre attendu » sur un échantillon.

---

## 5. Backend Java/Quarkus + SQLite sur jrec.fr

**Motivation** : `laws.js` statique est fragile (cet audit le prouve) ; les besoins récurrents — rafraîchissement open data, checks de liens planifiés, extraction IA avec workflow de validation — nécessitent un état persistant et des jobs.

### Architecture

```
meteox-laws (Quarkus, Java 21)
├── REST : GET /api/laws · GET /api/laws/{id}/indicators · GET /api/health
├── Jobs (@Scheduled) :
│   ├── sync-dossiers   : open data AN → table laws (statuts, dates, URLs)
│   ├── check-sources   : statut + expectedTitle de chaque URL ; invalide → carte dépubliée + issue GitHub
│   └── extract-scrutins: votes par groupe depuis l'open data Scrutins
├── Module laws-indicators (IA multi-backend, § 3.2)
└── SQLite (fichier, volume Docker ; backup = copie de fichier)
    tables : laws · scrutins · indicator_scores(draft/published) · source_checks(historique)
```

### Déploiement (pattern espace-client)

- Dockerfile + `build.sh <int|prod>` / `deploy.sh <int|prod>` tagués au SHA git court, `docker stack deploy` sur jrec.fr.
- Environnement `int` d'abord (comme l'int espace-client), CORS ouvert sur `jrechet.github.io`.
- Le front GitHub Pages appelle `https://<host jrec.fr>/api/laws` avec **fallback sur un snapshot JSON embarqué au build** (résilience : si l'API est down, on sert la dernière donnée vérifiée, jamais une donnée invalide).

### Étapes

1. [ ] Squelette Quarkus (REST + SQLite + Flyway) + Dockerfile + scripts build/deploy int.
2. [ ] Import initial : les 4 lois vérifiées de `laws.js` → base.
3. [ ] Job `check-sources` (@Scheduled quotidien) + dépublication + création d'issue GitHub.
4. [ ] Job `extract-scrutins` (open data AN) + mapping groupes documenté.
5. [ ] Module `laws-indicators` multi-backend + workflow draft→published.
6. [ ] Front : fetch API + snapshot fallback ; suppression de `LAWS_DATA` en dur.
7. [ ] Job `sync-dossiers` pour alimenter « Prochains scrutins » (17e législature).

---

## 6. Ordre d'exécution recommandé

| # | Action | État |
|---|---|---|
| 1 | Corriger les URLs mortes + LOA + suppression carte fictive (`laws.js`) | ✅ fait 2026-07-14 |
| 2 | `scripts/check-sources.mjs` + `npm run check:sources` + CI | ✅ fait 2026-07-14 |
| 3 | Golden Rules AGENTS.md + cohérence PRD | ✅ fait 2026-07-14 |
| 4 | Cron quotidien GitHub Action + issue auto (`check-sources.yml`) | ✅ fait 2026-07-14 |
| 5 | Backend Quarkus/SQLite étapes 1-3 (squelette, import, check-sources serveur) | ⬜ |
| 6 | Module indicateurs IA + méthodologie publiée | ⬜ |
| 7 | Alimentation automatique « Prochains scrutins » + élargissement du corpus | ⬜ |
