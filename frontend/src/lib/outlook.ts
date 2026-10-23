import type { OutlookSync } from '../types';

/** What to tell the person when Microsoft sends them back to Settings. */
export function connectMessage(search: string): { ok: boolean; text: string } | null {
  const params = new URLSearchParams(search);
  const outcome = params.get('outlook');
  if (outcome === 'connected') {
    return { ok: true, text: 'Outlook is connected. Your job emails from the last 30 days are being read now.' };
  }
  if (outcome === 'error') {
    switch (params.get('reason')) {
      case 'declined':
        return { ok: false, text: 'Outlook was not connected because access was not allowed.' };
      case 'expired':
        return { ok: false, text: 'That sign-in took too long. Please try connecting again.' };
      default:
        return { ok: false, text: 'Outlook could not be connected. Please try again.' };
    }
  }
  return null;
}

/** One sentence about a sync the person started. */
export function syncMessage(result: OutlookSync): string {
  if (result.error) return result.error;
  const changes = result.created + result.updated;
  if (changes === 0) {
    return result.read === 0 ? 'No new emails since the last check.' : 'Checked your new emails. No job updates.';
  }
  const parts = [];
  if (result.created > 0) parts.push(`added ${result.created} ${result.created === 1 ? 'application' : 'applications'}`);
  if (result.updated > 0) parts.push(`updated ${result.updated}`);
  const sentence = parts.join(' and ');
  return sentence.charAt(0).toUpperCase() + sentence.slice(1) + '.';
}

/** "just now", "5 minutes ago", "3 hours ago", "2 days ago". */
export function timeAgo(iso: string | null, now: Date = new Date()): string {
  if (!iso) return 'never';
  const minutes = Math.floor((now.getTime() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'} ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} ${hours === 1 ? 'hour' : 'hours'} ago`;
  const days = Math.floor(hours / 24);
  return `${days} ${days === 1 ? 'day' : 'days'} ago`;
}
