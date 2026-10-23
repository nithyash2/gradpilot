import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { TYPE_HELP, TYPE_LABELS } from '../lib/summary';
import type { TrackerType } from '../types';
import { errorMessage, useAsync } from '../useAsync';

const TYPES: TrackerType[] = ['CHECKLIST', 'PIPELINE', 'PROGRESS', 'LOG', 'COUNTDOWN', 'SCHEDULE'];

export default function NewTracker() {
  const navigate = useNavigate();
  const { data: templates, error: loadError } = useAsync(() => api.templates(), []);
  const [name, setName] = useState('');
  const [type, setType] = useState<TrackerType>('CHECKLIST');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function create(body: Parameters<typeof api.createTracker>[0]) {
    setBusy(true);
    setError(null);
    try {
      const created = await api.createTracker(body);
      navigate(`/trackers/${created.tracker.id}`, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  function submitBlank(event: FormEvent) {
    event.preventDefault();
    void create({ name, type });
  }

  return (
    <main className="page">
      <div className="page-head">
        <h1>New tracker</h1>
        <Link to="/trackers" className="small muted">
          Cancel
        </Link>
      </div>

      <section className="stack" aria-labelledby="templates-title">
        <h2 id="templates-title">Start from a template</h2>
        {loadError && <p className="error">{loadError}</p>}
        <div className="template-grid">
          {templates?.map((t) => (
            <button key={t.key} className="template" disabled={busy} onClick={() => void create({ template: t.key })}>
              <strong>{t.name}</strong>
              <span className="small muted">{t.description}</span>
            </button>
          ))}
        </div>
      </section>

      <form className="panel" onSubmit={submitBlank} aria-labelledby="blank-title">
        <h2 id="blank-title">Or track anything else</h2>
        <label>
          Name
          <input
            id="tracker-name"
            required
            maxLength={100}
            value={name}
            placeholder="For example, visa paperwork"
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label>
          Kind of tracker
          <select id="tracker-type" value={type} onChange={(e) => setType(e.target.value as TrackerType)}>
            {TYPES.map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}: {TYPE_HELP[t].toLowerCase()}
              </option>
            ))}
          </select>
        </label>
        <button className="primary" type="submit" disabled={busy}>
          Create tracker
        </button>
      </form>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </main>
  );
}
