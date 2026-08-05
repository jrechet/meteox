# Project Status — meteox · onglet « Lois & Climat »

_Last updated: 2026-08-05 · politique Renovate globale corrigée côté server-app (plus de majeures, automerge minor/patch, default branch only) ; CI sur Node 24 ; dépôt à une seule branche (`main`)._

> 📍 **Lis-moi en premier.** Tableau de bord compact tenu à jour à chaque livraison (skill
> `status-tracking`, enforced par le Stop hook `status-tracker.sh`). Historique détaillé = issues
> GitHub + PR ; ce fichier en est l'index rapide. Règles/DoD : [BACKLOG.md](BACKLOG.md).

**Prod** : front https://jrechet.github.io/meteox/ (GitHub Pages) · backend **int**
https://jrec.fr/meteox-laws-int (Quarkus+SQLite, swarm jrec.fr) · admin `…/admin.html` (GitHub OAuth
ou `X-Admin-Token`). Dernier changement applicatif : PR #59 (`d89932c`) ; l'image en int est `int-0a0c34f`
(05/08, même code backend, redéployée par le bump d'actions). Front déployé le 05/08 (`0a0c34f`).
Data : 11 lois publiées (7 « à venir »), 161 dossiers candidats (~123 avec auteur, ~92 cosignés).

## ✅ Done recently
- **Politique Renovate globale corrigée (côté `server-app`, hors dépôt)** — 2026-08-05. Le
  `renovate/config.js` self-hosted n'impose plus `baseBranches: ['main','master']` (chaque dépôt est
  suivi sur sa branche par défaut — le WARN « Base branch does not exist » du dashboard #11 disparaît
  à la source), **les majeures sont désactivées globalement** et les minor/patch restent groupées +
  automergées. 6 PR Renovate en attente mergées sur les autres dépôts jrechet ; service redéployé et
  conf vérifiée dans les logs du run.
- **Remise à niveau complète des dépendances + hygiène du dépôt** — `0a0c34f`, 2026-08-05.
  **Plus aucune mise à jour Renovate en attente** : #62 (playwright, vite), #60 (jsdom 30), #65 (Node 24),
  #67 (actions GitHub aux majeures : checkout v7, setup-node v7, setup-java v5, github-script v9, les 3
  actions Pages, docker/login-action v4). **jsdom 30 était bloqué par le pin Node 20** — il exige
  `^22.22.2 || ^24.15.0 || >=26`, et l'undici 8.9 qu'il embarque appelle `webidl.util.markAsUncloneable`,
  absent sur Node 20 : c'est le nouveau CI PR (#63) qui l'a attrapé avant le merge. Dashboard Renovate #11
  nettoyé de son avertissement permanent (voir handoff). Dépôt ramené à **une seule branche** : 9 branches
  de PR fusionnées purgées + `delete_branch_on_merge` activé.
