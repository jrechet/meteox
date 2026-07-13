# Météo Évolution — Guide de l'Agent

Ce document résume l'architecture, le fonctionnement et les commandes clés de l'application **Météo Évolution**, ainsi que l'état d'avancement de la fonctionnalité de comparaison de périodes historiques.

---

## 1. Présentation Générale de l'Application

**Météo Évolution** est une application web monopage (SPA) client-side permettant de comparer la météo du jour avec celle du même jour calendaire depuis 1940 en France. L'objectif est d'illustrer de manière interactive le réchauffement climatique à l'échelle locale.

* **URL de Production :** [https://jrechet.github.io/meteox/](https://jrechet.github.io/meteox/)
* **Hébergement :** GitHub Pages
* **Source des Données :** [Open-Meteo](https://open-meteo.com)
  * Réanalyse **ERA5** pour l'historique de 1940 à aujourd'hui.
  * Prévision à court terme pour le jour même.
  * Géocodage des communes françaises.
  * Reverse géocodage de la position utilisateur (via BigDataCloud).

---

## 2. Choix Technologiques & Stack

L'application est construite de manière ultra-légère et performante, sans framework lourd (pas de React/Vue/Angular) :
* **Vanilla JS (ES Modules)** : Composants écrits sous forme de fonctions pures retournant des templates HTML (chaînes de caractères).
* **Vite** : Outil de build et serveur de développement extrêmement rapide.
* **SVG fait main** : Les graphiques interactifs sont dessinés directement en SVG natif interpolé.
* **OKLCH & CSS Variables** : Utilisation d'un système de tokens de design modernes basés sur OKLCH pour des dégradés de couleurs fluides selon la température (effets de glow, etc.).
* **Zéro dépendance runtime** : Seul Vite est utilisé comme dépendance de développement.

---

## 3. Architecture du Code

Le projet est structuré comme suit :

```
/Users/jre/dev/meteox/
├── index.html                  # Squelette HTML de l'application
├── package.json                # Dépendances de dev et scripts de build
├── vite.config.js              # Configuration de Vite
└── src/
    ├── main.js                 # Orchestrateur de l'application (Router, Event Bindings, State)
    ├── components/
    │   ├── chart.js            # Courbe de tendance décennale (SVG)
    │   ├── period.js           # Vue du mode "Période" (Comparaison multi-jours)
    │   └── views.js            # Vues HTML de l'application (Hero, Focus, Chargement, Erreur)
    ├── lib/
    │   ├── weather.js          # API client Open-Meteo & Système de cache LocalStorage (mx:v2)
    │   ├── geo.js              # Géolocalisation, Reverse Geocoding & Recherche de communes
    │   ├── stats.js            # Outils statistiques (Moyennes, Médianes, Régression linéaire)
    │   ├── format.js           # Formatage de dates, températures, vent et précipitations
    │   └── color.js            # Normalisation et dégradés de couleurs OKLCH
    └── styles/
        ├── app.css             # Styles spécifiques à l'application (Layouts, Boutons, Composants)
        ├── global.css          # Reset de base et styles globaux
        └── tokens.css          # Variables de couleurs, polices et espacements
```

---

## 4. Gestion de l'État Global

L'application utilise un store d'état réactif minimaliste dans `src/main.js` :
```javascript
const state = {
  todayIso: now.toISOString().slice(0, 10), // Date du jour (YYYY-MM-DD)
  currentYear: now.getFullYear(),            // Année en cours (ex: 2026)
  mmdd: monthDay(now),                       // MM-DD du jour ciblé (ex: "07-09")
  dayLabel: dayMonthLabel(now),              // Libellé en français (ex: "9 juillet")
  location: null,                            // Commune sélectionnée { name, lat, lon, admin }
  today: null,                               // Conditions du jour même
  series: null,                              // MM-DD historique (1940-présent)
  windows: null,                             // Fenêtres de N jours par année { [year]: rows[] }
  recent: null,                              // Les 30 derniers jours réels de l'année en cours
  selectedYear: now.getFullYear(),           // Année sélectionnée par l'utilisateur via le slider
  mode: 'day',                               // 'day' (Jour même) | 'period' (Période historique)
  windowLen: 10,                             // Longueur de la période à analyser (5, 10 ou 30 jours)
};
```

---

## 5. Commandes de Développement

Toutes les commandes s'exécutent depuis la racine du projet :

```bash
# Installation des dépendances (Vite)
npm install

# Démarrage du serveur de développement (http://localhost:5180 par défaut)
npm run dev

# Construction de l'application pour la production (génère le dossier /dist)
npm run build

# Prévisualisation locale du build de production
npm run preview
```

---

## 6. Environnement de Production & Déploiement

Le déploiement est entièrement automatisé via un workflow GitHub Actions (`.github/workflows/deploy.yml`) :
1. Chaque push ou merge sur la branche `main` déclenche le workflow.
2. Le workflow installe Node.js 20, télécharge les dépendances via `npm ci`, et compile le projet en lançant `npm run build`.
3. Le dossier de sortie `/dist` est empaqueté comme artefact de page.
4. L'artefact est déployé automatiquement sur les serveurs de **GitHub Pages**.

---

## 7. Fonctionnalité : Comparaison de Période Historique

### Objectif initial :
Permettre à l'utilisateur de simuler la météo des jours précédents (jusqu'à 10 ou 30 jours) de l'année en cours versus ces mêmes jours $X$ années en arrière, à l'aide d'un curseur/slider (ex: les 10 jours précédant aujourd'hui en 2026 comparés aux mêmes 10 jours en 1976).

