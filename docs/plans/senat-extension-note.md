# Extension Sénat — note d'architecture (issue #3, tâche 4)

> Note exploratoire. Toutes les URLs et données citées ont été vérifiées en ligne le
> 2026-07-18 (téléchargement réel des jeux de données, requêtes sur le dump Dosleg,
> recoupement avec les pages officielles senat.fr). Les points non vérifiés sont
> signalés comme tels.

## 1. Verdict

**Faisable avec réserves** : toutes les données nécessaires existent en open data
(licence ouverte, mise à jour quotidienne), le chaînage AN ↔ Sénat et l'agrégation
par groupe ont été vérifiés de bout en bout sur les 4 lois déjà suivies — mais
(a) certains textes n'ont **aucun scrutin public** au Sénat (vote à main levée,
cas réel : la loi PFAS), (b) il n'existe **aucun groupe d'extrême droite** au Sénat
(bloc `extremeDroite` structurellement vide), et (c) le mapping en blocs de deux ou
trois groupes est ambigu et doit être tranché par l'utilisateur.

## 2. Jeux de données Sénat pertinents

| Jeu | URL | Format | Fraîcheur (vérifiée) | Rôle |
|---|---|---|---|---|
| Base **Dosleg** | `https://data.senat.fr/data/dosleg/dosleg.zip` | Dump PostgreSQL 8.4 (zip 15,9 Mo → SQL 126 Mo, sections `COPY` tabulées) | Quotidienne (Last-Modified du jour, 01:54 UTC ; ETag présent) | Dossiers législatifs + index des scrutins publics depuis oct. 2006 + **chaînage vers l'AN** |
| **Scrutin JSON** (par scrutin) | `https://www.senat.fr/scrutin-public/{session}/scr{session}-{n}.json` | JSON `{votes:[{matricule, vote: p/c/a/n, siege}]}` | Statique une fois publié (ETag présent) | Votes nominatifs d'un scrutin |
| **Scrutin HTML** (page officielle) | `https://www.senat.fr/scrutin-public/{session}/scr{session}-{n}.html` | HTML | — | **URL de traçabilité Golden Rule** (résultat + analyse par groupe affichés) |
| **ODSEN_HISTOGROUPES** | `https://data.senat.fr/data/senateurs/ODSEN_HISTOGROUPES.json` | JSON (1,46 Mo) — aussi CSV/XLS | Quotidienne (Last-Modified du jour, 02:12 UTC ; ETag) | Matricule sénateur → groupe politique, **avec dates de début/fin** (historique complet) |

