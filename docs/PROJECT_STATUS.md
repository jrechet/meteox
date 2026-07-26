# État du projet — meteox · onglet « Lois & Climat »

> **📍 LIS-MOI EN PREMIER.** Tableau de bord vivant : où on en est, ce qui reste, les pièges à
> ne pas réapprendre. Tenu à jour **à chaque livraison** (cf. skill `project-status`, DoD dans
> [BACKLOG.md](BACKLOG.md)). L'historique détaillé = les **issues GitHub** + les **PR** (liens plus bas) ;
> ce fichier est l'index rapide pour ne pas avoir à tout relire.
>
> **Dernière mise à jour : 2026-07-26** — après l'analyse réseau + résolution de l'incident signataires.

---

## Snapshot production

| Surface | URL | État |
|---|---|---|
| Front (onglet Lois) | https://jrechet.github.io/meteox/ | ✅ live — GitHub Pages, pages statiques par loi |
| Backend (env **int**) | https://jrec.fr/meteox-laws-int | ✅ live — Quarkus + SQLite, swarm jrec.fr (routage par chemin) |
| Admin (relecture) | https://jrec.fr/meteox-laws-int/admin.html | ✅ live — connexion GitHub OAuth **ou** `X-Admin-Token` |

- **`main`** — dernier changement **applicatif** : PR #55 (`9c38131`). CI/CD verts. (Des montées
  Renovate — #51, #56… — passent régulièrement par-dessus ; c'est normal, elles ne changent pas le produit.)
- Data prod : **11 lois publiées** (dont 7 « à venir » validées à la main) ; **161 dossiers candidats**,
  ~123 avec auteur résolu / ~92 cosignés (reconstruits depuis l'open data après incident, voir plus bas).

---

## ✅ Livré récemment (cette session, 2026-07-23 → 26)

| Quoi | PR / SHA | Détail |
|---|---|---|
| **Analyse réseau des soutiens** | #53 `84958a9` | `SupportNetworkRepository` (matrice de soutien par bloc, liens entre groupes, ponts transpartisans nominatifs) + `GET /api/admin/reseau` + section admin. Vérifié prod : droite & extrême droite ne cosignent QUE leurs propres textes ; gauche mobilise large ; centre le plus transversal. |
| **Fix perte de données signataires** | #54 `627225f` | `SignataireResolver.resolve → Optional` : un échec **préserve** l'existant (ne remplace plus par du vide). Test de régression. |
| **Auto-guérison des caches open data** | #55 `9c38131` | Cause racine de l'incident : zip AMO30 **tronqué** en cache + ETag → 304 éternel = poison scellé. Les 3 caches AN s'auto-réparent (ZipException → purge zip+ETag → re-télécharge une fois). Test reproduisant poison+304. |
| **Admin UX v2** | #52 `3391396` | Dépublication (`/demote`, re-promotion republie la même ligne), cartes publiées **persistantes** (badge « ✓ Publiée » sur place, plus de disparition), filtres « Porté par » (bloc) + tri par cosignataires, **session OIDC 12h** (fin des « jeton invalide » à répétition). |
| Outil : workflow `backend-logs` | `5b90934`, `81ccbf9` | `workflow_dispatch` → logs + disque du conteneur int à la demande (timeouts partout). A révélé la cause racine de l'incident. |

**Jalons antérieurs (résumé)** : #33 réseaux de soutien — données (initiateur + cosignataires par
groupe, table `dossier_signataires` V8) · #42 corpus élargi (`law_candidates`) · #43-46 connexion
admin GitHub OAuth · #47-49 Sénat (scrutins Dosleg + 2ᵉ facette « Au Sénat ») · #50 pages statiques
par loi (permaliens, OpenGraph, sitemap). Détail : issues fermées + PR correspondantes.

---

## 🚧 En cours / partiel / dette

- **Résolution des acteurs incomplète** : ~45/161 candidats sans auteur résolu, quelques groupes
  affichés « ? ». Causes constatées (logs) : la lég. 15 renvoie 404 côté open data, certains
  documents de dépôt n'ont « ni acteur ni organe » exploitable. Pas bloquant (le réseau exclut les
  non-résolus) mais limite la complétude. → piste d'amélioration ci-dessous.
