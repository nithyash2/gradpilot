import type {
  AuthResponse,
  CheckIn,
  Item,
  ItemRequest,
  JobImportResult,
  PushStatus,
  Template,
  Today,
  Tracker,
  TrackerDetail,
  TrackerType,
  User,
} from './types';

const BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? 'http://localhost:8080';
const TOKEN_KEY = 'gradpilot.token';

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

let onUnauthorised: () => void = () => {};

export function setUnauthorisedHandler(handler: () => void) {
  onUnauthorised = handler;
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Private browsing: the session lasts until the tab closes
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Could not reach GradPilot. Check your connection and try again.');
  }

  if (response.status === 401 && token) {
    setToken(null);
    onUnauthorised();
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    let fields: Record<string, string> = {};
    try {
      const problem = await response.json();
      message = problem.detail ?? problem.title ?? message;
      fields = problem.fields ?? {};
    } catch {
      // Not JSON; keep the generic message
    }
    throw new ApiError(response.status, message, fields);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  signup: (email: string, password: string, displayName: string, timezone: string) =>
    request<AuthResponse>('POST', '/api/auth/signup', { email, password, displayName, timezone }),
  login: (email: string, password: string) => request<AuthResponse>('POST', '/api/auth/login', { email, password }),

  me: () => request<User>('GET', '/api/me'),
  updateMe: (changes: Partial<Omit<User, 'id' | 'email'>>) => request<User>('PUT', '/api/me', changes),

  today: () => request<Today>('GET', '/api/today'),

  templates: () => request<Template[]>('GET', '/api/templates'),
  trackers: () => request<Tracker[]>('GET', '/api/trackers'),
  tracker: (id: string) => request<TrackerDetail>('GET', `/api/trackers/${id}`),
  createTracker: (body: {
    template?: string;
    name?: string;
    type?: TrackerType;
    settings?: Record<string, unknown>;
  }) => request<TrackerDetail>('POST', '/api/trackers', body),
  updateTracker: (id: string, body: { name: string; colour?: string | null; settings?: Record<string, unknown> }) =>
    request<TrackerDetail>('PUT', `/api/trackers/${id}`, body),
  deleteTracker: (id: string) => request<void>('DELETE', `/api/trackers/${id}`),

  addItem: (trackerId: string, body: ItemRequest) => request<Item>('POST', `/api/trackers/${trackerId}/items`, body),
  updateItem: (trackerId: string, itemId: string, body: ItemRequest) =>
    request<Item>('PUT', `/api/trackers/${trackerId}/items/${itemId}`, body),
  deleteItem: (trackerId: string, itemId: string) =>
    request<void>('DELETE', `/api/trackers/${trackerId}/items/${itemId}`),

  checkin: (day: string) => request<CheckIn>('GET', `/api/checkins/${day}`),
  saveCheckin: (day: string, body: Partial<CheckIn>) => request<CheckIn>('PUT', `/api/checkins/${day}`, body),

  pushStatus: () => request<PushStatus>('GET', '/api/push/status'),
  subscribe: (subscription: PushSubscriptionJSON) => request<void>('POST', '/api/push/subscriptions', subscription),
  unsubscribe: (endpoint: string) =>
    request<void>('DELETE', `/api/push/subscriptions?endpoint=${encodeURIComponent(endpoint)}`),
  testPush: () => request<{ delivered: number }>('POST', '/api/push/test', {}),

  importJobEmail: (text: string) => request<JobImportResult>('POST', '/api/jobs/emails', { text }),
};

/** Turns a saved item into an update request, applying changes on top. */
export function toRequest(item: Item, changes: Partial<ItemRequest> = {}, schedule = false): ItemRequest {
  return {
    title: item.title,
    notes: item.notes,
    done: item.done,
    stage: item.stage,
    progress: item.progress,
    amount: item.amount,
    itemDate: item.itemDate,
    startTime: item.startTime,
    endTime: item.endTime,
    // Shift reminders are worked out by the server from the start time
    reminderAt: schedule ? null : item.reminderAt,
    fields: item.fields,
    sortOrder: item.sortOrder,
    ...changes,
  };
}
