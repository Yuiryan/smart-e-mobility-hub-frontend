import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { MobileApi } from './mobile.js';
import { ApiError } from './client.js';
let api, send;
const session = () => ({
  sessionToken: 'test-token',
  tokenType: 'Bearer',
  expiresAt: new Date(Date.now() + 600000).toISOString(),
  user: { accountId: 'a', role: 'STUDENT' },
});
beforeEach(() => {
  send = vi.fn();
  api = new MobileApi(send, 'contract');
  api.session = session();
});
afterEach(() => vi.restoreAllMocks());
it('keeps one charging receipt when the same booking is confirmed twice', async () => {
  const receipt = { chargingRequestId: 'charge-1', status: 'SCHEDULED' };
  send.mockResolvedValue(receipt);
  await api.charge('my-bike', 'h', 80, '2026-12-01T10:00Z');
  await api.charge('my-bike', 'h', 80, '2026-12-01T10:00Z');
  expect(api.charging).toEqual([receipt]);
});
it('sends official login and registration payloads and keeps the token out of storage', async () => {
  const before = { ...localStorage };
  send.mockResolvedValueOnce(session());
  await api.login('me@example.com', 'secret');
  expect(JSON.parse(send.mock.calls[0][1].body)).toEqual({
    email: 'me@example.com',
    password: 'secret',
  });
  send.mockResolvedValueOnce({ accountId: 'b' });
  await api.register('123', 'me@example.com', 'secret');
  expect(JSON.parse(send.mock.calls[1][1].body)).toEqual({
    studentId: '123',
    email: 'me@example.com',
    password: 'secret',
  });
  expect({ ...localStorage }).toEqual(before);
});
it('encodes resource IDs and sends bearer tokens only for authenticated requests', async () => {
  send.mockResolvedValueOnce({ vehicleId: 'bike/a', status: 'AVAILABLE' });
  await api.vehicle('bike/a');
  expect(send.mock.calls[0][0]).toBe('/vehicles/bike%2Fa');
  expect(send.mock.calls[0][1].headers.Authorization).toBeUndefined();
  send.mockResolvedValueOnce({ reservationId: 'r' });
  await api.reserve(
    'bike/a',
    '2026-12-01T10:00:00+07:00',
    '2026-12-01T11:00:00+07:00',
  );
  expect(send.mock.calls[1][1].headers.Authorization).toBe('Bearer test-token');
  expect(JSON.parse(send.mock.calls[1][1].body)).toEqual({
    vehicleId: 'bike/a',
    startsAt: '2026-12-01T03:00:00.000Z',
    endsAt: '2026-12-01T04:00:00.000Z',
  });
});
it('rejects invalid windows without making a request', async () => {
  await expect(api.reserve('v', 'bad', 'bad')).rejects.toThrow();
  expect(send).not.toHaveBeenCalled();
});
it('does not create a receipt after a booking conflict', async () => {
  send.mockRejectedValue(new ApiError(409, 'Vehicle reserved'));
  await expect(
    api.reserve('v', '2026-12-01T10:00Z', '2026-12-01T11:00Z'),
  ).rejects.toMatchObject({ status: 409 });
  expect(api.reservations).toEqual([]);
});
it('supports cancellation with an empty HTTP 200 response', async () => {
  api.reservations = [{ reservationId: 'r', status: 'ACTIVE' }];
  send.mockResolvedValue(undefined);
  await api.cancel('r');
  expect(api.reservations[0].status).toBe('CANCELLED');
});
it('does not start a ride for 202 and completes it only after a matching operation succeeds', async () => {
  send.mockResolvedValueOnce({
    status: 202,
    data: { operationId: 'op', status: 'PENDING' },
  });
  await api.pickup('v', 'r', 'code');
  expect(api.ride).toBeNull();
  expect(send.mock.calls[0][1].headers['Idempotency-Key']).toMatch(
    /^[a-f0-9-]{36}$/,
  );
  send.mockResolvedValueOnce({ operationId: 'op', status: 'PENDING' });
  await api.poll();
  expect(api.ride).toBeNull();
  send.mockResolvedValueOnce({
    operationId: 'op',
    status: 'SUCCEEDED',
    result: { rideId: 'ride', vehicleId: 'v' },
  });
  await api.poll();
  expect(api.ride).toMatchObject({ rideId: 'ride', status: 'ACTIVE' });
});
it('keeps the same key and payload after a timeout and prevents a new command', async () => {
  send.mockRejectedValueOnce(new TypeError('Network failed'));
  await expect(api.pickup('v')).rejects.toThrow();
  const first = send.mock.calls[0];
  expect(api.command.state).toBe('UNKNOWN');
  await expect(api.pickup('other')).rejects.toThrow('unresolved');
  send.mockResolvedValueOnce({
    status: 201,
    data: { rideId: 'ride', vehicleId: 'v' },
  });
  await api.sendCommand();
  expect(send.mock.calls[1][1].headers['Idempotency-Key']).toBe(
    first[1].headers['Idempotency-Key'],
  );
  expect(send.mock.calls[1][1].body).toBe(first[1].body);
});
it('leaves the ride active until the return is confirmed', async () => {
  api.ride = { rideId: 'ride', status: 'ACTIVE' };
  send.mockRejectedValueOnce(new ApiError(503, 'Waiting for lock'));
  await expect(api.returnRide('ride', 'h', 's')).rejects.toThrow();
  expect(api.ride.status).toBe('ACTIVE');
  send.mockResolvedValueOnce({
    status: 200,
    data: { rideId: 'ride', status: 'COMPLETED' },
  });
  await api.sendCommand();
  expect(api.ride.status).toBe('COMPLETED');
});
it('maps parking and charging payloads exactly to the contract', async () => {
  send.mockResolvedValueOnce({ parkingReservationId: 'p' });
  await api.reserveParking('h', 's', '2026-12-01T10:00Z', '2026-12-01T11:00Z');
  expect(JSON.parse(send.mock.calls[0][1].body)).toEqual({
    hubId: 'h',
    spaceId: 's',
    startsAt: '2026-12-01T10:00:00.000Z',
    endsAt: '2026-12-01T11:00:00.000Z',
  });
  send.mockResolvedValueOnce({ chargingRequestId: 'c' });
  await api.charge('my-bike', 'h', 80, '2026-12-01T10:00Z');
  expect(JSON.parse(send.mock.calls[1][1].body)).toEqual({
    vehicleId: 'my-bike',
    hubId: 'h',
    targetBatteryPct: 80,
    requestedStartAt: '2026-12-01T10:00:00.000Z',
  });
});
it('clears session and private receipts on unauthorized response', async () => {
  api.reservations = [{ reservationId: 'private' }];
  send.mockRejectedValue(new ApiError(401, 'Expired'));
  await expect(api.cancel('private')).rejects.toThrow();
  expect(api.user).toBeNull();
  expect(api.reservations).toEqual([]);
});
it('rejects a mismatched operation result without confirming a ride', async () => {
  api.command = { operationId: 'op', state: 'PENDING' };
  send.mockResolvedValue({
    operationId: 'wrong',
    status: 'SUCCEEDED',
    result: { rideId: 'ride' },
  });
  await expect(api.poll()).rejects.toThrow();
  expect(api.ride).toBeNull();
});

