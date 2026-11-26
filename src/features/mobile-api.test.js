import { afterEach, expect, it, vi } from 'vitest';
import { mountMobileApi } from './mobile-api.js';
import { mobileApi } from '../shared/api/mobile.js';
import * as ui from '../shared/ui.js';
let dispose;
const parkingVehicle = {
  vehicleId: 'bike-7',
  ownership: 'SHARED',
  canBookParking: true,
};
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
it('loads an active trip from the backend in a fresh tab and offers to return it', async () => {
  mobileApi.session = {
    sessionToken: 'test-token',
    expiresAt: new Date(Date.now() + 600000).toISOString(),
    user: { role: 'STUDENT', accountId: 'student' },
  };
  const send = vi.spyOn(mobileApi, 'send').mockResolvedValue({
    reservation: null,
    ride: { rideId: 'ride-8', vehicleId: 'bike_008', status: 'ACTIVE' },
  });
  vi.spyOn(mobileApi, 'loadChargingReceipts').mockResolvedValue(false);
  document.body.innerHTML = '<div id="root"></div>';
  const root = document.querySelector('#root');
  dispose = mountMobileApi(root, '/student/reservations');
  await vi.waitFor(() =>
    expect(
      root.querySelector('main a[href="#/student/return"]'),
    ).not.toBeNull(),
  );
  expect(root.textContent).toContain('bike_008');
  expect(send).toHaveBeenCalledWith(
    '/student/journey',
    expect.objectContaining({
      headers: { Authorization: 'Bearer test-token' },
    }),
  );
});
it.each([201, 202])(
  'shows the pickup receipt after device confirmation following HTTP %s',
  async (status) => {
    mobileApi.session = {
      sessionToken: 'test-token',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      user: { role: 'STUDENT', accountId: 'pickup-test' },
    };
    mobileApi.journeyLoaded = true;
    vi.spyOn(ui, 'confirmAction').mockResolvedValue(true);
    const ride = { rideId: 'ride-8', vehicleId: 'bike_008', status: 'ACTIVE' };
    const send = vi.spyOn(mobileApi, 'send').mockResolvedValueOnce({
      status,
      data: status === 201 ? ride : { operationId: 'pickup-8' },
    });
    document.body.innerHTML = '<div id="root"></div>';
    const root = document.querySelector('#root');
    dispose = mountMobileApi(root, '/student/pickup');
    const form = root.querySelector('form[data-api="pickup"]');
    form.elements.vehicleId.value = 'bike_008';
    form.requestSubmit();

    await vi.waitFor(() =>
      expect(mobileApi.command?.state).toBe(
        status === 201 ? 'SUCCEEDED' : 'PENDING',
      ),
    );

    if (status === 202) {
      const poll = '[data-api-action="poll"]';
      await vi.waitFor(() => expect(root.querySelector(poll)).not.toBeNull());
      expect(root.querySelector('.success-card')).toBeNull();
      send.mockResolvedValueOnce({
        operationId: 'pickup-8',
        status: 'SUCCEEDED',
        result: ride,
      });
      root.querySelector(poll).click();
    }

    await vi.waitFor(() =>
      expect(root.querySelector('.success-card')).not.toBeNull(),
    );
    expect(root.querySelector('.success-card').textContent).toContain(
      'bike_008',
    );
    expect(
      root.querySelector('main a[href="#/student/return"]'),
    ).not.toBeNull();
    expect(root.querySelector('form[data-api="pickup"]')).toBeNull();
    sessionStorage.removeItem('emh-journey:pickup-test');
  },
);
it('retries loading the journey and clears the old trip when the backend has no active records', async () => {
  mobileApi.session = {
    sessionToken: 'test-token',
    expiresAt: new Date(Date.now() + 600000).toISOString(),
    user: { role: 'STUDENT', accountId: 'other-student' },
  };
  mobileApi.ride = {
    rideId: 'old-ride',
    vehicleId: 'old-bike',
    status: 'ACTIVE',
  };
  mobileApi.reservations = [
    { reservationId: 'picked-up', vehicleId: 'old-bike', status: 'PICKED_UP' },
  ];
  const send = vi
    .spyOn(mobileApi, 'send')
    .mockRejectedValueOnce(new Error('Network interrupted'))
    .mockResolvedValueOnce({ reservation: null, ride: null });
  vi.spyOn(mobileApi, 'loadChargingReceipts').mockResolvedValue(false);
  document.body.innerHTML = '<div id="root"></div>';
  const root = document.querySelector('#root');
  dispose = mountMobileApi(root, '/student/reservations');
  const retry = '[data-api-action="reload-journey"]';
  await vi.waitFor(() => expect(root.querySelector(retry)).not.toBeNull());
  root.querySelector(retry).click();
  await vi.waitFor(() => expect(mobileApi.journeyLoaded).toBe(true));
  expect(send).toHaveBeenCalledTimes(2);
  expect(mobileApi.ride).toBeNull();
  expect(root.textContent).not.toContain('old-bike');
  expect(root.querySelector('main a[href="#/student/return"]')).toBeNull();
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
  mobileApi.studentVehicles = [parkingVehicle];
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
  vi.spyOn(mobileApi, 'hubCatalog').mockResolvedValue([]);
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

it.each([
  ['/student/parking', 'parking'],
  ['/student/return', 'return'],
])(
  'loads real parking spaces for %s without replacing search results',
  async (path, formType) => {
    mobileApi.session = { user: { role: 'STUDENT' } };
    mobileApi.studentVehicles = [parkingVehicle];
    mobileApi.ride = {
      rideId: 'ride-1',
      vehicleId: 'bike-7',
      status: 'ACTIVE',
    };
    const searchResults = [
      { hubId: 'nearby', hubName: 'Nearby', vehicles: [] },
    ];
    mobileApi.hubs = searchResults;
    vi.spyOn(mobileApi, 'hubCatalog').mockResolvedValue([
      { hubId: 'h1', hubName: 'Hub 1' },
    ]);
    vi.spyOn(mobileApi, 'hubSpaces').mockResolvedValue([
      { spaceId: 'A-01', status: 'AVAILABLE', blocked: false },
      { spaceId: 'A-02', status: 'OCCUPIED', blocked: false },
    ]);
    document.body.innerHTML = '<div id="root"></div>';
    dispose = mountMobileApi(document.querySelector('#root'), path);
    await vi.waitFor(() =>
      expect(document.querySelector('[data-parking-hub="h1"]')).not.toBeNull(),
    );
    document.querySelector('[data-parking-hub="h1"]').click();
    await vi.waitFor(() =>
      expect(document.querySelector(`[data-api="${formType}"]`)).not.toBeNull(),
    );
    const form = document.querySelector(`[data-api="${formType}"]`);
    expect(form.querySelector('[value="A-01"]').disabled).toBe(false);
    expect(form.querySelector('[value="A-02"]').disabled).toBe(true);
    if (formType === 'return') {
      expect(form.querySelector('[name="rideId"]').value).toBe('ride-1');
      expect(document.body.textContent).toContain('bike-7');
      expect(document.querySelector('[data-api="parking"]')).toBeNull();
    }
    expect(mobileApi.hubs).toBe(searchResults);
  },
);

it('shows unavailable spaces without allowing a reservation', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  mobileApi.studentVehicles = [parkingVehicle];
  vi.spyOn(mobileApi, 'hubCatalog').mockResolvedValue([
    { hubId: 'h1', hubName: 'Hub 1' },
  ]);
  vi.spyOn(mobileApi, 'hubSpaces').mockResolvedValue([
    { spaceId: 'A-01', status: 'OCCUPIED', blocked: false },
  ]);
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(document.querySelector('#root'), '/student/parking');
  await vi.waitFor(() => document.querySelector('[data-parking-hub="h1"]'));
  document.querySelector('[data-parking-hub="h1"]').click();
  await vi.waitFor(() =>
    expect(document.querySelector('[value="A-01"]')?.disabled).toBe(true),
  );
  expect(document.querySelector('[data-api="parking"]')).toBeNull();
});

it('allows retrying a failed parking Hub catalog request', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  mobileApi.studentVehicles = [parkingVehicle];
  vi.spyOn(mobileApi, 'hubCatalog')
    .mockRejectedValueOnce(new Error('Hub catalog unavailable'))
    .mockResolvedValueOnce([{ hubId: 'h1', hubName: 'Hub 1' }]);
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(document.querySelector('#root'), '/student/parking');
  await vi.waitFor(() =>
    expect(document.querySelector('#api-feedback').textContent).toContain(
      'Hub catalog unavailable',
    ),
  );
  document.querySelector('[data-api-action="reload-parking"]').click();
  await vi.waitFor(() =>
    expect(document.querySelector('[data-parking-hub="h1"]')).not.toBeNull(),
  );
});

