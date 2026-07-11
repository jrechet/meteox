import { describe, test, expect } from 'vitest';
import {
  dayMonthLabel,
  monthDay,
  weekdayShort,
  shortDate,
  fmtTemp,
  fmtSigned,
  fmtMm,
  fmtWind,
  describeWeather,
  isoToday,
} from '../src/lib/format.js';

describe('format helpers', () => {
  describe('dayMonthLabel', () => {
    test('formats valid ISO dates', () => {
      expect(dayMonthLabel('2026-07-09')).toBe('9 juillet');
      expect(dayMonthLabel('1940-01-01')).toBe('1 janvier');
    });

    test('formats Date objects', () => {
      const d = new Date('2026-07-09T12:00:00');
      expect(dayMonthLabel(d)).toBe('9 juillet');
    });

    test('is robust to null, empty or invalid values', () => {
      expect(dayMonthLabel(null)).toBe('—');
      expect(dayMonthLabel(undefined)).toBe('—');
      expect(dayMonthLabel('')).toBe('—');
      expect(dayMonthLabel('not-a-date')).toBe('—');
    });
  });

  describe('monthDay', () => {
    test('formats Date objects to MM-DD', () => {
      const d = new Date(2026, 6, 9); // Month is 0-indexed, so 6 is July
      expect(monthDay(d)).toBe('07-09');
    });

    test('is robust to null, empty or invalid values', () => {
      expect(monthDay(null)).toBe('—');
      expect(monthDay(undefined)).toBe('—');
      expect(monthDay(new Date('invalid'))).toBe('—');
    });
  });

  describe('weekdayShort', () => {
    test('formats valid dates to short French weekdays', () => {
      // 2026-07-09 is a Thursday (jeu)
      expect(weekdayShort('2026-07-09')).toBe('jeu');
      // 2026-07-08 is a Wednesday (mer)
      expect(weekdayShort('2026-07-08')).toBe('mer');
    });

    test('is robust to null, empty or invalid values', () => {
      expect(weekdayShort(null)).toBe('—');
      expect(weekdayShort(undefined)).toBe('—');
      expect(weekdayShort('')).toBe('—');
      expect(weekdayShort('invalid')).toBe('—');
    });
  });

  describe('shortDate', () => {
    test('formats valid dates to compact French style', () => {
      expect(shortDate('2026-07-09')).toBe('9 juil');
      expect(shortDate('2026-12-25')).toBe('25 déc');
    });

    test('is robust to null, empty or invalid values', () => {
      expect(shortDate(null)).toBe('—');
      expect(shortDate(undefined)).toBe('—');
      expect(shortDate('')).toBe('—');
      expect(shortDate('invalid')).toBe('—');
    });
  });

  describe('fmtTemp', () => {
    test('formats temperature with or without unit', () => {
      expect(fmtTemp(21.4)).toBe('21°');
      expect(fmtTemp(21.4, false)).toBe('21');
      expect(fmtTemp(-5.6)).toBe('-6°');
    });

    test('returns fallback for invalid temperatures', () => {
      expect(fmtTemp(null)).toBe('—');
      expect(fmtTemp(NaN)).toBe('—');
    });
  });

  describe('fmtSigned', () => {
    test('adds + sign for positive values', () => {
      expect(fmtSigned(1.5)).toBe('+1.5');
      expect(fmtSigned(0)).toBe('0.0');
      expect(fmtSigned(-2.3)).toBe('-2.3');
    });

    test('respects customized decimals', () => {
      expect(fmtSigned(1.234, 2)).toBe('+1.23');
      expect(fmtSigned(-1.234, 0)).toBe('-1');
    });

    test('never shows a signed zero when the value rounds to 0', () => {
      expect(fmtSigned(-0.04)).toBe('0.0');
      expect(fmtSigned(0.04)).toBe('0.0');
      expect(fmtSigned(-0.4, 0)).toBe('0');
    });

    test('returns fallback for invalid values', () => {
      expect(fmtSigned(null)).toBe('—');
    });
  });

  describe('fmtMm', () => {
    test('formats precipitations correctly', () => {
      expect(fmtMm(0)).toBe('0.0 mm');
      expect(fmtMm(12.34)).toBe('12 mm');
      expect(fmtMm(4.56)).toBe('4.6 mm');
    });

    test('returns fallback for invalid values', () => {
      expect(fmtMm(null)).toBe('—');
    });
  });

  describe('fmtWind', () => {
    test('formats wind speed in km/h', () => {
      expect(fmtWind(15.2)).toBe('15 km/h');
    });

    test('returns fallback for invalid values', () => {
      expect(fmtWind(null)).toBe('—');
    });
  });

  describe('describeWeather', () => {
    test('maps valid WMO codes to French text and glyph', () => {
      expect(describeWeather(0)).toEqual({ label: 'Ciel dégagé', glyph: '☀' + '︎' });
      expect(describeWeather(3)).toEqual({ label: 'Couvert', glyph: '☁' + '︎' });
    });

    test('handles fallback for unknown or missing codes', () => {
      expect(describeWeather(null)).toEqual({ label: '—', glyph: '·' });
      expect(describeWeather(999)).toEqual({ label: '—', glyph: '·' });
    });
  });

  describe('isoToday', () => {
    test('returns current date in YYYY-MM-DD format', () => {
      const today = isoToday();
      expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(new Date(today).getTime()).not.toBeNaN();
    });
  });
});
