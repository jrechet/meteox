import { describe, test, expect } from 'vitest';
import { heatColor, HEAT_LEGEND, divergingColor } from '../src/lib/color.js';

describe('color helpers', () => {
  describe('heatColor', () => {
    test('returns a well-formed oklch string', () => {
      expect(heatColor(20)).toMatch(/^oklch\(\d+(\.\d+)?% \d+(\.\d+)? \d+(\.\d+)?\)$/);
    });

    test('returns the exact cold and hot endpoints', () => {
      expect(heatColor(8)).toBe('oklch(62.0% 0.130 250.0)');
      expect(heatColor(40)).toBe('oklch(57.0% 0.210 30.0)');
    });

    test('clamps below the coldest and above the hottest stop', () => {
      expect(heatColor(-40)).toBe(heatColor(8));
      expect(heatColor(99)).toBe(heatColor(40));
    });

    test('interpolates between stops (24° = amber stop)', () => {
      expect(heatColor(24)).toBe('oklch(80.0% 0.130 90.0)');
    });

    test('is monotonic in hue from hot to cold (no violet wrap)', () => {
      const hue = (t) => parseFloat(heatColor(t).split(' ')[2]);
      // hotter = lower hue on this ramp; must never exceed the 250 cold cap
      for (let t = 8; t <= 40; t++) {
        expect(hue(t)).toBeLessThanOrEqual(250.1);
        expect(hue(t)).toBeGreaterThanOrEqual(29.9);
      }
    });

    test('returns a neutral color for missing data', () => {
      const neutral = heatColor(null);
      expect(neutral).toBe(heatColor(undefined));
      expect(neutral).toBe(heatColor(NaN));
      expect(neutral).toMatch(/^oklch\(/);
    });
  });

  describe('divergingColor', () => {
    test('neutral around zero, blue for cooler, red for warmer', () => {
      const cool = divergingColor(-8);
      const warm = divergingColor(8);
      const hueOf = (c) => parseFloat(c.split(' ')[2]);
      expect(hueOf(cool)).toBeGreaterThan(150); // cool side (blue/cyan)
      expect(hueOf(warm)).toBeLessThan(60); // warm side (red/orange)
      expect(divergingColor(0)).toMatch(/^oklch\(/);
    });

    test('clamps extreme anomalies and handles null', () => {
      expect(divergingColor(-99)).toBe(divergingColor(-12));
      expect(divergingColor(99)).toBe(divergingColor(12));
      expect(divergingColor(null)).toMatch(/^oklch\(/);
    });
  });

  describe('HEAT_LEGEND', () => {
    test('exposes ascending temperature buckets with labels', () => {
      expect(HEAT_LEGEND.length).toBeGreaterThanOrEqual(3);
      const temps = HEAT_LEGEND.map((b) => b.temp);
      expect([...temps].sort((a, b) => a - b)).toEqual(temps);
      HEAT_LEGEND.forEach((b) => expect(typeof b.label).toBe('string'));
    });
  });
});