- **Licence** : « Licence Ouverte » (data.gouv.fr) pour tout data.senat.fr —
  réutilisation libre y compris commerciale, obligation de mentionner la source et la
  date de mise à jour (<https://data.senat.fr/licence/>). Compatible avec l'usage meteox.
- Les extraits CSV de Dosleg (`dossiers-legislatifs.csv`, etc.) ont été examinés :
  ils ne contiennent **ni scrutins ni lien AN** — insuffisants seuls.
- Il n'existe **pas de jeu JSON global des scrutins** : l'index des scrutins est dans le
  dump Dosleg (table `scr`, 4 766 scrutins depuis oct. 2006, dont 855 « sur l'ensemble »
  d'un texte, ≈ 45/an) ; le détail nominatif est dans le JSON par scrutin ci-dessus.
- Le dump Dosleg contient aussi `votsen` (votes nominatifs, ≈ 75 % du volume du fichier) :
  **inutile pour meteox** puisque le JSON par scrutin fournit la même chose à l'unité.

## 3. Chaînage AN ↔ Sénat (vérifié)

### 3.1 Clé de jointure : `loi.url_an` (Dosleg)

La table Dosleg `loi` (12 393 dossiers) porte un champ `url_an` (renseigné sur
3 074 dossiers) pointant vers le dossier AN, sous **deux formes** :

- forme uid : `http://www.assemblee-nationale.fr/16/dossiers/DLR5L16N46539.asp` (APER) ;
- forme slug : `http://www.assemblee-nationale.fr/16/dossiers/proteger_population_risques_pfas.asp` (PFAS).

Le slug est le `titreChemin` du dossier AN — **déjà parsé** par `DossierParser`
(`backend/src/main/java/fr/jrec/meteox/laws/opendata/DossierParser.java`). La jointure
se fait donc sur le dernier segment de `url_an` (sans `.asp`), comparé à `{uid, titreChemin}`
du dossier AN. Côté meteox, les lois publiées portent déjà cette référence dans `text_url`
(ex. `…/16/dossiers/DLR5L16N49455`, `…/16/dossiers/souverainete_agricole_renouvellement_generations`).

En sens inverse, `loi.signet` donne l'URL officielle du dossier Sénat :
`https://www.senat.fr/dossier-legislatif/{signet}.html` (ex. `pjl21-889`).

### 3.2 Du dossier aux scrutins : la chaîne interne Dosleg

```
scr (scrutin) ──code──▶ date_seance ──lecidt──▶ lecass ──lecidt──▶ lecture ──loicod──▶ loi
```

Chaîne vérifiée sur données réelles (APER, scrutin CMP) :
`scr(2022, 125)` → `date_seance(code=27519)` → `lecass(94651)` → `lecture(83630)` →
`loi(70063)` = `pjl21-889`, `url_an` contenant `DLR5L16N46539`. Les scrutins « sur
l'ensemble » d'un texte sont identifiables par le préfixe de leur intitulé (`scrint`).

### 3.3 Vérification sur les 4 lois suivies par meteox

| Loi meteox | Dossier Sénat (`signet`) | Scrutins Sénat « sur l'ensemble » trouvés dans Dosleg |
|---|---|---|
| APER (`eco-eau-1`) | [pjl21-889](https://www.senat.fr/dossier-legislatif/pjl21-889.html) | 1ʳᵉ lecture scr **2022-29** (04/11/2022, 320 pour / 5 contre) · CMP scr **2022-125** (07/02/2023, 300 / 13 / 30 abst.) |
| Industrie verte (`eco-canicule-1`) | pjl22-607 | 1ʳᵉ lecture scr **2022-320** (22/06/2023, 251 / 12) · CMP scr **2023-3** (11/10/2023, 243 / 17) |
| LOA (`eco-agri-loa`) | pjl23-639 | Ensemble scr **2024-196** (18/02/2025, 218 / 107) · CMP scr **2024-211** (20/02/2025, 236 / 103) |
| PFAS (`eco-agri-1`) | ppl23-514 | **Aucun** — 3 scrutins d'amendements seulement ; le vote sur l'ensemble a eu lieu à main levée (pas de scrutin public) |

Pages officielles vérifiées (traçabilité Golden Rule) :
[scr2022-125.html](https://www.senat.fr/scrutin-public/2022/scr2022-125.html) (résultat +
analyse par groupe affichés) et son
[JSON](https://www.senat.fr/scrutin-public/2022/scr2022-125.json) ;
[scr2024-211.json](https://www.senat.fr/scrutin-public/2024/scr2024-211.json).

### 3.4 Agrégation par groupe : reproduite exactement

Le JSON de scrutin ne donne que des matricules. Croisé avec `ODSEN_HISTOGROUPES.json`
(appartenance au groupe **à la date du scrutin**), l'agrégat calculé reproduit **à
l'identique** l'analyse par groupe de la page officielle du scrutin 2022-125
(0 matricule inconnu sur 348 votes) :

| Groupe | Pour | Contre | Abst. | Officiel (page HTML) |
|---|---|---|---|---|
| Les Républicains | 133 | 10 | 0 | 133 / 10 / 0 ✔ |
| Socialiste, Écologiste et Républicain | 64 | 0 | 0 | 64 / 0 / 0 ✔ |
| Union Centriste | 51 | 3 | 3 | 51 / 3 / 3 ✔ |
| RDPI | 24 | 0 | 0 | 24 / 0 / 0 ✔ |
| CRCE-Kanaky | 0 | 0 | 15 | 0 / 0 / 15 ✔ |
| Les Indépendants | 14 | 0 | 0 | 14 / 0 / 0 ✔ |
| RDSE | 14 | 0 | 0 | 14 / 0 / 0 ✔ |
| Écologiste – Solidarité et Territoires | 0 | 0 | 12 | 0 / 0 / 12 ✔ |

C'est l'équivalent Sénat de la vérification faite pour l'AN sur les scrutins 844/3643/823/2721
(cf. `reference/organe-blocs.json`).

## 4. Mapping groupes Sénat → blocs meteox

Groupes actuels (extraits d'ODSEN_HISTOGROUPES, appartenances ouvertes au 2026-07-18).
Attention : les **codes ODSEN sont historiques** (`UMP` = Les Républicains,
`LREM` = RDPI, `CRC` = CRCE-Kanaky) — à documenter dans le fichier de référence.

| Code ODSEN | Groupe | Effectif | Bloc proposé | Statut |
|---|---|---|---|---|
| SOC | Socialiste, Écologiste et Républicain | 66 | `gauche` | clair |
| CRC | CRCE-Kanaky (communiste) | 18 | `gauche` | clair |
| GEST | Écologiste – Solidarité et Territoires | 16 | `gauche` | clair |
| LREM | RDPI (majorité présidentielle, Renaissance) | 19 | `milieu` | clair |
| RTLI | Les Indépendants – République et Territoires | 20 | `milieu` | clair (proche Horizons, cohérent avec HOR→milieu côté AN) |
| UC | Union Centriste | 61 | `milieu` | **décision utilisateur** — centre-droit (UDI/MoDem), cohérent avec MoDem→milieu côté AN, mais allié structurel de LR dans la majorité sénatoriale |
| RDSE | Rassemblement Démocratique et Social Européen | 16 | `milieu` | **décision utilisateur** — groupe transversal (radicaux, élus divers gauche et droite) |
| UMP | Les Républicains | 134 | `droite` | clair |
| NI | Réunion administrative des non-inscrits | 4 | `horsBlocs` | **décision utilisateur** — contient les élus RN (pas de groupe RN au Sénat) : les classer `horsBlocs` rend leurs votes invisibles, les classer `extremeDroite` mélange RN et autres non-inscrits |

Constat structurel : **il n'existe aucun groupe d'extrême droite au Sénat**. Le bloc
`extremeDroite` d'une météo Sénat serait donc vide (ou quasi vide) sur tous les
scrutins — question éditoriale à trancher avant tout développement front (§ 7).

Pour les scrutins passés (législatures 16/17, sessions 2022→), les mêmes codes couvrent
la période : la jointure historique par dates d'ODSEN_HISTOGROUPES a donné 0 inconnu
sur le scrutin testé de 2023.

## 5. Architecture d'intégration proposée (KISS)

Calquée sur le pipeline AN existant, dans un nouveau package
`fr.jrec.meteox.laws.opendata.senat` :

| Composant | Rôle | Modèle AN copié |
|---|---|---|
| `OpenDataDosleg` | Télécharge `dosleg.zip` (GET conditionnel ETag, cache disque, réutilisation du cache si indisponible). Parse **en streaming** uniquement les sections `COPY` utiles : `loi`, `lecture`, `lecass`, `date_seance`, `scr` (~25 % du fichier, quelques Mo en mémoire ; on saute `votsen`). Format tabulé simple, échappements PostgreSQL (`\N`, `\r\n`) à gérer. | `OpenDataScrutins` / `OpenDataDossiers` |
| `SenatScrutinResolver` | Pour une loi meteox : extrait la référence AN de `text_url` (uid `DLR…` ou slug), retrouve la `loi` Dosleg via `url_an`, remonte la chaîne § 3.2 et rend les scrutins « sur l'ensemble » (session, numéro, date, intitulé, URL officielle `senat.fr/scrutin-public/...`). | `ScrutinRef` + logique dossier |
| `SenatBlocMapping` + `reference/senat-groupe-blocs.json` | Code groupe ODSEN → bloc. Même contrat que `BlocMapping` : groupe inconnu = échec explicite, jamais de total faussé (Golden Rule). | `BlocMapping` / `organe-blocs.json` |
| `SenatScrutinVotes` | GET du JSON de scrutin + `ODSEN_HISTOGROUPES.json` (tous deux cachés par ETag), jointure matricule → groupe à la date du scrutin, agrégation par bloc. | `ScrutinParser` + `ScrutinExtractionService` |
| `SenatSyncService` + `SenatSyncJob` | Passe périodique : décomptes réalignés sur l'open data (« l'open data fait foi »), divergences archivées + issue GitHub, un échec n'interrompt pas les autres. | `ScrutinSyncService` / `ScrutinSyncJob` |

**Persistance** (migration `V9`) — on ne touche pas à la table `scrutins` (AN) :

```sql
CREATE TABLE scrutins_senat (
    law_id          TEXT    NOT NULL REFERENCES laws (id) ON DELETE CASCADE,
    session         INTEGER NOT NULL,   -- ex. 2022 (session 2022-2023)
    numero          INTEGER NOT NULL,
    scrutin_url     TEXT    NOT NULL,   -- page officielle senat.fr (traçabilité)
    scrutin_date    TEXT    NOT NULL,
    bloc            TEXT    NOT NULL CHECK (bloc IN ('gauche','milieu','droite','extremeDroite')),
    votes_for       INTEGER NOT NULL,
    votes_against   INTEGER NOT NULL,
    votes_abstained INTEGER NOT NULL,
    PRIMARY KEY (law_id, session, numero, bloc)
);
```

**Workflow** : comme pour les dossiers AN (`dossier_candidates`), l'appariement
loi ↔ scrutin Sénat passe par une **validation humaine** dans l'admin existante : le
resolver propose les scrutins « sur l'ensemble » trouvés (souvent deux : 1ʳᵉ lecture
et CMP), l'humain choisit celui/ceux à publier ; ensuite seulement la sync automatique
maintient les décomptes. Pas d'appariement auto publié sans relecture.

## 6. Effort estimé et risques

**Effort backend : ~3 à 5 jours** (hors front) :
parser COPY Dosleg 1–1,5 j · resolver + mapping + référence blocs 0,5–1 j ·
sync + table + API 1 j · écran de validation admin 0,5–1 j · tests (dont reproduction
du scrutin 2022-125 comme test d'intégration, à l'image des tests AN).

Risques et limites :

- **Pas de scrutin public pour tous les textes** (risque principal, avéré : PFAS).
  Le Sénat vote souvent à main levée ; ~855 scrutins « sur l'ensemble » en 20 ans.
  Il faut un état « pas de scrutin public au Sénat » assumé côté produit, pas un zéro.
- **Bloc `extremeDroite` vide** au Sénat : question éditoriale, pas technique (§ 7).
- **Format du dump non contractuel** : PostgreSQL 8.4, structure stable de fait depuis
  des années, mais aucun engagement du Sénat ; le parser doit échouer bruyamment si une
  colonne attendue disparaît.
- **`url_an` hétérogène et incomplet** (3 074 / 12 393 dossiers renseignés ; deux formes
  d'URL). Sur les 4 lois suivies : 4/4 renseignés. En cas d'absence, l'appariement reste
  possible manuellement à la validation.
- **Codes de groupes ODSEN historiques** (UMP/LREM/CRC) : source de confusion, à
  neutraliser par le fichier de référence commenté.
- Scrutins disponibles **depuis octobre 2006 seulement** — sans impact pour meteox.
- Fraîcheur J+1 (dumps quotidiens) — suffisant pour l'usage.

Aucune nouvelle dépendance externe payante ni credential : uniquement des fichiers
statiques publics du Sénat (à ajouter à `docs/DEPENDENCIES.md` lors de l'implémentation).

## 7. Ce qui reste à décider par l'utilisateur

1. **Mapping des groupes ambigus** : UC, RDSE, NI/RN (§ 4).
2. **Traitement du bloc `extremeDroite` vide** au Sénat : afficher 0, masquer le bloc,
   ou autre représentation.
3. **Quel scrutin publier par loi** quand il en existe plusieurs (1ʳᵉ lecture et CMP) :
   dernière lecture au Sénat, ou les deux ?
4. **Produit** : la météo Sénat est-elle une seconde carte/facette par loi, et que
   montre-t-on pour une loi sans scrutin public (cas PFAS) ?
5. **Priorité** de ce chantier par rapport au reste de l'issue #3 et au module
   cosignataires (#33).
