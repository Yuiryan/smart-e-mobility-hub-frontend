import { beforeEach, describe, it, expect } from 'vitest';
import { DemoEngine, DEMO_PASSWORD } from './engine.js';
let engine, now, w;
const login = () =>
  engine.login('student@demo.local', DEMO_PASSWORD, 'STUDENT');
beforeEach(() => {
  now = 1800000000000;
  engine = new DemoEngine(() => now);
  w = { start: now, end: now + 7200000 };
  login();
});
function pickup(outcome = 'SUCCESS') {
  engine.reserveVehicle('E-024', w);
  return engine.pickup(engine.snapshot().bookings[0].code, outcome);
}
describe('Demo business boundaries', () => {
  it('rejects incorrect role and anonymous mutations', () => {
    expect(() =>
      engine.login('student@demo.local', DEMO_PASSWORD, 'OPERATOR'),
    ).toThrow('INVALID_CREDENTIALS');
    engine.logout();
    expect(() => engine.reserveVehicle('E-024', w)).toThrow('UNAUTHORIZED');
  });
  it('prevents a second active vehicle booking', () => {
    engine.reserveVehicle('E-024', w);
    expect(() => engine.reserveVehicle('E-052', w)).toThrow('ACTIVE_BOOKING');
  });
  it('rejects stale telemetry and low battery', () => {
    expect(() => engine.reserveVehicle('E-019', w)).toThrow('STALE');
    expect(() => engine.reserveVehicle('E-011', w)).toThrow('UNAVAILABLE');
  });
  it('creates a ride only after ACK and handles repeated polls once', () => {
    const id = pickup();
    expect(engine.snapshot().rides).toHaveLength(0);
    engine.pollCommand(id);
    expect(engine.snapshot().rides).toHaveLength(0);
    now += 1600;
    engine.pollCommand(id);
    engine.pollCommand(id);
    expect(engine.snapshot().rides).toHaveLength(1);
    expect(engine.snapshot().vehicles[0].status).toBe('IN_USE');
  });
  it.each(['FAILED', 'TIMEOUT'])(
    'does not allocate a ride after %s',
    (outcome) => {
      const id = pickup(outcome);
      now += 1600;
      engine.pollCommand(id);
      expect(engine.snapshot().rides).toHaveLength(0);
      expect(engine.snapshot().vehicles[0].status).toBe('RESERVED');
      if (outcome === 'TIMEOUT') {
        expect(() =>
          engine.cancelBooking(engine.snapshot().bookings[0].id),
        ).toThrow('PENDING_COMMAND');
      }
    },
  );
  it('finishes return only after ACK, marking reported issues for maintenance', () => {
    const id = pickup();
    now += 1600;
    engine.pollCommand(id);
    const back = engine.returnVehicle('metro-P1', true, 'SUCCESS');
    expect(engine.snapshot().rides[0].end).toBeUndefined();
    now += 1600;
    engine.pollCommand(back);
    expect(engine.snapshot().rides[0].end).toBe(now);
    expect(engine.snapshot().vehicles[0].status).toBe('MAINTENANCE');
    expect(engine.snapshot().spaces[0].occupied).toBe(true);
  });
  it('expires holds and frees the vehicle', () => {
    engine.reserveVehicle('E-024', w);
    now += 600001;
    expect(engine.snapshot().bookings[0].status).toBe('EXPIRED');
    expect(engine.snapshot().vehicles[0].status).toBe('AVAILABLE');
  });
  it('prevents overlapping parking and allows it after cancellation', () => {
    engine.reserveParking('metro-P1', w);
    expect(() => engine.reserveParking('metro-P1', w)).toThrow('CONFLICT');
    engine.cancelBooking(engine.snapshot().bookings[0].id);
    expect(() => engine.reserveParking('metro-P1', w)).not.toThrow();
  });
  it('checks charging target, time and double booking', () => {
    expect(() => engine.bookCharging('PERSONAL-01', 'metro', 20, w)).toThrow(
      'INVALID_TARGET',
    );
    expect(() =>
      engine.bookCharging('PERSONAL-01', 'metro', 80, {
        start: now,
        end: now + 60000,
      }),
    ).toThrow('SHORT_WINDOW');
    engine.bookCharging('PERSONAL-01', 'metro', 80, w);
    expect(() => engine.bookCharging('PERSONAL-01', 'dorm', 80, w)).toThrow(
      'CONFLICT',
    );
  });
  it('isolates bookings across accounts', () => {
    engine.reserveVehicle('E-024', w);
    const code = engine.snapshot().bookings[0].code;
    engine.register('Another student', 'second@demo.local', 'Password1');
    engine.login('second@demo.local', 'Password1', 'STUDENT');
    expect(engine.snapshot().bookings).toHaveLength(0);
    expect(() => engine.pickup(code, 'SUCCESS')).toThrow('INVALID_CODE');
  });
  it('expires sessions without reviving them through touch', () => {
    now += 30 * 60000;
    engine.touch();
    expect(engine.snapshot().user).toBeNull();
    login();
    expect(engine.snapshot().user).not.toBeNull();
  });
  it('enforces one-time password reset and expiry', () => {
    engine.requestReset('student@demo.local');
    engine.resetPassword('student@demo.local', '123456', 'NewPassword1');
    expect(() =>
      engine.resetPassword('student@demo.local', '123456', 'OtherPass1'),
    ).toThrow('INVALID_CODE');
    expect(() =>
      engine.login('student@demo.local', DEMO_PASSWORD, 'STUDENT'),
    ).toThrow('INVALID_CREDENTIALS');
    engine.login('student@demo.local', 'NewPassword1', 'STUDENT');
    engine.requestReset('student@demo.local');
    now += 300001;
    expect(() =>
      engine.resetPassword('student@demo.local', '123456', 'OtherPass1'),
    ).toThrow('INVALID_CODE');
  });
  it('rejects invalid dates and occupied spaces', () => {
    expect(() =>
      engine.reserveParking('metro-P1', { start: NaN, end: NaN }),
    ).toThrow('INVALID_TIME');
    expect(() => engine.reserveParking('metro-P4', w)).toThrow('UNAVAILABLE');
  });
});

it('supports a second trip and frees the former parking space at pickup ACK', () => {
  let id = pickup();
  now += 1600;
  engine.pollCommand(id);
  id = engine.returnVehicle('metro-P1', false, 'SUCCESS');
  now += 1600;
  engine.pollCommand(id);
  engine.reserveVehicle('E-024', { start: now, end: now + 7200000 });
  id = engine.pickup(engine.snapshot().bookings[0].code, 'SUCCESS');
  expect(engine.snapshot().spaces[0].occupied).toBe(true);
  now += 1600;
  engine.pollCommand(id);
  expect(engine.snapshot().spaces[0].occupied).toBe(false);
  expect(engine.snapshot().rides.filter((r) => !r.end)).toHaveLength(1);
});
it('holds a return space while its device command is unresolved', () => {
  let id = pickup();
  now += 1600;
  engine.pollCommand(id);
  engine.returnVehicle('metro-P1', false, 'TIMEOUT');
  expect(() => engine.reserveParking('metro-P1', w)).toThrow('CONFLICT');
});
