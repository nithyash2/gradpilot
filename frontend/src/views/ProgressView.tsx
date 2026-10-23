import { useState, type FormEvent } from 'react';
import type { ItemActions } from '../pages/TrackerPage';
import type { Item } from '../types';

function TopicRow({ item, actions }: { item: Item; actions: ItemActions }) {
  const [progress, setProgress] = useState(item.progress ?? 0);
  const [where, setWhere] = useState(typeof item.fields.where === 'string' ? item.fields.where : '');
  const [next, setNext] = useState(item.notes ?? '');

  return (
    <section className="panel" aria-label={item.title}>
      <div className="page-head">
        <h2>{item.title}</h2>
        <button className="icon" aria-label={`Delete ${item.title}`} onClick={() => void actions.remove(item)}>
          ×
        </button>
      </div>
      <label>
        Progress: {progress}%
        <input
          id={`progress-${item.id}`}
          type="range"
          min={0}
          max={100}
          step={5}
          value={progress}
          onChange={(e) => setProgress(Number(e.target.value))}
          onPointerUp={() => progress !== item.progress && void actions.update(item, { progress })}
          onKeyUp={() => progress !== item.progress && void actions.update(item, { progress })}
        />
      </label>
      <label>
        Where I am
        <input
          id={`where-${item.id}`}
          value={where}
          placeholder="For example, week 2, lesson 3"
          onChange={(e) => setWhere(e.target.value)}
          onBlur={() => where !== (item.fields.where ?? '') && void actions.update(item, { fields: { ...item.fields, where } })}
        />
      </label>
      <label>
        Next small step
        <input
          id={`next-${item.id}`}
          value={next}
          placeholder="What you will do next session"
          onChange={(e) => setNext(e.target.value)}
          onBlur={() => next !== (item.notes ?? '') && void actions.update(item, { notes: next })}
        />
      </label>
    </section>
  );
}

export default function ProgressView({ items, actions }: { items: Item[]; actions: ItemActions }) {
  const [title, setTitle] = useState('');

  async function add(event: FormEvent) {
    event.preventDefault();
    if (await actions.add({ title, progress: 0 })) setTitle('');
  }

  return (
    <>
      {items.map((item) => (
        <TopicRow key={item.id} item={item} actions={actions} />
      ))}
      {items.length === 0 && <p className="muted">Add a course, book or topic you are working through.</p>}
      <form className="panel" onSubmit={add}>
        <h2>Add a topic</h2>
        <div className="row">
          <input
            id="topic-title"
            className="grow"
            required
            maxLength={200}
            value={title}
            placeholder="For example, PyTorch basics"
            aria-label="Topic"
            onChange={(e) => setTitle(e.target.value)}
          />
          <button className="primary" type="submit">
            Add
          </button>
        </div>
      </form>
    </>
  );
}
