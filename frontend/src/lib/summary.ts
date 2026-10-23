import type { Tracker, TrackerType } from '../types';
import { daysBetween, formatDate, relativeDays } from './time';

export const TYPE_LABELS: Record<TrackerType, string> = {
  CHECKLIST: 'Checklist',
  PIPELINE: 'Pipeline',
  PROGRESS: 'Progress',
  LOG: 'Log',
  COUNTDOWN: 'Countdown',
  SCHEDULE: 'Schedule',
};

export const TYPE_HELP: Record<TrackerType, string> = {
  CHECKLIST: 'Steps to tick off',
  PIPELINE: 'Items that move through stages',
  PROGRESS: 'Topics with how far through you are',
  LOG: 'Dated entries you add up',
  COUNTDOWN: 'Dates to count down to',
  SCHEDULE: 'Shifts or classes with times',
};

/** One line that tells you where a tracker stands, for the tracker list. */
export function trackerHeadline(tracker: Tracker, today: string): string {
  const s = tracker.stats;
  switch (tracker.type) {
    case 'CHECKLIST':
      return s.total === 0 ? 'No steps yet' : `${s.done} of ${s.total} done`;
    case 'PIPELINE': {
      if (s.total === 0) return 'Nothing added yet';
      const stages = Object.entries(s.byStage ?? {}).filter(([, count]) => count > 0);
      return stages.map(([stage, count]) => `${count} ${stage.toLowerCase()}`).join(', ');
    }
    case 'PROGRESS':
      return s.averageProgress === null ? 'Nothing added yet' : `${s.averageProgress}% on average`;
    case 'LOG': {
      const unit = typeof tracker.settings.unit === 'string' ? tracker.settings.unit : '';
      const total = s.totalAmount ?? 0;
      return s.total === 0 ? 'No entries yet' : `${formatNumber(total)} ${unit} logged`.replace(/\s+/g, ' ').trim();
    }
    case 'COUNTDOWN':
      return s.nextDate
        ? `Next: ${formatDate(s.nextDate)}, ${relativeDays(daysBetween(today, s.nextDate))}`
        : 'No upcoming dates';
    case 'SCHEDULE':
      return s.nextDate ? `Next: ${formatDate(s.nextDate)}` : 'No upcoming shifts';
  }
}

export function formatNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

/** Pipeline stages from tracker settings. */
export function stagesOf(tracker: Tracker): string[] {
  const stages = tracker.settings.stages;
  return Array.isArray(stages) ? stages.map(String) : [];
}

export function travelMinutesOf(tracker: Tracker): number {
  const value = tracker.settings.travelMinutes;
  return typeof value === 'number' ? value : 0;
}
