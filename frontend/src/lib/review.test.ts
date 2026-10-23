import { describe, expect, it } from 'vitest';
import type { WeekReview } from '../types';
import { weekHeadline, weekNumbers } from './review';

const quiet: WeekReview = {
  start: '2026-10-19',
  end: '2026-10-25',
  days: [],
  focusDays: 0,
  streak: 0,
  jobs: { applied: 0, assessments: 0, interviews: 0, offers: 0, rejections: 0, active: 0 },
  leetcodeSolved: 0,
  shifts: { count: 0, hours: 0 },
  logs: [],
  study: [],
};

describe('weekHeadline', () => {
  it('celebrates the best thing that happened', () => {
    expect(weekHeadline({ ...quiet, jobs: { ...quiet.jobs, offers: 1, interviews: 2 } })).toContain('offer');
    expect(weekHeadline({ ...quiet, jobs: { ...quiet.jobs, interviews: 1 } })).toBe(
      '1 interview this week. Your applications are working.',
    );
    expect(weekHeadline({ ...quiet, focusDays: 6 })).toContain('strong week');
    expect(weekHeadline({ ...quiet, jobs: { ...quiet.jobs, applied: 4 } })).toBe(
      '4 applications sent. Every one counts.',
    );
    expect(weekHeadline(quiet)).toContain('quiet week');
  });
});

describe('weekNumbers', () => {
  it('shows only what happened', () => {
    expect(weekNumbers(quiet)).toEqual([{ label: 'Days with your one thing done', value: '0/7' }]);

    const busy = weekNumbers({
      ...quiet,
      focusDays: 4,
      streak: 3,
      jobs: { ...quiet.jobs, applied: 5, interviews: 1, active: 6 },
      leetcodeSolved: 9,
      shifts: { count: 3, hours: 16.5 },
      logs: [{ trackerId: 'l', name: 'Driving practice', amount: 120, unit: 'minutes' }],
    });
    expect(busy.map((r) => r.label)).toEqual([
      'Days with your one thing done',
      'Current streak',
      'Applications sent',
      'Interviews',
      'Applications still open',
      'LeetCode problems',
      'Shifts',
      'Driving practice',
    ]);
    expect(busy.find((r) => r.label === 'Shifts')?.value).toBe('3 (16.5 h)');
    expect(busy.find((r) => r.label === 'Driving practice')?.value).toBe('120 minutes');
  });
});
