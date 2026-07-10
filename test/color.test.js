import { describe, test, expect } from 'vitest';
import { rampColor, normalize } from '../src/lib/color.js';

describe('color helpers', () => {
  describe('normalize', () => {
    test('normalizes values to [0,1]', () => {
      expect(normalize(15, 10, 20)).toBe(0.5);
      expect(normalize(10, 10, 20)).toBe(0);
      expect(normalize(20, 10, 20)).toBe(1);
    });

    test('handles edge cases', () => {
      expect(normalize(null, 10, 20)).toBe(0.5);
      expect(normalize(undefined, 10, 20)).toBe(0.5);
      expect(normalize(15, 10, 10)).toBe(0.5); // min === max
    });
  });

  describe('rampColor', () => {
    test('clamps t to [0,1]', () => {
      expect(rampColor(-1)).toBe(rampColor(0));
      expect(rampColor(2)).toBe(rampColor(1));
    });

    test('returns correct oklch format', () => {
      const color = rampColor(0.5);
      expect(color).toMatch(/^oklch\(\d+(\.\d+)?% \d+(\.\d+)? \d+(\.\d+)?\)$/);
    });

    test('handles stop boundaries', () => {
      // Test exact stops or bounds
      expect(rampColor(0)).toBe('oklch(60.0% 0.150 250.0)');
      expect(rampColor(1)).toBe('oklch(52.0% 0.210 305.0)');
    });

    test('interpolates correctly between stops', () => {
      // For instance, let's verify intermediate points
      const colorMid = rampColor(0.5); // Stop at 0.5 is L=0.72, C=0.16, H=72
      expect(colorMid).toBe('oklch(72.0% 0.160 72.0)');
      
      const colorClose = rampColor(0.1); // between 0.0 and 0.25
      // 0.0 stop: L=0.6, C=0.15, H=250
      // 0.25 stop: L=0.68, C=0.16, H=142
      // k = 0.1 / 0.25 = 0.4
      // L = 0.6 + 0.08 * 0.4 = 0.632 => 63.2%
      expect(colorClose).toContain('63.2%');
    });
  });
});
