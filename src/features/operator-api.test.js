import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { mobileApi } from '../shared/api/mobile.js';
import { operatorApi } from '../shared/api/operator.js';
import { mountOperatorApi } from './operator-api.js';
let dispose;
beforeEach(() => {
  document.body.innerHTML = '<div id="test-root"></div>';
  mobileApi.session = {
    sessionToken: crypto.randomUUID(),
    expiresAt: new Date(Date.now() + 600000).toISOString(),
    user: { accountId: 'op', role: 'OPERATOR' },
  };
});
afterEach(() => {
  dispose?.();
  dispose = null;
  vi.useRealTimers();
  vi.restoreAllMocks();
  mobileApi.clear();
});
it('renders backend overview values and escapes Hub names', async () => {
  vi.spyOn(operatorApi, 'network').mockResolvedValue({
    generatedAt: '2026-11-25',
    hubs: [
      {
        hubId: 'h',
        hubName: '<img src=x>',
        freeSpaceCount: 7,
        occupancyPct: 30,
        vehicleCountsByStatus: { AVAILABLE: 2 },
        chargingPortsByStatus: { AVAILABLE: 1 },
        severity: 'NORMAL',
        dataQuality: 'FRESH',
      },
    ],
  });
  dispose = mountOperatorApi(
    document.querySelector('#test-root'),
    '/operator/overview',
  );
  await vi.waitFor(() =>
    expect(document.querySelector('.op-table')).not.toBeNull(),
  );
  expect(document.body.textContent).toContain('<img src=x>');
  expect(document.querySelector('img')).toBeNull();
  expect(document.body.textContent).not.toContain('DEMO');
});
it('does not invent data when the backend rejects a request', async () => {
  vi.spyOn(operatorApi, 'network').mockRejectedValue(
    new Error('Backend unavailable'),
  );
  dispose = mountOperatorApi(
    document.querySelector('#test-root'),
    '/operator/overview',
  );
  await vi.waitFor(() =>
    expect(document.querySelector('#feedback').textContent).toContain(
      'Backend unavailable',
    ),
  );
  expect(document.querySelector('.op-metrics')).toBeNull();
});
it('refreshes charging ports for the selected hub while resources are open', async () => {
  vi.useFakeTimers();
  vi.spyOn(operatorApi, 'hubs').mockResolvedValue({
    items: [{ hubId: 'h', hubName: 'Hub' }],
  });
  vi.spyOn(operatorApi, 'vehicles').mockResolvedValue({ items: [] });
  vi.spyOn(operatorApi, 'spaces').mockResolvedValue({ items: [] });
  const available = { portId: 'p1', status: 'AVAILABLE' };
  const ports = vi
    .spyOn(operatorApi, 'ports')
    .mockResolvedValue({ items: [available] })
    .mockResolvedValueOnce({ items: [available] })
    .mockResolvedValueOnce({
      items: [{ ...available, vehicleId: 'bike_007', status: 'CHARGING' }],
    });
  const root = document.querySelector('#test-root');
  dispose = mountOperatorApi(root, '/operator/resources');
  await vi.advanceTimersByTimeAsync(0);
  expect(root.textContent).toContain('AVAILABLE');

  await vi.advanceTimersByTimeAsync(5000);
  expect(root.textContent).toContain('CHARGING');
  expect(root.textContent).toContain('bike_007');
  expect(ports).toHaveBeenLastCalledWith('h', expect.any(AbortSignal));

  await vi.advanceTimersByTimeAsync(5000);
  expect(root.textContent).toContain('AVAILABLE');
  expect(root.textContent).not.toContain('bike_007');

  dispose();
  dispose = null;
  await vi.advanceTimersByTimeAsync(5000);
  expect(ports).toHaveBeenCalledTimes(3);
});
it('does not overwrite another page with a late read response', async () => {
  let resolve;
  vi.spyOn(operatorApi, 'network').mockImplementation(
    () => new Promise((r) => (resolve = r)),
  );
  dispose = mountOperatorApi(
    document.querySelector('#test-root'),
    '/operator/overview',
  );
  dispose();
  dispose = null;
  document.querySelector('#test-root').textContent = 'Next page';
  resolve({ hubs: [] });
  await Promise.resolve();
  await Promise.resolve();
  expect(document.querySelector('#test-root').textContent).toBe('Next page');
});
it('hides LOCK and UNLOCK from vehicle intervention action choices', async () => {
  vi.spyOn(operatorApi, 'audit').mockResolvedValue({ items: [] });
  vi.spyOn(operatorApi, 'resource').mockResolvedValue({
    vehicleId: 'bike_008',
    status: 'AVAILABLE',
    connectionStatus: 'ONLINE',
    version: 22,
    allowedActions: ['UNLOCK', 'LOCK', 'MARK_MAINTENANCE', 'SET_BATTERY'],
  });
  dispose = mountOperatorApi(
    document.querySelector('#test-root'),
    '/operator/interventions',
  );
  await vi.waitFor(() =>
    expect(
      document.querySelector('[data-live-form="resource"]'),
    ).not.toBeNull(),
  );
  const form = document.querySelector('[data-live-form="resource"]');
  form.querySelector('[name="resourceId"]').value = 'bike_008';
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  await vi.waitFor(() =>
    expect(
      document.querySelector('[data-live-form="intervene"]'),
    ).not.toBeNull(),
  );
  const actions = [...document.querySelectorAll('[name="action"] option')].map(
    (o) => o.value,
  );
  expect(actions).toEqual(['MARK_MAINTENANCE', 'SET_BATTERY']);
});