- **Analyse réseau = admin uniquement.** Exposition publique = décision **éditoriale** non prise.

---

## ⏭️ Prochaines étapes candidates (rien n'est prioritaire tant qu'on n'a pas décidé ensemble)

1. **Exposer (ou non) l'analyse réseau au public** — décision éditoriale, puis éventuelle page. → **[#57](https://github.com/jrechet/meteox/issues/57)**.
2. **Fiabiliser la résolution des acteurs « ? »** — meilleure couverture AMO / fallback. → **[#58](https://github.com/jrechet/meteox/issues/58)**.
3. **Continuer la validation humaine** des dossiers candidats (promouvoir les plus pertinents via l'admin).

## 💤 Backlog / parké

- **V2 FranceConnect / envoi direct** — bloqué par étude de faisabilité juridique (cf. PRD).
- **Renovate** — `#11` (dashboard bot, reste ouverte par design) ; PR temurin v26 : décision utilisateur.

---

## ⚠️ Pièges & incidents connus (NE PAS réapprendre à la dure)

- **Snapshot front = état SEED.** `src/data/laws-snapshot.json` doit rester l'état seed (sans facette
  `senat`) : `LawsApiTest` le compare à un backend frais. La donnée live (facette Sénat, votes
  synchronisés) vient de l'**API au runtime**, jamais du snapshot committé.
- **Cache open data empoisonné** : un zip corrompu + ETag = 304 éternel = résolution cassée en boucle.
  Auto-guérison en place (#55). Si `dossier_signataires` se retrouve vide → relancer un sync admin
  (`POST /api/admin/dossiers/sync`) : tout se **re-dérive de l'open data**, rien n'est perdu.
- **OIDC GitHub** : NE JAMAIS mettre `quarkus.oidc.logout.path` (pas d'`end_session_endpoint` → crash au
  boot, a fait tomber la prod). Session interne = `internal-id-token-lifespan=12H`.
- **`verify-int` « cancelled / not acquired by runner »** = runner self-hosted éphémère indisponible,
  **pas** un échec du code → `gh run rerun <id> --failed`.
- **rtk réécrit la sortie `gh`** : lire un corps d'issue en **curl + token** (jamais round-trip
  `gh issue view`) ; et `tok=$(rtk proxy gh ...)` en sous-shell **casse** (parse error) → écrire dans un fichier.
- **`git add -A` interdit** : a fuité des jetons le 2026-07-20 (dépôt public) — stager explicitement.
- **`MX_ADMIN_TOKEN`** sensible (secret GitHub) — ne jamais l'écrire dans le repo.
- **Toujours déployer + vérifier en prod** avant de clore (règle globale). Env cible backend = **int**.

---

## Suivi GitHub (historique détaillé)

- **Issues** : `gh issue list --state all`. Ouvertes : **#57** (exposition publique réseau — éditorial),
  **#58** (fiabiliser résolution acteurs), **#11** (Renovate, bot). Épics livrées fermées :
  #2 socle, #3 pipeline open data (t1-4), #4 indicateurs, #5 front, #6 éditorial, #33 (+#34-37) réseaux de soutien, #39/#41 validations.
- **PR récentes** = le journal des changements : #52 (admin UX), #53 (analyse réseau), #54 (fix data-loss), #55 (auto-guérison caches).
- **Mémoire agent** (hors repo, perso) : `~/.claude/projects/-Users-jre-dev-meteox/memory/` — état prod + pièges.

## Comment maintenir ce fichier

C'est un livrable de **chaque** tâche (Definition of Done). Le skill **`project-status`**
(`.claude/skills/`) décrit la procédure : mettre à jour les sections ci-dessus, synchroniser les
issues GitHub (fermer ce qui est fait, ouvrir ce qui se profile), consigner tout nouveau piège.
Objectif : **si on vide le contexte, le prochain agent ne perd aucune information importante.**
