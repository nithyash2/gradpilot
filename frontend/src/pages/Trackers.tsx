import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { TYPE_LABELS, trackerHeadline } from '../lib/summary';
import { todayIn } from '../lib/time';
import { useAsync } from '../useAsync';
import JobEmailImport from '../views/JobEmailImport';

export default function Trackers() {
  const { user } = useAuth();
  const { data: trackers, error, reload } = useAsync(() => api.trackers(), []);
  const today = todayIn(user?.timezone ?? 'Australia/Melbourne');

  return (
    <main className="page">
      <div className="page-head">
        <h1>Trackers</h1>
        <Link to="/trackers/new" className="button primary">
          + New tracker
        </Link>
      </div>

      <JobEmailImport onImported={() => void reload()} />

      {error && (
        <div className="stack">
          <p className="error">{error}</p>
          <button onClick={() => void reload()}>Try again</button>
        </div>
      )}

      {trackers && trackers.length === 0 && (
        <section className="panel">
          <h2>Start with one thing you keep forgetting</h2>
          <p className="muted">
            Your work shifts, job applications, a course, or the steps to your licence. Templates set each one up for
            you.
          </p>
          <Link to="/trackers/new" className="button primary">
            Choose a template
          </Link>
        </section>
      )}

      {trackers && trackers.length > 0 && (
        <div className="stack">
          {trackers.map((t) => (
            <Link key={t.id} to={`/trackers/${t.id}`} className="tracker-link">
              <span className="stack" style={{ gap: 2 }}>
                <strong>{t.name}</strong>
                <span className="small muted">{trackerHeadline(t, today)}</span>
              </span>
              <span className="tag">{TYPE_LABELS[t.type]}</span>
            </Link>
          ))}
        </div>
      )}
    </main>
  );
}
