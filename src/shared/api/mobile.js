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
const isPickupCode = (value) =>
  typeof value === 'string' && /^[0-9]{6}$/.test(value);
export function rentalWindow(start, end) {
  const from = new Date(start),
    to = new Date(end);
  if (!Number.isFinite(+from) || !Number.isFinite(+to) || to <= from)
    throw new Error('Invalid rental time window');
  return { startsAt: from.toISOString(), endsAt: to.toISOString() };
}
// Session tokens and idempotency keys deliberately live only in memory.
export class MobileApi {
  constructor(
    send = request,
    authProfile = import.meta.env.VITE_AUTH_PROFILE || 'backend',
  ) {
    this.authProfile = authProfile;
    this.send = send;
    this.clear();
  }
  clear() {
    this.session = null;
    this.hubs = [];
    this.discoveryOrigin = null;
    this.reservations = [];
    this.studentVehicles = [];
    this.parking = [];
    this.charging = [];
    this.ride = null;
    this.journeyLoaded = false;
    this.command = null;
    this.recovery = null;
  }
  saveJourney() {
    if (!this.user?.accountId) return;
    try {
      sessionStorage.setItem(
        'emh-journey:' + this.user.accountId,
        JSON.stringify({
          reservations: this.reservations,
          ride: this.ride,
          at: Date.now(),
        }),
      );
    } catch {
      /* Storage is optional; keep the current session usable. */
    }
  }
  restoreJourney() {
    try {
      const saved = JSON.parse(
        sessionStorage.getItem('emh-journey:' + this.user.accountId),
      );
      if (
        saved &&
        Date.now() - saved.at < 86400000 &&
        Array.isArray(saved.reservations)
      ) {
        this.reservations = saved.reservations;
        this.ride = saved.ride?.rideId ? saved.ride : null;
      }
    } catch {
      /* Ignore invalid local context. Backend validates every mutation. */
    }
  }
  get #pickupCodeStorageKey() {
    const accountId = this.user?.accountId;
    return accountId ? `emh-pickup-code:${accountId}` : null;
  }
  #rememberPickupCode(reservation) {
    const key = this.#pickupCodeStorageKey;
    const { reservationId, pickupCode, status } = reservation;
    const expiresAt = Date.parse(reservation.expiresAt);
    if (
      !key ||
      status !== 'ACTIVE' ||
      !isPickupCode(pickupCode) ||
      !(expiresAt > Date.now())
    )
      return;
    try {
      localStorage.setItem(
        key,
        JSON.stringify({ reservationId, pickupCode, expiresAt }),
      );
    } catch {
      /* The current tab can still use its receipt when storage is unavailable. */
    }
  }
  #storedPickupCode(reservation) {
    const key = this.#pickupCodeStorageKey;
    if (!key) return;
    try {
      const saved = JSON.parse(localStorage.getItem(key));
      if (!saved) return;
      const now = Date.now();
      if (!(saved.expiresAt > now) || !isPickupCode(saved.pickupCode)) {
        localStorage.removeItem(key);
        return;
      }
      if (
        reservation &&
        reservation.reservationId === saved.reservationId &&
        Date.parse(reservation.expiresAt) > now
      )
        return saved.pickupCode;
    } catch {
      /* Ignore unreadable local storage. */
    }
  }
  #forgetPickupCode(reservationId) {
    const key = this.#pickupCodeStorageKey;
    if (!key || !reservationId) return;
    try {
      const saved = JSON.parse(localStorage.getItem(key));
      if (saved?.reservationId === reservationId) localStorage.removeItem(key);
    } catch {
      /* The backend has already closed the reservation. */
    }
  }
  async loadJourney(signal) {
    const session = this.session;
    const data = await this.call('/student/journey', undefined, { signal });
    if (signal?.aborted || this.session !== session) return;
    const { reservation, ride } = data || {};
    if (
      (reservation !== null &&
        (!reservation?.reservationId || reservation.status !== 'ACTIVE')) ||
      (ride !== null && (!ride?.rideId || ride.status !== 'ACTIVE'))
    )
      throw new Error('Invalid active journey response');
    const saved = this.reservations.find(
      (r) => r.reservationId === reservation?.reservationId,
    );
    const storedCode = this.#storedPickupCode(reservation);
    this.reservations = this.reservations.filter((r) => r.status !== 'ACTIVE');
    if (reservation) {
      const pickupCode =
        Date.parse(reservation.expiresAt) > Date.now()
          ? saved?.pickupCode || storedCode
          : undefined;
      const receipt = { ...reservation, ...(pickupCode ? { pickupCode } : {}) };
      this.reservations.unshift(receipt);
      if (!storedCode && saved?.pickupCode) this.#rememberPickupCode(receipt);
    }
    if (Array.isArray(data.parking)) {
      this.parking = [
        ...data.parking,
        ...this.parking.filter(
          (r) =>
            !['ACTIVE', 'CHECKED_IN'].includes(r.status) &&
            !data.parking.some(
              (p) => p.parkingReservationId === r.parkingReservationId,
            ),
        ),
      ];
    }
    this.ride = ride;
    this.studentVehicles = Array.isArray(data.vehicles) ? data.vehicles : [];
    this.journeyLoaded = true;
    this.saveJourney();
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
    if (!['STUDENT', 'OPERATOR'].includes(role))
      throw new Error('Select an account role');
    const payload = { email, password, role };
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
    this.restoreJourney();
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
    this.clear();
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
    if (!signal?.aborted) {
      this.hubs = hubs;
      this.discoveryOrigin = { latitude, longitude };
    }
    return hubs;
  }
  removeListedVehicle(id) {
    for (const hub of this.hubs) {
      const vehicles = hub.vehicles.filter((v) => v.vehicleId !== id);
      if (vehicles.length !== hub.vehicles.length) {
        hub.vehicles = vehicles;
        if (Number.isInteger(hub.availableVehicleCount))
          hub.availableVehicleCount = Math.max(
            0,
            hub.availableVehicleCount - 1,
          );
      }
    }
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
    if (!data.startsAt || Date.parse(data.startsAt) <= Date.now())
      this.removeListedVehicle(vehicleId);
    this.reservations.unshift({
      ...data,
      vehicleId: data.vehicleId || vehicleId,
    });
    this.#rememberPickupCode(data);
    this.saveJourney();
    return data;
  }
  async cancel(id) {
    await this.call(`/reservations/${encodeURIComponent(id)}/cancel`, {});
    const receipt = this.reservations.find((r) => r.reservationId === id);
    if (receipt) {
      receipt.status = 'CANCELLED';
      delete receipt.pickupCode;
    }
    this.#forgetPickupCode(id);
    this.saveJourney();
  }
  async hubCatalog(signal) {
    const data = await this.call('/hubs', undefined, { signal });
    if (!Array.isArray(data?.items)) throw new Error('Invalid Hub catalog');
    return data.items;
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
  async confirmParking(id) {
    await this.call(
      `/parking-reservations/${encodeURIComponent(id)}/confirm`,
      {},
    );
    const receipt = this.parking.find(
      (hold) => hold.parkingReservationId === id,
    );
    if (receipt) receipt.status = 'CHECKED_IN';
    await this.loadJourney();
  }
  async stopCharging(id) {
    await this.call(`/charging-requests/${encodeURIComponent(id)}/stop`, {});
    await this.loadChargingReceipts();
  }
  async cancelParking(id) {
    await this.call(
      '/parking-reservations/' + encodeURIComponent(id) + '/cancel',
      {},
    );
    const receipt = this.parking.find(
      (hold) => hold.parkingReservationId === id,
    );
    if (receipt) receipt.status = 'CANCELLED';
  }
  async releaseParking(id) {
    await this.cancelParking(id);
    await this.loadJourney();
  }
  async reserveParking(vehicleId, hubId, spaceId, start, end) {
    const data = await this.call('/parking-reservations', {
      vehicleId: required(vehicleId, 'vehicleId'),
      hubId: required(hubId, 'hubId'),
      spaceId: required(spaceId, 'spaceId'),
      ...(end
        ? rentalWindow(start, end)
        : { startsAt: new Date(start).toISOString() }),
    });
    if (!data?.parkingReservationId)
      throw new Error('Invalid parking response');
    this.parking.unshift(data);
    return data;
  }
  async chargingOptions(vehicleId, hubId, targetBatteryPct, dayStart, signal) {
    const params = new URLSearchParams({
      vehicleId,
      hubId,
      targetBatteryPct,
      dayStart,
    });
    const data = await this.call(
      `/charging-requests/options?${params}`,
      undefined,
      { signal },
    );
    if (!Array.isArray(data?.ports))
      throw new Error('Invalid charging options response');
    return data;
  }
  async cancelCharging(id) {
    await this.call(`/charging-requests/${encodeURIComponent(id)}/cancel`, {});
    this.charging = this.charging.map((booking) =>
      booking.chargingRequestId === id
        ? { ...booking, status: 'CANCELLED' }
        : booking,
    );
  }
  async loadChargingReceipts(signal) {
    const session = this.session;
    const previous = this.charging;
    const data = await this.call('/charging-requests', undefined, { signal });
    if (!Array.isArray(data))
      throw new Error('Invalid charging receipts response');
    if (
      signal?.aborted ||
      this.session !== session ||
      this.charging !== previous ||
      JSON.stringify(previous) === JSON.stringify(data)
    )
      return false;
    this.charging = data;
    return true;
  }
  async confirmCharging(id) {
    const data = await this.call(
      `/charging-requests/${encodeURIComponent(id)}/confirm`,
      {},
    );
    if (data?.chargingRequestId !== id || data.status !== 'CHARGING')
      throw new Error('Invalid charging confirmation response');
    this.charging = this.charging.map((booking) =>
      booking.chargingRequestId === id ? data : booking,
    );
    return data;
  }
  async charge(vehicleId, hubId, target, start, portId) {
    const requestedStartAt = new Date(start).toISOString(),
      targetBatteryPct = Number(target);
    if (!(targetBatteryPct > 0 && targetBatteryPct <= 100))
      throw new Error('Invalid target battery');
    const data = await this.call('/charging-requests', {
      vehicleId: required(vehicleId, 'vehicleId'),
      hubId: required(hubId, 'hubId'),
      targetBatteryPct,
      requestedStartAt,
      ...(portId ? { portId: required(portId, 'portId') } : {}),
    });
    if (!data?.chargingRequestId) throw new Error('Invalid charging response');
    this.charging = [
      data,
      ...this.charging.filter(
        (booking) => booking.chargingRequestId !== data.chargingRequestId,
      ),
    ];
    return data;
  }
  async pickup(vehicleId, reservationId, pickupCode) {
    const body = { vehicleId: required(vehicleId, 'vehicleId') };
    if (reservationId)
      Object.assign(body, {
        reservationId,
        ...(pickupCode ? { pickupCode } : {}),
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
      ...(command.kind === 'RETURN' ? this.ride : {}),
      ...(command.kind === 'PICKUP'
        ? {
            vehicleId: command.body.vehicleId,
            reservationId: command.body.reservationId,
          }
        : {}),
      ...data,
      status: command.kind === 'RETURN' ? 'COMPLETED' : 'ACTIVE',
    };
    this.studentVehicles = this.studentVehicles.filter(
      (v) => v.ownership !== 'SHARED',
    );
    if (command.kind === 'PICKUP') {
      this.studentVehicles.push({
        vehicleId: this.ride.vehicleId,
        ownership: 'SHARED',
        canBookParking: true,
      });
      const reservationId = command.body.reservationId;
      const r = this.reservations.find(
        (r) => reservationId && r.reservationId === reservationId,
      );
      this.removeListedVehicle(command.body.vehicleId);
      if (r) {
        r.status = 'USED';
        r.rideId = data.rideId;
        delete r.pickupCode;
      }
      this.#forgetPickupCode(reservationId);
    } else if (command.kind === 'RETURN') {
      this.journeyLoaded = false;
      const receipt = this.reservations.find(
        (r) =>
          r.rideId === data.rideId ||
          (this.ride.reservationId &&
            r.reservationId === this.ride.reservationId),
      );
      if (receipt) receipt.status = 'RETURNED';
      else
        this.reservations.unshift({
          rideId: data.rideId,
          vehicleId: this.ride.vehicleId,
          status: 'RETURNED',
        });
    }
    this.saveJourney();
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
