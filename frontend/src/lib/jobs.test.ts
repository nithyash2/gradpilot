import { describe, expect, it } from 'vitest';
import type { JobImportResult } from '../types';
import { changedSomething, describeImport } from './jobs';

const base: JobImportResult = {
  outcome: 'CREATED',
  kind: 'APPLIED',
  company: 'Canva',
  role: 'Graduate Engineer',
  source: 'LinkedIn',
  trackerId: 't',
  itemId: 'i',
  title: 'Canva – Graduate Engineer',
  stage: 'Applied',
  previousStage: null,
};

describe('describeImport', () => {
  it('says where a new application went', () => {
    expect(describeImport(base)).toBe('Added Canva – Graduate Engineer to Applied.');
  });

  it('says how an application moved', () => {
    expect(describeImport({ ...base, outcome: 'UPDATED', stage: 'Interview', previousStage: 'Applied' })).toBe(
      'Moved Canva – Graduate Engineer from Applied to Interview.',
    );
    expect(describeImport({ ...base, outcome: 'UPDATED', stage: 'Offer' })).toContain('Congratulations');
    expect(describeImport({ ...base, outcome: 'UPDATED', stage: 'Rejected' })).toContain('On to the next one');
  });

  it('explains emails that changed nothing', () => {
    expect(describeImport({ ...base, outcome: 'DUPLICATE' })).toBe('You have already added this email.');
    expect(describeImport({ ...base, outcome: 'NOT_A_JOB_EMAIL', title: null })).toContain('does not look like');
  });
});

describe('changedSomething', () => {
  it('is true only when an application was touched', () => {
    expect(changedSomething(base)).toBe(true);
    expect(changedSomething({ ...base, outcome: 'DUPLICATE' })).toBe(false);
    expect(changedSomething({ ...base, outcome: 'NOT_A_JOB_EMAIL' })).toBe(false);
  });
});