it('selects a rented bike, a charging port and an available window on the same screen', async () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  mobileApi.studentVehicles = [
    parkingVehicle,
    { vehicleId: 'personal', ownership: 'PRIVATE', canBookParking: false },
  ];
  const catalog = vi
    .spyOn(mobileApi, 'hubCatalog')
    .mockResolvedValue([{ hubId: 'h1', hubName: 'Hub KTX B' }]);
  const start = new Date(Date.now() + 3600000).toISOString();
  const end = new Date(Date.parse(start) + 1800000).toISOString();
  const loadOptions = vi.spyOn(mobileApi, 'chargingOptions').mockResolvedValue({
    currentBatteryPct: 35,
    ports: [
      {
        portId: 'busy',
        portName: 'Port busy',
        slots: [
          {
            startAt: start,
            endAt: end,
            estimatedDurationMinutes: 30,
            available: false,
          },
        ],
      },
      {
        portId: 'free',
        portName: 'Port free',
        slots: [
          {
            startAt: start,
            endAt: end,
            estimatedDurationMinutes: 30,
            available: true,
          },
        ],
      },
    ],
  });
  const charge = vi
    .spyOn(mobileApi, 'charge')
    .mockResolvedValue({ chargingRequestId: 'c1' });
  vi.spyOn(ui, 'confirmAction').mockResolvedValue(true);
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(
    document.querySelector('#root'),
    '/student/charging',
  );
  await vi.waitFor(() =>
    expect(document.querySelector('select[name="hubId"]')).not.toBeNull(),
  );
  expect(catalog).toHaveBeenCalledWith(expect.any(AbortSignal));
  expect(
    document.querySelector('select[name="hubId"] option[value="h1"]')
      .textContent,
  ).toBe('Hub KTX B');
  expect(document.querySelector('input[name="hubId"]')).toBeNull();
  const choose = (name, value) => {
    const field = document.querySelector(`[name="${name}"]`);
    field.value = value;
    field.dispatchEvent(new Event('change', { bubbles: true }));
  };
  choose('vehicleId', 'bike-7');
  await vi.waitFor(() =>
    expect(document.querySelector('option[value="free"]')).not.toBeNull(),
  );
  expect(loadOptions).toHaveBeenCalledWith(
    'bike-7',
    'h1',
    '80',
    expect.any(String),
    expect.any(AbortSignal),
  );
  choose('portId', 'busy');
  expect(document.querySelector('[name="startAt"]').disabled).toBe(true);
  choose('portId', 'free');
  document.querySelector('[name="startAt"]').click();
  document.querySelector('form[data-api="charging"]').requestSubmit();
  await vi.waitFor(() =>
    expect(charge).toHaveBeenCalledWith('bike-7', 'h1', '80', start, 'free'),
  );
});

