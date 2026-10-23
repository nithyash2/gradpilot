import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { changedSomething, describeImport } from '../lib/jobs';
import type { JobImportResult } from '../types';
import { errorMessage } from '../useAsync';

/**
 * Paste a job email (a confirmation, invitation, offer or rejection) and the
 * matching application is added or moved to the right stage.
 */
export default function JobEmailImport({
  onImported,
  startOpen = false,
}: {
  onImported?: (result: JobImportResult) => void;
  startOpen?: boolean;
}) {
  const [open, setOpen] = useState(startOpen);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<JobImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const imported = await api.importJobEmail(text);
      setResult(imported);
      if (changedSomething(imported)) {
        setText('');
        onImported?.(imported);
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)}>
        Paste a job email
      </button>
    );
  }

  return (
    <form className="panel" onSubmit={submit}>
      <h2>Paste a job email</h2>
      <p className="small muted">
        Copy a confirmation, assessment, interview, offer or rejection email from your inbox and paste it here,
        including the From and Subject lines if you can. GradPilot adds the application or moves it to the right
        stage.
      </p>
      <textarea
        id="job-email"
        required
        rows={8}
        maxLength={100000}
        value={text}
        aria-label="The email"
        placeholder={'From: Canva Careers <careers@canva.com>\nSubject: Thanks for applying\n\nHi Nithya, thanks for applying for…'}
        onChange={(e) => setText(e.target.value)}
      />
      {result && (
        <p className={changedSomething(result) ? 'success' : 'muted'} role="status">
          {describeImport(result)}
        </p>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="row">
        <button className="primary grow" type="submit" disabled={busy || !text.trim()}>
          {busy ? 'Reading…' : 'Read email'}
        </button>
        <button type="button" onClick={() => setOpen(false)}>
          Close
        </button>
      </div>
    </form>
  );
}
