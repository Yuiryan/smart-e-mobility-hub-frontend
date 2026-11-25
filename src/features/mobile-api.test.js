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
it('requires device location and loads Hubs automatically after permission', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  let accept;
  vi.stubGlobal('navigator', {
    geolocation: {
      getCurrentPosition: vi.fn((ok) => {
        accept = ok;
      }),
    },
  });
  const discover = vi
    .spyOn(mobileApi, 'discoverHubs')
    .mockImplementation(async () => {
      mobileApi.hubs = [
        {
          hubId: 'h',
          hubName: 'Test Hub',
          distanceKm: 1,
          availableVehicleCount: 0,
          vehicles: [],
        },
      ];
    });
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(document.querySelector('#root'), '/student/search');
  expect(document.querySelector('input')).toBeNull();
  expect(discover).not.toHaveBeenCalled();
  document.querySelector('[data-api-action="gps"]').click();
  expect(document.querySelector('[data-api-action="gps"]').disabled).toBe(true);
  accept({ coords: { latitude: 10.77, longitude: 106.66 } });
  await vi.waitFor(() =>
    expect(document.querySelector('.hub-list')?.textContent).toContain(
      'Test Hub',
    ),
  );
  expect(discover).toHaveBeenCalledWith(
    { latitude: 10.77, longitude: 106.66 },
    expect.any(AbortSignal),
  );
});
it('does not request Hubs when location permission is denied', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  vi.stubGlobal('navigator', {
    geolocation: { getCurrentPosition: (_ok, fail) => fail({ code: 1 }) },
  });
  const discover = vi.spyOn(mobileApi, 'discoverHubs');
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(document.querySelector('#root'), '/student/search');
  document.querySelector('[data-api-action="gps"]').click();
  await vi.waitFor(() =>
    expect(document.querySelector('#api-feedback').className).toContain(
      'error',
    ),
  );
  expect(discover).not.toHaveBeenCalled();
  expect(document.querySelector('.hub-list')).toBeNull();
  expect(document.querySelector('[data-api-action="gps"]').disabled).toBe(
    false,
  );
});
it('ignores location received after leaving the page', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  let accept;
  vi.stubGlobal('navigator', {
    geolocation: {
      getCurrentPosition: (ok) => {
        accept = ok;
      },
    },
  });
  const discover = vi.spyOn(mobileApi, 'discoverHubs');
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(document.querySelector('#root'), '/student/search');
  document.querySelector('[data-api-action="gps"]').click();
  dispose();
  accept({ coords: { latitude: 10, longitude: 106 } });
  await Promise.resolve();
  expect(discover).not.toHaveBeenCalled();
});
it('selects a Hub with no vehicles and returns to the loaded Hub list', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  vi.stubGlobal('navigator', {
    geolocation: {
      getCurrentPosition: (ok) =>
        ok({ coords: { latitude: 10, longitude: 106 } }),
    },
  });
  const discover = vi
    .spyOn(mobileApi, 'discoverHubs')
    .mockImplementation(async () => {
      mobileApi.hubs = [
        {
          hubId: 'empty',
          hubName: 'Empty Hub',
          distanceKm: 1,
          freeSpaceCount: 4,
          availableVehicleCount: 0,
          vehicles: [],
        },
        {
          hubId: 'other',
          hubName: 'Other Hub',
          distanceKm: 2,
          availableVehicleCount: 1,
          vehicles: [{ vehicleId: 'other-bike', batteryPct: 90 }],
        },
      ];
    });
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(document.querySelector('#root'), '/student/search');
  document.querySelector('[data-api-action="gps"]').click();
  await vi.waitFor(() =>
    expect(document.querySelector('[data-select-hub="empty"]')).not.toBeNull(),
  );
  document.querySelector('[data-select-hub="empty"]').click();
  expect(
    document.querySelector('[data-api-action="hub-vehicles"]'),
  ).not.toBeNull();
  expect(document.body.textContent).toContain('Empty Hub');
  expect(document.body.textContent).not.toContain('Mã Hub:');
  const spaces = vi.spyOn(mobileApi, 'hubSpaces').mockResolvedValue([
    { spaceId: 'A-01', hubId: 'empty', status: 'AVAILABLE', blocked: false },
    { spaceId: 'A-02', hubId: 'empty', status: 'OCCUPIED', blocked: false },
    { spaceId: 'A-03', hubId: 'empty', status: 'AVAILABLE', blocked: true },
  ]);
  document.querySelector('[data-api-action="hub-parking"]').click();
  await vi.waitFor(() =>
    expect(document.querySelector('[data-api="parking"]')).not.toBeNull(),
  );
  expect(spaces).toHaveBeenCalledWith('empty', expect.any(AbortSignal));
  const form = document.querySelector('[data-api="parking"]');
  expect(form.querySelector('[name="hubId"]').type).toBe('hidden');
  expect(form.querySelector('[name="hubId"]').value).toBe('empty');
  expect(
    [...form.querySelectorAll('input[name="spaceId"]:not(:disabled)')].map(
      (o) => o.value,
    ),
  ).toEqual(['A-01']);

  expect(document.body.textContent).not.toContain('Other Hub');
  document.querySelector('[data-api-action="hub-vehicles"]').click();
  expect(document.querySelector('.available-vehicles').textContent).not.toBe(
    '',
  );
  expect(document.querySelector('.available-vehicles a')).toBeNull();
  document.querySelector('[data-api-action="hub-list"]').click();
  expect(document.querySelectorAll('[data-select-hub]')).toHaveLength(2);
  expect(discover).toHaveBeenCalledTimes(1);
  document.querySelector('[data-select-hub="other"]').click();
  document.querySelector('[data-api-action="hub-vehicles"]').click();
  expect(
    document.querySelector('.available-vehicles a').getAttribute('href'),
  ).toBe('#/student/vehicles/other-bike');
});

it('returns the remembered vehicle without asking the student for a ride ID', () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  mobileApi.ride = { rideId: 'ride-1', vehicleId: 'bike-7', status: 'ACTIVE' };
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(document.querySelector('#root'), '/student/return');
  expect(document.body.textContent).toContain('bike-7');
  expect(
    document.querySelector('input[name="rideId"]:not([type="hidden"])'),
  ).toBeNull();
  expect(document.querySelector('.design-status')).toBeNull();
  expect(document.querySelector('.preview-tools')).toBeNull();
  expect(
    document.querySelector('a[href="#/student/notifications"]'),
  ).toBeNull();
});
