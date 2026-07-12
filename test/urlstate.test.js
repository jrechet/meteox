import { describe, test, expect } from 'vitest';
import { parseHash } from '../src/lib/urlstate.js';

describe('parseHash', () => {
  test('returns null for an empty hash', () => {
    expect(parseHash('')).toBeNull();
    expect(parseHash('#')).toBeNull();
  });

  test('parses a full shared link', () => {
    const s = parseHash('#lat=48.8566&lon=2.3522&name=Paris&admin=Île-de-France&mode=period&year=1976&win=10&date=2026-07-03');
    expect(s.lat).toBeCloseTo(48.8566, 4);
    expect(s.lon).toBeCloseTo(2.3522, 4);
    expect(s.name).toBe('Paris');
    expect(s.admin).toBe('Île-de-France');
    expect(s.mode).toBe('period');
    expect(s.year).toBe(1976);
    expect(s.win).toBe(10);
    expect(s.date).toBe('2026-07-03');
  });

  test('accepts the politics mode (shareable Lois & Climat tab)', () => {
    expect(parseHash('#lat=48&lon=2&mode=politics').mode).toBe('politics');
  });

  test('rejects out-of-vocabulary values', () => {
    const s = parseHash('#lat=48&lon=2&mode=bogus&win=7&date=nope');
    expect(s.mode).toBeNull(); // only day|period
    expect(s.win).toBeNull(); // only 5|10|30
    expect(s.date).toBeNull(); // must be YYYY-MM-DD
  });

  test('tolerates a partial hash (location only)', () => {
    const s = parseHash('#lat=43.6&lon=1.44');
    expect(s.lat).toBeCloseTo(43.6, 2);
    expect(s.year).toBeNull();
    expect(s.mode).toBeNull();
  });
});
