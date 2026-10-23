import type { Tracker } from '../types';

export interface LeetCodeStats {
  username: string;
  easy: number;
  medium: number;
  hard: number;
  total: number;
  syncedAt: string | null;
}

/** The solved counts saved by the last sync, if there was one. */
export function leetcodeStats(tracker: Tracker): LeetCodeStats | null {
  const raw = tracker.settings.leetcode;
  if (!raw || typeof raw !== 'object') return null;
  const s = raw as Record<string, unknown>;
  const num = (v: unknown) => (typeof v === 'number' ? v : 0);
  if (typeof s.username !== 'string') return null;
  return {
    username: s.username,
    easy: num(s.easy),
    medium: num(s.medium),
    hard: num(s.hard),
    total: num(s.total),
    syncedAt: typeof s.syncedAt === 'string' ? s.syncedAt : null,
  };
}
