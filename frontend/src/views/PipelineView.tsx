import { useState, type FormEvent } from 'react';
import { stagesOf } from '../lib/summary';
import { formatDate } from '../lib/time';
import type { ItemActions } from '../pages/TrackerPage';
import type { Item, Tracker } from '../types';

export default function PipelineView({
  tracker,
  items,
  actions,
}: {
  tracker: Tracker;
  items: Item[];
  actions: ItemActions;
}) {
  const stages = stagesOf(tracker);
  const [title, setTitle] = useState('');
  const [stage, setStage] = useState(stages[1] ?? stages[0] ?? '');
  const [date, setDate] = useState(actions.today);
  const [link, setLink] = useState('');

  async function add(event: FormEvent) {
    event.preventDefault();
    const fields = link.trim() ? { link: link.trim() } : {};
    if (await actions.add({ title, stage: stage || null, itemDate: date || null, fields })) {
      setTitle('');
      setLink('');
    }
  }

  const groups = stages.map((s) => ({ stage: s, items: items.filter((i) => i.stage === s) }));
  const other = items.filter((i) => !i.stage || !stages.includes(i.stage));
  if (other.length > 0) groups.push({ stage: 'Other', items: other });

  return (
    <>
      <form className="panel" onSubmit={add}>
        <h2>Add an application</h2>
        <input
          id="job-title"
          required
          maxLength={200}
          value={title}
          placeholder="Company and role"
          aria-label="Company and role"
          onChange={(e) => setTitle(e.target.value)}
        />
        <div className="row">
          <label className="grow">
            Stage
            <select id="job-stage" value={stage} onChange={(e) => setStage(e.target.value)}>
              {stages.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          <label className="grow">
            Date
            <input id="job-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
        </div>
        <label>
          Link to the ad (optional)
          <input id="job-link" type="url" value={link} onChange={(e) => setLink(e.target.value)} />
        </label>
        <button className="primary" type="submit">
          Add application
        </button>
      </form>

      {groups
        .filter((g) => g.items.length > 0)
        .map((group) => (
          <section className="panel" key={group.stage} aria-label={group.stage}>
            <div className="stage-head">
              <h2>{group.stage}</h2>
              <span className="small muted">{group.items.length}</span>
            </div>
            <ul className="list">
              {group.items.map((item) => (
                <li key={item.id} style={{ flexWrap: 'wrap' }}>
                  <span className="title">
                    {typeof item.fields.link === 'string' ? (
                      <a href={item.fields.link} target="_blank" rel="noreferrer">
                        {item.title}
                      </a>
                    ) : (
                      item.title
                    )}
                    {item.itemDate && (
                      <>
                        <br />
                        <span className="small muted">{formatDate(item.itemDate)}</span>
                      </>
                    )}
                  </span>
                  <select
                    id={`stage-${item.id}`}
                    aria-label={`Stage for ${item.title}`}
                    value={item.stage ?? ''}
                    style={{ width: 'auto' }}
                    onChange={(e) => void actions.update(item, { stage: e.target.value })}
                  >
                    {stages.map((s) => (
                      <option key={s}>{s}</option>
                    ))}
                  </select>
                  <button className="icon" aria-label={`Delete ${item.title}`} onClick={() => void actions.remove(item)}>
                    ×
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}

      {items.length === 0 && <p className="muted">Nothing here yet. Add your first application above.</p>}
    </>
  );
}
