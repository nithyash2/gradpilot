import { useState } from 'react';
import { parseRoster, type RosterShift } from '../lib/roster';
import { formatDate, formatTime } from '../lib/time';
import type { ItemActions } from '../pages/TrackerPage';
import type { Item } from '../types';

/**
 * Paste roster text (for example from a screenshot with iPhone Live Text),
 * check the shifts found, and add them in one go.
 */
export default function RosterImport({
  items,
  actions,
  title,
}: {
  items: Item[];
  actions: ItemActions;
  title: string;
}) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [found, setFound] = useState<RosterShift[] | null>(null);
  const [chosen, setChosen] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const existing = new Set(items.map((i) => `${i.itemDate} ${(i.startTime ?? '').slice(0, 5)}`));

  function read() {
    const shifts = parseRoster(text, actions.today);
    setFound(shifts);
    setChosen(new Set(shifts.map((s, i) => (existing.has(`${s.date} ${s.start}`) ? -1 : i)).filter((i) => i >= 0)));
    setMessage(shifts.length === 0 ? 'No shifts found. Check the text has dates and times.' : null);
  }

  async function add() {
    if (!found) return;
    setBusy(true);
    let added = 0;
    for (const [index, shift] of found.entries()) {
      if (!chosen.has(index)) continue;
      const ok = await actions.add({
        title: shift.note || title,
        itemDate: shift.date,
        startTime: shift.start,
        endTime: shift.end,
      });
      if (ok) added++;
    }
    setBusy(false);
    setMessage(`Added ${added} ${added === 1 ? 'shift' : 'shifts'}.`);
    setFound(null);
    setText('');
  }

  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)}>
        Paste your roster
      </button>
    );
  }

  return (
    <section className="panel" aria-labelledby="roster-title">
      <h2 id="roster-title">Paste your roster</h2>
      <p className="small muted">
        On iPhone, take a screenshot of your roster, open it in Photos, press and hold on the text, tap Select All,
        then Copy. Paste it here.
      </p>
      <textarea
        id="roster-text"
        rows={6}
        value={text}
        aria-label="Roster text"
        placeholder={'Mon 27 Oct 5:00 PM - 11:30 PM\nWed 29 Oct 10:00 AM - 4:00 PM'}
        onChange={(e) => setText(e.target.value)}
      />
      <div className="row">
        <button className="primary grow" disabled={!text.trim()} onClick={read}>
          Find shifts
        </button>
        <button onClick={() => setOpen(false)}>Close</button>
      </div>

      {found && found.length > 0 && (
        <>
          <ul className="list">
            {found.map((shift, index) => {
              const duplicate = existing.has(`${shift.date} ${shift.start}`);
              return (
                <li key={`${shift.date}-${shift.start}`}>
                  <input
                    id={`roster-${index}`}
                    type="checkbox"
                    checked={chosen.has(index)}
                    aria-label={`Add shift on ${formatDate(shift.date)}`}
                    onChange={(e) => {
                      const next = new Set(chosen);
                      if (e.target.checked) next.add(index);
                      else next.delete(index);
                      setChosen(next);
                    }}
                  />
                  <span className="title">
                    <strong>{formatDate(shift.date)}</strong> {formatTime(shift.start)}
                    {shift.end ? ` to ${formatTime(shift.end)}` : ''}
                    {duplicate && <span className="small muted"> (already added)</span>}
                  </span>
                </li>
              );
            })}
          </ul>
          <button className="primary" disabled={busy || chosen.size === 0} onClick={() => void add()}>
            {busy ? 'Adding…' : `Add ${chosen.size} ${chosen.size === 1 ? 'shift' : 'shifts'}`}
          </button>
        </>
      )}
      {message && (
        <p className="small" role="status">
          {message}
        </p>
      )}
    </section>
  );
}
