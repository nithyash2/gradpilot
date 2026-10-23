import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import {
  currentSubscription,
  disableNotifications,
  enableNotifications,
  isIos,
  pushSupported,
  runningAsInstalledApp,
} from '../lib/push';
import { toInputTime } from '../lib/time';
import { connectMessage, syncMessage, timeAgo } from '../lib/outlook';
import type { OutlookStatus, PushStatus } from '../types';
import { errorMessage } from '../useAsync';

function timezones(): string[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (key: string) => string[] };
  return intl.supportedValuesOf?.('timeZone') ?? ['Australia/Melbourne', 'Australia/Sydney', 'UTC'];
}

function Notifications() {
  const [status, setStatus] = useState<PushStatus | null>(null);
  const [subscribed, setSubscribed] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const supported = pushSupported();
  const needsInstall = isIos() && !runningAsInstalledApp();

  useEffect(() => {
    api.pushStatus().then(setStatus).catch(() => setStatus(null));
    if (supported) {
      currentSubscription()
        .then((s) => setSubscribed(s !== null))
        .catch(() => setSubscribed(false));
    }
  }, [supported]);

  async function act(action: () => Promise<void>, done: string) {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      setMessage(done);
      setSubscribed((await currentSubscription()) !== null);
      setStatus(await api.pushStatus());
    } catch (e) {
      setMessage(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function test() {
    setBusy(true);
    try {
      const result = await api.testPush();
      setMessage(
        result.delivered > 0
          ? 'Test sent. It should arrive in a few seconds.'
          : 'No device accepted the test. Turn notifications off and on again.',
      );
    } catch (e) {
      setMessage(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel" aria-labelledby="notify-title">
      <h2 id="notify-title">Notifications</h2>

      {needsInstall ? (
        <div className="stack">
          <p>On iPhone, notifications work once GradPilot is on your Home Screen:</p>
          <ol style={{ margin: 0, paddingLeft: 20 }}>
            <li>Tap the Share button in Safari.</li>
            <li>Choose Add to Home Screen, then Add.</li>
            <li>Open GradPilot from your Home Screen and come back to Settings.</li>
          </ol>
        </div>
      ) : !supported ? (
        <p className="muted">This browser cannot show notifications. Try Safari on iPhone or Chrome on a laptop.</p>
      ) : status && !status.enabled ? (
        <p className="muted">Notifications are not set up on the server yet.</p>
      ) : (
        <>
          <p className="small muted">
            A morning plan, an evening wind-down and a reminder when it is time to leave for work.
            {status ? ` ${status.devices} ${status.devices === 1 ? 'device' : 'devices'} signed up.` : ''}
          </p>
          <div className="row">
            {subscribed ? (
              <>
                <button disabled={busy} onClick={() => void test()}>
                  Send a test
                </button>
                <button disabled={busy} onClick={() => void act(disableNotifications, 'Notifications turned off.')}>
                  Turn off on this device
                </button>
              </>
            ) : (
              <button
                className="primary"
                disabled={busy}
                onClick={() => void act(enableNotifications, 'Notifications are on for this device.')}
              >
                Turn on notifications
              </button>
            )}
          </div>
        </>
      )}
      {message && (
        <p className="small" role="status">
          {message}
        </p>
      )}
    </section>
  );
}

function JobEmails() {
  const [status, setStatus] = useState<OutlookStatus | null>(null);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(() =>
    connectMessage(window.location.search),
  );
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.outlookStatus().then(setStatus).catch(() => setStatus(null));
    if (window.location.search.includes('outlook=')) {
      // Keep the address tidy so a refresh does not show the message again
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);

  async function run(action: () => Promise<string | void>) {
    setBusy(true);
    setMessage(null);
    try {
      const text = await action();
      if (text) setMessage({ ok: true, text });
      setStatus(await api.outlookStatus());
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  }

  async function connect() {
    setBusy(true);
    try {
      const { url } = await api.outlookConnect();
      window.location.assign(url);
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) });
      setBusy(false);
    }
  }

  return (
    <section className="panel" aria-labelledby="jobmail-title">
      <h2 id="jobmail-title">Job emails</h2>
      {!status ? (
        <p className="muted small">Loading…</p>
      ) : !status.available ? (
        <p className="small muted">
          Reading Outlook automatically is not set up on the server yet. You can still paste job emails on the
          Trackers screen.
        </p>
      ) : !status.connected ? (
        <>
          <p className="small muted">
            Connect Outlook and GradPilot reads your inbox every 15 minutes. Confirmations from SEEK, LinkedIn, Indeed
            and careers sites are added to Job hunt, and interviews, assessments, offers and rejections move them
            along. It only reads; it never sends or deletes email.
          </p>
          <button className="primary" disabled={busy} onClick={() => void connect()}>
            Connect Outlook
          </button>
        </>
      ) : (
        <>
          <p className="small">
            Connected to <strong>{status.email ?? 'Outlook'}</strong>. Last checked {timeAgo(status.lastSyncedAt)}
            {status.importedCount > 0 ? `, ${status.importedCount} job updates so far` : ''}.
          </p>
          {status.lastError && <p className="error">{status.lastError}</p>}
          <div className="row">
            {status.needsReconnect ? (
              <button className="primary" disabled={busy} onClick={() => void connect()}>
                Connect again
              </button>
            ) : (
              <button disabled={busy} onClick={() => void run(async () => syncMessage(await api.outlookSync()))}>
                {busy ? 'Checking…' : 'Check now'}
              </button>
            )}
            <button
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await api.outlookDisconnect();
                  return 'Outlook disconnected. Your applications stay in Job hunt.';
                })
              }
            >
              Disconnect
            </button>
          </div>
        </>
      )}
      {message && (
        <p className={message.ok ? 'success' : 'error'} role="status">
          {message.text}
        </p>
      )}
    </section>
  );
}

