// Browser geolocation + place-name resolution (Open-Meteo forward, BigDataCloud reverse).

const GEOCODE = 'https://geocoding-api.open-meteo.com/v1/search';
const REVERSE = 'https://api.bigdatacloud.net/data/reverse-geocode-client';

/** Wrap navigator.geolocation in a promise. */
export function currentPosition(timeout = 8000) {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('unsupported'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude }),
      (err) => reject(err),
      { timeout, maximumAge: 300000, enableHighAccuracy: false },
    );
  });
}

/** Reverse geocode coords -> readable place. Falls back to coords string. */
export async function reverseName({ lat, lon }) {
  try {
    const url = `${REVERSE}?latitude=${lat}&longitude=${lon}&localityLanguage=fr`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('reverse failed');
    const d = await res.json();
    const name = d.city || d.locality || d.principalSubdivision;
    return {
      name: name || `${lat.toFixed(2)}, ${lon.toFixed(2)}`,
      admin: d.principalSubdivision || 'France',
      lat,
      lon,
    };
  } catch {
    return { name: `${lat.toFixed(2)}, ${lon.toFixed(2)}`, admin: 'France', lat, lon };
  }
}

/** Forward search, France-only, for the manual picker. */
export async function searchPlaces(query) {
  const q = query.trim();
  if (q.length < 2) return [];
  const url = `${GEOCODE}?name=${encodeURIComponent(q)}&count=6&language=fr&countryCode=FR`;
  const res = await fetch(url);
  if (!res.ok) return [];
  const data = await res.json();
  return (data.results ?? []).map((r) => ({
    name: r.name,
    admin: r.admin1 || r.admin2 || 'France',
    lat: r.latitude,
    lon: r.longitude,
  }));
}
