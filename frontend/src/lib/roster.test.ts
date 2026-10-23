import { describe, expect, it } from 'vitest';
import { inferYear, parseRoster, toTime } from './roster';

const TODAY = '2026-10-23';

describe('parseRoster', () => {
  it('reads one shift per line', () => {
    const text = `My shifts
Mon 27 Oct 5:00 PM - 11:30 PM Kitchen
Wed 29 Oct 10:00 AM - 4:00 PM Kitchen
Sat 1 Nov 4pm – 10pm`;

    expect(parseRoster(text, TODAY)).toEqual([
      { date: '2026-10-27', start: '17:00', end: '23:30', note: 'Kitchen' },
      { date: '2026-10-29', start: '10:00', end: '16:00', note: 'Kitchen' },
      { date: '2026-11-01', start: '16:00', end: '22:00', note: '' },
    ]);
  });

  it('reads Live Text from a roster app, where the time is under the date', () => {
    const text = `Tuesday, 28 October
17:00 - 23:00
Kitchen Hand · Chapel St
Thursday, 30 October
11:30 - 15:30`;

    expect(parseRoster(text, TODAY)).toEqual([
      { date: '2026-10-28', start: '17:00', end: '23:00', note: '' },
      { date: '2026-10-30', start: '11:30', end: '15:30', note: '' },
    ]);
  });

  it('reads Australian day/month dates and 24-hour times', () => {
    expect(parseRoster('27/10 17:00-23:30\n03/11/2026 09:00-13:00', TODAY)).toEqual([
      { date: '2026-10-27', start: '17:00', end: '23:30', note: '' },
      { date: '2026-11-03', start: '09:00', end: '13:00', note: '' },
    ]);
  });

  it('works out the year for January rosters read in December', () => {
    expect(parseRoster('Fri 2 Jan 9am - 5pm', '2026-12-28')).toEqual([
      { date: '2027-01-02', start: '09:00', end: '17:00', note: '' },
    ]);
  });

  it('shares am or pm across a range like "5 - 11pm"', () => {
    expect(parseRoster('Sun 26 Oct 5 - 11pm', TODAY)[0]).toMatchObject({ start: '17:00', end: '23:00' });
  });

  it('ignores lines without a shift and drops duplicates', () => {
    expect(parseRoster('Total hours 24\nMon 27 Oct 5:00 PM - 11:30 PM\nMon 27 Oct 5:00 PM - 11:30 PM', TODAY)).toHaveLength(1);
    expect(parseRoster('Nothing to see here', TODAY)).toEqual([]);
  });
});

describe('toTime', () => {
  it('converts to 24-hour time', () => {
    expect(toTime('5', undefined, 'pm')).toBe('17:00');
    expect(toTime('12', '30', 'am')).toBe('00:30');
    expect(toTime('12', '00', 'pm')).toBe('12:00');
    expect(toTime('9', '15', 'a.m.')).toBe('09:15');
    expect(toTime('25', undefined, undefined)).toBeNull();
  });
});

describe('inferYear', () => {
  it('prefers the coming months', () => {
    expect(inferYear(27, 10, TODAY)).toBe(2026);
    expect(inferYear(2, 1, '2026-12-28')).toBe(2027);
    expect(inferYear(20, 10, TODAY)).toBe(2026);
  });
});
