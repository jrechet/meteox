# Project Status — meteox · onglet « Lois & Climat »

_Last updated: 2026-08-12 · premier chargement de l'onglet Climat ramené de ~8,6 s à ~0,4 s (historique en deux passes, températures seules ; les autres variables à la demande)._

> 📍 **Lis-moi en premier.** Tableau de bord compact tenu à jour à chaque livraison (skill
> `status-tracking`, enforced par le Stop hook `status-tracker.sh`). Historique détaillé = issues
> GitHub + PR ; ce fichier en est l'index rapide. Règles/DoD : [BACKLOG.md](BACKLOG.md).

**Prod** : front https://jrechet.github.io/meteox/ (GitHub Pages) · backend **int**
https://jrec.fr/meteox-laws-int (Quarkus+SQLite, swarm jrec.fr) · admin `…/admin.html` (GitHub OAuth
ou `X-Admin-Token`). Dernier changement applicatif : PR #59 (`d89932c`) ; l'image en int est `int-0a0c34f`
(05/08, même code backend, redéployée par le bump d'actions). Front déployé le 05/08 (`0a0c34f`).
Data : 11 lois publiées (7 « à venir »), 161 dossiers candidats (~123 avec auteur, ~92 cosignés).

## ✅ Done recently
- **Archive pré-générée des cartes : l'animation ne touche plus Open-Meteo** — 2026-08-15. Le plafond
  identifié plus bas (une image = une requête pondérée par ses 20 lieux → 1940→aujourd'hui hors budget,
  arrêt vers 1948) est levé en **inversant la découpe** : au build on demande **une requête par ville**
  couvrant toute la série (20 requêtes au lieu de 87 par animation), réparties en **366 fichiers de
  ~10 ko** (`public/data/heatmap/MM-DD.json`, ~3,4 Mo au total). L'app n'en charge **qu'un** — celui du
  jour affiché — et rejoue toute la portée sans un seul appel API. Repli conservé : l'année en cours
  (incomplète, jamais dans l'archive) et les années postérieures à la dernière génération passent par
  l'API. Régénération **annuelle** (`refresh-heatmap-archive.yml`, cron 5 janvier) et **déclenchable à la
  main** — les années passées étant de la réanalyse figée, elles ne bougent plus.
- **Cartes : quatre gains d'API, et un plafond structurel identifié** — 2026-08-15.
  (1) `weather_code` n'était **jamais affiché** sur la carte mais demandé à chaque requête : **1,3–3,1 s
  contre 0,2–0,8 s** sans lui. Supprimé. (2) Cache `mx:heatmap:v2` : les années passées (réanalyse figée)
  sont gardées **sans expiration** → une relecture d'animation coûte **0 requête** ; l'année courante
  garde un TTL de 12 h (avant : tout était caché pour toujours, la carte du jour ne bougeait jamais).
  (3) Déduplication des requêtes en vol (le pool et la tête de lecture demandaient la même année).
  (4) Le pool ne devance plus la lecture que de 6 images et se met en pause au premier échec : arrêter au
  bout de 3 s sur 87 ans coûte **10 requêtes** au lieu de lancer toute la portée. (5) Limiteur de débit
  (seau à jetons, rafale 4 puis 1 toutes les 2,1 s) : mesuré **29 req/min**.
  **Plafond restant** — voir les notes de passation : une animation longue à froid ne va toujours pas au
  bout. Décision produit à prendre.
- **Scrutin AN : la source est enfin à côté des votes** — 2026-08-15. La facette « À l'Assemblée
  nationale » n'avait pas de pied de source : le scrutin AN n'était lié que par un **« Scrutin
  officiel » générique en bas de carte**, à côté d'un Sénat daté et sourcé sous ses barres — l'AN
  paraissait donc sans source alors que `law.sourceUrl` **est** sa page de scrutin (la page statique,
  elle, l'étiquetait déjà « Scrutin officiel — Assemblée nationale »). Pied commun aux deux chambres
  (`chamberSourceHTML`) : date + lien situé. Le lien générique disparaît. Effet de bord assumé : une URL
  refusée par `safeUrl` n'affiche plus un lien mort vers `#` — la ligne de source disparaît, conforme à
  la règle d'or « aucune carte sans source valide ».
- **Lien « Admin » dans le pied de page** — 2026-08-15. Aucun chemin depuis le site vers l'admin de
  relecture ; lien construit depuis `LAWS_API_BASE`. Le back-office reste protégé (302 → OAuth GitHub).
- **Animation des cartes : rejouer le jour choisi année après année** — 2026-08-15. Bouton
  « ▶ Animer &lt;année&gt; → &lt;année courante&gt; » sur la carte double : la carte de droite défile année par
  année (600 ms/image) pendant que celle de gauche reste la référence actuelle ; barre de progression,
  arrêt, et reprise de la main dès que l'utilisateur touche curseur / onglet / puces / jour.
  Contraintes API mesurées : **une image = 1 requête (20 villes)**, ~0,4 s à concurrence 4 — le
  préchargement devance donc la lecture. Open-Meteo refuse au-delà d'une poignée de requêtes simultanées
  (« Too many concurrent requests ») : concurrence plafonnée à 4, **ne pas l'augmenter à l'aveugle**.
  Cadence vérifiée en navigateur réel : 612 ms de moyenne (607–619). (e2e : feature `animation des cartes`)
- **E2E : toutes les features du site sont couvertes** — 2026-08-12. La suite passait de 22 à
  **91 vérifications sur 13 features**, chacune déclarée dans `FEATURES` (`test/e2e.mjs`) : une feature
  listée sans aucune vérification **fait échouer le run**, ce qui attrape une feature livrée sans test.
  Ajouté : amorçage sans lien partagé (repli géoloc), vignettes du hero, graphe des décennies (bornes,
  fusion des deux passes sans doublon, tendance), curseur → carte focus + graphe, règle des deux cartes
  **dans les deux onglets et sans clic préalable**, bascule Absolu/Écart, chips 5/10/30, chips de mesure
  + teintes du badge, restauration depuis un lien partagé, recherche de commune, filtres de lois,
  modale d'interpellation (code postal → lettre), **pages de loi statiques + sitemap**, et l'absence de
  débordement horizontal aux 4 breakpoints **sur les deux onglets** (le climat n'était testé qu'à 375).
  `test:e2e` lance désormais `npm run build` (et non `vite build`) : les pages statiques sont générées,
  donc testables. Vérifié que la suite **échoue** sur le code d'avant le correctif des cartes.
- **Cartes : la comparaison à deux cartes s'affiche dès qu'une année passée est choisie** — 2026-08-12.
  En « Période », les deux cartes exigeaient en plus un **clic sur un jour du bandeau** : en entrant dans
  l'onglet on ne voyait qu'une seule carte, sans rien qui indique comment obtenir la comparaison. La
  règle était **dupliquée dans 3 fichiers** et avait divergé (`views.js` avait perdu le garde
  « année ≠ année en cours », donc la mise en page pouvait s'empiler pour une carte simple). Extraite en
  `showsDualMaps(state)` dans `heatmap.js`, utilisée par `views.js` et `main.js`. Règle unique :
  **deux cartes ⟺ année sélectionnée ≠ année en cours**, dans les deux onglets. Test de cohérence
  panneau/carte sur les 8 combinaisons.
- **Badge d'écart : couleur neutre pour Pluie et Vent** — 2026-08-12. Le badge réutilisait les teintes
  chaud/froid de la température pour toutes les mesures (« +12 mm » en orange). `tone` par mesure : seule
  la température garde sa teinte, la pluie et le vent passent en neutre encre pleine. Au passage, le
  seuil « écart significatif » (0,3, calibré en °C) devient `noticeable` par unité — 1 mm, 2 km/h — pour
  que la phrase n'affirme plus « plus venté » sur +1 km/h.
- **Période : l'écart moyen dit enfin par rapport à quoi** — 2026-08-12. Le badge affichait « -4.8°
  d'écart moyen sur 10 j » sans nommer sa référence : on pouvait le lire « 2026 est plus frais » **ou**
  « l'année passée était plus fraîche », et il s'affichait en bleu « froid » dans une app dont le propos
  est le réchauffement — d'où un signalement d'« inversion ». Vérifié : **aucune inversion**, ni dans les
  données (août 2003 = canicule, moy 35,9 réels contre 31,5 en 2026 à Paris) ni dans le mapping
  valeur → étiquette (sondé avec 10° vs 90°). Le badge énonce maintenant le sens : « 2026 plus frais que
  2003, en moyenne sur 10 j », avec un vocabulaire par mesure (chaud/frais · pluvieux/sec · venté/moins
  venté) et une invite (« glissez le curseur… ») sur l'année en cours, où il n'y a rien à comparer.
- **Climat : premier chargement ~8,6 s → ~0,4 s** — 2026-08-12. L'onglet attendait **une** requête ERA5
  `1940→aujourd'hui × 5 variables` (1,1 Mo, TTFB 7,6 s) dont **91,8 % du payload était jeté**
  (31 631 jours téléchargés, 2 580 utilisés). Désormais : la série longue ne demande que les **deux
  températures** (les 3 autres variables coûtent ~4,5× le temps d'extraction serveur) et arrive en
  **deux passes concurrentes** — 30 dernières années d'abord (0,37 s, débloque graphe + curseur), retour
  à 1940 en fond ; **précipitations/vent/code météo à la demande**, une fenêtre de 30 j pour l'année
  affichée (~130 ms, 1,5 kB), qui sert aussi le bandeau « Période ». Le cache `localStorage` passe de
  ~249 kB à ~2,6 kB par lieu/jour (il dépassait le quota au bout d'une vingtaine de lieux, `writeCache`
  avalait l'exception → plus aucun cache et 8 s repayées à chaque visite). Bornes du curseur figées à
  1940–année courante pour que la plage ne bouge plus sous le pouce. (closes #74)
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
- **L'archive des cartes se régénère avec `npm run generate:heatmap-archive`** (~15-25 min : 20 requêtes
  très lourdes, ~31 000 jours chacune, à concurrence 4 avec backoff sur les limites de débit). À lancer
  via le workflow `refresh-heatmap-archive` (annuel ou `workflow_dispatch`), **pas** dans le build : ce
  serait 20 min ajoutées à chaque déploiement et un build cassé si Open-Meteo tousse. Ne pas monter la
  concurrence au-delà de 4 (« Too many concurrent requests »).
- **[RÉSOLU par l'archive pré-générée]** L'animation longue ne passait pas à froid, et ce n'était pas un
  réglage à trouver — on garde le raisonnement, il explique le repli API qui subsiste :
  Open-Meteo pondère une requête par son **nombre de lieux** : la carte à 20 villes coûte ~20 appels sur
  les 600/minute du palier gratuit, et **le chargement de page en consomme déjà une bonne part** (les
  deux passes d'historique portent des dizaines de milliers de jours). Mesures du 15/08 : à 225 req/min
  → 81 refus sur 90 ; **même ramené à 29 req/min, une animation 1940→2026 s'arrête vers 1948**
  (« Minutely API request limit exceeded »). Les garde-fous font leur travail (arrêt propre + message),
  mais la portée complète est hors budget. Pistes non tranchées, par ordre d'efficacité :
  **(a)** pré-générer la donnée au build (20 villes × 87 ans pour un jour ≈ 20 ko, coût runtime nul,
  mais impose un build quotidien) · **(b)** moins de villes pendant l'animation (poids ÷2,5) ·
  **(c)** plafonner la portée (~20 ans) · **(d)** pas de 3–5 ans au-delà d'un certain écart.
  Ne pas « corriger » en augmentant la concurrence : au-delà de 4 l'API répond
  « Too many concurrent requests ».
- **Toute nouvelle feature s'ajoute à `FEATURES` dans `test/e2e.mjs`** et doit y avoir au moins une
  vérification, sinon le run échoue (« features with no e2e check »). C'est le garde-fou contre le
  scénario du 12/08 : les cartes doubles « marchaient » en test parce que le test cliquait un jour avant
  de compter les colonnes — il encodait la règle d'alors, pas ce que l'utilisateur rencontre en entrant
  dans l'onglet. **Tester l'état d'arrivée d'une feature, pas seulement son chemin nominal.**
- **`page.goto(base + '#autre-hash')` ne recharge pas la page** (navigation same-document) : `main.js`
  ne rejoue pas et un test « restaure depuis un lien partagé » vérifie alors la vue précédente. Le
  helper `bootTo()` passe par `about:blank` pour forcer un vrai chargement. Ça m'a fait croire une
  minute à un bug de deep-link qui n'existait pas.
- **Ne jamais mesurer une cadence d'animation depuis un onglet masqué** : les navigateurs bornent
  `setTimeout` à ~1 s en arrière-plan (et jusqu'à plusieurs secondes). Le 15/08, l'animation semblait
  tourner à 1,3 s/image dans le volet caché alors qu'elle est à **612 ms** mesurée par Playwright au
  premier plan. Mesurer avec Playwright, pas avec le volet navigateur.
- **`npm run test:e2e` lance `npm run build`** (pas `vite build`) pour que `dist/loi/<id>/` et
  `sitemap.xml` existent : sans ça les pages statiques ne sont pas testables et le e2e ne teste pas ce
  qui est réellement déployé.
- **Open-Meteo facture au poids (variables × jours), pas à la requête.** Une requête
  `1940→2026 × 5 variables` consomme à elle seule ~la limite minute du plan gratuit : pendant les mesures
  du 12/08, elle a suffi à faire tomber les appels suivants en `429 Minutely API request limit exceeded`.
  Corollaire : **ajouter une variable à la série longue est bien plus cher qu'ajouter une requête**.
  Les variables non-températures se demandent par fenêtre de 30 j (`fetchYearWindow`), jamais en masse.
- **`&models=era5` est un piège** : mesuré à **49 s** contre 8,6 s pour le modèle par défaut sur la même
  plage. Ne pas « préciser » le modèle en croyant optimiser.
- **Les temps de réponse Open-Meteo varient énormément** selon la chaleur de leur cache (même requête
  mesurée à 0,13 s puis 3,5 s). Toujours mesurer sur des coordonnées neuves et espacer les appels avant
  de conclure à une régression.
- **Le graphe des décennies a un axe X figé (1940→année courante)** pendant que la 2ᵉ passe charge, mais
  la **droite de tendance ne se trace que sur les années réellement ajustées** — l'étendre à tout l'axe
  extrapolerait une pente de 30 ans jusqu'en 1940 comme si elle était mesurée. Test de garde :
  `components.test.js` › « pins the axis to the given bounds without extrapolating the trend ».
- **`loadToken`** (`main.js`) invalide les requêtes d'un lieu précédent : sans lui, un « Paris → Nice »
  rapide écrit les jours de Paris dans l'état de Nice.
- **Les deux passes d'historique sont indépendantes** : si l'une échoue (429), les années de l'autre
  s'affichent quand même. Constaté en vrai sur la prod le 12/08 — la passe récente a pris un 429 pendant
  que la passe profonde réussissait, et une première version jetait le tout. Garde :
  `test/main-degraded.test.js`.
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
