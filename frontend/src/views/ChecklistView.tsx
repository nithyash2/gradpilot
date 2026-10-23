import { useState, type FormEvent } from 'react';
import { formatDate } from '../lib/time';
import type { ItemActions } from '../pages/TrackerPage';
import type { Item } from '../types';

export default function ChecklistView({ items, actions }: { items: Item[]; actions: ItemActions }) {
  const [title, setTitle] = useState('');
  const [date, setDate] = useState('');
  const done = items.filter((i) => i.done).length;

  async function add(event: FormEvent) {
    event.preventDefault();
    if (await actions.add({ title, itemDate: date || null })) {
      setTitle('');
      setDate('');
    }
  }

  return (
    <>
      <section className="panel">
        <div className="page-head">
          <h2>Steps</h2>
          <span className="small muted">
            {done} of {items.length} done
          </span>
        </div>
        {items.length > 0 && (
          <div className="bar" aria-hidden="true">
            <span style={{ width: `${items.length ? (done / items.length) * 100 : 0}%` }} />
          </div>
        )}
        {items.length === 0 ? (
          <p className="muted">Add the first step below.</p>
        ) : (
          <ul className="list">
            {items.map((item) => (
              <li key={item.id}>
                <input
                  id={`done-${item.id}`}
                  type="checkbox"
                  checked={item.done}
                  aria-label={`Mark ${item.title} done`}
                  onChange={(e) => void actions.update(item, { done: e.target.checked })}
                />
                <span className={`title ${item.done ? 'done-text' : ''}`}>{item.title}</span>
                {item.itemDate && <span className="tag">{formatDate(item.itemDate)}</span>}
                <button className="icon" aria-label={`Delete ${item.title}`} onClick={() => void actions.remove(item)}>
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <form className="panel" onSubmit={add}>
        <h2>Add a step</h2>
        <input
          id="step-title"
          required
          maxLength={200}
          value={title}
          placeholder="For example, book the driving test"
          aria-label="Step"
          onChange={(e) => setTitle(e.target.value)}
        />
        <label>
          Due date, if it has one
          <input id="step-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <button className="primary" type="submit">
          Add step
        </button>
      </form>
    </>
  );
}
