import { useSyncExternalStore } from 'react';
import type { Locale } from '../features/catalog';
type Preferences = { locale: Locale; theme: 'light' | 'dark' };
const key = 'emobility.preferences';
const defaults: Preferences = { locale: 'vi', theme: 'light' };
export function parsePreferences(raw: string | null): Preferences {
  try {
    const value = JSON.parse(raw ?? '{}');
    return {
      locale: value?.locale === 'en' ? 'en' : 'vi',
      theme: value?.theme === 'dark' ? 'dark' : 'light',
    };
  } catch {
    return defaults;
  }
}
function read(): Preferences {
  try {
    return parsePreferences(localStorage.getItem(key));
  } catch {
    return defaults;
  }
}
let current = read();
const listeners = new Set<() => void>();
function apply() {
  document.documentElement.dataset.theme = current.theme;
  document.documentElement.lang = current.locale;
}
apply();
window.addEventListener('storage', (event) => {
  if (event.key === key || event.key === null) {
    current = read();
    apply();
    listeners.forEach((fn) => fn());
  }
});
export function setPreferences(next: Partial<Preferences>) {
  current = { ...current, ...next };
  try {
    localStorage.setItem(key, JSON.stringify(current));
  } catch {
    /* In-memory fallback when browser storage is unavailable. */
  }
  apply();
  listeners.forEach((fn) => fn());
}
const subscribe = (fn: () => void) => {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
};
export function usePreferences() {
  return useSyncExternalStore(subscribe, () => current);
}