it('sends the selected arrival hub and count and renders the capacity outcome', async () => {
  vi.spyOn(operatorApi, 'hubs').mockResolvedValue({
    items: [
      { hubId: 'a', hubName: 'Hub A' },
      { hubId: 'b', hubName: 'Hub B' },
    ],
  });
  const simulate = vi.spyOn(operatorApi, 'simulate').mockResolvedValue({
    simulationId: 'scenario',
    status: 'COMPLETED',
    projectedOverloadHubs: [],
    failedBookingsCount: 0,
    failedReturnsCount: 2,
    additionalArrivalsResult: {
      hubId: 'b',
      requested: 7,
      accepted: 5,
      rejected: 2,
    },
    recommendedDispatch: [],
    missingData: [],
  });
  const root = document.querySelector('#test-root');
  dispose = mountOperatorApi(root, '/operator/simulation');
  await vi.waitFor(() =>
    expect(root.querySelector('[name=arrivalHubId]')).not.toBeNull(),
  );
  const form = root.querySelector('form');
  form.elements.arrivalHubId.value = 'b';
  form.elements.vehicleCount.value = '7';
  form.requestSubmit();
  await vi.waitFor(() =>
    expect(simulate).toHaveBeenCalledWith(
      expect.objectContaining({
        parameters: expect.objectContaining({
          additionalArrivals: { hubId: 'b', vehicleCount: 7 },
        }),
      }),
    ),
  );
  await vi.waitFor(() =>
    expect(root.querySelector('h3')?.textContent).toBe('Hub B'),
  );
});

it('only offers charging hubs with both free parking and an available port', async () => {
  const hubs = [
    ['full', 0, 1],
    ['busy', 1, 0],
    ['ready', 2, 1],
  ].map(([hubId, freeSpaceCount, ports]) => ({
    hubId,
    hubName: hubId,
    freeSpaceCount,
    chargingPortsByStatus: { AVAILABLE: ports },
  }));
  vi.spyOn(operatorApi, 'network').mockResolvedValue({ hubs });
  vi.spyOn(operatorApi, 'hubs').mockResolvedValue({ items: hubs });
  const root = document.querySelector('#test-root');
  dispose = mountOperatorApi(root, '/operator/charging');
  await vi.waitFor(() =>
    expect(root.querySelector('select[name=hubId]')).not.toBeNull(),
  );
  expect(
    [...root.querySelectorAll('select[name=hubId] option')].map(
      (option) => option.value,
    ),
  ).toEqual(['ready']);
});
