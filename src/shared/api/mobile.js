import { request, ApiError } from './client.js';
import {
  searchPayload,
  validateSearchResponse,
} from '../../features/hub-search-api.js';

const required = (value, field) => {
  if (typeof value !== 'string' || !value.trim())
    throw new Error(`${field}: required`);
  return value.trim();
};
export function rentalWindow(start, end) {
  const from = new Date(start),
    to = new Date(end);
  if (!Number.isFinite(+from) || !Number.isFinite(+to) || to <= from)
    throw new Error('Invalid rental time window');
  return { startsAt: from.toISOString(), endsAt: to.toISOString() };
}
// Session, receipts and idempotency keys deliberately live only in memory.
export class MobileApi {
  constructor(
    send = request,
    authProfile = import.meta.env.VITE_AUTH_PROFILE || 'contract',
  ) {
    this.authProfile = authProfile;
    this.send = send;
    this.clear();
  }
  clear() {
    this.session = null;
    this.hubs = [];
    this.reservations = [];
    this.parking = [];
    this.charging = [];
    this.ride = null;
    this.command = null;
    this.recovery = null;
  }
  get user() {
    return this.session?.user || null;
  }
  async call(path, body, { auth = true, signal, key, withStatus } = {}) {
    const headers = {};
    if (auth) {
      if (!this.session || Date.parse(this.session.expiresAt) <= Date.now()) {
        this.clear();
        throw new ApiError(401, 'Session expired. Please sign in again.');
      }
      headers.Authorization = `Bearer ${this.session.sessionToken}`;
    }
    if (key) headers['Idempotency-Key'] = key;
    try {
      return await this.send(path, {
        method: body === undefined ? 'GET' : 'POST',
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        headers,
        signal,
        withStatus,
      });
    } catch (error) {
      if (error.status === 401 && auth) this.clear();
      throw error;
    }
  }
  async login(email, password, role) {
    const payload = { email, password };
    if (this.authProfile === 'backend') {
      if (!['STUDENT', 'OPERATOR'].includes(role))
        throw new Error('Select an account role');
      payload.role = role;
    }
    const data = await this.call('/auth/login', payload, { auth: false });
    if (
      !data?.sessionToken ||
      data.tokenType !== 'Bearer' ||
      !data.user?.accountId ||
      !Number.isFinite(Date.parse(data.expiresAt)) ||
      Date.parse(data.expiresAt) <= Date.now()
    )
      throw new Error('Invalid login response');
    this.clear();
    this.session = {
      ...data,
      user: { ...data.user, email: data.user.email || email },
    };
    return this.user;
  }
  register(studentId, email, password, fullName) {
    const payload = { studentId, email, password };
    if (this.authProfile === 'backend')
      payload.fullName = required(fullName, 'fullName');
    return this.call('/auth/register', payload, { auth: false });
  }
  async logout() {
    try {
      await this.call('/auth/logout', {});
    } finally {
      this.clear();
    }
  }
  async requestRecovery(email) {
    await this.call(
      '/auth/password/recovery/request',
      { email },
      { auth: false },
    );
    this.recovery = { email };
  }
  async verifyRecovery(code) {
    if (!this.recovery?.email) throw new Error('Request a recovery code first');
    const grant = await this.call(
      '/auth/password/recovery/verify',
      { email: this.recovery.email, code },
      { auth: false },
    );
    if (
      !grant?.recoveryToken ||
      Date.parse(grant.expiresAt) <= Date.now() ||
      !Number.isFinite(Date.parse(grant.expiresAt))
    )
      throw new Error('Invalid recovery grant');
    this.recovery = { ...this.recovery, ...grant };
  }
  async resetPassword(newPassword, confirmPassword) {
    if (newPassword !== confirmPassword)
      throw new Error('Passwords do not match');
    if (
      !this.recovery?.recoveryToken ||
      Date.parse(this.recovery.expiresAt) <= Date.now()
    )
      throw new Error('Recovery grant expired');
    await this.call(
      '/auth/password/recovery/reset',
      {
        recoveryToken: this.recovery.recoveryToken,
        newPassword,
        confirmPassword,
      },
      { auth: false },
    );
    this.recovery = null;
  }
  async changePassword(currentPassword, newPassword, confirmPassword) {
    if (newPassword !== confirmPassword)
      throw new Error('Passwords do not match');
    await this.call('/auth/password/change', {
      currentPassword,
      newPassword,
      confirmPassword,
    });
    this.clear();
  }
  async discoverHubs({ latitude, longitude }, signal) {
    // The search endpoint caps vehicle discovery at 50 km; catalog keeps all Hubs visible.
    const [catalog, nearby] = await Promise.all([
      this.call('/hubs', undefined, { signal }),
      this.call(
        '/search/hubs',
        searchPayload({ latitude, longitude, radiusKm: 50 }),
        { auth: false, signal },
      ),
    ]);
    if (!Array.isArray(catalog?.items)) throw new Error('Invalid Hub catalog');
    const matches = validateSearchResponse(nearby);
    const hubs = catalog.items
      .map((h) => {
        if (typeof h.hubId !== 'string' || typeof h.hubName !== 'string')
          throw new Error('Invalid Hub catalog');
        return {
          ...h,
          distanceKm: null,
          availableVehicleCount: null,
          vehicles: [],
          ...matches.find((m) => m.hubId === h.hubId),
        };
      })
      .sort((a, b) => (a.distanceKm ?? Infinity) - (b.distanceKm ?? Infinity));
    if (!signal?.aborted) this.hubs = hubs;
    return hubs;
  }
  async search(values, signal) {
    const hubs = validateSearchResponse(
      await this.call('/search/hubs', searchPayload(values), {
        auth: false,
        signal,
      }),
    );
    if (!signal?.aborted) this.hubs = hubs;
    return hubs;
  }
  async vehicle(id, signal) {
    const data = await this.call(
      '/vehicles/' + encodeURIComponent(id),
      undefined,
      { auth: false, signal },
    );
    if (data?.vehicleId !== id || typeof data.status !== 'string')
      throw new Error('Invalid vehicle response');
    return data;
  }
  async reserve(vehicleId, start, end) {
    const data = await this.call('/reservations', {
      vehicleId,
      ...rentalWindow(start, end),
    });
    if (!data?.reservationId)
      throw new Error(
        'Invalid reservation response; check with backend before retrying',
      );
    this.reservations.unshift(data);
    return data;
  }
  async cancel(id) {
    await this.call(`/reservations/${encodeURIComponent(id)}/cancel`, {});
    const receipt = this.reservations.find((r) => r.reservationId === id);
    if (receipt) receipt.status = 'CANCELLED';
  }
  async hubSpaces(hubId, signal) {
    const data = await this.call(
      '/hubs/' + encodeURIComponent(required(hubId, 'hubId')) + '/spaces',
      undefined,
      { signal },
    );
    if (
      !Array.isArray(data?.items) ||
      data.items.some(
        (s) =>
          typeof s.spaceId !== 'string' ||
          s.hubId !== hubId ||
          typeof s.status !== 'string' ||
          typeof s.blocked !== 'boolean',
      )
    )
      throw new Error('Invalid parking spaces response');
    return data.items;
  }
  async reserveParking(hubId, spaceId, start, end) {
    const data = await this.call('/parking-reservations', {
      hubId: required(hubId, 'hubId'),
      spaceId: required(spaceId, 'spaceId'),
      ...rentalWindow(start, end),
    });
    if (!data?.parkingReservationId)
      throw new Error('Invalid parking response');
    this.parking.unshift(data);
    return data;
  }
  async charge(vehicleId, hubId, target, start) {
    const requestedStartAt = new Date(start).toISOString(),
      targetBatteryPct = Number(target);
    if (!(targetBatteryPct > 0 && targetBatteryPct <= 100))
      throw new Error('Invalid target battery');
    const data = await this.call('/charging-requests', {
      vehicleId: required(vehicleId, 'vehicleId'),
      hubId: required(hubId, 'hubId'),
      targetBatteryPct,
      requestedStartAt,
    });
    if (!data?.chargingRequestId) throw new Error('Invalid charging response');
    this.charging = [
      data,
      ...this.charging.filter(
        (r) => r.chargingRequestId !== data.chargingRequestId,
      ),
    ];
    return data;
  }
  async pickup(vehicleId, reservationId, pickupCode) {
    const body = { vehicleId: required(vehicleId, 'vehicleId') };
    if (reservationId)
      Object.assign(body, {
        reservationId,
        pickupCode: required(pickupCode, 'pickupCode'),
      });
    return this.mutateCommand('/pickups', body, 'PICKUP');
  }
  async returnRide(rideId, hubId, spaceId) {
    return this.mutateCommand(
      `/rides/${encodeURIComponent(required(rideId, 'rideId'))}/return`,
      {
        hubId: required(hubId, 'hubId'),
        spaceId: required(spaceId, 'spaceId'),
      },
      'RETURN',
    );
  }
  async mutateCommand(path, body, kind) {
    if (this.command && ['PENDING', 'UNKNOWN'].includes(this.command.state))
      throw new Error(
        'A device command is unresolved. Check its status or retry the same command.',
      );
    this.command = {
      path,
      body,
      kind,
      key: crypto.randomUUID(),
      state: 'PENDING',
    };
    return this.sendCommand();
  }
  async sendCommand() {
    const command = this.command;
    try {
      const { data, status } = await this.call(command.path, command.body, {
        key: command.key,
        withStatus: true,
      });
      if (status === 202) {
        if (!data?.operationId) throw new Error('Missing operationId');
        command.operationId = data.operationId;
        command.state = 'PENDING';
      } else {
        if (!data?.rideId) throw new Error('Missing rideId');
        this.finishCommand(command, data);
      }
      return command;
    } catch (error) {
      command.state = error.status && error.status < 500 ? 'FAILED' : 'UNKNOWN';
      throw error;
    }
  }
  finishCommand(command, data) {
    command.state = 'SUCCEEDED';
    command.result = data;
    this.ride = {
      ...this.ride,
      ...data,
      status: command.kind === 'RETURN' ? 'COMPLETED' : 'ACTIVE',
    };
    if (command.kind === 'PICKUP') {
      const r = this.reservations.find(
        (r) => r.reservationId === command.body.reservationId,
      );
      if (r) r.status = 'USED';
    }
  }
  async poll(signal) {
    const c = this.command;
    if (!c?.operationId || c.state !== 'PENDING') return c;
    const data = await this.call(
      '/operations/' + encodeURIComponent(c.operationId),
      undefined,
      { signal },
    );
    if (signal?.aborted) return c;
    if (
      data.operationId !== c.operationId ||
      !['PENDING', 'SUCCEEDED', 'FAILED'].includes(data.status)
    )
      throw new Error('Invalid operation response');
    if (data.status === 'SUCCEEDED') {
      if (!data.result?.rideId) throw new Error('Missing confirmed ride');
      this.finishCommand(c, data.result);
    } else c.state = data.status;
    return c;
  }
}
export const mobileApi = new MobileApi();
