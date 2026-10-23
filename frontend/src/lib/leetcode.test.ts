import { describe, expect, it } from 'vitest';
import type { Tracker } from '../types';
import { leetcodeStats } from './leetcode';

const tracker = (settings: Record<string, unknown>): Tracker => ({
  id: 't',
  name: 'LeetCode',
  type: 'LOG',
  template: 'leetcode',
  colour: null,
  settings,
  sortOrder: 0,
  stats: { total: 0, done: 0, averageProgress: null, totalAmount: 0, byStage: null, nextDate: null },
});

describe('leetcodeStats', () => {
  it('reads the counts saved by the last sync', () => {
    expect(
      leetcodeStats(
        tracker({ leetcode: { username: 'kp', easy: 10, medium: 5, hard: 1, total: 16, syncedAt: '2026-10-23T08:00:00Z' } }),
      ),
    ).toEqual({ username: 'kp', easy: 10, medium: 5, hard: 1, total: 16, syncedAt: '2026-10-23T08:00:00Z' });
  });

  it('is empty before the first sync', () => {
    expect(leetcodeStats(tracker({ unit: 'problems' }))).toBeNull();
    expect(leetcodeStats(tracker({ leetcode: 'oops' }))).toBeNull();
  });
});
