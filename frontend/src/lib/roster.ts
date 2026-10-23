/**
 * Reads shifts out of roster text. On iPhone, Live Text copies the text out
 * of a roster screenshot (open it in Photos, press and hold the text, Copy),
 * so this works for apps such as Operandio that cannot be connected directly.
 */

export interface RosterShift {
  date: string; // YYYY-MM-DD
  start: string; // HH:mm
  end: string | null; // HH:mm
  note: string;
}

const MONTHS: Record<string, number> = {
  jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, sept: 9, oct: 10, nov: 11, dec: 12,
};

const DAY = '(?:mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)[a-z]*\\.?,?\\s*';
const MONTH = '(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?';

/** "Mon 27 Oct", "27th October 2026", "Oct 27", "27/10", "27/10/2026", "2026-10-27". */
const DATE_PATTERNS: { regex: RegExp; read: (m: RegExpMatchArray) => [number, number, number | null] }[] = [
  {
    regex: /\b(\d{4})-(\d{2})-(\d{2})\b/,
    read: (m) => [Number(m[3]), Number(m[2]), Number(m[1])],
  },
  {
    regex: new RegExp(`(?:${DAY})?\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+${MONTH}(?:,?\\s+(\\d{4}))?`, 'i'),
    read: (m) => [Number(m[1]), MONTHS[m[2].toLowerCase()], m[3] ? Number(m[3]) : null],
  },
  {
    regex: new RegExp(`(?:${DAY})?\\b${MONTH}\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?`, 'i'),
    read: (m) => [Number(m[2]), MONTHS[m[1].toLowerCase()], m[3] ? Number(m[3]) : null],
  },
  {
    // Australian order: day/month
    regex: /\b(\d{1,2})[/.](\d{1,2})(?:[/.](\d{2,4}))?\b/,
    read: (m) => [Number(m[1]), Number(m[2]), m[3] ? normaliseYear(Number(m[3])) : null],
  },
];

const TIME = '(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?';
const RANGE = new RegExp(`${TIME}\\s*(?:-|–|—|to|until)\\s*${TIME}`, 'i');
const SINGLE = new RegExp(`(?:start(?:s|ing)?\\s*(?:at)?\\s*)?\\b${TIME}`, 'i');

function normaliseYear(year: number): number {
  return year < 100 ? 2000 + year : year;
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** 24-hour "HH:mm", or null when it is not a time. */
export function toTime(hourText: string, minuteText: string | undefined, meridiem: string | undefined, fallbackPm = false): string | null {
  let hour = Number(hourText);
  const minute = minuteText ? Number(minuteText) : 0;
  if (Number.isNaN(hour) || hour > 23 || minute > 59) return null;
  const m = meridiem?.toLowerCase().replace(/\./g, '');
  if (m === 'pm' && hour < 12) hour += 12;
  else if (m === 'am' && hour === 12) hour = 0;
  else if (!m && fallbackPm && hour < 12) hour += 12;
  return `${pad(hour)}:${pad(minute)}`;
}

/** The year that puts a day and month closest to today, preferring the coming months. */
export function inferYear(day: number, month: number, today: string): number {
  const [y] = today.split('-').map(Number);
  const todayTime = Date.parse(`${today}T00:00:00Z`);
  let best = y;
  let bestScore = Infinity;
  for (const candidate of [y - 1, y, y + 1]) {
    const time = Date.parse(`${candidate}-${pad(month)}-${pad(day)}T00:00:00Z`);
    const days = (time - todayTime) / 86400000;
    // Rosters are mostly about the coming weeks; a little of the past is allowed
    const score = days < -30 ? Infinity : Math.abs(days);
    if (score < bestScore) {
      best = candidate;
      bestScore = score;
    }
  }
  return best;
}

function findDate(line: string, today: string): { date: string; index: number; length: number } | null {
  for (const pattern of DATE_PATTERNS) {
    const m = line.match(pattern.regex);
    if (!m || m.index === undefined) continue;
    const [day, month, year] = pattern.read(m);
    if (!month || day < 1 || day > 31 || month < 1 || month > 12) continue;
    const y = year ?? inferYear(day, month, today);
    const iso = `${y}-${pad(month)}-${pad(day)}`;
    if (Number.isNaN(Date.parse(`${iso}T00:00:00Z`))) continue;
    return { date: iso, index: m.index, length: m[0].length };
  }
  return null;
}

function findTimes(text: string): { start: string; end: string | null; index: number; length: number } | null {
  const range = text.match(RANGE);
  if (range && range.index !== undefined) {
    const endMeridiem = range[6] ?? undefined;
    const startMeridiem = range[3] ?? undefined;
    // "5 - 11pm": the start shares the end's am/pm when it makes sense
    const inferredStartMeridiem =
      startMeridiem ?? (endMeridiem && Number(range[1]) <= Number(range[4]) ? endMeridiem : undefined);
    const start = toTime(range[1], range[2], inferredStartMeridiem);
    const end = toTime(range[4], range[5], endMeridiem);
    if (start && end && (range[2] || range[3] || range[5] || range[6] || range[0].includes(':'))) {
      return { start, end, index: range.index, length: range[0].length };
    }
  }
  const single = text.match(SINGLE);
  if (single && single.index !== undefined && (single[2] || single[3])) {
    const start = toTime(single[1], single[2], single[3]);
    if (start) return { start, end: null, index: single.index, length: single[0].length };
  }
  return null;
}

/** Shifts found in roster text, in date order, without duplicates. */
export function parseRoster(text: string, today: string): RosterShift[] {
  const lines = text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  const shifts: RosterShift[] = [];
  let pendingDate: string | null = null;

  for (const line of lines) {
    const date = findDate(line, today);
    const rest = date ? line.slice(0, date.index) + ' ' + line.slice(date.index + date.length) : line;
    const times = findTimes(rest);

    if (date && times) {
      shifts.push({ date: date.date, start: times.start, end: times.end, note: noteFrom(rest, times) });
      pendingDate = null;
    } else if (date) {
      pendingDate = date.date;
    } else if (times && pendingDate) {
      shifts.push({ date: pendingDate, start: times.start, end: times.end, note: noteFrom(rest, times) });
      pendingDate = null;
    }
  }

  const seen = new Set<string>();
  return shifts
    .filter((s) => {
      const key = `${s.date} ${s.start}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => `${a.date} ${a.start}`.localeCompare(`${b.date} ${b.start}`));
}

function noteFrom(text: string, times: { index: number; length: number }): string {
  return (text.slice(0, times.index) + ' ' + text.slice(times.index + times.length))
    .replace(/\b(?:shift|start|starts|finish|finishes|time|hrs?|hours?)\b/gi, ' ')
    .replace(/[|•·,:;()\-–—]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 60);
}