- **Déploiement Pages débloqué + CI front sur les PR** — `af0ef2f`, 2026-08-04. Les 14 derniers déploiements
  Pages échouaient au job `test` : le snapshot régénéré depuis l'API contient 7 lois `upcoming` **sans date**
  (contrat `isValidLaw`), or `politics.test.js` en exigeait une sur toutes les lois → `TypeError`. Front figé
  au 23/07. Corrigé + course e2e sur la modale d'interpellation (montée après un `import()` dynamique) +
  `front-ci.yml` réutilisable, joué **aussi sur les PR** (il n'y avait aucun check front sur PR). Vérifié en
  prod : 11 pages `/loi/<id>/` + sitemap à 12 URL, toutes 200. (PR #63)
- **Résolution #58 (PR A)** — `d89932c`, 2026-07-26. Mesure de couverture (`GET /api/admin/reseau/couverture`
  + tracée en fin de sync, scopée à la relecture) + résolution des dépôts multi-législatures
  (`SignataireResolver` lit le token `L<NN>B` de l'uid → bon zip ; `…L16B…` se résout, `…L15B…` reste
  préservé sans perte). Déployé+vérifié int. (PR #59). **Reste (PR B)** : fallback « dernier groupe connu, marqué comme tel ».
- **Analyse réseau des soutiens** — `84958a9`, 2026-07-24. `SupportNetworkRepository` (matrice de
  soutien par bloc, liens entre groupes, ponts transpartisans) + `GET /api/admin/reseau` + section admin. (PR #53, prolonge #33)
- **Fix perte de données signataires** — `627225f`, 2026-07-24. `SignataireResolver.resolve → Optional` :
  un échec préserve l'existant au lieu de le vider. (PR #54)
- **Auto-guérison des caches open data** — `9c38131`, 2026-07-24. Zip corrompu + ETag 304 = poison
  éternel → les 3 caches AN se réparent (purge zip+ETag → re-télécharge). (PR #55)
- **Admin UX v2** — `3391396`, 2026-07-24. Dépublication, cartes publiées persistantes (badge « ✓ Publiée »),
  filtres bloc + tri cosignataires, session OIDC 12h. (PR #52)
- _Antérieur_ : #33 données signataires (V8) · #42 corpus élargi · #43-46 admin OAuth · #47-49 Sénat · #50 pages statiques.

## 🚧 In progress / not finished
- **Résolution des acteurs — PR B** — reste le fallback « dernier groupe connu, marqué comme tel »
  (mandat GP actif manquant → « ? ») + complétion du mapping `organe-blocs.json` (sigles remontés par
  `siglesSansBloc`). À dimensionner sur les chiffres réels de `/couverture` (nécessite `MX_ADMIN_TOKEN` +
  un `POST …/dossiers/sync`). La sémantique réseau d'une affiliation historique recoupe #57. (#58)
- **Analyse réseau = admin uniquement** — exposition publique = décision éditoriale non prise. (#57)

## 📋 Todo / backlog
- Décider l'exposition publique (ou non) de l'analyse réseau — éditorial. (#57)
- #58 PR B : fallback « dernier groupe connu » (marqué) + mapping `organe-blocs` + surfacer `/couverture` en admin.
- Continuer la validation humaine des dossiers candidats via l'admin.
- _Parké_ : V2 FranceConnect (bloqué juridique) · Renovate `#11` (dashboard bot, plus aucune mise à jour en
  attente au 05/08) · purge des ~515 enregistrements de runners éphémères morts (côté serveur).

## 🔑 Handoff notes (à ne pas réapprendre à la dure)
- **Snapshot front = état SEED.** `src/data/laws-snapshot.json` reste l'état seed (sans facette `senat`) :
  `LawsApiTest` le compare à un backend frais. La donnée live (Sénat, votes) vient de l'API au runtime.
- **Cache open data empoisonné** : zip corrompu + ETag = 304 éternel. Auto-guérison en place (#55). Si
  `dossier_signataires` se vide → relancer `POST /api/admin/dossiers/sync` : tout se re-dérive de l'open data, rien de perdu.
- **OIDC GitHub** : jamais de `quarkus.oidc.logout.path` (pas d'`end_session_endpoint` → crash boot, a
  fait tomber la prod). Session interne = `internal-id-token-lifespan=12H`.
- **`verify-int` « cancelled / not acquired by runner »** = runner self-hosted éphémère indisponible,
  pas un échec du code → `gh run rerun <id> --failed`. Le 05/08 : **1 runner en ligne sur 516 enregistrés**
  (`gh api repos/jrechet/meteox/actions/runners`) — les enregistrements éphémères morts s'accumulent (464 la
  veille), et tout job `[self-hosted, meteox]` peut rester en `queued`. À purger côté serveur quand tu voudras.
  Attention : un rerun qui traîne en `queued` **bloque le déploiement suivant** (groupe de concurrence
  `backend-cd`, `cancel-in-progress: false`) — annuler le run superseded plutôt que le laisser attendre.
- **Un déploiement backend rend 502 pendant ~1 à 2 min** (bascule stop-first, démarrage Quarkus+OIDC) :
  ce n'est pas un incident, c'est la fenêtre que `verify-int` absorbe avec ses retries.
- **Renovate global corrigé le 05/08** : l'instance self-hosted (`jrec.fr:5422`,
  `~/dev/server-app/renovate/config.js`) n'impose plus de `baseBranches` (default branch par dépôt),
  désactive les majeures et automerge les minor/patch. Le `baseBranches: ["main"]` local de
  `renovate.json` est devenu redondant (inoffensif — supprimable à l'occasion). Pour recharger la conf
  après modif : `docker service update --force renovate_renovate`.
- **`eclipse-temurin` reste en 21 (LTS jusqu'en 2029)** — décision prise le 20/07 en fermant la PR #24 ;
  la v26 est non-LTS (~6 mois). Renovate ignore désormais les 26.x, et l'entrée « PR Closed (Blocked) » du
  dashboard est le reflet normal de cette décision, pas un reliquat.
- **CI front sur Node 24** (`front-ci`, `deploy`, `check-sources`). Un pin Node périmé se manifeste comme un
  échec de dépendance incompréhensible (cf. jsdom 30 / undici 8.9) : vérifier `engines` avant de conclure.
- **`docker stack up` peut sortir en `DeadlineExceeded`** alors que la mise à jour du service aboutit :
  relancer le job (idempotent) plutôt que conclure à un échec de déploiement.
- **Permaliens des lois** : `/loi/<id>/` (dossier + `index.html`, cf. `generate-law-pages.mjs`), pas
  `/lois/<id>.html`. Le `sitemap.xml` liste l'accueil + une URL par loi.
- **Le front est testé sur les PR** depuis #63 (`front-ci.yml`, réutilisé par `deploy.yml` via
  `workflow_call`) : une seule définition, donc PR verte = déploiement vert. Le job régénère le snapshot
  depuis l'API int — un changement de forme de la donnée casse la CI avant le déploiement, pas après.
- **rtk réécrit `gh`** : corps d'issue en curl+token (jamais round-trip `gh issue view`) ; `tok=$(rtk proxy gh …)`
  en sous-shell casse (parse error) → écrire le token dans un fichier.
- **`git add -A` interdit** (a fuité des jetons le 2026-07-20, dépôt public) ; `MX_ADMIN_TOKEN` sensible.
- **Toujours déployer + vérifier en prod** (env cible backend = **int**) avant de clore.
- **Outil debug** : workflow `backend-logs` (`workflow_dispatch`) → logs + disque du conteneur int à la demande.