it('uses the backend recovery grant only in memory and validates confirmation', async () => {
  send
    .mockResolvedValueOnce(undefined)
    .mockResolvedValueOnce({
      recoveryToken: 'grant',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    })
    .mockResolvedValueOnce(undefined);
  const before = { ...localStorage };
  await api.requestRecovery('user@example.com');
  await api.verifyRecovery('123456');
  await expect(api.resetPassword('new-password', 'different')).rejects.toThrow(
    'match',
  );
  await api.resetPassword('new-password', 'new-password');
  expect(send.mock.calls[1][0]).toBe('/auth/password/recovery/verify');
  expect(JSON.parse(send.mock.calls[2][1].body)).toEqual({
    recoveryToken: 'grant',
    newPassword: 'new-password',
    confirmPassword: 'new-password',
  });
  expect(api.recovery).toBeNull();
  expect({ ...localStorage }).toEqual(before);
});

it('sends the running backend DTO fields when the backend profile is selected', async () => {
  const backend = new MobileApi(send, 'backend');
  send
    .mockResolvedValueOnce(session())
    .mockResolvedValueOnce({ accountId: 'new' });
  await backend.login('user@example.com', 'secret', 'STUDENT');
  expect(JSON.parse(send.mock.calls[0][1].body)).toEqual({
    email: 'user@example.com',
    password: 'secret',
    role: 'STUDENT',
  });
  await backend.register('123', 'user@example.com', 'secret', 'Full Name');
  expect(JSON.parse(send.mock.calls[1][1].body)).toEqual({
    studentId: '123',
    email: 'user@example.com',
    password: 'secret',
    fullName: 'Full Name',
  });
});
it('validates backend-only required fields before sending', async () => {
  const backend = new MobileApi(send, 'backend');
  await expect(backend.login('user@example.com', 'secret')).rejects.toThrow(
    'role',
  );
  expect(() =>
    backend.register('123', 'user@example.com', 'secret', ''),
  ).toThrow('fullName');
  expect(send).not.toHaveBeenCalled();
});
it('keeps all catalog Hubs and sorts known distances without inventing remote availability', async () => {
  send
    .mockResolvedValueOnce({
      items: [
        { hubId: 'far', hubName: 'Far Hub' },
        { hubId: 'near', hubName: 'Near Hub', freeSpaceCount: 4 },
      ],
    })
    .mockResolvedValueOnce({
      hubs: [
        {
          hubId: 'near',
          hubName: 'Near Hub',
          distanceKm: 1,
          availableVehicleCount: 0,
          telemetryStatus: 'FRESH',
          vehicles: [],
        },
      ],
    });
  await api.discoverHubs({ latitude: 10, longitude: 106 });
  expect(api.hubs.map((h) => h.hubId)).toEqual(['near', 'far']);
  expect(api.hubs[1]).toMatchObject({
    distanceKm: null,
    availableVehicleCount: null,
    vehicles: [],
  });
  expect(send.mock.calls[0][0]).toBe('/hubs');
  expect(send.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token');
  expect(JSON.parse(send.mock.calls[1][1].body)).toEqual({
    origin: { latitude: 10, longitude: 106 },
    destination: null,
    radiusKm: 50,
  });
});
it('loads real parking spaces for the selected Hub and rejects mismatched results', async () => {
  send.mockResolvedValueOnce({
    items: [
      { spaceId: 'A-01', hubId: 'hub/a', status: 'AVAILABLE', blocked: false },
    ],
  });
  expect(await api.hubSpaces('hub/a')).toHaveLength(1);
  expect(send.mock.calls[0][0]).toBe('/hubs/hub%2Fa/spaces');
  expect(send.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token');
  send.mockResolvedValueOnce({
    items: [
      { spaceId: 'B-01', hubId: 'other', status: 'AVAILABLE', blocked: false },
    ],
  });
  await expect(api.hubSpaces('hub/a')).rejects.toThrow(
    'Invalid parking spaces',
  );
});
