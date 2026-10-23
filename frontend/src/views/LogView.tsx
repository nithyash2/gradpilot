import { useState, type FormEvent } from 'react';
import { formatNumber } from '../lib/summary';
import { formatDate } from '../lib/time';
import type { ItemActions } from '../pages/TrackerPage';
import type { Item, Tracker } from '../types';

export default function LogView({ tracker, items, actions }: { tracker: Tracker; items: Item[]; actions: ItemActions }) {
  const unit = typeof tracker.settings.unit === 'string' ? tracker.settings.unit : '';
  const [date, setDate] = useState(actions.today);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');

  const total = items.reduce((sum, i) => sum + (i.amount ?? 0), 0);
  const sorted = [...items].sort((a, b) => (b.itemDate ?? '').localeCompare(a.itemDate ?? ''));

  async function add(event: FormEvent) {
    event.preventDefault();
    const value = Number(amount);
    if (await actions.add({ title: note.trim() || 'Entry', amount: value, itemDate: date })) {
      setAmount('');
      setNote('');
    }
  }

  return (
    <>
      <section className="panel">
        <p className="muted small">Total so far</p>
        <p className="days" style={{ textAlign: 'left' }}>
          {formatNumber(total)} <span className="small muted">{unit}</span>
        </p>
      </section>

      <form className="panel" onSubmit={add}>
        <h2>Add an entry</h2>
        <div className="row">
          <label className="grow">
            Date
            <input id="log-date" type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
          <label className="grow">
            Amount{unit ? ` (${unit})` : ''}
            <input
              id="log-amount"
              type="number"
              inputMode="decimal"
              min={0}
              step="any"
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>
        </div>
        <label>
          Note
          <input id="log-note" maxLength={200} value={note} placeholder="For example, roundabouts and parking" onChange={(e) => setNote(e.target.value)} />
        </label>
        <button className="primary" type="submit">
          Add entry
        </button>
      </form>

      {sorted.length > 0 && (
        <section className="panel">
          <h2>Entries</h2>
          <ul className="list">
            {sorted.map((item) => (
              <li key={item.id}>
                <span className="title">
                  {item.title}
                  <br />
                  <span className="small muted">{formatDate(item.itemDate)}</span>
                </span>
                <strong>
                  {formatNumber(item.amount ?? 0)} {unit}
                </strong>
                <button className="icon" aria-label={`Delete entry from ${formatDate(item.itemDate)}`} onClick={() => void actions.remove(item)}>
                  ×
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}
