# PRD — Onglet « Lois & Climat » (Political Tab)

> Source du plan : `political_tab_plan.md`
> Plan d'évolution phase 2 : [navigation-lois-fiabilisation.md](file:///Users/jre/dev/meteox/docs/plans/navigation-lois-fiabilisation.md)
> Ce document suit l'avancement réel de l'implémentation. Cochez au fur et à mesure.
> Légende : `[x]` fait · `[ ]` à faire · **(partiel)** = commencé mais incomplet.

---

## 0. Statut global (au 2026-07-12)

| Phase | État | Avancement |
|-------|------|-----------|
| **Étape 1 — V1 statique interactive** | 🟢 Terminée | ~95 % |
| **Étape 2 — V1.5 automatisation données** | 🔴 Non commencée | 0 % |
| **Étape 3 — V2 FranceConnect / envoi direct** | 🔴 Non commencée | 0 % |
| **Qualité (tests, a11y, revue, deploy)** | 🟢 Bon | ~65 % |

**Bloquants avant prod** : fiabilité des données de votes (actuellement exemples), ciblage réel de l'élu, tests, décision éditoriale sur le ton.

---

## 1. Objectifs (rappel)

- [ ] **Transparence démocratique** — visualiser l'impact des lois + alignement des partis _(rendu OK, données à fiabiliser)_
- [ ] **Action citoyenne** — interpeller les élus sur les votes clés _(modal OK, ciblage élu manquant)_
- [x] **Simplicité** — indicateurs visuels clairs plutôt que texte législatif

---

## 2. Étape 1 — Interface client (V1)

### 2.1 Navigation & intégration
- [x] Onglet « Lois & Climat 📜 » ajouté (`views.js`)
- [x] Mode `politics` câblé dans `machineContentHTML`
- [x] Masquage du curseur d'année + de la courbe en mode politics
- [x] Pattern ARIA tab (`role=tab` / `tabpanel`, `aria-selected`, tabindex, flèches clavier)

### 2.2 Modèle de données (`src/lib/laws.js`)
- [x] Interface `Law` (id, title, category, status, date, summary, sourceUrl)
- [x] Indicateurs d'impact `-2..+2` : `pesticides`, `pognonPuissants`, `peupleSante`, `partageEau`
- [x] Structure `votes` par groupe (gauche / milieu / droite / extremeDroite)
- [x] Jeu d'exemple (5 lois : 3 votées, 2 à venir)
- [ ] **Données de votes réelles et sourcées** _(actuellement illustratives — à remplacer avant prod)_

### 2.3 Composant `src/components/politics.js`
- [x] Section « Prochains scrutins » (grille de cartes `upcoming`)
- [ ] **Carrousel horizontal** des futurs votes _(actuellement grille verticale — à confirmer vs plan)_
- [x] Section « Bilan des lois votées » (grille de cartes `passed`)
- [x] Jauges d'indicateurs (`indicatorMeterHTML`, −2→+2 en position, code couleur pos/neg/neutre)
- [x] Matrice de vote par groupe (`voteGroupHTML`, barres Pour/Contre/Abstention)
- [x] Filtres par catégorie (Toutes / pesticides / eau / canicule / agriculture)
- [x] Liens officiels (Legifrance / assemblee-nationale.fr)
- [x] Bouton « Interpeller mon député » sur les votes à venir