### Analyse du travail accompli par le précédent agent :
Le travail d'infrastructure et d'interface utilisateur est **extrêmement avancé** et très propre :
1. **Cache LocalStorage mis à niveau** : Passage au namespace `mx:v2` pour stocker de façon compacte les séries historiques et les fenêtres de jours glissants.
2. **Récupération des données multi-jours** :
   * `fetchRecent` récupère les 30 derniers jours de l'année en cours via le paramètre `past_days` de l'API Open-Meteo Forecast.
   * `fetchHistory` récupère la totalité de la série ERA5 depuis 1940 et isole à la fois le point du jour calendaire (`series`) et une fenêtre de 30 jours se terminant ce jour calendaire pour chaque année historique (`windows`).
3. **Mise en place de l'UI** :
   * Des onglets **Jour même** et **Période** ont été ajoutés pour alterner entre le focus historique standard et la comparaison de fenêtres multi-jours.
   * Des boutons de sélection (puces/chips) permettent de choisir des périodes de **5**, **10** ou **30 jours**.
   * Le slider existant pilote dynamiquement l'année sélectionnée, réactualisant à la fois le panneau "Période" et le graphique principal.
4. **Composant Période (`src/components/period.js`)** :
   * Affiche l'écart moyen global en température (avec code couleur chaud/froid).
   * Contient un graphique SVG double courbe (`dualChart`) affichant la température maximale de chaque jour de la période pour l'année en cours (ligne continue noire) vs l'année sélectionnée (ligne en pointillés orange/accent) avec une zone ombragée d'écart (`.pband`).
   * Affiche un bandeau horizontal défilant (`pstrip`) de cartes jour par jour avec l'icône météo, la température de cette année, celle de l'année passée, et l'écart thermique précis par jour.
5. **Styles CSS complets** : Ajout de styles très soignés dans `src/styles/app.css` pour tous les nouveaux éléments (onglets, chips, tableau défilant, graphique bi-courbe).

---

## 8. Golden Rules (Règles d'Or)

* **ZÉRO MOCK / AUCUNE FAUSSE DONNÉE** : Il est strictement interdit d'inventer, d'approximer ou d'injecter de fausses données législatives ou de faux résultats de votes dans l'application. Toutes les données de lois, scrutins publics et indicateurs présentés aux utilisateurs doivent provenir de sources officielles vérifiables (.gouv.fr, Assemblée Nationale, etc.).
* **RECHERCHE ET FIABILITÉ** : Si une nouvelle loi ou un vote doit être intégré, le développeur ou l'agent doit obligatoirement rechercher les informations et liens réels de Légifrance ou de l'Assemblée Nationale correspondants.

