# Project Status — meteox · onglet « Lois & Climat »

_Last updated: 2026-07-26 · analyse réseau livrée + incident signataires (cache ETag) résolu ; mise en place du suivi._

> 📍 **Lis-moi en premier.** Tableau de bord compact tenu à jour à chaque livraison (skill
> `status-tracking`, enforced par le Stop hook `status-tracker.sh`). Historique détaillé = issues
> GitHub + PR ; ce fichier en est l'index rapide. Règles/DoD : [BACKLOG.md](BACKLOG.md).

**Prod** : front https://jrechet.github.io/meteox/ (GitHub Pages) · backend **int**
https://jrec.fr/meteox-laws-int (Quarkus+SQLite, swarm jrec.fr) · admin `…/admin.html` (GitHub OAuth
ou `X-Admin-Token`). Dernier changement applicatif : PR #55 (`9c38131`), CI/CD verts. Data : 11 lois
publiées (7 « à venir »), 161 dossiers candidats (~123 avec auteur, ~92 cosignés).

## ✅ Done recently
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
- **Résolution des acteurs incomplète** — ~45/161 candidats sans auteur, quelques groupes « ? »
  (lég. 15 en 404, documents sans auteur exploitable). Non bloquant (le réseau exclut les non-résolus). (#58)
- **Analyse réseau = admin uniquement** — exposition publique = décision éditoriale non prise. (#57)

## 📋 Todo / backlog
- Décider l'exposition publique (ou non) de l'analyse réseau — éditorial. (#57)
- Fiabiliser la résolution des acteurs « ? » (couverture AMO / fallback). (#58)
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
  pas un échec du code → `gh run rerun <id> --failed`.
- **rtk réécrit `gh`** : corps d'issue en curl+token (jamais round-trip `gh issue view`) ; `tok=$(rtk proxy gh …)`
  en sous-shell casse (parse error) → écrire le token dans un fichier.
- **`git add -A` interdit** (a fuité des jetons le 2026-07-20, dépôt public) ; `MX_ADMIN_TOKEN` sensible.
- **Toujours déployer + vérifier en prod** (env cible backend = **int**) avant de clore.
- **Outil debug** : workflow `backend-logs` (`workflow_dispatch`) → logs + disque du conteneur int à la demande.
