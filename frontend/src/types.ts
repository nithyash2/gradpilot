// Shapes returned by the GradPilot API. Dates are ISO strings ("2026-10-23"),
// times are "HH:mm:ss" and instants are full ISO timestamps.

export type TrackerType = 'CHECKLIST' | 'PIPELINE' | 'PROGRESS' | 'LOG' | 'COUNTDOWN' | 'SCHEDULE';

export interface User {
  id: string;
  email: string;
  displayName: string | null;
  timezone: string;
  wakeTime: string | null;
  bedTime: string | null;
  morningTime: string;
  eveningTime: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface Template {
  key: string;
  name: string;
  type: TrackerType;
  description: string;
  settings: Record<string, unknown>;
  starterItems: string[];
}

export interface TrackerStats {
  total: number;
  done: number;
  averageProgress: number | null;
  totalAmount: number | null;
  byStage: Record<string, number> | null;
  nextDate: string | null;
}

export interface Tracker {
  id: string;
  name: string;
  type: TrackerType;
  template: string | null;
  colour: string | null;
  settings: Record<string, unknown>;
  sortOrder: number;
  stats: TrackerStats;
}

export interface Item {
  id: string;
  trackerId: string;
  title: string;
  notes: string | null;
  done: boolean;
  stage: string | null;
  progress: number | null;
  amount: number | null;
  itemDate: string | null;
  startTime: string | null;
  endTime: string | null;
  reminderAt: string | null;
  fields: Record<string, unknown>;
  sortOrder: number;
}

export interface ItemRequest {
  title: string;
  notes?: string | null;
  done?: boolean;
  stage?: string | null;
  progress?: number | null;
  amount?: number | null;
  itemDate?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  reminderAt?: string | null;
  fields?: Record<string, unknown>;
  sortOrder?: number;
}

export interface TrackerDetail {
  tracker: Tracker;
  items: Item[];
}

export interface CheckIn {
  day: string;
  saved: boolean;
  wakeTime: string | null;
  breakfast: string | null;
  lunch: string | null;
  dinner: string | null;
  selfCare: boolean;
  dishes: boolean;
  bedOnTime: boolean;
  focus: string | null;
  focusDone: boolean;
  notes: string | null;
}

export interface ShiftView {
  trackerId: string;
  trackerName: string;
  shift: Item;
  leaveAt: string | null;
  today: boolean;
}

export interface TrackedItem {
  trackerId: string;
  trackerName: string;
  item: Item;
}

export interface CountdownView {
  trackerId: string;
  trackerName: string;
  itemId: string;
  title: string;
  date: string;
  daysLeft: number;
}

export interface Today {
  date: string;
  now: string;
  timezone: string;
  nextShift: ShiftView | null;
  dueToday: TrackedItem[];
  countdowns: CountdownView[];
  checkin: CheckIn;
  lastSevenDays: { date: string; focusDone: boolean }[];
}

export interface PushStatus {
  enabled: boolean;
  publicKey: string | null;
  devices: number;
}

export type JobEmailKind = 'APPLIED' | 'ASSESSMENT' | 'INTERVIEW' | 'OFFER' | 'REJECTED';

export type JobImportOutcome =
  | 'CREATED'
  | 'UPDATED'
  | 'UNCHANGED'
  | 'DUPLICATE'
  | 'NOT_ENOUGH_DETAIL'
  | 'NOT_A_JOB_EMAIL';

export interface JobImportResult {
  outcome: JobImportOutcome;
  kind: JobEmailKind | null;
  company: string | null;
  role: string | null;
  source: string | null;
  trackerId: string | null;
  itemId: string | null;
  title: string | null;
  stage: string | null;
  previousStage: string | null;
}
