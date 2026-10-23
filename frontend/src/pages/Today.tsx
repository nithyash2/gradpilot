import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, toRequest } from '../api';
import { useAuth } from '../auth';
import { formatDate, formatLongDate, formatTime, relativeDays } from '../lib/time';
import type { CheckIn, Today } from '../types';
import { errorMessage, useAsync } from '../useAsync';

const WEEKDAY = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

function DepartureBoard({ today }: { today: Today }) {
  const next = today.nextShift;
  if (!next) {
    return (
      <section className="board quiet" aria-label="Next shift">
        <div>
          <p className="board-label">Next shift</p>
          <p className="board-time">None booked</p>
        </div>
        <p className="board-detail small">
          <span>Add your roster to see when to leave.</span>
          <Link to="/trackers">Add shifts</Link>
        </p>
      </section>
    );
  }
  const shift = next.shift;
  const when = next.today ? 'Today' : formatDate(shift.itemDate);
  return (
    <section className="board" aria-label="Next shift">
      <div>
        <p className="board-label">Leave by</p>
        <p className="board-time">{formatTime(next.leaveAt ?? shift.startTime)}</p>
      </div>
      <p className="board-when">{when}</p>
      <div className="board-detail">
        <span>{shift.title}</span>
        <span>
          {formatTime(shift.startTime)}
          {shift.endTime ? ` to ${formatTime(shift.endTime)}` : ''}
        </span>
      </div>
    </section>
  );
}

function CheckInPanel({ day, initial }: { day: string; initial: CheckIn }) {
  const [checkin, setCheckin] = useState(initial);
  const [status, setStatus] = useState<string | null>(null);
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => setCheckin(initial), [initial]);

  function update(changes: Partial<CheckIn>, immediate = false) {
    const next = { ...checkin, ...changes };
    setCheckin(next);
    window.clearTimeout(timer.current);
    const save = async () => {
      try {
        await api.saveCheckin(day, next);
        setStatus('Saved');
      } catch (e) {
        setStatus(errorMessage(e));
      }
    };
    if (immediate) void save();
    else timer.current = window.setTimeout(save, 700);
  }

  return (
    <section className="panel" aria-labelledby="checkin-title">
      <div className="page-head">
        <h2 id="checkin-title">Daily check-in</h2>
        {status && <span className="small muted">{status}</span>}
      </div>
      <label>
        Woke up at
        <input
          id="wake"
          type="time"
          value={checkin.wakeTime?.slice(0, 5) ?? ''}
          onChange={(e) => update({ wakeTime: e.target.value || null })}
        />
      </label>
      {(['breakfast', 'lunch', 'dinner'] as const).map((meal) => (
        <label key={meal}>
          {meal[0].toUpperCase() + meal.slice(1)}
          <input
            id={meal}
            value={checkin[meal] ?? ''}
            placeholder="What did you eat?"
            onChange={(e) => update({ [meal]: e.target.value })}
          />
        </label>
      ))}
      <ul className="list">
        {(
          [
            ['selfCare', 'Skin and body care'],
            ['dishes', 'Dishes done'],
            ['bedOnTime', 'In bed on time'],
          ] as const
        ).map(([key, label]) => (
          <li key={key}>
            <input
              id={key}
              type="checkbox"
              checked={checkin[key]}
              onChange={(e) => update({ [key]: e.target.checked }, true)}
            />
            <label htmlFor={key} className={`title ${checkin[key] ? 'done-text' : ''}`} style={{ color: 'inherit', fontSize: 'inherit' }}>
              {label}
            </label>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function TodayPage() {
  const { user } = useAuth();
  const { data: today, setData, error, reload } = useAsync(() => api.today(), []);
  const [focus, setFocus] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (today) setFocus(today.checkin.focus ?? '');
  }, [today]);

  if (error) {
    return (
      <main className="page">
        <p className="error">{error}</p>
        <button onClick={() => void reload()}>Try again</button>
      </main>
    );
  }
  if (!today) {
    return (
      <main className="page">
        <p className="muted">Loading your day…</p>
      </main>
    );
  }

  async function saveFocus(changes: Partial<CheckIn>) {
    if (!today) return;
    try {
      const saved = await api.saveCheckin(today.date, { ...today.checkin, focus, ...changes });
      setData({ ...today, checkin: saved });
      setMessage(changes.focusDone ? 'Nice. That counts.' : null);
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }

  async function finish(index: number) {
    if (!today) return;
    const entry = today.dueToday[index];
    try {
      await api.updateItem(entry.trackerId, entry.item.id, toRequest(entry.item, { done: true }));
      setData({ ...today, dueToday: today.dueToday.filter((_, i) => i !== index) });
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }

  const greeting = user?.displayName ? `Hi ${user.displayName}` : 'Today';

  return (
    <main className="page">
      <header className="stack" style={{ gap: 2 }}>
        <p className="muted">{formatLongDate(today.date)}</p>
        <h1>{greeting}</h1>
      </header>

      <DepartureBoard today={today} />

      <section className="panel" aria-labelledby="focus-title">
        <h2 id="focus-title">Today's one thing</h2>
        <p className="small muted">One small step is enough. On a work day, 20 to 30 minutes counts.</p>
        <div className="row">
          <input
            id="focus-done"
            type="checkbox"
            aria-label="Done"
            checked={today.checkin.focusDone}
            disabled={!focus.trim()}
            onChange={(e) => void saveFocus({ focusDone: e.target.checked })}
          />
          <input
            id="focus"
            className="grow"
            value={focus}
            placeholder="For example, apply to one graduate role"
            onChange={(e) => setFocus(e.target.value)}
            onBlur={() => focus !== (today.checkin.focus ?? '') && void saveFocus({})}
          />
        </div>
        <div className="week" aria-label="Last seven days">
          {today.lastSevenDays.map((d) => (
            <span key={d.date} title={`${formatDate(d.date)}: ${d.focusDone ? 'done' : 'not done'}`}>
              <i className={d.focusDone ? 'on' : ''} />
              {WEEKDAY[new Date(`${d.date}T00:00:00Z`).getUTCDay()]}
            </span>
          ))}
        </div>
        {message && <p className="small">{message}</p>}
      </section>

      {today.dueToday.length > 0 && (
        <section className="panel" aria-labelledby="due-title">
          <h2 id="due-title">Due today</h2>
          <ul className="list">
            {today.dueToday.map((entry, index) => (
              <li key={entry.item.id}>
                <input
                  id={`due-${entry.item.id}`}
                  type="checkbox"
                  aria-label={`Mark ${entry.item.title} done`}
                  onChange={() => void finish(index)}
                />
                <span className="title">{entry.item.title}</span>
                <Link className="tag" to={`/trackers/${entry.trackerId}`}>
                  {entry.trackerName}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {today.countdowns.length > 0 && (
        <section className="panel" aria-labelledby="dates-title">
          <h2 id="dates-title">Coming up</h2>
          <ul className="list">
            {today.countdowns.map((c) => (
              <li key={c.itemId}>
                <span className="days">{c.daysLeft}</span>
                <span className="title">
                  {c.title}
                  <br />
                  <span className="small muted">
                    {formatDate(c.date)}, {relativeDays(c.daysLeft)}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <CheckInPanel day={today.date} initial={today.checkin} />
    </main>
  );
}
