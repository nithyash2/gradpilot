import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, toRequest } from '../api';
import { useAuth } from '../auth';
import { TYPE_HELP } from '../lib/summary';
import { todayIn } from '../lib/time';
import type { Item, ItemRequest, TrackerDetail } from '../types';
import { errorMessage, useAsync } from '../useAsync';
import ChecklistView from '../views/ChecklistView';
import CountdownView from '../views/CountdownView';
import LogView from '../views/LogView';
import PipelineView from '../views/PipelineView';
import ProgressView from '../views/ProgressView';
import ScheduleView from '../views/ScheduleView';

/** Actions every tracker view uses to change its items. */
export interface ItemActions {
  add: (body: ItemRequest) => Promise<boolean>;
  update: (item: Item, changes: Partial<ItemRequest>) => Promise<void>;
  remove: (item: Item) => Promise<void>;
  today: string;
}

export default function TrackerPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { data, setData, error, reload } = useAsync<TrackerDetail>(() => api.tracker(id), [id]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [name, setName] = useState('');

  if (error) {
    return (
      <main className="page">
        <p className="error">{error}</p>
        <Link to="/trackers">Back to trackers</Link>
      </main>
    );
  }
  if (!data) {
    return (
      <main className="page">
        <p className="muted">Loading…</p>
      </main>
    );
  }

  const { tracker, items } = data;
  const isSchedule = tracker.type === 'SCHEDULE';

  async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
    setActionError(null);
    try {
      return await action();
    } catch (e) {
      setActionError(errorMessage(e));
      return undefined;
    }
  }

  const actions: ItemActions = {
    today: todayIn(user?.timezone ?? 'Australia/Melbourne'),
    add: async (body) => {
      const created = await run(() => api.addItem(tracker.id, body));
      if (created) await reload();
      return created !== undefined;
    },
    update: async (item, changes) => {
      // Show the change straight away, then save it
      setData({ ...data, items: items.map((i) => (i.id === item.id ? { ...i, ...changes } as Item : i)) });
      await run(() => api.updateItem(tracker.id, item.id, toRequest(item, changes, isSchedule)));
      await reload();
    },
    remove: async (item) => {
      setData({ ...data, items: items.filter((i) => i.id !== item.id) });
      await run(() => api.deleteItem(tracker.id, item.id));
      await reload();
    },
  };

  async function rename() {
    const updated = await run(() => api.updateTracker(tracker.id, { name, colour: tracker.colour }));
    if (updated) {
      setData(updated);
      setEditing(false);
    }
  }

  async function updateSettings(settings: Record<string, unknown>) {
    const updated = await run(() =>
      api.updateTracker(tracker.id, { name: tracker.name, colour: tracker.colour, settings }),
    );
    if (updated) setData(updated);
  }

  async function deleteTracker() {
    const done = await run(() => api.deleteTracker(tracker.id));
    if (done !== undefined) navigate('/trackers', { replace: true });
  }

  let view;
  switch (tracker.type) {
    case 'CHECKLIST':
      view = <ChecklistView items={items} actions={actions} />;
      break;
    case 'PIPELINE':
      view = <PipelineView tracker={tracker} items={items} actions={actions} />;
      break;
    case 'PROGRESS':
      view = <ProgressView items={items} actions={actions} />;
      break;
    case 'LOG':
      view = <LogView tracker={tracker} items={items} actions={actions} />;
      break;
    case 'COUNTDOWN':
      view = <CountdownView items={items} actions={actions} />;
      break;
    case 'SCHEDULE':
      view = <ScheduleView tracker={tracker} items={items} actions={actions} onSettings={updateSettings} />;
      break;
  }

  return (
    <main className="page">
      <Link to="/trackers" className="small muted">
        Back to trackers
      </Link>

      {editing ? (
        <div className="row">
          <input
            id="rename"
            className="grow"
            value={name}
            maxLength={100}
            onChange={(e) => setName(e.target.value)}
            aria-label="Tracker name"
          />
          <button className="primary" onClick={() => void rename()} disabled={!name.trim()}>
            Save
          </button>
          <button onClick={() => setEditing(false)}>Cancel</button>
        </div>
      ) : (
        <div className="page-head">
          <div className="stack" style={{ gap: 2 }}>
            <h1>{tracker.name}</h1>
            <p className="small muted">{TYPE_HELP[tracker.type]}</p>
          </div>
          <button
            className="link"
            onClick={() => {
              setName(tracker.name);
              setEditing(true);
            }}
          >
            Rename
          </button>
        </div>
      )}

      {actionError && (
        <p className="error" role="alert">
          {actionError}
        </p>
      )}

      {view}

      <section className="row" style={{ justifyContent: 'flex-end' }}>
        {confirmDelete ? (
          <>
            <span className="small">Delete this tracker and everything in it?</span>
            <button className="danger" onClick={() => void deleteTracker()}>
              Delete
            </button>
            <button onClick={() => setConfirmDelete(false)}>Keep it</button>
          </>
        ) : (
          <button className="link" onClick={() => setConfirmDelete(true)}>
            Delete tracker
          </button>
        )}
      </section>
    </main>
  );
}