function CalendarFeed() {
  const [feed, setFeed] = useState<{ url?: string; webcal?: string } | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.calendar().then(setFeed).catch(() => setFeed({}));
  }, []);

  async function run(action: () => Promise<{ url?: string; webcal?: string }>, done: string) {
    setBusy(true);
    setMessage(null);
    try {
      setFeed(await action());
      setMessage(done);
    } catch (e) {
      setMessage(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function copy(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setMessage('Link copied.');
    } catch {
      setMessage('Press and hold the link to copy it.');
    }
  }

  return (
    <section className="panel" aria-labelledby="calendar-title">
      <h2 id="calendar-title">Calendar</h2>
      {!feed ? (
        <p className="muted small">Loading…</p>
      ) : !feed.url ? (
        <>
          <p className="small muted">
            See your shifts (with a time-to-leave alert), countdown dates and dated tasks in Apple, Google or Outlook
            Calendar. They update by themselves.
          </p>
          <button className="primary" disabled={busy} onClick={() => void run(() => api.newCalendar(), 'Calendar link ready.')}>
            Add to my calendar
          </button>
        </>
      ) : (
        <>
          <div className="row">
            {feed.webcal && (
              <a className="button primary" href={feed.webcal}>
                Apple Calendar
              </a>
            )}
            {feed.webcal && (
              <a
                className="button"
                href={`https://calendar.google.com/calendar/r?cid=${encodeURIComponent(feed.webcal)}`}
                target="_blank"
                rel="noreferrer"
              >
                Google Calendar
              </a>
            )}
            <button onClick={() => void copy(feed.url!)}>Copy link</button>
          </div>
          <p className="small muted" style={{ wordBreak: 'break-all' }}>
            {feed.url}
          </p>
          <p className="small muted">Keep this link private: anyone with it can see these dates.</p>
          <div className="row">
            <button
              disabled={busy}
              onClick={() => void run(() => api.newCalendar(), 'New link made. The old one no longer works.')}
            >
              Make a new link
            </button>
            <button
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await api.calendarOff();
                  return {};
                }, 'Calendar link turned off.')
              }
            >
              Turn off
            </button>
          </div>
        </>
      )}
      {message && (
        <p className="small" role="status">
          {message}
        </p>
      )}
    </section>
  );
}

export default function Settings() {
  const { user, setUser, logout } = useAuth();
  const [form, setForm] = useState({
    displayName: user?.displayName ?? '',
    timezone: user?.timezone ?? 'Australia/Melbourne',
    wakeTime: toInputTime(user?.wakeTime),
    bedTime: toInputTime(user?.bedTime),
    morningTime: toInputTime(user?.morningTime),
    eveningTime: toInputTime(user?.eveningTime),
  });
  const [message, setMessage] = useState<string | null>(null);

  if (!user) return null;

  async function save(event: FormEvent) {
    event.preventDefault();
    try {
      const updated = await api.updateMe({
        displayName: form.displayName,
        timezone: form.timezone,
        wakeTime: form.wakeTime || null,
        bedTime: form.bedTime || null,
        morningTime: form.morningTime || undefined,
        eveningTime: form.eveningTime || undefined,
      });
      setUser(updated);
      setMessage('Settings saved.');
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm({ ...form, [key]: e.target.value });

  return (
    <main className="page">
      <h1>Settings</h1>

      <Notifications />

      <JobEmails />

      <CalendarFeed />

      <form className="panel" onSubmit={save} aria-labelledby="profile-title">
        <h2 id="profile-title">Your day</h2>
        <label>
          Name
          <input id="displayName" maxLength={100} value={form.displayName} onChange={set('displayName')} />
        </label>
        <div className="row">
          <label className="grow">
            Wake up by
            <input id="wakeTime" type="time" value={form.wakeTime} onChange={set('wakeTime')} />
          </label>
          <label className="grow">
            In bed by
            <input id="bedTime" type="time" value={form.bedTime} onChange={set('bedTime')} />
          </label>
        </div>
        <div className="row">
          <label className="grow">
            Morning plan at
            <input id="morningTime" type="time" value={form.morningTime} onChange={set('morningTime')} />
          </label>
          <label className="grow">
            Evening wind-down at
            <input id="eveningTime" type="time" value={form.eveningTime} onChange={set('eveningTime')} />
          </label>
        </div>
        <label>
          Timezone
          <select id="timezone" value={form.timezone} onChange={set('timezone')}>
            {timezones().map((tz) => (
              <option key={tz}>{tz}</option>
            ))}
          </select>
        </label>
        <button className="primary" type="submit">
          Save settings
        </button>
        {message && (
          <p className="small" role="status">
            {message}
          </p>
        )}
      </form>

      <section className="panel">
        <p className="small muted">Logged in as {user.email}</p>
        <button onClick={logout}>Log out</button>
      </section>
    </main>
  );
}
