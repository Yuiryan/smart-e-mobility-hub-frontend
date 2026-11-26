import { DomainError } from '../domain/types.js';
import { overlaps } from '../domain/time.js';
import { validWindow } from './engine.js';

const fail = (code) => {
  throw new DomainError(code);
};
// Demo-only adapter. All production decisions belong to the backend.
export class OperatorEngine {
  constructor(engine) {
    this.engine = engine;
    this.orders = [];
    this.commands = [];
    this.audit = [];
    this.incidents = [];
    this.proposal = null;
    this.simulation = null;
    this.scenarios = [];
    this.thresholds = { warning: 0.75, critical: 0.9 };
    engine.data.ports = engine.data.hubs.flatMap((h) =>
      Array.from({ length: h.ports }, (_, i) => ({
        id: `${h.id}-C${i + 1}`,
        hubId: h.id,
        status: 'AVAILABLE',
        power: 7,
      })),
    );
  }
  get d() {
    return this.engine.data;
  }
  now() {
    return this.engine.now();
  }
  guard() {
    this.engine.expire();
    if (!this.d.user) fail('UNAUTHORIZED');
    if (this.d.user.role !== 'OPERATOR') fail('FORBIDDEN');
  }
  log(action, resource, result, reason = '') {
    this.audit.unshift({
      id: this.engine.id('AUD'),
      at: this.now(),
      actor: this.d.user.id,
      action,
      resource,
      result,
      reason,
    });
  }
  resource(id) {
    return (
      [...this.d.vehicles, ...this.d.ports, ...this.d.spaces].find(
        (x) => x.id === id,
      ) || fail('NOT_FOUND')
    );
  }
  activeBooking(id) {
    return this.d.bookings.some(
      (b) =>
        b.status === 'ACTIVE' && (b.resourceId === id || b.vehicleId === id),
    );
  }
  blocked(id) {
    return (
      this.commands.some(
        (c) => c.resourceId === id && ['PENDING', 'TIMEOUT'].includes(c.state),
      ) ||
      this.d.commands.some(
        (c) =>
          (c.vehicleId === id || c.spaceId === id) &&
          ['PENDING', 'TIMEOUT'].includes(c.state),
      )
    );
  }
  metrics() {
    this.guard();
    return this.d.hubs.map((h) => {
      const spaces = this.d.spaces.filter((s) => s.hubId === h.id);
      const used = spaces.filter(
        (s) =>
          s.occupied ||
          s.dispatchId ||
          this.d.bookings.some(
            (b) =>
              b.kind === 'PARKING' &&
              b.resourceId === s.id &&
              b.status === 'ACTIVE' &&
              b.start <= this.now() &&
              b.end > this.now(),
          ),
      ).length;
      const ports = this.d.ports.filter((p) => p.hubId === h.id);
      const availablePorts = ports.filter(
        (p) => p.status === 'AVAILABLE' && !p.fault && !this.blocked(p.id),
      ).length;
      const charging = this.d.bookings.filter(
        (b) =>
          b.hubId === h.id &&
          b.kind === 'CHARGING' &&
          b.status === 'ACTIVE' &&
          b.start <= this.now() &&
          b.end > this.now(),
      ).length;
      const vehicles = this.d.vehicles.filter((v) => v.hubId === h.id);
      const ratio = Math.max(
        used / spaces.length,
        availablePorts ? charging / availablePorts : charging ? 1 : 0,
      );
      return {
        ...h,
        used,
        capacity: spaces.length,
        availablePorts,
        charging,
        vehicles: vehicles.length,
        stale: vehicles.some((v) => this.now() - v.observedAt > 120000),
        ratio,
        severity:
          ratio >= this.thresholds.critical
            ? 'CRITICAL'
            : ratio >= this.thresholds.warning
              ? 'WARNING'
              : 'NORMAL',
      };
    });
  }
  snapshot() {
    this.guard();
    return structuredClone({
      ...this.d,
      orders: this.orders,
      operatorCommands: this.commands,
      audit: this.audit,
      incidents: this.incidents,
      proposal: this.proposal,
      simulation: this.simulation,
      scenarios: this.scenarios,
      metrics: this.metrics(),
      thresholds: this.thresholds,
    });
  }
  validateAction(id, action) {
    const r = this.resource(id);
    if (this.blocked(id)) fail('PENDING_COMMAND');
    if (
      ['IN_USE', 'DISPATCHING'].includes(r.status) ||
      this.d.rides.some((x) => x.vehicleId === id && !x.end) ||
      r.dispatchId
    )
      fail('UNAVAILABLE');
    if (action === 'RELEASE') {
      if (!this.d.spaces.includes(r) || r.occupied) fail('UNAVAILABLE');
    } else {
      if (
        !['LOCK', 'RESTORE', 'MAINTENANCE', 'STOP_CHARGING'].includes(action) ||
        this.d.spaces.includes(r)
      )
        fail('INVALID_OPERATION');
      if (action === 'STOP_CHARGING' && !this.d.ports.includes(r))
        fail('UNAVAILABLE');
      if (action !== 'STOP_CHARGING' && this.activeBooking(id))
        fail('ACTIVE_BOOKING');
      if (action === 'RESTORE' && !['LOCKED', 'MAINTENANCE'].includes(r.status))
        fail('UNAVAILABLE');
    }
    return r;
  }
  command(id, action, reason, outcome = 'SUCCESS', incidentId = null) {
    this.guard();
    if (
      incidentId &&
      !this.incidents.some((i) => i.id === incidentId && i.resourceId === id)
    )
      fail('NOT_FOUND');
    if (
      !reason?.trim() ||
      reason.length > 500 ||
      !['SUCCESS', 'FAILED', 'TIMEOUT'].includes(outcome)
    )
      fail('INVALID_OPERATION');
    try {
      this.validateAction(id, action);
    } catch (error) {
      this.log(action, id, 'REJECTED', reason);
      throw error;
    }
    const c = {
      id: this.engine.id('OPCMD'),
      resourceId: id,
      action,
      reason: reason.trim(),
      outcome,
      state: 'PENDING',
      readyAt: this.now() + 1500,
      incidentId,
    };
    this.resource(id).operatorPending = true;
    this.commands.unshift(c);
    this.log(action, id, 'PENDING', reason);
    return c.id;
  }
  settle(c, outcome) {
    // Revalidate at acknowledgement, excluding only this command's own lock.
    c.state = 'CHECKING';
    if (outcome === 'SUCCESS') {
      try {
        const r = this.validateAction(c.resourceId, c.action);
        if (c.action === 'RELEASE')
          this.d.bookings
            .filter(
              (b) =>
                b.resourceId === r.id &&
                b.kind === 'PARKING' &&
                b.status === 'ACTIVE',
            )
            .forEach((b) => (b.status = 'CANCELLED'));
        else if (c.action === 'STOP_CHARGING')
          this.d.bookings
            .filter(
              (b) =>
                b.resourceId === r.id &&
                b.kind === 'CHARGING' &&
                b.status === 'ACTIVE',
            )
            .forEach((b) => (b.status = 'CANCELLED'));
        else
          r.status =
            c.action === 'RESTORE'
              ? r.battery < 20
                ? 'LOW_BATTERY'
                : 'AVAILABLE'
              : c.action === 'LOCK'
                ? 'LOCKED'
                : 'MAINTENANCE';
        if (c.action === 'RESTORE') {
          r.fault = false;
          this.incidents
            .filter((i) => i.resourceId === r.id)
            .forEach((i) => (i.status = 'RESOLVED'));
        } else if (c.incidentId)
          this.incidents.find((i) => i.id === c.incidentId).status =
            'MAINTENANCE';
      } catch {
        outcome = 'FAILED';
      }
    }
    c.state = outcome;
    this.resource(c.resourceId).operatorPending = outcome === 'TIMEOUT';
    this.log(c.action, c.resourceId, outcome, c.reason);
  }
  poll() {
    this.guard();
    let changed = false;
    for (const c of this.commands.filter(
      (c) => c.state === 'PENDING' && c.readyAt <= this.now(),
    )) {
      this.settle(c, c.outcome);
      changed = true;
    }
    return changed;
  }
  reconcile(id, outcome) {
    this.guard();
    const c = this.commands.find((c) => c.id === id);
    if (!c || c.state !== 'TIMEOUT' || !['SUCCESS', 'FAILED'].includes(outcome))
      fail('UNAVAILABLE');
    this.settle(c, outcome);
  }
  recordIncident(id, reason, severity) {
    this.guard();
    this.resource(id);
    if (
      !reason.trim() ||
      reason.length > 500 ||
      !['WARNING', 'CRITICAL'].includes(severity)
    )
      fail('INVALID_OPERATION');
    this.resource(id).fault = true;
    this.incidents.unshift({
      id: this.engine.id('INC'),
      resourceId: id,
      reason,
      severity,
      status: 'OPEN',
      at: this.now(),
    });
    this.log('INCIDENT', id, 'OPEN', reason);
  }
  dismiss(id) {
    this.guard();
    const i = this.incidents.find((i) => i.id === id);
    if (!i || i.status !== 'OPEN') fail('UNAVAILABLE');
    i.status = 'DISMISSED';
    this.log('DISMISS', i.resourceId, 'SUCCESS', i.reason);
  }
  eligible(v) {
    return (
      !v.personal &&
      !v.fault &&
      v.status === 'AVAILABLE' &&
      v.battery >= 20 &&
      this.now() - v.observedAt <= 120000 &&
      !this.activeBooking(v.id) &&
      !this.blocked(v.id) &&
      !this.incidents.some(
        (i) =>
          i.resourceId === v.id &&
          ['OPEN', 'MAINTENANCE', 'DISMISSED'].includes(i.status),
      )
    );
  }
  dispatch(source, target, ids) {
    this.guard();
    if (
      source === target ||
      !this.d.hubs.some((h) => h.id === target) ||
      !ids.length ||
      new Set(ids).size !== ids.length
    )
      fail('CONFLICT');
    const vehicles = ids.map((id) => this.d.vehicles.find((v) => v.id === id));
    if (vehicles.some((v) => !v || v.hubId !== source || !this.eligible(v)))
      fail('UNAVAILABLE');
    const free = this.d.spaces.filter(
      (s) =>
        s.hubId === target &&
        !s.occupied &&
        !s.dispatchId &&
        !this.activeBooking(s.id) &&
        !this.blocked(s.id),
    );
    const selected = vehicles.map((v) => {
      const i = free.findIndex((s) => s.type === v.type);
      if (i < 0) fail('CONFLICT');
      return free.splice(i, 1)[0];
    });
    const order = {
      id: this.engine.id('DSP'),
      source,
      target,
      vehicleIds: ids,
      spaceIds: selected.map((s) => s.id),
      state: 'IN_TRANSIT',
      at: this.now(),
    };
    selected.forEach((s) => (s.dispatchId = order.id));
    vehicles.forEach((v) => (v.status = 'DISPATCHING'));
    this.orders.unshift(order);
    this.log('DISPATCH', order.id, 'IN_TRANSIT');
    return order;
  }
  finishDispatch(id, cancel = false) {
    this.guard();
    const o = this.orders.find((x) => x.id === id);
    if (!o || o.state !== 'IN_TRANSIT') fail('UNAVAILABLE');
    if (
      !cancel &&
      o.spaceIds.some((id) => {
        const s = this.resource(id);
        return s.occupied || s.dispatchId !== o.id;
      })
    )
      fail('CONFLICT');
    o.vehicleIds.forEach((id, i) => {
      const v = this.resource(id),
        s = this.resource(o.spaceIds[i]);
      if (!cancel) {
        this.d.spaces
          .filter((x) => x.vehicleId === id)
          .forEach((x) => {
            x.occupied = false;
            delete x.vehicleId;
          });
        s.occupied = true;
        s.vehicleId = id;
        v.hubId = o.target;
      }
      v.status = v.fault ? 'MAINTENANCE' : 'AVAILABLE';
      delete s.dispatchId;
    });
    o.state = cancel ? 'CANCELLED' : 'COMPLETED';
    this.log('DISPATCH', id, o.state);
  }
  buildSchedule(hubId, start) {
    this.guard();
    validWindow({ start, end: start + 60000 }, this.now());
    const hub = this.d.hubs.find((h) => h.id === hubId);
    if (!hub) fail('NOT_FOUND');
    const ports = this.d.ports.filter(
      (p) =>
        p.hubId === hubId &&
        p.status === 'AVAILABLE' &&
        !p.fault &&
        !this.blocked(p.id),
    );
    const parking = this.metrics().find((h) => h.id === hubId);
    const candidates = this.d.vehicles
      .filter(
        (v) =>
          v.hubId === hubId &&
          !v.personal &&
          ['AVAILABLE', 'LOW_BATTERY'].includes(v.status) &&
          parking.used < parking.capacity &&
          v.battery < 30 &&
          this.now() - v.observedAt <= 120000 &&
          !this.blocked(v.id) &&
          !this.activeBooking(v.id) &&
          !this.incidents.some(
            (i) => i.resourceId === v.id && i.status !== 'RESOLVED',
          ),
      )
      .sort((a, b) => a.battery - b.battery);
    const slots = [],
      queue = [];
    for (const v of candidates) {
      const duration = Math.ceil((80 - v.battery) * 1.5) * 60000;
      let found;
      for (let offset = 0; offset < 24 * 60 && !found; offset += 15)
        for (const p of ports) {
          const w = {
            start: start + offset * 60000,
            end: start + offset * 60000 + duration,
          };
          if (
            ![
              ...slots,
              ...this.d.bookings.filter((b) => b.status === 'ACTIVE'),
            ].some(
              (b) =>
                (b.portId === p.id || b.resourceId === p.id) && overlaps(b, w),
            )
          ) {
            found = {
              id: this.engine.id('SLOT'),
              vehicleId: v.id,
              portId: p.id,
              ...w,
            };
            break;
          }
        }
      if (found) slots.push(found);
      else queue.push(v.id);
    }
    this.proposal = {
      id: this.engine.id('PROP'),
      hubId,
      start,
      slots,
      queue,
      state: 'DRAFT',
    };
    return this.proposal;
  }
  validateSchedule(p) {
    if (!p || p.state !== 'DRAFT' || !p.slots.length) fail('UNAVAILABLE');
    for (const s of p.slots) {
      validWindow(s, this.now());
      const v = this.resource(s.vehicleId),
        port = this.resource(s.portId);
      if (
        port.hubId !== p.hubId ||
        port.status !== 'AVAILABLE' ||
        port.fault ||
        v.fault ||
        v.hubId !== p.hubId ||
        !['AVAILABLE', 'LOW_BATTERY'].includes(v.status) ||
        this.blocked(v.id) ||
        this.blocked(port.id) ||
        this.activeBooking(v.id) ||
        this.now() - v.observedAt > 120000 ||
        this.incidents.some(
          (i) => i.resourceId === v.id && i.status !== 'RESOLVED',
        )
      )
        fail('UNAVAILABLE');
      if (s.end - s.start < Math.ceil((80 - v.battery) * 1.5) * 60000)
        fail('SHORT_WINDOW');
      if (
        p.slots.some(
          (other) =>
            other.id !== s.id &&
            (other.portId === s.portId || other.vehicleId === s.vehicleId) &&
            overlaps(other, s),
        ) ||
        this.d.bookings.some(
          (b) =>
            b.status === 'ACTIVE' &&
            b.resourceId === s.portId &&
            overlaps(b, s),
        )
      )
        fail('CONFLICT');
    }
  }
  editSlot(id, portId, start, end) {
    this.guard();
    const copy = structuredClone(this.proposal),
      s = copy?.slots.find((s) => s.id === id);
    if (!s) fail('NOT_FOUND');
    Object.assign(s, { portId, start, end });
    this.validateSchedule(copy);
    this.proposal = copy;
  }
  confirmSchedule() {
    this.guard();
    const p = this.proposal;
    this.validateSchedule(p);
    const bookings = p.slots.map((s) => ({
      id: this.engine.id('SCH'),
      userId: this.d.user.id,
      kind: 'CHARGING',
      resourceId: s.portId,
      vehicleId: s.vehicleId,
      hubId: p.hubId,
      start: s.start,
      end: s.end,
      expiresAt: s.end,
      status: 'ACTIVE',
      target: 80,
      scheduleId: p.id,
    }));
    this.d.bookings.push(...bookings);
    p.state = 'CONFIRMED';
    this.log('SCHEDULE', p.id, 'CONFIRMED');
  }
  simulate(hubId, arrivals, departures, hours) {
    this.guard();
    if (
      ![arrivals, departures, hours].every(Number.isInteger) ||
      arrivals < 0 ||
      departures < 0 ||
      arrivals > 100 ||
      departures > 100 ||
      hours < 1 ||
      hours > 24
    )
      fail('INVALID_TIME');
    const metrics = this.metrics(),
      h = metrics.find((h) => h.id === hubId);
    if (!h) fail('NOT_FOUND');
    const available = this.d.vehicles.filter(
      (v) => v.hubId === hubId && this.eligible(v),
    ).length;
    const timeline = Array.from({ length: hours }, (_, i) => {
      const progress = (i + 1) / hours;
      return {
        hour: i + 1,
        occupancy: Math.max(
          0,
          h.used +
            Math.ceil(arrivals * progress) -
            Math.min(available, Math.floor(departures * progress)),
        ),
      };
    });
    const peak = Math.max(h.used, ...timeline.map((x) => x.occupancy)),
      failed = Math.max(0, departures - available),
      overflow = Math.max(0, peak - h.capacity);
    const source = failed
      ? metrics.find(
          (x) =>
            x.id !== hubId &&
            this.d.vehicles.some((v) => v.hubId === x.id && this.eligible(v)),
        )
      : h;
    const target = failed
      ? h
      : metrics.find((x) => x.id !== hubId && x.used < x.capacity);
    const candidateIds = source
      ? this.d.vehicles
          .filter((v) => v.hubId === source.id && this.eligible(v))
          .map((v) => v.id)
      : [];
    const count = target
      ? Math.min(
          failed || overflow,
          target.capacity - target.used,
          candidateIds.length,
        )
      : 0;
    this.simulation = {
      id: this.engine.id('SIM'),
      at: this.now(),
      hubId,
      arrivals,
      departures,
      hours,
      timeline,
      peak,
      failed,
      overflow,
      plan:
        count > 0
          ? {
              source: source.id,
              target: target.id,
              vehicleIds: candidateIds.slice(0, count),
            }
          : null,
    };
    return this.simulation;
  }
  applySimulation() {
    this.guard();
    const p = this.simulation?.plan;
    if (!p || this.simulation.applied) fail('UNAVAILABLE');
    const order = this.dispatch(p.source, p.target, p.vehicleIds);
    this.simulation.applied = true;
    return order;
  }
  saveScenario(name) {
    this.guard();
    if (!this.simulation || !name?.trim() || name.length > 100)
      fail('INVALID_OPERATION');
    this.scenarios.unshift({
      ...structuredClone(this.simulation),
      name: name.trim(),
    });
  }
  loadScenario(id) {
    this.guard();
    const s = this.scenarios.find((s) => s.id === id);
    if (!s) fail('NOT_FOUND');
    return this.simulate(s.hubId, s.arrivals, s.departures, s.hours);
  }
}
