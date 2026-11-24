import { beforeEach, describe, expect, it } from 'vitest';
import { DemoEngine, DEMO_PASSWORD } from './engine.js';
import { OperatorEngine } from './operator-engine.js';
let engine, ops, now;
const login = (role = 'OPERATOR') =>
  engine.login(
    role === 'OPERATOR' ? 'operator@demo.local' : 'student@demo.local',
    DEMO_PASSWORD,
    role,
  );
const ack = () => {
  now += 1600;
  ops.poll();
};
beforeEach(() => {
  now = 1800000000000;
  engine = new DemoEngine(() => now);
  ops = new OperatorEngine(engine);
  login();
});
describe('Operator access and monitoring', () => {
  it('rejects student and expired sessions', () => {
    login('STUDENT');
    expect(() => ops.snapshot()).toThrow('FORBIDDEN');
    login();
    now += 1800001;
    expect(() => ops.dispatch('metro', 'dorm', ['E-024'])).toThrow(
      'UNAUTHORIZED',
    );
  });
  it('returns detached snapshots and flags stale data separately', () => {
    const d = ops.snapshot();
    d.vehicles[0].status = 'CHANGED';
    expect(engine.data.vehicles[0].status).toBe('AVAILABLE');
    expect(d.metrics.find((h) => h.id === 'campus').stale).toBe(true);
    engine.data.spaces
      .filter((s) => s.hubId === 'metro')
      .forEach((s) => (s.occupied = true));
    expect(ops.metrics()[0].severity).toBe('CRITICAL');
  });
});
describe('Interventions and incidents', () => {
  it('does not lock before ACK, applies once and records audit', () => {
    ops.command('E-024', 'LOCK', 'Inspection');
    expect(ops.resource('E-024').status).toBe('AVAILABLE');
    ack();
    ops.poll();
    expect(ops.resource('E-024').status).toBe('LOCKED');
    expect(ops.audit.filter((a) => a.result === 'SUCCESS')).toHaveLength(1);
  });
  it.each(['FAILED', 'TIMEOUT'])(
    'leaves state unchanged after %s',
    (outcome) => {
      const id = ops.command('E-024', 'LOCK', 'Inspection', outcome);
      ack();
      expect(ops.resource('E-024').status).toBe('AVAILABLE');
      if (outcome === 'TIMEOUT') {
        expect(() => ops.command('E-024', 'LOCK', 'Retry')).toThrow(
          'PENDING_COMMAND',
        );
        ops.reconcile(id, 'SUCCESS');
        expect(ops.resource('E-024').status).toBe('LOCKED');
        expect(() => ops.reconcile(id, 'SUCCESS')).toThrow('UNAVAILABLE');
      }
    },
  );
  it('rejects moving and reserved vehicles and requires a reason', () => {
    ops.resource('E-024').status = 'IN_USE';
    expect(() => ops.command('E-024', 'LOCK', 'Reason')).toThrow('UNAVAILABLE');
    expect(() => ops.command('E-052', 'LOCK', ' ')).toThrow();
    login('STUDENT');
    engine.reserveVehicle('E-052', { start: now, end: now + 600000 });
    login();
    expect(() => ops.command('E-052', 'LOCK', 'Reason')).toThrow(
      'ACTIVE_BOOKING',
    );
  });
  it('blocks student reservation while an operator command is pending', () => {
    ops.command('E-024', 'LOCK', 'Reason');
    login('STUDENT');
    expect(() =>
      engine.reserveVehicle('E-024', { start: now, end: now + 600000 }),
    ).toThrow('UNAVAILABLE');
  });
  it('keeps dismissed faulty resources unavailable until recovery ACK', () => {
    ops.recordIncident('E-024', 'Broken lock', 'CRITICAL');
    const i = ops.incidents[0];
    ops.dismiss(i.id);
    expect(ops.eligible(ops.resource('E-024'))).toBe(false);
    login('STUDENT');
    expect(() =>
      engine.reserveVehicle('E-024', { start: now, end: now + 600000 }),
    ).toThrow('UNAVAILABLE');
    login();
    ops.command('E-024', 'MAINTENANCE', 'Repair', 'SUCCESS', i.id);
    ack();
    expect(i.status).toBe('MAINTENANCE');
    ops.command('E-024', 'RESTORE', 'Verified repair', 'SUCCESS', i.id);
    ack();
    expect(i.status).toBe('RESOLVED');
    expect(ops.eligible(ops.resource('E-024'))).toBe(true);
  });
  it('does not allocate a faulty charging port to a student', () => {
    ops.recordIncident('campus-C1', 'Port fault', 'WARNING');
    login('STUDENT');
    expect(() =>
      engine.bookCharging('PERSONAL-01', 'campus', 50, {
        start: now,
        end: now + 7200000,
      }),
    ).toThrow('CONFLICT');
  });
  it('stops charging only after ACK', () => {
    login('STUDENT');
    engine.bookCharging('PERSONAL-01', 'dorm', 50, {
      start: now,
      end: now + 7200000,
    });
    login();
    const b = engine.data.bookings[0];
    ops.command(b.resourceId, 'STOP_CHARGING', 'Emergency');
    expect(b.status).toBe('ACTIVE');
    ack();
    expect(b.status).toBe('CANCELLED');
  });
});
describe('Dispatch atomicity', () => {
  it('reserves compatible spaces, moves only on arrival and prevents duplicate completion', () => {
    const o = ops.dispatch('metro', 'dorm', ['E-024', 'E-052']);
    expect(ops.resource('E-024').hubId).toBe('metro');
    expect(ops.resource(o.spaceIds[0]).dispatchId).toBe(o.id);
    login('STUDENT');
    expect(() =>
      engine.reserveParking(o.spaceIds[0], { start: now, end: now + 600000 }),
    ).toThrow('UNAVAILABLE');
    login();
    ops.finishDispatch(o.id);
    expect(ops.resource('E-024').hubId).toBe('dorm');
    expect(ops.resource(o.spaceIds[0]).vehicleId).toBe('E-024');
    expect(() => ops.finishDispatch(o.id)).toThrow('UNAVAILABLE');
  });
  it('rolls back held destinations on cancellation', () => {
    const o = ops.dispatch('metro', 'dorm', ['E-024']);
    ops.finishDispatch(o.id, true);
    expect(ops.resource('E-024').hubId).toBe('metro');
    expect(ops.resource('E-024').status).toBe('AVAILABLE');
    expect(ops.resource(o.spaceIds[0]).dispatchId).toBeUndefined();
  });
  it('does not partially mutate when there is insufficient capacity', () => {
    ops.resource('dorm-P1').occupied = true;
    const before = structuredClone(engine.data);
    expect(() => ops.dispatch('metro', 'dorm', ['E-024', 'E-052'])).toThrow(
      'CONFLICT',
    );
    expect(engine.data).toEqual(before);
    expect(ops.orders).toHaveLength(0);
  });
  it('rejects low battery, stale readings, duplicate selections and same-Hub dispatch', () => {
    expect(() => ops.dispatch('campus', 'metro', ['E-011'])).toThrow(
      'UNAVAILABLE',
    );
    expect(() => ops.dispatch('campus', 'metro', ['E-019'])).toThrow(
      'UNAVAILABLE',
    );
    expect(() => ops.dispatch('metro', 'dorm', ['E-024', 'E-024'])).toThrow(
      'CONFLICT',
    );
    expect(() => ops.dispatch('metro', 'metro', ['E-024'])).toThrow('CONFLICT');
  });
});
describe('Charging and isolated simulation', () => {
  it('keeps schedule drafts separate, checks current state and confirms once', () => {
    const p = ops.buildSchedule('campus', now + 60000);
    expect(p.slots[0].vehicleId).toBe('E-011');
    expect(engine.data.bookings).toHaveLength(0);
    ops.confirmSchedule();
    expect(engine.data.bookings).toHaveLength(1);
    expect(() => ops.confirmSchedule()).toThrow('UNAVAILABLE');
  });
  it('rejects a schedule made stale by a new student port booking', () => {
    ops.buildSchedule('campus', now + 60000);
    login('STUDENT');
    engine.bookCharging('PERSONAL-01', 'campus', 60, {
      start: now,
      end: now + 7200000,
    });
    login();
    expect(() => ops.confirmSchedule()).toThrow('CONFLICT');
    expect(engine.data.bookings).toHaveLength(1);
  });
  it('rejects invalid slot edits without corrupting the previous proposal', () => {
    const p = ops.buildSchedule('campus', now + 60000),
      before = structuredClone(p);
    expect(() =>
      ops.editSlot(p.slots[0].id, 'campus-C1', now + 60000, now + 120000),
    ).toThrow('SHORT_WINDOW');
    expect(ops.proposal).toEqual(before);
  });
  it('excludes a faulty or unavailable port from scheduling', () => {
    ops.recordIncident('campus-C1', 'Fault', 'CRITICAL');
    const p = ops.buildSchedule('campus', now + 60000);
    expect(p.slots).toHaveLength(0);
    expect(p.queue).toContain('E-011');
  });
  it('does not mutate real demo resources during simulation and revalidates an applied plan', () => {
    const before = structuredClone(engine.data);
    const s = ops.simulate('metro', 6, 0, 4);
    expect(engine.data).toEqual(before);
    expect(s.overflow).toBeGreaterThan(0);
    expect(s.plan).not.toBeNull();
    ops.resource(s.plan.vehicleIds[0]).status = 'MAINTENANCE';
    expect(() => ops.applySimulation()).toThrow('UNAVAILABLE');
    expect(ops.orders).toHaveLength(0);
  });
  it('applies a valid simulation only once through dispatch', () => {
    ops.simulate('metro', 6, 0, 4);
    ops.applySimulation();
    expect(ops.orders).toHaveLength(1);
    expect(() => ops.applySimulation()).toThrow('UNAVAILABLE');
  });
});
