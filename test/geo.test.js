import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { currentPosition, reverseName, searchPlaces } from '../src/lib/geo.js';

describe('geo helpers', () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('currentPosition', () => {
    test('rejects if geolocation is not supported', async () => {
      const originalNavigator = global.navigator;
      // Temporarily define navigator without geolocation
      Object.defineProperty(global, 'navigator', {
        value: {},
        writable: true,
        configurable: true,
      });

      await expect(currentPosition()).rejects.toThrow('unsupported');

      Object.defineProperty(global, 'navigator', {
        value: originalNavigator,
        writable: true,
        configurable: true,
      });
    });

    test('resolves coordinates on success', async () => {
      const mockGeolocation = {
        getCurrentPosition: vi.fn().mockImplementation((success) => {
          success({
            coords: {
              latitude: 48.8566,
              longitude: 2.3522,
            },
          });
        }),
      };
      const originalNavigator = global.navigator;
      Object.defineProperty(global, 'navigator', {
        value: { geolocation: mockGeolocation },
        writable: true,
        configurable: true,
      });

      const pos = await currentPosition();
      expect(pos).toEqual({ lat: 48.8566, lon: 2.3522 });

      Object.defineProperty(global, 'navigator', {
        value: originalNavigator,
        writable: true,
        configurable: true,
      });
    });

    test('rejects on geolocation failure', async () => {
      const mockGeolocation = {
        getCurrentPosition: vi.fn().mockImplementation((success, error) => {
          error(new Error('Permission denied'));
        }),
      };
      const originalNavigator = global.navigator;
      Object.defineProperty(global, 'navigator', {
        value: { geolocation: mockGeolocation },
        writable: true,
        configurable: true,
      });

      await expect(currentPosition()).rejects.toThrow('Permission denied');

      Object.defineProperty(global, 'navigator', {
        value: originalNavigator,
        writable: true,
        configurable: true,
      });
    });
  });

  describe('reverseName', () => {
    test('resolves locality name from API on success', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          city: 'Paris',
          principalSubdivision: 'Île-de-France',
        }),
      });

      const res = await reverseName({ lat: 48.85, lon: 2.35 });
      expect(res).toEqual({
        name: 'Paris',
        admin: 'Île-de-France',
        lat: 48.85,
        lon: 2.35,
      });
    });

    test('falls back if city is empty but subdivision is present', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          principalSubdivision: 'Île-de-France',
        }),
      });

      const res = await reverseName({ lat: 48.85, lon: 2.35 });
      expect(res).toEqual({
        name: 'Île-de-France',
        admin: 'Île-de-France',
        lat: 48.85,
        lon: 2.35,
      });
    });

    test('falls back if API returns no useful fields', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({}),
      });

      const res = await reverseName({ lat: 48.85, lon: 2.35 });
      expect(res).toEqual({
        name: '48.85, 2.35',
        admin: 'France',
        lat: 48.85,
        lon: 2.35,
      });
    });

    test('falls back if fetch fails', async () => {
      global.fetch.mockRejectedValueOnce(new Error('Network error'));

      const res = await reverseName({ lat: 48.85, lon: 2.35 });
      expect(res).toEqual({
        name: '48.85, 2.35',
        admin: 'France',
        lat: 48.85,
        lon: 2.35,
      });
    });

    test('falls back if API response is not ok', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: false,
      });

      const res = await reverseName({ lat: 48.85, lon: 2.35 });
      expect(res).toEqual({
        name: '48.85, 2.35',
        admin: 'France',
        lat: 48.85,
        lon: 2.35,
      });
    });
  });

  describe('searchPlaces', () => {
    test('returns empty array if query length < 2', async () => {
      const res = await searchPlaces('a');
      expect(res).toEqual([]);
      expect(global.fetch).not.toHaveBeenCalled();
    });

    test('maps search results from API', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          results: [
            { name: 'Lyon', admin1: 'Auvergne-Rhône-Alpes', latitude: 45.76, longitude: 4.83 },
            { name: 'Marseille', admin2: 'Bouches-du-Rhône', latitude: 43.29, longitude: 5.37 },
            { name: 'Nice', latitude: 43.71, longitude: 7.26 },
          ],
        }),
      });

      const res = await searchPlaces('Lyo');
      expect(res).toEqual([
        { name: 'Lyon', admin: 'Auvergne-Rhône-Alpes', lat: 45.76, lon: 4.83 },
        { name: 'Marseille', admin: 'Bouches-du-Rhône', lat: 43.29, lon: 5.37 },
        { name: 'Nice', admin: 'France', lat: 43.71, lon: 7.26 },
      ]);
    });

    test('returns empty array if results is missing', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({}),
      });

      const res = await searchPlaces('Lyo');
      expect(res).toEqual([]);
    });

    test('returns empty array on network/API failure', async () => {
      global.fetch.mockResolvedValueOnce({ ok: false });

      const res = await searchPlaces('Lyo');
      expect(res).toEqual([]);
    });
  });
});
