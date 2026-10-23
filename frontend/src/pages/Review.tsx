import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { weekHeadline, weekNumbers } from '../lib/review';
import { useAuth } from '../auth';
import { addDays, formatDate, todayIn } from '../lib/time';
import { useAsync } from '../useAsync';

const WEEKDAY = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

/** A look back at seven days, with a way to step to earlier weeks. */
export default function Review() {
  const { user } = useAuth();
  const today = todayIn(user?.timezone ?? 'Australia/Melbourne');
  const [end, setEnd] = useState<string | undefined>(undefined);
  const { data: review, error, reload } = useAsync(() => api.weekReview(end), [end]);

  if (error) {
    return (
      <main className="page">
        <p className="error">{error}</p>
        <button onClick={() => void reload()}>Try again</button>
      </main>
    );
  }
  if (!review) {
    return (
      <main className="page">
        <p className="muted">Loading your week…</p>
      </main>
    );
  }

  const isThisWeek = end === undefined;

  return (
    <main className="page">
      <div className="page-head">
        <h1>Your week</h1>
      </div>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <button onClick={() => setEnd(addDays(review.end, -7))}>‹ Earlier</button>
        <span className="small muted">
          {formatDate(review.start)} to {formatDate(review.end)}
        </span>
        <button disabled={isThisWeek} onClick={() => setEnd(nextEnd(review.end, today))}>
          Later ›
        </button>
      </div>

      <section className="panel">
        <h2>{weekHeadline(review)}</h2>
        <div className="week" aria-label="Days with your one thing done">
          {review.days.map((d) => (
            <span key={d.date} title={`${formatDate(d.date)}: ${d.focus ?? 'no one thing set'}`}>
              <i className={d.focusDone ? 'on' : ''} />
              {WEEKDAY[new Date(`${d.date}T00:00:00Z`).getUTCDay()]}
            </span>
          ))}
        </div>
      </section>

      <section className="panel" aria-labelledby="numbers-title">
        <h2 id="numbers-title">The numbers</h2>
        <ul className="list">
          {weekNumbers(review).map((row) => (
            <li key={row.label}>
              <span className="title">{row.label}</span>
              <strong>{row.value}</strong>
            </li>
          ))}
        </ul>
      </section>

      {review.study.length > 0 && (
        <section className="panel" aria-labelledby="study-title">
          <h2 id="study-title">Study</h2>
          <ul className="list">
            {review.study.map((s) => (
              <li key={s.trackerId}>
                <Link className="title" to={`/trackers/${s.trackerId}`}>
                  {s.name}
                </Link>
                <strong>{s.averageProgress === null ? 'Not started' : `${s.averageProgress}%`}</strong>
              </li>
            ))}
          </ul>
        </section>
      )}

      {review.days.some((d) => d.focus) && (
        <section className="panel" aria-labelledby="focus-title">
          <h2 id="focus-title">Your one thing each day</h2>
          <ul className="list">
            {review.days
              .filter((d) => d.focus)
              .map((d) => (
                <li key={d.date}>
                  <span className="title">
                    {d.focus}
                    <br />
                    <span className="small muted">{formatDate(d.date)}</span>
                  </span>
                  <span className={d.focusDone ? 'tag' : 'small muted'}>{d.focusDone ? 'Done' : 'Open'}</span>
                </li>
              ))}
          </ul>
        </section>
      )}
    </main>
  );
}

/** The end of the following week, or this week when that would reach today. */
function nextEnd(current: string, today: string): string | undefined {
  const next = addDays(current, 7);
  return next >= today ? undefined : next;
}
