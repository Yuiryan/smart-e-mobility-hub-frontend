import { overlaps } from '../domain/time.js';
import { DomainError } from '../domain/types.js';
const fail = (code) => {
  throw new DomainError(code);
};
export const DEMO_PASSWORD = 'Demo123!';
export const DEMO_RESET_CODE = '123456';
export function validWindow(w, now) {
  if (
    !Number.isFinite(w.start) ||
    !Number.isFinite(w.end) ||
    w.start < now - 60000 ||
    w.end <= w.start
  )
    fail('INVALID_TIME');
}
export class DemoEngine {
  data;
  accounts;
  resets = new Map();
  serial = 100;
  lastActivity = 0;
  now;
  constructor(now = () => Date.now()) {
    this.now = now;
    this.accounts = [
      {
        id: 'student-demo',
        name: 'Sinh viên Demo',
        email: 'student@demo.local',
        password: DEMO_PASSWORD,
        role: 'STUDENT',
      },
      {
        id: 'operator-demo',
        name: 'Operator Demo',
        email: 'operator@demo.local',
        password: DEMO_PASSWORD,
        role: 'OPERATOR',
      },
    ];
    this.data = {
      user: null,
      hubs: [
        {
          id: 'metro',
          name: 'Metro · ĐHQG',
          address: 'Khu vực ga Metro — dữ liệu mẫu',
          lat: 10.879,
          lng: 106.802,
          ports: 2,
        },
        {
          id: 'dorm',
          name: 'KTX B',
          address: 'Ký túc xá khu B — dữ liệu mẫu',
          lat: 10.881,
          lng: 106.779,
          ports: 2,
        },
        {
          id: 'campus',
          name: 'Bách Khoa',
          address: 'Cơ sở Dĩ An — dữ liệu mẫu',
          lat: 10.88,
          lng: 106.806,
          ports: 1,
        },
      ],
      vehicles: [
        {
          id: 'E-024',
          hubId: 'metro',
          name: 'Electric bicycle E-024',
          type: 'BIKE',
          battery: 84,
          status: 'AVAILABLE',
          observedAt: this.now(),
        },
        {
          id: 'E-052',
          hubId: 'metro',
          name: 'Electric bicycle E-052',
          type: 'BIKE',
          battery: 67,
          status: 'AVAILABLE',
          observedAt: this.now(),
        },
        {
          id: 'S-018',
          hubId: 'dorm',
          name: 'Electric scooter S-018',
          type: 'SCOOTER',
          battery: 76,
          status: 'AVAILABLE',
          observedAt: this.now(),
        },
        {
          id: 'E-011',
          hubId: 'campus',
          name: 'Electric bicycle E-011',
          type: 'BIKE',
          battery: 16,
          status: 'LOW_BATTERY',
          observedAt: this.now(),
        },
        {
          id: 'E-019',
          hubId: 'campus',
          name: 'Electric bicycle E-019',
          type: 'BIKE',
          battery: 60,
          status: 'AVAILABLE',
          observedAt: this.now() - 300000,
        },
        {
          id: 'PERSONAL-01',
          hubId: 'dorm',
          name: 'Personal electric scooter',
          type: 'SCOOTER',
          battery: 35,
          status: 'AVAILABLE',
          observedAt: this.now(),
          personal: true,
        },
      ],
      spaces: ['metro', 'dorm', 'campus'].flatMap((h) =>
        Array.from(
          {
            length: 4,
          },
          (_, i) => ({
            id: `${h}-P${i + 1}`,
            hubId: h,
            occupied: i === 3,
            type: i < 2 ? 'BIKE' : 'SCOOTER',
          }),
        ),
      ),
      bookings: [],
      rides: [],
      commands: [],
      notices: [],
    };
  }
  id(prefix) {
    return `${prefix}-${++this.serial}`;
  }
  notify(code) {
    this.data.notices.unshift({
      id: this.id('N'),
      userId: this.data.user.id,
      code,
      createdAt: this.now(),
    });
  }
  expire() {
    for (const b of this.data.bookings)
      if (
        b.status === 'ACTIVE' &&
        b.expiresAt <= this.now() &&
        !this.data.commands.some(
          (c) =>
            c.bookingId === b.id &&
            (c.state === 'PENDING' || c.state === 'TIMEOUT'),
        )
      ) {
        b.status = 'EXPIRED';
        if (b.kind === 'VEHICLE') {
          const v = this.data.vehicles.find((x) => x.id === b.resourceId);
          if (v?.status === 'RESERVED') v.status = 'AVAILABLE';
        }
      }
    if (this.data.user && this.now() - this.lastActivity >= 30 * 60000)
      this.data.user = null;
  }
  touch() {
    this.expire();
    if (this.data.user) this.lastActivity = this.now();
  }
  snapshot() {
    this.expire();
    for (const v of this.data.vehicles)
      if (v.id !== 'E-019') v.observedAt = this.now();
    const d = structuredClone(this.data);
    const id = d.user?.id;
    d.bookings = d.bookings.filter((x) => x.userId === id);
    d.rides = d.rides.filter((x) => x.userId === id);
    d.commands = d.commands.filter((x) => x.userId === id);
    d.notices = d.notices.filter((x) => x.userId === id);
    return d;
  }
  student() {
    this.expire();
    if (!this.data.user) return fail('UNAUTHORIZED');
    if (this.data.user.role !== 'STUDENT') return fail('FORBIDDEN');
    this.touch();
    return this.data.user;
  }
  login(email, password, role) {
    const a = this.accounts.find(
      (x) =>
        x.email === email.trim().toLowerCase() &&
        x.password === password &&
        x.role === role,
    );
    if (!a) return fail('INVALID_CREDENTIALS');
    const { password: _password, ...user } = a;
    this.data.user = user;
    this.lastActivity = this.now();
  }
  register(name, email, password) {
    email = email.trim().toLowerCase();
    if (
      !name.trim() ||
      !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) ||
      password.length < 8
    )
      return fail('INVALID_ACCOUNT');
    if (this.accounts.some((x) => x.email === email))
      return fail('EMAIL_EXISTS');
    this.accounts.push({
      id: this.id('U'),
      name: name.trim(),
      email,
      password,
      role: 'STUDENT',
    });
  }
  requestReset(email) {
    this.resets.set(email.trim().toLowerCase(), this.now() + 300000);
  }
  resetPassword(email, code, password) {
    email = email.trim().toLowerCase();
    if (code !== DEMO_RESET_CODE || (this.resets.get(email) ?? 0) <= this.now())
      return fail('INVALID_CODE');
    if (password.length < 8) return fail('INVALID_ACCOUNT');
    const a = this.accounts.find((x) => x.email === email);
    if (!a) return fail('INVALID_CODE');
    a.password = password;
    this.resets.delete(email);
    this.data.user = null;
  }
  changePassword(current, next) {
    if (!this.data.user) return fail('UNAUTHORIZED');
    const a = this.accounts.find((x) => x.id === this.data.user.id);
    if (a.password !== current) return fail('INVALID_CREDENTIALS');
    if (next.length < 8) return fail('INVALID_ACCOUNT');
    a.password = next;
  }
  logout() {
    this.data.user = null;
  }
  booking(kind, resourceId, hubId, w) {
    const u = this.student();
    validWindow(w, this.now());
    if (
      this.data.bookings.some(
        (b) =>
          b.status === 'ACTIVE' &&
          b.kind === kind &&
          b.resourceId === resourceId &&
          overlaps(b, w),
      )
    )
      return fail('CONFLICT');
    const b = {
      id: this.id('B'),
      userId: u.id,
      kind,
      resourceId,
      hubId,
      ...w,
      expiresAt:
        kind === 'VEHICLE' ? Math.min(w.end, this.now() + 10 * 60000) : w.end,
      status: 'ACTIVE',
      code: this.id('EMH'),
    };
    this.data.bookings.unshift(b);
    this.notify('BOOKED');
    return b;
  }
  reserveVehicle(id, w) {
    const u = this.student();
    if (
      this.data.rides.some((r) => r.userId === u.id && !r.end) ||
      this.data.bookings.some(
        (b) =>
          b.userId === u.id && b.kind === 'VEHICLE' && b.status === 'ACTIVE',
      )
    )
      return fail('ACTIVE_BOOKING');
    const v = this.data.vehicles.find((v) => v.id === id);
    if (!v || v.personal) return fail('NOT_FOUND');
    if (v.status !== 'AVAILABLE' || v.battery < 20) return fail('UNAVAILABLE');
    if (this.now() - v.observedAt > 120000) return fail('STALE');
    this.booking('VEHICLE', id, v.hubId, w);
    v.status = 'RESERVED';
  }
  reserveParking(id, w) {
    this.student();
    const s = this.data.spaces.find((s) => s.id === id);
    if (!s) return fail('NOT_FOUND');
    if (s.occupied) return fail('UNAVAILABLE');
    if (
      this.data.commands.some(
        (c) => c.spaceId === id && ['PENDING', 'TIMEOUT'].includes(c.state),
      )
    )
      return fail('CONFLICT');
    this.booking('PARKING', id, s.hubId, w);
  }
  bookCharging(vehicleId, hubId, target, w) {
    this.student();
    validWindow(w, this.now());
    const v = this.data.vehicles.find((v) => v.id === vehicleId && v.personal);
    const h = this.data.hubs.find((h) => h.id === hubId);
    if (!v || !h) return fail('NOT_FOUND');
    if (!Number.isFinite(target) || target <= v.battery || target > 100)
      return fail('INVALID_TARGET');
    if (
      this.data.bookings.some(
        (b) =>
          b.kind === 'CHARGING' &&
          b.vehicleId === vehicleId &&
          b.status === 'ACTIVE' &&
          overlaps(b, w),
      )
    )
      return fail('CONFLICT');
    const minutes = Math.ceil((target - v.battery) * 1.5);
    if (w.end - w.start < minutes * 60000) return fail('SHORT_WINDOW');
    const port = Array.from(
      {
        length: h.ports,
      },
      (_, i) => `${hubId}-C${i + 1}`,
    ).find(
      (id) =>
        !this.data.bookings.some(
          (b) => b.resourceId === id && b.status === 'ACTIVE' && overlaps(b, w),
        ),
    );
    if (!port) return fail('CONFLICT');
    Object.assign(this.booking('CHARGING', port, hubId, w), {
      vehicleId,
      target,
    });
  }
  cancelBooking(id) {
    const u = this.student();
    const b = this.data.bookings.find((x) => x.id === id && x.userId === u.id);
    if (!b) return fail('NOT_FOUND');
    if (
      this.data.commands.some(
        (c) =>
          c.bookingId === id &&
          (c.state === 'PENDING' || c.state === 'TIMEOUT'),
      )
    )
      return fail('PENDING_COMMAND');
    if (b.status !== 'ACTIVE') return fail('UNAVAILABLE');
    b.status = 'CANCELLED';
    if (b.kind === 'VEHICLE')
      this.data.vehicles.find((v) => v.id === b.resourceId).status =
        'AVAILABLE';
    this.notify('CANCELLED');
  }
  pickup(code, outcome) {
    const u = this.student();
    const b = this.data.bookings.find(
      (b) =>
        b.code === code.trim() &&
        b.userId === u.id &&
        b.kind === 'VEHICLE' &&
        b.status === 'ACTIVE',
    );
    if (!b) return fail('INVALID_CODE');
    if (b.start > this.now() + 60000) return fail('TOO_EARLY');
    if (this.data.rides.some((r) => r.userId === u.id && !r.end))
      return fail('ACTIVE_BOOKING');
    if (
      this.data.commands.some(
        (c) =>
          c.userId === u.id && (c.state === 'PENDING' || c.state === 'TIMEOUT'),
      )
    )
      return fail('PENDING_COMMAND');
    const id = this.id('CMD');
    this.data.commands.unshift({
      id,
      userId: u.id,
      kind: 'PICKUP',
      vehicleId: b.resourceId,
      bookingId: b.id,
      state: 'PENDING',
      outcome,
      readyAt: this.now() + 1500,
    });
    return id;
  }
  returnVehicle(spaceId, issue, outcome) {
    const u = this.student();
    const r = this.data.rides.find((r) => r.userId === u.id && !r.end);
    if (!r) return fail('NO_RIDE');
    if (
      this.data.commands.some(
        (c) =>
          c.userId === u.id && (c.state === 'PENDING' || c.state === 'TIMEOUT'),
      )
    )
      return fail('PENDING_COMMAND');
    const s = this.data.spaces.find((s) => s.id === spaceId);
    const v = this.data.vehicles.find((v) => v.id === r.vehicleId);
    if (!s || s.occupied || s.type !== v.type) return fail('UNAVAILABLE');
    if (
      this.data.bookings.some(
        (b) =>
          b.kind === 'PARKING' &&
          b.resourceId === spaceId &&
          b.status === 'ACTIVE' &&
          b.userId !== u.id &&
          b.start <= this.now() &&
          b.end > this.now(),
      )
    )
      return fail('CONFLICT');
    const id = this.id('CMD');
    this.data.commands.unshift({
      id,
      userId: u.id,
      kind: 'RETURN',
      rideId: r.id,
      vehicleId: v.id,
      spaceId,
      issue,
      state: 'PENDING',
      outcome,
      readyAt: this.now() + 1500,
    });
    return id;
  }
  pollCommand(id) {
    const u = this.student();
    const c = this.data.commands.find((c) => c.id === id && c.userId === u.id);
    if (!c) return fail('NOT_FOUND');
    if (c.state !== 'PENDING' || this.now() < c.readyAt) return;
    if (c.outcome !== 'SUCCESS') {
      c.state = c.outcome;
      this.notify(c.state);
      return;
    }
    const v = this.data.vehicles.find((v) => v.id === c.vehicleId);
    if (c.kind === 'PICKUP') {
      const b = this.data.bookings.find((b) => b.id === c.bookingId);
      b.status = 'COMPLETED';
      v.status = 'IN_USE';
      for (const space of this.data.spaces)
        if (space.vehicleId === v.id) {
          space.occupied = false;
          delete space.vehicleId;
        }
      this.data.rides.unshift({
        id: this.id('R'),
        userId: u.id,
        vehicleId: v.id,
        start: this.now(),
      });
    } else {
      const s = this.data.spaces.find((s) => s.id === c.spaceId);
      if (s.occupied) {
        c.state = 'FAILED';
        this.notify('FAILED');
        return;
      }
      const r = this.data.rides.find((r) => r.userId === u.id && !r.end);
      if (!r) return fail('NO_RIDE');
      r.end = this.now();
      r.hubId = s.hubId;
      s.occupied = true;
      s.vehicleId = v.id;
      v.hubId = s.hubId;
      v.status = c.issue
        ? 'MAINTENANCE'
        : v.battery < 20
          ? 'LOW_BATTERY'
          : 'AVAILABLE';
      this.data.bookings
        .filter(
          (b) =>
            b.kind === 'PARKING' &&
            b.resourceId === s.id &&
            b.userId === u.id &&
            b.status === 'ACTIVE' &&
            b.start <= this.now(),
        )
        .forEach((b) => (b.status = 'COMPLETED'));
    }
    c.state = 'SUCCESS';
    this.notify(c.kind === 'PICKUP' ? 'RIDE_STARTED' : 'RIDE_COMPLETED');
  }
}
