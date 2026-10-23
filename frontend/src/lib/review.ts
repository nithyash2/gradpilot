import type { WeekReview } from '../types';

/** A short, kind headline for the week. */
export function weekHeadline(review: WeekReview): string {
  const { jobs } = review;
  if (jobs.offers > 0) return 'An offer this week. Well done!';
  if (jobs.interviews > 0) return `${jobs.interviews} ${jobs.interviews === 1 ? 'interview' : 'interviews'} this week. Your applications are working.`;
  if (review.focusDays >= 5) return 'A strong week. You showed up most days.';
  if (jobs.applied > 0) return `${jobs.applied} ${jobs.applied === 1 ? 'application' : 'applications'} sent. Every one counts.`;
  if (review.focusDays > 0) return 'You kept going. Small steps add up.';
  return 'A quiet week. Pick one small thing for tomorrow.';
}

/** Rows for the numbers grid, leaving out what did not happen. */
export function weekNumbers(review: WeekReview): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  rows.push({ label: 'Days with your one thing done', value: `${review.focusDays}/7` });
  if (review.streak > 1) rows.push({ label: 'Current streak', value: `${review.streak} days` });
  if (review.jobs.applied > 0) rows.push({ label: 'Applications sent', value: String(review.jobs.applied) });
  if (review.jobs.assessments > 0) rows.push({ label: 'Assessments', value: String(review.jobs.assessments) });
  if (review.jobs.interviews > 0) rows.push({ label: 'Interviews', value: String(review.jobs.interviews) });
  if (review.jobs.offers > 0) rows.push({ label: 'Offers', value: String(review.jobs.offers) });
  if (review.jobs.rejections > 0) rows.push({ label: 'Rejections (their loss)', value: String(review.jobs.rejections) });
  if (review.jobs.active > 0) rows.push({ label: 'Applications still open', value: String(review.jobs.active) });
  if (review.leetcodeSolved > 0) rows.push({ label: 'LeetCode problems', value: String(review.leetcodeSolved) });
  if (review.shifts.count > 0) {
    rows.push({
      label: 'Shifts',
      value: review.shifts.hours > 0 ? `${review.shifts.count} (${review.shifts.hours} h)` : String(review.shifts.count),
    });
  }
  for (const log of review.logs) {
    rows.push({ label: log.name, value: `${Number(log.amount)} ${log.unit ?? ''}`.trim() });
  }
  return rows;
}
