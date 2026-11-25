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
