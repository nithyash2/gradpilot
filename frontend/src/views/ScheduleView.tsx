import { useState, type FormEvent } from 'react';
import { travelMinutesOf } from '../lib/summary';
import { formatDate, formatDuration, formatTime, minutesBetween, toInputTime } from '../lib/time';
import type { ItemActions } from '../pages/TrackerPage';
import type { Item, Tracker } from '../types';

function leaveTime(start: string | null, travel: number): string {
  if (!start) return '';
  const [h, m] = start.split(':').map(Number);
  const total = (((h * 60 + m - travel) % 1440) + 1440) % 1440;
  return formatTime(`${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`);
}

export default function ScheduleView({
  tracker,
  items,
  actions,
  onSettings,
}: {
  tracker: Tracker;
  items: Item[];
  actions: ItemActions;
  onSettings: (settings: Record<string, unknown>) => Promise<void>;
}) {
  const travel = travelMinutesOf(tracker);
  const [travelInput, setTravelInput] = useState(String(travel));
  const [title, setTitle] = useState(tracker.name === 'Work shifts' ? 'Shift' : '');
  const [date, setDate] = useState(actions.today);
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [showPast, setShowPast] = useState(false);

  const sorted = [...items].sort((a, b) =>
    `${a.itemDate}${a.startTime}`.localeCompare(`${b.itemDate}${b.startTime}`),
  );
  const upcoming = sorted.filter((i) => (i.itemDate ?? '') >= actions.today);
  const past = sorted.filter((i) => (i.itemDate ?? '') < actions.today).reverse();

  async function add(event: FormEvent) {
    event.preventDefault();
    if (await actions.add({ title: title.trim() || 'Shift', itemDate: date, startTime: start, endTime: end || null })) {
      setStart('');
      setEnd('');
    }
  }

  function row(item: Item) {
    const length = item.startTime && item.endTime ? minutesBetween(toInputTime(item.startTime), toInputTime(item.endTime)) : null;
    return (
      <li key={item.id}>
        <span className="title">
          <strong>{formatDate(item.itemDate)}</strong> {item.title}
          <br />
          <span className="small muted">
            {formatTime(item.startTime)}
            {item.endTime ? ` to ${formatTime(item.endTime)}` : ''}
            {length !== null ? `, ${formatDuration(length)}` : ''}
          </span>
        </span>
        {travel > 0 && (item.itemDate ?? '') >= actions.today && (
          <span className="tag">Leave {leaveTime(item.startTime, travel)}</span>
        )}
        <button className="icon" aria-label={`Delete shift on ${formatDate(item.itemDate)}`} onClick={() => void actions.remove(item)}>
          ×
        </button>
      </li>
    );
  }

  return (
    <>
      <form className="panel" onSubmit={add}>
        <h2>Add a shift</h2>
        <div className="row">
          <label className="grow">
            Date
            <input id="shift-date" type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
          <label className="grow">
            What
            <input id="shift-title" maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} />
          </label>
        </div>
        <div className="row">
          <label className="grow">
            Starts
            <input id="shift-start" type="time" required value={start} onChange={(e) => setStart(e.target.value)} />
          </label>
          <label className="grow">
            Finishes
            <input id="shift-end" type="time" value={end} onChange={(e) => setEnd(e.target.value)} />
          </label>
        </div>
        <button className="primary" type="submit">
          Add shift
        </button>
      </form>

      <section className="panel">
        <h2>Coming up</h2>
        {upcoming.length === 0 ? (
          <p className="muted">No shifts coming up. Add them as soon as your roster comes out.</p>
        ) : (
          <ul className="list">{upcoming.map(row)}</ul>
        )}
        {past.length > 0 && (
          <button className="link" onClick={() => setShowPast(!showPast)}>
            {showPast ? 'Hide past shifts' : `Show ${past.length} past ${past.length === 1 ? 'shift' : 'shifts'}`}
          </button>
        )}
        {showPast && <ul className="list">{past.map(row)}</ul>}
      </section>

      <section className="panel">
        <h2>Travel time</h2>
        <p className="small muted">
          How long it takes to get there. You get a "time to leave" notification this long before each shift.
        </p>
        <div className="row">
          <input
            id="travel"
            className="grow"
            type="number"
            inputMode="numeric"
            min={0}
            max={600}
            value={travelInput}
            aria-label="Travel time in minutes"
            onChange={(e) => setTravelInput(e.target.value)}
          />
          <span className="muted">minutes</span>
          <button
            disabled={Number(travelInput) === travel || travelInput === ''}
            onClick={() => void onSettings({ ...tracker.settings, travelMinutes: Number(travelInput) })}
          >
            Save
          </button>
        </div>
      </section>
    </>
  );
}