it('shows the confirmed charge and counts down to its scheduled start', () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  mobileApi.charging = [
    {
      chargingRequestId: 'c',
      vehicleId: 'bike-7',
      status: 'SCHEDULED',
      hubId: 'h',
      portId: 'p2',
      scheduledStartAt: new Date(Date.now() + 300000).toISOString(),
      scheduledEndAt: new Date(Date.now() + 600000).toISOString(),
    },
  ];
  document.body.innerHTML = '<div id="root"></div>';
  const root = document.querySelector('#root');
  dispose = mountMobileApi(root, '/student/charging-confirmed');
  expect(root.querySelector('.success-card')).not.toBeNull();
  expect(root.textContent).toContain('p2');
  expect(
    root.querySelector('[data-charge-start] [role="timer"]'),
  ).not.toBeNull();
});
it('confirms charging during the check-in window and removes the cancel action', async () => {
  const now = Date.now();
  mobileApi.session = {
    sessionToken: 'test-token',
    expiresAt: new Date(now + 3600000).toISOString(),
    user: { role: 'STUDENT' },
  };
  const booking = {
    chargingRequestId: 'charge-1',
    vehicleId: 'bike-7',
    status: 'SCHEDULED',
    hubId: 'Hub-1',
    portId: 'Port-1',
    scheduledStartAt: new Date(now - 60000).toISOString(),
    scheduledEndAt: new Date(now + 3600000).toISOString(),
    checkInDeadline: new Date(now + 540000).toISOString(),
  };
  mobileApi.charging = [booking];
  const send = vi
    .spyOn(mobileApi, 'send')
    .mockResolvedValue({ ...booking, status: 'CHARGING' });
  document.body.innerHTML = '<div id="root"></div>';
  const root = document.querySelector('#root');
  dispose = mountMobileApi(root, '/student/reservations');
  expect(root.querySelector('[data-cancel-charging]')).not.toBeNull();
  const confirm = root.querySelector('[data-confirm-charging]');
  expect(confirm.disabled).toBe(false);
  confirm.click();
  await vi.waitFor(() =>
    expect(root.querySelector('[data-confirm-charging]')).toBeNull(),
  );
  expect(root.querySelector('[data-cancel-charging]')).toBeNull();
  expect(send).toHaveBeenCalledWith(
    '/charging-requests/charge-1/confirm',
    expect.objectContaining({ method: 'POST' }),
  );
});
it('shows backend ISO expiry and rental times for parking receipts', () => {
  mobileApi.session = { user: { role: 'STUDENT' } };
  mobileApi.parking = [
    {
      parkingReservationId: 'p1',
      spaceId: 's1',
      status: 'ACTIVE',
      startsAt: '2026-11-25T16:00:00Z',
      endsAt: '2026-11-25T17:00:00Z',
      expiresAt: '2026-11-25T16:15:00Z',
    },
  ];
  document.body.innerHTML = '<div id="root"></div>';
  dispose = mountMobileApi(
    document.querySelector('#root'),
    '/student/reservations',
  );
  const receipt = document.querySelector('.panel');
  expect(receipt.textContent).not.toContain('—');
  expect(receipt.textContent).toContain('2026');
  expect(receipt.textContent).toContain('→');
});
