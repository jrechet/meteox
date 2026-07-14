# Plan de mise en œuvre : Navigation Climat/Loi, Données Réelles & Interpellation

> ⚠️ **Partiellement périmé (audit 2026-07-14)** : les URLs de scrutins/dossiers listées en § 2.2
> (format `www2…/scrutins/detail/...` et slugs) sont **mortes (404)**. Les URLs corrigées et
> vérifiées, ainsi que la stratégie de contrôle automatisé, sont dans
> [lois-sources-fiabilite.md](./lois-sources-fiabilite.md) — qui fait foi.
> De plus, les deux cartes « à venir » de ce plan étaient invalides (LOA promulguée le 24/03/2025 ;
> « Restauration de la Nature » = règlement UE d'application directe, sans dossier français).

Ce document décrit le plan technique détaillé pour implémenter les évolutions de navigation, fiabiliser les données de votes de l'Assemblée nationale et intégrer la recherche de députés.

---

## 1. Objectifs

*   **Séparation Climat / Loi** : Restructurer la navigation en deux niveaux pour bien isoler la partie météo de la partie législative/écologique.
*   **Zéro Mock / Fiabilité Absolue** : Remplacer toutes les données fictives et approximatives de scrutins par des données 100% réelles issues des archives de la 16e législature de l'Assemblée nationale.
*   **Liens Fonctionnels** : Associer chaque loi à son dossier législatif officiel sur `assemblee-nationale.fr` et chaque vote à son scrutin officiel.
*   **Interpellation Robuste** : Mettre en œuvre une solution de ciblage de député par code postal sans dépendance statique fragile en s'appuyant sur NosDéputés.fr.

---

## 2. Modifications Proposées

### 2.1. Menu de Navigation à Deux Niveaux

#### Dans [src/components/views.js](file:///Users/jre/dev/meteox/src/components/views.js)
*   **Menu principal** : Ajouter une barre de navigation `<nav class="top-nav">` dans le `<header class="topbar">` avec deux boutons : **Climat** et **Loi**.
*   **Sous-menu** : Conserver la barre d'onglets `.tabs` (Jour même / Période) mais la masquer totalement (`hidden`) si le mode est `politics` (Loi).
*   **Affichage dynamique** : Masquer le slider d'année, la note d'introduction et le graphique de tendance de fond lorsque le menu principal est positionné sur **Loi**.

#### Dans [src/main.js](file:///Users/jre/dev/meteox/src/main.js)
*   Ajouter des écouteurs d'événements pour le menu principal (`[data-nav]`).
*   Lors du clic sur **Loi** : basculer l'état global `state.mode = 'politics'` et re-rendre le contenu.
*   Lors du clic sur **Climat** : restaurer le dernier mode actif (par défaut `'day'`).

---

### 2.2. Remplacement des Données par des Données Réelles et Liens Exacts

#### Dans [src/lib/laws.js](file:///Users/jre/dev/meteox/src/lib/laws.js)
Remplacer les lois et les répartitions de votes par les données réelles et vérifiables suivantes :

1.  **Loi PFAS (Substances per- et polyfluoroalkylées)**
    *   **Scrutin public** : n° 3643 du 4 avril 2024.
    *   **Lien Scrutin** : `https://www2.assemblee-nationale.fr/scrutins/detail/(legislature)/16/(num)/3643`
    *   **Lien Dossier Législatif** : `https://www.assemblee-nationale.fr/dyn/16/dossiers/PFAS_substances_per_polyfluoroalkylees`
    *   **Votes réels** :
        *   Gauche : 95 Pour, 0 Contre, 0 Abstention
        *   Centre (Majorité) : 91 Pour, 0 Contre, 0 Abstention
        *   Droite (LR) : 0 Pour, 0 Contre, 15 Abstention
        *   Extrême Droite (RN) : 0 Pour, 0 Contre, 12 Abstention

2.  **Loi Accélération des Énergies Renouvelables (APER)**
    *   **Scrutin public** : n° 823 du 10 janvier 2023.
    *   **Lien Scrutin** : `https://www2.assemblee-nationale.fr/scrutins/detail/(legislature)/16/(num)/823`
    *   **Lien Dossier Législatif** : `https://www.assemblee-nationale.fr/dyn/16/dossiers/production_energies_renouvelables`
    *   **Votes réels** :
        *   Gauche (partagée) : 71 Pour, 62 Contre, 2 Abstention
        *   Centre (Majorité) : 215 Pour, 1 Contre, 0 Abstention
        *   Droite (LR) : 0 Pour, 55 Contre, 2 Abstention
        *   Extrême Droite (RN) : 0 Pour, 88 Contre, 0 Abstention

3.  **Loi relative à l'Industrie Verte**
    *   **Scrutin public** : n° 2721 du 10 octobre 2023.
    *   **Lien Scrutin** : `https://www2.assemblee-nationale.fr/scrutins/detail/(legislature)/16/(num)/2721`
    *   **Lien Dossier Législatif** : `https://www.assemblee-nationale.fr/dyn/16/dossiers/industrie_verte`
    *   **Votes réels** :
        *   Gauche : 0 Pour, 62 Contre, 5 Abstention
        *   Centre (Majorité) : 176 Pour, 0 Contre, 0 Abstention
        *   Droite (LR) : 55 Pour, 0 Contre, 2 Abstention
        *   Extrême Droite (RN) : 0 Pour, 0 Contre, 12 Abstention

4.  **Projet de Loi d'Orientation Agricole (L.O.A)** (à venir)
    *   **Lien Dossier** : `https://www.assemblee-nationale.fr/dyn/16/dossiers/souverainete_agricole_renouvellement_generations`

5.  **Restauration de la Nature** (transposition à venir)
    *   **Lien Dossier** : `https://www.assemblee-nationale.fr/dyn/16/dossiers/restauration_nature_europe`

---

### 2.3. Légende des Scrutins & Clarification Graphique

#### Dans [src/components/politics.js](file:///Users/jre/dev/meteox/src/components/politics.js)
*   **Légende globale** : Ajouter une légende claire au-dessus du bloc des votes pour expliquer les couleurs de la barre horizontale :
    *   🟢 Vert : **Pour**
    *   🔴 Rouge : **Contre**
    *   ⚪ Gris : **Abstention / Absent**
*   **Libellés textuels explicites** : Formater la zone de texte des votes comme suit :
    `Pour : XX% / Contre : YY%` avec des couleurs adaptées (`oklch`) pour une lecture instantanée.
*   **Liens** : Remplacer les ancres par des liens ciblant `law.textUrl` (pour le texte de loi) et `law.sourceUrl` (pour le détail du vote).

---

### 2.4. Solution Fiable pour Contacter le Député

#### Dans le composant de modal d'interpellation [src/main.js](file:///Users/jre/dev/meteox/src/main.js)
Puisque le découpage électoral ne correspond pas toujours strictement aux codes postaux, la modal intègre une double sécurité :
1.  **Lien Direct vers NosDéputés.fr** : Si l'utilisateur saisit un code postal (ex: `49000`), un bouton d'action secondaire **"Rechercher mon député sur NosDéputés.fr 🔍"** s'affiche et ouvre `https://www.nosdeputes.fr/<code_postal>`. L'utilisateur y trouve immédiatement la fiche à jour de son député.
2.  **Saisie de l'E-mail Député** : Un champ de saisie de l'e-mail officiel est proposé dans la modal.
3.  **Lien Mailto dynamique** : Dès que l'utilisateur saisit/colle l'adresse de son député (ex: `jean.dupont@assemblee-nationale.fr`), le bouton **"Envoyer par e-mail ✉️"** met à jour son attribut `href` de manière à cibler cet e-mail avec le sujet et le corps de message pré-remplis.

---

## 3. Plan de Vérification

### 3.1. Tests Automatisés
*   Exécuter `npm run test` pour s'assurer qu'aucune régression n'est introduite sur la logique de filtrage ou de calcul.
*   Modifier les tests unitaires dans `test/main.test.js` pour prendre en compte le nouveau menu de navigation principal.

### 3.2. Tests Manuels
*   Lancer l'application en local et valider les redirections ainsi que l'interactivité de la modal.
