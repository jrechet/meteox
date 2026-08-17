// Pré-génère les cartes de France de l'animation : pour CHAQUE jour calendaire,
// la température maximale des 20 villes, année par année depuis 1940.
//
// Pourquoi : à l'exécution, une image d'animation = une requête d'archive portant
// les 20 villes, et Open-Meteo pondère une requête par son nombre de lieux — la
// carte coûte donc ~20 des 600 appels/minute du palier gratuit. Rejouer 1940 →
// aujourd'hui (87 images) est structurellement hors budget : mesuré, l'animation
// s'arrêtait vers 1948 sur « Minutely API request limit exceeded ».
//
// Ici on inverse la découpe : **une requête par ville** couvrant toute la série,
// soit 20 requêtes au total, réparties en 366 fichiers d'environ 10 ko. L'app
// n'en charge qu'un (le jour affiché) et l'animation ne touche plus l'API.
//
// Les années passées sont de la réanalyse figée : elles ne changent plus, d'où
// une régénération annuelle (workflow `refresh-heatmap-archive`, également
// déclenchable à la main). L'année en cours reste servie par l'API au runtime.
//
// Sortie : public/data/heatmap/MM-DD.json
//   { "from": 1940, "cities": ["Lille", …], "t": [[18.2, 19.1, …], …] }
//   `t[i]` = l'année `from + i`, une valeur par ville dans l'ordre de `cities`.
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { HEATMAP_CITIES } from '../src/lib/weather.js';

const OUT_DIR = fileURLToPath(new URL('../public/data/heatmap/', import.meta.url));
// Séries brutes par ville, hors dépôt : elles rendent le script reprenable. Vingt
// requêtes très lourdes contre une API à quotas ne peuvent pas être du
// tout-ou-rien — une limite horaire atteinte à la 16ᵉ ville jetait les 15 autres.
const CACHE_DIR = fileURLToPath(new URL('../.cache/heatmap-cities/', import.meta.url));
const ARCHIVE = 'https://archive-api.open-meteo.com/v1/archive';
const START_YEAR = 1940;
// Open-Meteo refuse au-delà d'une poignée d'appels simultanés
// (« Too many concurrent requests ») — ne pas augmenter à l'aveugle.
const CONCURRENCY = 4;
const RETRIES = 6;

const fail = (msg) => {
  console.error(`✗ ${msg}`);
  process.exit(1);
};

/** Dernière année complète : l'année en cours est incomplète et servie par l'API. */
const END_YEAR = new Date().getUTCFullYear() - 1;

async function fetchCity(city, attempt = 1) {
  const url =
    `${ARCHIVE}?latitude=${city.lat}&longitude=${city.lon}` +
    `&start_date=${START_YEAR}-01-01&end_date=${END_YEAR}-12-31` +
    `&daily=temperature_2m_max&timezone=Europe%2FParis`;

  // Une requête porte ~31 000 jours : elle est longue, et elle peut tomber au
  // niveau réseau (socket coupée) et pas seulement en HTTP. Les deux cas se
  // réessaient — sinon une coupure passagère jette 15 villes déjà récupérées.
  let reason = null;
  try {
    const res = await fetch(url);
    const body = await res.json().catch(() => null);
    if (res.ok && body?.daily?.time) return body.daily;
    reason = body?.reason ?? `HTTP ${res.status}`;
  } catch (err) {
    reason = err.message;
  }

  if (attempt > RETRIES) fail(`${city.name} : ${reason}`);
  // Les refus d'Open-Meteo sont des limites de débit : attendre, pas insister.
  // Une limite horaire ne se rattrape pas en une minute — l'attente suit l'échelle
  // annoncée par l'API plutôt qu'un backoff générique.
  const waitMs = /Hourly/i.test(reason) ? 10 * 60_000 : 60_000 * attempt;
  console.warn(`  ${city.name} : ${reason} — nouvelle tentative dans ${Math.round(waitMs / 1000)}s`);
  await new Promise((r) => setTimeout(r, waitMs));
  return fetchCity(city, attempt + 1);
}

/** Série d'une ville, depuis le cache disque si elle a déjà été récupérée. */
async function loadCity(city) {
  const file = `${CACHE_DIR}${city.name}.json`;
  if (existsSync(file)) {
    const cached = JSON.parse(readFileSync(file, 'utf8'));
    if (cached.endYear === END_YEAR) {
      console.log(`  · ${city.name} (déjà en cache)`);
      return cached.daily;
    }
  }
  const daily = await fetchCity(city);
  mkdirSync(CACHE_DIR, { recursive: true });
  writeFileSync(file, JSON.stringify({ endYear: END_YEAR, daily }));
  console.log(`  ✓ ${city.name} (${daily.time.length} jours)`);
  return daily;
}

async function run() {
  console.log(`Séries ${START_YEAR}–${END_YEAR} pour ${HEATMAP_CITIES.length} villes…`);
  const byCity = new Array(HEATMAP_CITIES.length);
  let next = 0;
  const worker = async () => {
    while (next < HEATMAP_CITIES.length) {
      const index = next++;
      const city = HEATMAP_CITIES[index];
      const daily = await loadCity(city);
      byCity[index] = new Map(daily.time.map((t, i) => [t, daily.temperature_2m_max[i]]));
    }
  };
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));

  const names = HEATMAP_CITIES.map((c) => c.name);
  mkdirSync(OUT_DIR, { recursive: true });

  // Un fichier par jour calendaire. 2000 est bissextile : il donne bien 366 jours.
  let written = 0;
  let unchanged = 0;
  for (let d = new Date(Date.UTC(2000, 0, 1)); d.getUTCFullYear() === 2000; d.setUTCDate(d.getUTCDate() + 1)) {
    const mmdd = d.toISOString().slice(5, 10);
    const rows = [];
    for (let year = START_YEAR; year <= END_YEAR; year++) {
      const key = `${year}-${mmdd}`;
      // Le 29/02 n'existe pas les années non bissextiles : la ligne reste absente.
      if (!byCity[0].has(key)) {
        rows.push(null);
        continue;
      }
      rows.push(byCity.map((series) => {
        const v = series.get(key);
        return v == null ? null : Math.round(v * 10) / 10;
      }));
    }
    const payload = JSON.stringify({ from: START_YEAR, cities: names, t: rows });
    const file = `${OUT_DIR}${mmdd}.json`;
    if (existsSync(file) && readFileSync(file, 'utf8') === payload) {
      unchanged++;
      continue;
    }
    writeFileSync(file, payload);
    written++;
  }

  console.log(`✓ ${written} fichier(s) écrit(s), ${unchanged} inchangé(s) dans public/data/heatmap/`);
  console.log(`  couverture : ${START_YEAR}–${END_YEAR} · ${names.length} villes`);
}

run().catch((e) => fail(e.message));
