import { useState, type FormEvent } from 'react';
import { daysBetween, formatDate, relativeDays } from '../lib/time';
import type { ItemActions } from '../pages/TrackerPage';
import type { Item } from '../types';

export default function CountdownView({ items, actions }: { items: Item[]; actions: ItemActions }) {
  const [title, setTitle] = useState('');
  const [date, setDate] = useState('');
  const sorted = [...items].sort((a, b) => (a.itemDate ?? '').localeCompare(b.itemDate ?? ''));

  async function add(event: FormEvent) {
    event.preventDefault();
    if (await actions.add({ title, itemDate: date })) {
      setTitle('');
      setDate('');
    }
  }

  return (
    <>
      {sorted.length > 0 ? (
        <section className="panel">
          <ul className="list">
            {sorted.map((item) => {
              const days = item.itemDate ? daysBetween(actions.today, item.itemDate) : 0;
              const past = days < 0;
              return (
                <li key={item.id} className={past ? 'muted' : ''}>
                  <span className="days">{past ? '' : days}</span>
                  <span className="title">
                    {item.title}
                    <br />
                    <span className="small muted">
                      {formatDate(item.itemDate)}, {relativeDays(days)}
                    </span>
                  </span>
                  <button className="icon" aria-label={`Delete ${item.title}`} onClick={() => void actions.remove(item)}>
                    ×
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      ) : (
        <p className="muted">Add a date to count down to.</p>
      )}

      <form className="panel" onSubmit={add}>
        <h2>Add a date</h2>
        <input
          id="countdown-title"
          required
          maxLength={200}
          value={title}
          placeholder="For example, parents arrive"
          aria-label="What is happening"
          onChange={(e) => setTitle(e.target.value)}
        />
        <input id="countdown-date" type="date" required value={date} aria-label="Date" onChange={(e) => setDate(e.target.value)} />
        <button className="primary" type="submit">
          Add date
        </button>
      </form>
    </>
  );
}
