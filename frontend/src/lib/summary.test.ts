import { describe, expect, it } from 'vitest';
import type { Tracker, TrackerStats, TrackerType } from '../types';
import { stagesOf, trackerHeadline, travelMinutesOf } from './summary';

function tracker(type: TrackerType, stats: Partial<TrackerStats>, settings: Record<string, unknown> = {}): Tracker {
  return {
    id: 't1',
    name: 'Test',
    type,
    template: null,
    colour: null,
    settings,
    sortOrder: 0,
    stats: {
      total: 0,
      done: 0,
      averageProgress: null,
      totalAmount: null,
      byStage: null,
      nextDate: null,
      ...stats,
    },
  };
}

const TODAY = '2026-10-23';

describe('trackerHeadline', () => {
  it('summarises a checklist', () => {
    expect(trackerHeadline(tracker('CHECKLIST', { total: 4, done: 1 }), TODAY)).toBe('1 of 4 done');
    expect(trackerHeadline(tracker('CHECKLIST', {}), TODAY)).toBe('No steps yet');
  });

  it('lists only pipeline stages that have items', () => {
    const jobs = tracker('PIPELINE', {
      total: 7,
      byStage: { 'To apply': 0, Applied: 6, Interview: 1, Offer: 0 },
    });
    expect(trackerHeadline(jobs, TODAY)).toBe('6 applied, 1 interview');
  });

  it('shows average progress and logged totals', () => {
    expect(trackerHeadline(tracker('PROGRESS', { total: 3, averageProgress: 42 }), TODAY)).toBe('42% on average');
    expect(trackerHeadline(tracker('LOG', { total: 2, totalAmount: 105 }, { unit: 'minutes' }), TODAY)).toBe(
      '105 minutes logged',
    );
  });

  it('shows the next date for countdowns and schedules', () => {
    expect(trackerHeadline(tracker('COUNTDOWN', { total: 1, nextDate: '2026-12-12' }), TODAY)).toBe(
      'Next: Sat 12 Dec, in 50 days',
    );
    expect(trackerHeadline(tracker('SCHEDULE', { nextDate: '2026-10-24' }), TODAY)).toBe('Next: Sat 24 Oct');
    expect(trackerHeadline(tracker('SCHEDULE', {}), TODAY)).toBe('No upcoming shifts');
  });
});

describe('settings helpers', () => {
  it('reads stages and travel minutes safely', () => {
    expect(stagesOf(tracker('PIPELINE', {}, { stages: ['A', 'B'] }))).toEqual(['A', 'B']);
    expect(stagesOf(tracker('PIPELINE', {}))).toEqual([]);
    expect(travelMinutesOf(tracker('SCHEDULE', {}, { travelMinutes: 45 }))).toBe(45);
    expect(travelMinutesOf(tracker('SCHEDULE', {}, { travelMinutes: 'x' }))).toBe(0);
  });
});
