import { api } from '../api';

/** True when this browser can receive web push at all. */
export function pushSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

/** On iPhone, notifications only work once the app is added to the Home Screen. */
export function runningAsInstalledApp(): boolean {
  const standalone = (navigator as Navigator & { standalone?: boolean }).standalone;
  return window.matchMedia('(display-mode: standalone)').matches || standalone === true;
}

export function isIos(): boolean {
  return /iPhone|iPad|iPod/.test(navigator.userAgent);
}

export async function registerServiceWorker(): Promise<void> {
  if (!('serviceWorker' in navigator)) return;
  try {
    await navigator.serviceWorker.register('/sw.js');
  } catch {
    // The app still works without offline support or notifications
  }
}

function keyToBytes(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!pushSupported()) return null;
  const registration = await navigator.serviceWorker.ready;
  return registration.pushManager.getSubscription();
}

/**
 * Asks permission and registers this device for notifications.
 * Must be called from a tap, or iOS refuses to show the permission prompt.
 */
export async function enableNotifications(): Promise<void> {
  if (!pushSupported()) {
    throw new Error('This browser cannot receive notifications.');
  }
  const status = await api.pushStatus();
  if (!status.enabled || !status.publicKey) {
    throw new Error('Notifications are not set up on the server yet.');
  }
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    throw new Error('Notifications are blocked. Turn them on for GradPilot in your phone settings.');
  }
  const registration = await navigator.serviceWorker.ready;
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: keyToBytes(status.publicKey) as BufferSource,
    }));
  await api.subscribe(subscription.toJSON());
}

export async function disableNotifications(): Promise<void> {
  const subscription = await currentSubscription();
  if (!subscription) return;
  await api.unsubscribe(subscription.endpoint);
  await subscription.unsubscribe();
}
