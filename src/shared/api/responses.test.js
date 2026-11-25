import { expect, it, vi, afterEach } from 'vitest';
import { request } from './client.js';
afterEach(() => vi.unstubAllGlobals());
it('preserves structured backend error details', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          error: {
            code: 'VEHICLE_ALREADY_RESERVED',
            message: 'Reserved',
            requestId: 'req',
            details: [{ field: 'vehicleId', issue: 'taken' }],
          },
        }),
        { status: 409 },
      ),
    ),
  );
  await expect(request('/reservations')).rejects.toMatchObject({
    status: 409,
    code: 'VEHICLE_ALREADY_RESERVED',
    requestId: 'req',
    details: [{ field: 'vehicleId', issue: 'taken' }],
  });
});
it('preserves HTTP 202 for asynchronous device commands', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValue(new Response('{"operationId":"op"}', { status: 202 })),
  );
  await expect(request('/pickups', { withStatus: true })).resolves.toEqual({
    status: 202,
    data: { operationId: 'op' },
  });
});
it('accepts empty HTTP 200 mutation responses', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(null, { status: 200 })),
  );
  await expect(
    request('/reservations/r/cancel', { method: 'POST' }),
  ).resolves.toBeUndefined();
});
