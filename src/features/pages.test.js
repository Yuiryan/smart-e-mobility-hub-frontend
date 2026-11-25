import { describe, it, expect } from 'vitest';
import { DemoEngine } from '../mocks/engine.js';
import { accountPage } from './account.js';
import {
  searchPage,
  hubsPage,
  availableVehiclesPage,
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
      searchPage,
      availableVehiclesPage,
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
    expect(document.querySelector('h1').textContent).toBe('Sign in');
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(JSON.parse(localStorage.getItem('emh-preferences'))).toEqual({
      locale: 'en',
      theme: 'dark',
    });
    preference('locale', 'vi');
    preference('theme', 'light');
  });
});

describe('Figma account and scanner states', () => {
  it('separates registration, verification, reset and success screens', () => {
    const d = new DemoEngine().snapshot();
    document.body.innerHTML = accountPage(d, 'register');
    expect(document.querySelector('[name="studentId"]')).not.toBeNull();
    document.body.innerHTML = accountPage(d, 'verify');
    expect(document.querySelector('[name="code"]')).not.toBeNull();
    expect(document.querySelector('[name="password"]')).toBeNull();
    document.body.innerHTML = accountPage(d, 'reset');
    expect(document.querySelector('[name="password"]')).not.toBeNull();
    expect(document.querySelector('[name="code"]')).toBeNull();
    for (const state of ['registered', 'changed']) {
      document.body.innerHTML = accountPage(d, state);
      expect(document.querySelector('.success-card')).not.toBeNull();
      expect(document.querySelector('form')).toBeNull();
    }
  });
  it('allows opening the scanner before creating a reservation', () => {
    document.body.innerHTML = pickupPage(new DemoEngine().snapshot());
    expect(document.querySelector('#camera')).not.toBeNull();
    expect(document.querySelector('[data-form="scan-vehicle"]')).not.toBeNull();
  });
});

describe('Vehicle booking navigation', () => {
  it('opens details directly from Book a vehicle and keeps Hub search separate', () => {
    const d = new DemoEngine().snapshot();
    document.body.innerHTML = hubsPage(d);
    const links = document.querySelectorAll('.home-quick a');
    expect(links[0].getAttribute('href')).toBe('#/student/vehicles/E-024');
    expect(links[1].getAttribute('href')).toBe('#/student/search');
    document.body.innerHTML = vehiclePage(d, 'E-024');
    expect(
      document.querySelector('a.button.secondary').getAttribute('href'),
    ).toBe('#/student/vehicles');
  });
  it('lists only available vehicles and links each one to its details', () => {
    const d = new DemoEngine().snapshot();
    d.vehicles.find((v) => v.id === 'E-052').fault = true;
    document.body.innerHTML = availableVehiclesPage(d);
    const hrefs = [...document.querySelectorAll('.available-vehicle')].map(
      (a) => a.getAttribute('href'),
    );
    expect(hrefs).toEqual([
      '#/student/vehicles/E-024',
      '#/student/vehicles/S-018',
    ]);
    d.vehicles.forEach((v) => {
      v.status = 'RESERVED';
    });
    document.body.innerHTML = hubsPage(d);
    expect(document.querySelector('.home-quick a').getAttribute('href')).toBe(
      '#/student/vehicles',
    );
    document.body.innerHTML = availableVehiclesPage(d);
    expect(document.querySelector('[role="status"]')).not.toBeNull();
    expect(document.querySelector('.available-vehicle')).toBeNull();
  });
});
