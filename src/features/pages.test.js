import { describe, it, expect } from 'vitest';
import { DemoEngine } from '../mocks/engine.js';
import { accountPage } from './account.js';
import {
  hubsPage,
  vehiclePage,
  bookingsPage,
  parkingPage,
  chargingPage,
  pickupPage,
  returnPage,
} from './student.js';
import { e, preference } from '../shared/ui.js';
describe('Vanilla HTML pages', () => {
  it('escapes untrusted text rather than rendering executable HTML', () => {
    expect(e('<img src=x onerror="alert(1)">')).not.toContain('<img');
    const d = new DemoEngine().snapshot();
    d.user = { name: '<script>bad()</script>', email: 'demo', role: 'STUDENT' };
    expect(accountPage(d)).not.toContain('<script>');
  });
  it('renders student forms and explicit empty states', () => {
    const d = new DemoEngine().snapshot();
    for (const page of [
      hubsPage,
      bookingsPage,
      parkingPage,
      chargingPage,
      pickupPage,
      returnPage,
    ]) {
      document.body.innerHTML = page(d);
      expect(document.querySelector('h1')).not.toBeNull();
    }
    document.body.innerHTML = vehiclePage(d, 'E-019');
    expect(document.querySelector('button').disabled).toBe(true);
  });
  it('renders English labels and dark mode without persisting credentials', () => {
    preference('locale', 'en');
    preference('theme', 'dark');
    document.body.innerHTML = accountPage(new DemoEngine().snapshot());
    expect(document.querySelector('h1').textContent).toBe('Welcome back');
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(JSON.parse(localStorage.getItem('emh-preferences'))).toEqual({
      locale: 'en',
      theme: 'dark',
    });
    preference('locale', 'vi');
    preference('theme', 'light');
  });
});
