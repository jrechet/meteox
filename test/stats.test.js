import { describe, test, expect } from 'vitest';
import { mean, median, linearFit, baselineMean, baselineMedian } from '../src/lib/stats.js';

describe('stats helpers', () => {
  describe('mean', () => {
    test('calculates correct mean for non-empty arrays', () => {
      expect(mean([10, 20, 30])).toBe(20);
      expect(mean([5, -5])).toBe(0);
    });

    test('ignores non-numeric or null values', () => {
      expect(mean([10, null, 20, NaN])).toBe(15);
    });

    test('returns null for empty or all-null arrays', () => {
      expect(mean([])).toBeNull();
      expect(mean([null, NaN])).toBeNull();
    });
  });

  describe('median', () => {
    test('calculates correct median for odd length arrays', () => {
      expect(median([10, 30, 20])).toBe(20);
      expect(median([5])).toBe(5);
    });

    test('calculates correct median for even length arrays', () => {
      expect(median([10, 40, 20, 30])).toBe(25);
    });

    test('ignores non-numeric or null values', () => {
      expect(median([10, null, 30, NaN, 20])).toBe(20);
    });

    test('returns null for empty or all-null arrays', () => {
      expect(median([])).toBeNull();
      expect(median([null, NaN])).toBeNull();
    });
  });

  describe('linearFit', () => {
    test('calculates correct slope and intercept', () => {
      const points = [
        { x: 0, y: 1 },
        { x: 1, y: 3 },
        { x: 2, y: 5 },
      ];
      const fit = linearFit(points);
      expect(fit).not.toBeNull();
      expect(fit.slope).toBe(2);
      expect(fit.intercept).toBe(1);
      expect(fit.predict(3)).toBe(7);
    });

    test('returns null for insufficient data', () => {
      expect(linearFit([])).toBeNull();
      expect(linearFit([{ x: 1, y: 2 }])).toBeNull();
    });

    test('returns null for vertical line (zero denominator)', () => {
      const points = [
        { x: 1, y: 2 },
        { x: 1, y: 3 },
      ];
      expect(linearFit(points)).toBeNull();
    });
  });

  describe('baselines', () => {
    const mockSeries = [
      { year: 1940, tmax: 10 },
      { year: 1941, tmax: 20 },
      { year: 1942, tmax: 15 },
      { year: 1943, tmax: 25 },
      { year: 1944, tmax: null }, // should be ignored
      { year: 1945, tmax: 30 },
    ];

    test('baselineMean computes mean of earliest non-null years', () => {
      // slice(0, 3) non-null values: 10, 20, 15 => mean is 15
      expect(baselineMean(mockSeries, 3)).toBe(15);
    });

    test('baselineMedian computes median of earliest non-null years', () => {
      // slice(0, 3) non-null values: 10, 20, 15 => sorted is [10, 15, 20] => median is 15
      expect(baselineMedian(mockSeries, 3)).toBe(15);
    });
  });
});
