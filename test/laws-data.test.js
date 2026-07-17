// Client API lois + bascule snapshot (issue #5). L'acceptance exige : API up → données
// API ; API down (timeout, 5xx, offline) → snapshot embarqué, sans erreur.
import { describe, test, expect, vi, beforeEach } from 'vitest';
import snapshot from '../src/data/laws-snapshot.json';

async function freshModule() {
  vi.resetModules();
  return import('../src/lib/laws-data.js');
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('snapshot embarqué', () => {
  test('est non vide, daté, et de forme valide (Golden Rule : fallback toujours servable)', () => {
    expect(snapshot.generatedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    expect(snapshot.laws.length).toBeGreaterThan(0);
    for (const l of snapshot.laws) {
      expect(typeof l.id).toBe('string');
      expect(['passed', 'upcoming']).toContain(l.status);
      expect(l.sourceUrl).toMatch(/^https:\/\/www\.assemblee-nationale\.fr\//);
    }
  });
});

describe('loadLaws', () => {
  test('sert les données API quand elle répond', async () => {
    const apiLaws = snapshot.laws.map((l) => ({ ...l, title: `[api] ${l.title}` }));
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => apiLaws }),
    );
    const { loadLaws } = await freshModule();

    const { laws, meta } = await loadLaws();

    expect(meta.source).toBe('api');
    expect(laws[0].title).toMatch(/^\[api\]/);
    expect(meta.dataDate).toBeTruthy();
  });

  test('bascule sur le snapshot quand le fetch échoue (offline)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const { loadLaws } = await freshModule();

    const { laws, meta } = await loadLaws();

    expect(meta.source).toBe('snapshot');
    expect(meta.dataDate).toBe(snapshot.generatedAt);
    expect(laws).toEqual(snapshot.laws);
  });

  test('bascule sur le snapshot sur un 5xx', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }));
    const { loadLaws } = await freshModule();

    const { meta } = await loadLaws();

    expect(meta.source).toBe('snapshot');
  });

  test('rejette une réponse API de forme invalide au profit du snapshot', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => [{ pas: 'une loi' }] }),
    );
    const { loadLaws } = await freshModule();

    const { meta } = await loadLaws();

    expect(meta.source).toBe('snapshot');
  });

  test('mémoïse : un seul fetch par session', async () => {
    const spy = vi.fn().mockResolvedValue({ ok: true, json: async () => snapshot.laws });
    vi.stubGlobal('fetch', spy);
    const { loadLaws, getLoadedLaws } = await freshModule();

    await loadLaws();
    await loadLaws();

    expect(spy).toHaveBeenCalledTimes(1);
    expect(getLoadedLaws().length).toBe(snapshot.laws.length);
  });
});