### 2.4 Icône « Intervenir / Geste Citoyen »
- [x] SVG original (enveloppe + éclair d'action) — `citizenActionIcon`
- [x] Distinct de l'icône « Partager »

### 2.5 Action d'interpellation (modal)
- [x] Modal (`role=dialog`, `aria-modal`) déclenchée par `data-action="interpellate"`
- [x] Génération d'un template d'e-mail pré-rempli (sujet + corps contextualisé par loi)
- [x] Bouton « Copier le message » (clipboard + feedback)
- [x] Bouton « Envoyer par e-mail » (`mailto:`)
- [x] Fermeture (croix, clic hors-modal, touche Échap)
- [x] Personnalisation par code postal (département dérivé, lettre localisée, `mailto:` mis à jour en direct) + lien annuaire officiel « Trouver votre député »
- [x] Piège de focus (focus-trap) dans la modal + retour focus à l'ouvreur + autofocus du champ
- [x] Validation du code postal (filtrage numérique, 5 chiffres) + libellé de circonscription
- [ ] **Ciblage direct de l'e-mail du député** _(résolution circonscription → adresse e-mail réelle — reste en V2, nécessite un référentiel des 577 élus)_

### 2.6 Design system
- [x] Styles intégrés (crème / Fraunces·Inter / OKLCH) — `politics-*`, `pcard`, `indicator-meter`, `vote-group`, `cmodal`, `btn--citoyen`
- [ ] Revue responsive **375 / 768 / 1280 / 1920** (Playwright) — pas encore vérifiée
- [ ] Vérif contraste des couleurs Pour/Contre/Abstention

---

## 3. Étape 2 — Automatisation des données (V1.5)

- [ ] Script Node de récupération des **scrutins publics** (API ouverte Assemblée nationale)
- [ ] Extension au **Sénat**
- [ ] Mapping scrutin → indicateurs d'impact (règles ou saisie manuelle assistée)
- [ ] Génération/écriture automatique du JSON `laws.js` (ou `laws.json`)
- [ ] **GitHub Action** de rafraîchissement périodique + PR/commit auto
- [ ] Cache / gestion des quotas de l'API AN

---

## 4. Étape 3 — Connexion citoyenne intégrée (V2)

- [ ] Intégration **OAuth FranceConnect** (bouton + flux)
- [ ] Récupération sécurisée de l'adresse + **circonscription électorale** certifiée
- [ ] Résolution circonscription → élu (référentiel députés/sénateurs)
- [ ] **Signature de pétition** ou **envoi direct** du courriel via API sécurisée
- [ ] Conformité RGPD (consentement, minimisation, conservation) + mentions légales
- [ ] Anti-abus (rate-limit, anti-bot) sur l'envoi

---

## 5. Qualité & transverse

### Tests
- [x] Unitaires `laws.js` (intégrité du modèle : bornes indicateurs −2..+2, totaux votes cohérents, URLs valides) + `departementLabel` + `interpellationLetter`
- [x] Unitaires `politics.js` (rendu cartes, jauges, matrice, filtre catégorie, icône)
- [x] E2E (`e2e.mjs`) : onglet visible, cartes rendues, slider masqué, pas d'overflow, modal ouvre/ferme
- [ ] Intégration jsdom (`main.test.js`) dédiée politics (filtre, copier) — E2E couvre déjà le comportement

### Accessibilité
- [x] Onglet dans le pattern ARIA tablist
- [x] Modal : focus-trap + `aria-labelledby` + restitution du focus + autofocus
- [ ] Cartes rétractables : `aria-expanded` si repli implémenté
- [ ] Jauges/matrice : équivalent texte pour lecteurs d'écran

### URL / partage
- [x] Mode `politics` restaurable via lien partageable (`#…&mode=politics`)

### Livraison
- [x] Revue UI Playwright (375 + 1280 vérifiés ; overflow gardé par E2E)
- [ ] Revue UI complète 768 + 1920
- [x] Build + suite de tests verts en CI (gate en place)
- [ ] Déploiement GitHub Pages + vérif prod _(en cours)_

---

## 6. Questions ouvertes / risques (à trancher)

- [ ] **Ton éditorial & neutralité** — libellés orientés (« pognon des puissants », « lobbies », « méga-bassines »). Choix assumé mais expose à un reproche de biais ; définir la ligne éditoriale et une méthodologie de notation transparente des indicateurs.
- [ ] **Fiabilité/sourcing des votes** — les chiffres actuels sont illustratifs ; ne pas publier de données de scrutin non vérifiées (risque de désinformation).
- [ ] **Méthodo des indicateurs −2..+2** — documenter comment chaque score est attribué (sinon perçu comme arbitraire).
- [ ] **Périmètre légal V2** — FranceConnect impose habilitation, conventions et contraintes fortes ; valider la faisabilité avant tout dev.
- [ ] **Carrousel vs grille** — confirmer le format voulu pour les futurs votes.

---

## 7. Prochaines actions recommandées (ordre)

1. [x] Revue Playwright de l'onglet (375 + 1280) + corrections (bug slider masqué corrigé)
2. [x] Ciblage élu : code postal → département + annuaire officiel (résolution e-mail directe → V2)
3. [x] Tests `laws.js` / `politics.js` + E2E politics
4. [ ] Décision éditoriale (ton + méthodo indicateurs) documentée dans le repo
5. [ ] Remplacer les données d'exemple par des scrutins réels sourcés (amorce Étape 2)
6. [ ] Déploiement + vérif prod _(en cours)_
