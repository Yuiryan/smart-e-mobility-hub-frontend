import { beforeEach, expect, it, vi } from 'vitest';
import { OperatorApi, interventionParameters } from './operator.js';
import { MobileApi } from './mobile.js';
import { ApiError } from './client.js';
let api, session, send;
beforeEach(() => {
  send = vi.fn();
  session = new MobileApi(send);
  session.session = {
    sessionToken: 'operator-test-token',
    expiresAt: new Date(Date.now() + 600000).toISOString(),
    user: { accountId: 'op', role: 'OPERATOR' },
  };
  api = new OperatorApi(session, send);
});
it('encodes IDs and sends an operator bearer token', async () => {
  send.mockResolvedValue({});
  await api.resource('VEHICLE', 'a/b');
  expect(send.mock.calls[0][0]).toBe('/operator/vehicles/a%2Fb');
  expect(send.mock.calls[0][1].headers.Authorization).toBe(
    'Bearer operator-test-token',
  );
});
it('does not send an API request for students or expired sessions', async () => {
  session.session.user.role = 'STUDENT';
  await expect(api.network()).rejects.toMatchObject({ status: 403 });
  expect(send).not.toHaveBeenCalled();
  session.session.expiresAt = '2000-01-01';
  await expect(api.network()).rejects.toMatchObject({ status: 401 });
  expect(session.user).toBeNull();
});
it('retains the exact idempotency key and payload after a network failure', async () => {
  send
    .mockRejectedValueOnce(new TypeError('network'))
    .mockResolvedValueOnce({ dispatchOrderId: 'd' });
  const body = {
    sourceHubId: 'a',
    destinationHubId: 'b',
    vehicleIds: ['v'],
    transportMode: 'TRUCK',
    reason: 'Move',
  };
  await expect(api.dispatch(body)).rejects.toThrow('network');
  body.vehicleIds.push('other');
  await expect(api.dispatch(body)).rejects.toThrow('reconciliation');
  await api.retry();
  expect(send.mock.calls[1]).toEqual(send.mock.calls[0]);
  expect(JSON.parse(send.mock.calls[1][1].body).vehicleIds).toEqual(['v']);
  expect(api.pending).toBeNull();
});
it('clears a rejected request and preserves version conflict details', async () => {
  send.mockRejectedValue(
    new ApiError(409, 'changed', { code: 'VERSION_CONFLICT' }),
  );
  await expect(
    api.dispatchAction({ dispatchOrderId: 'd', version: 7 }, 'arrival'),
  ).rejects.toMatchObject({ status: 409, code: 'VERSION_CONFLICT' });
  expect(JSON.parse(send.mock.calls[0][1].body)).toEqual({
    expectedVersion: 7,
  });
  expect(api.pending).toBeNull();
});
it('uses both incident and resource versions and the endpoint-specific note', async () => {
  send.mockResolvedValue({ operationId: 'op', status: 'PENDING' });
  await api.incidentAction(
    { incidentId: 'i', version: 3, resourceVersion: 9 },
    'resolve',
    'Repaired',
  );
  expect(send.mock.calls[0][0]).toBe('/operator/incidents/i/resolve');
  expect(JSON.parse(send.mock.calls[0][1].body)).toEqual({
    expectedVersion: 3,
    expectedResourceVersion: 9,
    resolutionNote: 'Repaired',
  });
});
it('edits charging slots with PUT, UTC times and the proposal revision', async () => {
  send.mockResolvedValue({ slots: [] });
  await api.editSlot(
    { id: 'p', revision: 4 },
    's',
    '2026-11-25T09:00:00+07:00',
    '2026-11-25T10:00:00+07:00',
  );
  expect(send.mock.calls[0][1].method).toBe('PUT');
  expect(JSON.parse(send.mock.calls[0][1].body)).toEqual({
    revision: 4,
    startAt: '2026-11-25T02:00:00.000Z',
    endAt: '2026-11-25T03:00:00.000Z',
  });
});
it('does not blindly retry scheduling APIs without idempotency support', async () => {
  send.mockRejectedValue(new TypeError('network'));
  await expect(api.buildSchedule('h')).rejects.toThrow();
  expect(() => api.retry()).toThrow('idempotency');
  expect(send).toHaveBeenCalledTimes(1);
});
it('clears authentication on 401 without falling back to demo', async () => {
  send.mockRejectedValue(new ApiError(401, 'expired'));
  await expect(api.network()).rejects.toMatchObject({ status: 401 });
  expect(session.user).toBeNull();
});
it('uses active backend IDs for intervention parameters', () => {
  expect(
    interventionParameters(
      { activeChargingRequestId: 'cr', allocationId: 'al' },
      'STOP_CHARGING',
    ),
  ).toEqual({ chargingRequestId: 'cr', allocationId: 'al' });
  expect(
    interventionParameters(
      { activeUsage: { reservationId: 'r' } },
      'CANCEL_RESERVATION',
    ),
  ).toEqual({ reservationId: 'r' });
  expect(() => interventionParameters({}, 'STOP_CHARGING')).toThrow('Missing');
  expect(() => interventionParameters({}, 'SET_BATTERY', '')).toThrow();
});
it('never replays another account’s uncertain operation', async () => {
  send.mockRejectedValue(new TypeError('network'));
  await expect(api.dispatch({})).rejects.toThrow();
  session.session.sessionToken = 'another-token';
  expect(() => api.retry()).toThrow('session');
});

it('does not clear a new session when an old request returns 401', async () => {
  let reject;
  send.mockImplementation(() => new Promise((_, r) => (reject = r)));
  const oldRequest = api.network();
  session.session = { ...session.session, sessionToken: 'new-session' };
  reject(new ApiError(401, 'old session expired'));
  await expect(oldRequest).rejects.toMatchObject({ status: 401 });
  expect(session.session.sessionToken).toBe('new-session');
});
