import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { leetcodeStats } from '../lib/leetcode';
import { timeAgo } from '../lib/outlook';
import type { Tracker } from '../types';
import { errorMessage } from '../useAsync';

/** Username, solved counts by difficulty and a sync button for a LeetCode tracker. */
export default function LeetCodePanel({
  tracker,
  onSettings,
  onSynced,
}: {
  tracker: Tracker;
  onSettings: (settings: Record<string, unknown>) => Promise<void>;
  onSynced: () => void;
}) {
  const saved = typeof tracker.settings.leetcodeUsername === 'string' ? tracker.settings.leetcodeUsername : '';
  const [username, setUsername] = useState(saved);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const stats = leetcodeStats(tracker);

  async function sync() {
    setBusy(true);
    setMessage(null);
    try {
      const result = await api.leetcodeSync(tracker.id);
      if (result.error) {
        setMessage({ ok: false, text: result.error });
      } else {
        setMessage({
          ok: true,
          text:
            result.newToday > 0
              ? `${result.newToday} solved today. Keep going!`
              : `Up to date: ${result.total} solved in total.`,
        });
      }
      onSynced();
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    await onSettings({ ...tracker.settings, leetcodeUsername: username.trim() });
    setMessage({ ok: true, text: 'Saved. Tap Sync now to read your profile.' });
  }

  return (
    <section className="panel" aria-labelledby="leetcode-title">
      <h2 id="leetcode-title">LeetCode profile</h2>
      {stats ? (
        <>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <span>
              <span className="days">{stats.total}</span> <span className="small muted">solved</span>
            </span>
            <span className="small">
              <span className="tag">Easy {stats.easy}</span> <span className="tag">Medium {stats.medium}</span>{' '}
              <span className="tag">Hard {stats.hard}</span>
            </span>
          </div>
          <p className="small muted">
            {stats.username}, checked {timeAgo(stats.syncedAt)}. GradPilot checks every hour and logs new problems on
            the day you solve them.
          </p>
        </>
      ) : (
        <p className="small muted">
          Add your LeetCode username. GradPilot reads your public profile every hour and logs the problems you solve
          each day. No password needed.
        </p>
      )}
      <form className="row" onSubmit={save}>
        <input
          id="leetcode-username"
          className="grow"
          value={username}
          maxLength={40}
          pattern="[A-Za-z0-9_\-]*"
          placeholder="LeetCode username"
          aria-label="LeetCode username"
          onChange={(e) => setUsername(e.target.value)}
        />
        <button type="submit" disabled={username.trim() === saved}>
          Save
        </button>
        <button type="button" className="primary" disabled={busy || !saved} onClick={() => void sync()}>
          {busy ? 'Syncing…' : 'Sync now'}
        </button>
      </form>
      {message && (
        <p className={message.ok ? 'success' : 'error'} role="status">
          {message.text}
        </p>
      )}
    </section>
  );
}
