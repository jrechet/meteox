# Météo Évolution

Compare aujourd'hui à la météo du **même jour calendaire** depuis 1940. Un curseur continu remonte les décennies ; une courbe montre la tendance de fond du réchauffement. France uniquement.

**Live :** https://jrechet.github.io/meteox/

## Données

- [Open-Meteo](https://open-meteo.com) — réanalyse **ERA5** (archive 1940→) + **prévision** pour le jour même. Aucune clé, 100 % client-side.
- Géocodage commune : Open-Meteo (`countryCode=FR`). Reverse (position → nom) : BigDataCloud.

## Stack

Vanilla JS + Vite, zéro dépendance runtime. Chart SVG fait main. Tokens OKLCH.

## Dev

```bash
npm install
npm run dev      # http://localhost:5180
npm run build    # -> dist/
```

## Déploiement

Push sur `main` → GitHub Actions build + publie sur GitHub Pages (`.github/workflows/deploy.yml`).
