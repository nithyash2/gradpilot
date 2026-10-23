import type { JobImportResult } from '../types';

/** One sentence telling the person what happened to the email they pasted. */
export function describeImport(result: JobImportResult): string {
  const name = result.title ?? 'the application';
  switch (result.outcome) {
    case 'CREATED':
      return `Added ${name} to ${result.stage ?? 'your job hunt'}.`;
    case 'UPDATED':
      return result.stage === 'Rejected'
        ? `Moved ${name} to Rejected. On to the next one.`
        : result.stage === 'Offer'
          ? `Moved ${name} to Offer. Congratulations!`
          : `Moved ${name} from ${result.previousStage ?? 'its stage'} to ${result.stage}.`;
    case 'UNCHANGED':
      return `${name} is already at ${result.stage ?? 'that stage'}, so nothing changed.`;
    case 'DUPLICATE':
      return 'You have already added this email.';
    case 'NOT_ENOUGH_DETAIL':
      return 'This looks like a job email, but it does not say the company or the role. Add it by hand below.';
    case 'NOT_A_JOB_EMAIL':
      return 'This does not look like an email about a job application.';
  }
}

/** Whether the result changed the person's applications. */
export function changedSomething(result: JobImportResult): boolean {
  return result.outcome === 'CREATED' || result.outcome === 'UPDATED' || result.outcome === 'UNCHANGED';
}
