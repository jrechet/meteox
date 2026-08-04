# Project Status — meteox · onglet « Lois & Climat »

_Last updated: 2026-08-04 · déploiement Pages débloqué après 12 jours de rouge (PR #63) + CI front désormais jouée sur les PR ; bump Renovate #61 posé en int._

> 📍 **Lis-moi en premier.** Tableau de bord compact tenu à jour à chaque livraison (skill
> `status-tracking`, enforced par le Stop hook `status-tracker.sh`). Historique détaillé = issues
> GitHub + PR ; ce fichier en est l'index rapide. Règles/DoD : [BACKLOG.md](BACKLOG.md).

**Prod** : front https://jrechet.github.io/meteox/ (GitHub Pages) · backend **int**
https://jrec.fr/meteox-laws-int (Quarkus+SQLite, swarm jrec.fr) · admin `…/admin.html` (GitHub OAuth
ou `X-Admin-Token`). Dernier changement applicatif : PR #59 (`d89932c`), déployé+vérifié int ; l'image
en int porte le bump Renovate #61 (`int-7a4a59d`, posée le 04/08). Front redéployé le 04/08 (`af0ef2f`) après
12 jours de blocage. Data : 11 lois publiées (7 « à venir »), 161 dossiers candidats (~123 avec auteur, ~92 cosignés).

## ✅ Done recently
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
- _Parké_ : V2 FranceConnect (bloqué juridique) · Renovate `#11` (dashboard bot).

## 🔑 Handoff notes (à ne pas réapprendre à la dure)
- **Snapshot front = état SEED.** `src/data/laws-snapshot.json` reste l'état seed (sans facette `senat`) :
  `LawsApiTest` le compare à un backend frais. La donnée live (Sénat, votes) vient de l'API au runtime.
- **Cache open data empoisonné** : zip corrompu + ETag = 304 éternel. Auto-guérison en place (#55). Si
  `dossier_signataires` se vide → relancer `POST /api/admin/dossiers/sync` : tout se re-dérive de l'open data, rien de perdu.
- **OIDC GitHub** : jamais de `quarkus.oidc.logout.path` (pas d'`end_session_endpoint` → crash boot, a
  fait tomber la prod). Session interne = `internal-id-token-lifespan=12H`.
- **`verify-int` « cancelled / not acquired by runner »** = runner self-hosted éphémère indisponible,
  pas un échec du code → `gh run rerun <id> --failed`. Le 04/08 : **0 runner en ligne sur 464 enregistrés**
  (`gh api repos/jrechet/meteox/actions/runners`) — les enregistrements éphémères morts s'accumulent, et tout
  job `[self-hosted, meteox]` reste en `queued`. À purger côté serveur quand tu voudras.
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
