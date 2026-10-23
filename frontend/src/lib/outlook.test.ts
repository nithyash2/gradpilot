import { describe, expect, it } from 'vitest';
import { connectMessage, syncMessage, timeAgo } from './outlook';

describe('connectMessage', () => {
  it('reads the result Microsoft sends back', () => {
    expect(connectMessage('?outlook=connected')?.ok).toBe(true);
    expect(connectMessage('?outlook=error&reason=declined')?.text).toContain('not allowed');
    expect(connectMessage('?outlook=error&reason=expired')?.text).toContain('took too long');
    expect(connectMessage('?outlook=error')?.ok).toBe(false);
    expect(connectMessage('')).toBeNull();
  });
});

describe('syncMessage', () => {
  it('summarises a sync', () => {
    expect(syncMessage({ read: 0, created: 0, updated: 0, error: null })).toBe('No new emails since the last check.');
    expect(syncMessage({ read: 4, created: 0, updated: 0, error: null })).toBe(
      'Checked your new emails. No job updates.',
    );
    expect(syncMessage({ read: 4, created: 1, updated: 2, error: null })).toBe('Added 1 application and updated 2.');
    expect(syncMessage({ read: 4, created: 0, updated: 1, error: null })).toBe('Updated 1.');
    expect(syncMessage({ read: 0, created: 0, updated: 0, error: 'Connect it again' })).toBe('Connect it again');
  });
});

describe('timeAgo', () => {
  const now = new Date('2026-10-23T08:00:00Z');
  it('describes how long ago something happened', () => {
    expect(timeAgo(null, now)).toBe('never');
    expect(timeAgo('2026-10-23T07:59:40Z', now)).toBe('just now');
    expect(timeAgo('2026-10-23T07:55:00Z', now)).toBe('5 minutes ago');
    expect(timeAgo('2026-10-23T05:00:00Z', now)).toBe('3 hours ago');
    expect(timeAgo('2026-10-21T08:00:00Z', now)).toBe('2 days ago');
  });
});
