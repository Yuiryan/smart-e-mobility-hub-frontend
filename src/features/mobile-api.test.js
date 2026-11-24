import { afterEach, expect, it, vi } from 'vitest';
import { mountMobileApi } from './mobile-api.js';
import { mobileApi } from '../shared/api/mobile.js';
let dispose;
afterEach(() => {
  dispose?.();
  mobileApi.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it('renders API mode with the mobile design and no demo banner', () => {
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(document.querySelector('#root'), '/');
  expect(document.querySelector('.student-frame')).not.toBeNull();
  expect(document.querySelector('.welcome-logo')).not.toBeNull();
  expect(document.body.textContent).not.toContain('DEMO');
});
it('ignores a delayed detail response after leaving the route', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  let resolve;
  vi.spyOn(mobileApi, 'vehicle').mockImplementation(
    () =>
      new Promise((r) => {
        resolve = r;
      }),
  );
  document.body.innerHTML = '<div id="root"></div>';
  const root = document.querySelector('#root');
  dispose = mountMobileApi(root, '/student/vehicles/v');
  dispose();
  root.innerHTML = '<p>New page</p>';
  resolve({ vehicleId: 'v', status: 'AVAILABLE', batteryLevel: 80 });
  await Promise.resolve();
  expect(root.textContent).toBe('New page');
});
it('renders backend details without inventing missing battery or location', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  vi.spyOn(mobileApi, 'vehicle').mockResolvedValue({
    vehicleId: 'v',
    status: 'AVAILABLE',
    batteryLevel: null,
    updatedAt: null,
  });
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(
    document.querySelector('#root'),
    '/student/vehicles/v',
  );
  await Promise.resolve();
  expect(document.querySelector('.battery-readout strong').textContent).toBe(
    '—',
  );
  expect(document.querySelector('[data-api="reserve"] button').disabled).toBe(
    true,
  );
});
