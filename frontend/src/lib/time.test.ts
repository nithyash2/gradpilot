import { describe, expect, it } from 'vitest';
import {
  addDays,
  daysBetween,
  formatDate,
  formatDuration,
  formatTime,
  minutesBetween,
  relativeDays,
  todayIn,
  toInputTime,
} from './time';

describe('formatTime', () => {
  it('uses a 12 hour clock with am and pm', () => {
    expect(formatTime('16:00:00')).toBe('4:00pm');
    expect(formatTime('09:05')).toBe('9:05am');
    expect(formatTime('00:30:00')).toBe('12:30am');
    expect(formatTime('12:00:00')).toBe('12:00pm');
  });

  it('returns nothing for empty values', () => {
    expect(formatTime(null)).toBe('');
    expect(formatTime('')).toBe('');
  });
});

describe('dates', () => {
  it('formats calendar dates without timezone drift', () => {
    expect(formatDate('2026-10-23')).toBe('Fri 23 Oct');
    expect(formatDate('2026-12-12')).toBe('Sat 12 Dec');
  });

  it('counts whole days across months and daylight saving changes', () => {
    expect(daysBetween('2026-10-23', '2026-12-12')).toBe(50);
    expect(daysBetween('2026-10-03', '2026-10-05')).toBe(2);
    expect(daysBetween('2026-10-23', '2026-10-20')).toBe(-3);
  });

  it('adds days', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31');
  });

  it('describes relative days in plain words', () => {
    expect(relativeDays(0)).toBe('today');
    expect(relativeDays(1)).toBe('tomorrow');
    expect(relativeDays(-1)).toBe('yesterday');
    expect(relativeDays(50)).toBe('in 50 days');
    expect(relativeDays(-3)).toBe('3 days ago');
  });

  it('works out today in a given timezone', () => {
    // 2026-10-23 14:30 UTC is already the 24th in Melbourne (UTC+11)
    const instant = new Date('2026-10-23T14:30:00Z');
    expect(todayIn('Australia/Melbourne', instant)).toBe('2026-10-24');
    expect(todayIn('UTC', instant)).toBe('2026-10-23');
  });
});

describe('durations', () => {
  it('handles normal and overnight shifts', () => {
    expect(minutesBetween('16:00', '22:00')).toBe(360);
    expect(minutesBetween('22:00', '02:00')).toBe(240);
  });

  it('formats minutes', () => {
    expect(formatDuration(45)).toBe('45 min');
    expect(formatDuration(120)).toBe('2 h');
    expect(formatDuration(75)).toBe('1 h 15 min');
  });

  it('trims seconds for time inputs', () => {
    expect(toInputTime('16:00:00')).toBe('16:00');
    expect(toInputTime(null)).toBe('');
  });
});
