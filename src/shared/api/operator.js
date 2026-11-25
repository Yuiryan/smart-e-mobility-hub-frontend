import { request, ApiError } from './client.js';
import { mobileApi } from './mobile.js';
const id = encodeURIComponent;
const query = (values = {}) => {
  const q = new URLSearchParams(
    Object.entries(values).filter(([, v]) => v !== '' && v != null),
  );
  return q.size ? '?' + q : '';
};
export function interventionParameters(resource, action, battery) {
  const usage = resource.activeUsage || resource;
  const available = {
    ...usage,
    chargingRequestId:
      usage.chargingRequestId || resource.activeChargingRequestId,
  };
  const fields = {
    STOP_CHARGING: ['chargingRequestId', 'allocationId'],
    CANCEL_CHARGING: ['chargingRequestId'],
    CANCEL_RESERVATION: ['reservationId'],
    CANCEL_PARKING_RESERVATION: ['parkingReservationId'],
  };
  if (action === 'SET_BATTERY') {
    if (
      battery === '' ||
      !Number.isFinite(Number(battery)) ||
      Number(battery) < 0 ||
      Number(battery) > 100
    )
      throw new Error('Battery must be between 0 and 100');
    return { batteryPct: Number(battery) };
  }
  return Object.fromEntries(
    (fields[action] || []).map((key) => {
      if (!available[key])
        throw new Error(
          `Missing ${key}; refresh the resource before intervening`,
        );
      return [key, available[key]];
    }),
  );
}
export class OperatorApi {
  constructor(session = mobileApi, send = request) {
    this.session = session;
    this.send = send;
    this.pending = null;
    this.owner = null;
  }
  async call(path, { body, method, signal, key } = {}) {
    const session = this.session.session;
    if (!session || Date.parse(session.expiresAt) <= Date.now()) {
      this.session.clear();
      throw new ApiError(401, 'Session expired');
    }
    if (session.user.role !== 'OPERATOR')
      throw new ApiError(403, 'Operator access required');
    const headers = { Authorization: `Bearer ${session.sessionToken}` };
    if (key) headers['Idempotency-Key'] = key;
    try {
      return await this.send(path, {
        method: method || (body === undefined ? 'GET' : 'POST'),
        headers,
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        signal,
      });
    } catch (error) {
      if (
        error.status === 401 &&
        this.session.session?.sessionToken === session.sessionToken
      )
        this.session.clear();
      throw error;
    }
  }
  read(path, signal) {
    return this.call(path, { signal });
  }
  network(signal) {
    return this.read('/operator/network-report', signal);
  }
  hubs(signal) {
    return this.read('/hubs', signal);
  }
  vehicles(hubId, signal) {
    return this.read('/operator/vehicles' + query({ hubId }), signal);
  }
  ports(hubId, signal) {
    return this.read('/operator/charging-ports' + query({ hubId }), signal);
  }
  spaces(hubId, signal) {
    return this.read(`/hubs/${id(hubId)}/spaces`, signal);
  }
  resource(type, resourceId, signal) {
    const collections = {
      VEHICLE: 'vehicles',
      CHARGING_PORT: 'charging-ports',
      PARKING_SPACE: 'parking-spaces',
    };
    if (!collections[type]) throw new Error('Invalid resource type');
    return this.read(
      `/operator/${collections[type]}/${id(resourceId)}`,
      signal,
    );
  }
  alerts(signal) {
    return this.read('/operator/alerts', signal);
  }
  audit(signal) {
    return this.read('/operator/audit-logs', signal);
  }
  candidates(sourceHubId, transportMode, signal) {
    return this.read(
      '/operator/dispatch-candidates' + query({ sourceHubId, transportMode }),
      signal,
    );
  }
  orders(signal) {
    return this.read('/operator/dispatch-orders', signal);
  }
  order(orderId, signal) {
    return this.read(`/operator/dispatch-orders/${id(orderId)}`, signal);
  }
  incidents(signal) {
    return this.read('/operator/incidents', signal);
  }
  incident(incidentId, signal) {
    return this.read(`/operator/incidents/${id(incidentId)}`, signal);
  }
  proposal(proposalId, signal) {
    return this.read(`/operator/charging-schedules/${id(proposalId)}`, signal);
  }
  // Preserve the exact request/key after an ambiguous network result. Never replay with a new key.
  async mutate(path, body, method = 'POST', keyed = true) {
    const owner = this.session.session?.sessionToken;
    if (this.owner !== owner) {
      this.pending = null;
      this.owner = owner;
    }
    if (this.pending)
      throw new Error(
        'A previous request needs reconciliation. Retry that request first.',
      );
    const operation = {
      path,
      body: structuredClone(body),
      method,
      key: keyed ? crypto.randomUUID() : undefined,
    };
    this.pending = operation;
    return this.execute(operation);
  }
  async execute(operation) {
    try {
      const result = await this.call(operation.path, operation);
      if (this.pending === operation) this.pending = null;
      return result;
    } catch (error) {
      if (
        error.status >= 400 &&
        error.status < 500 &&
        this.pending === operation
      )
        this.pending = null;
      throw error;
    }
  }
  retry() {
    if (!this.pending || this.owner !== this.session.session?.sessionToken)
      throw new Error('No pending request for this session');
    if (!this.pending.key)
      throw new Error(
        'This endpoint has no idempotency guarantee. Refresh its state before continuing.',
      );
    return this.execute(this.pending);
  }
  intervene(body) {
    return this.mutate('/operator/interventions', body);
  }
  dispatch(body) {
    return this.mutate('/operator/dispatch-orders', body);
  }
  dispatchAction(order, action) {
    if (!['arrival', 'cancel'].includes(action))
      throw new Error('Invalid dispatch action');
    return this.mutate(
      `/operator/dispatch-orders/${id(order.dispatchOrderId)}/${action}`,
      { expectedVersion: order.version },
    );
  }
  createIncident(body) {
    return this.mutate('/operator/incidents', body);
  }
  incidentAction(incident, action, note) {
    const body = { expectedVersion: incident.version };
    if (action === 'dismiss') body.dismissNote = note;
    else if (['maintenance', 'resolve'].includes(action)) {
      body.expectedResourceVersion = incident.resourceVersion;
      body[action === 'maintenance' ? 'reason' : 'resolutionNote'] = note;
    } else throw new Error('Invalid incident action');
    return this.mutate(
      `/operator/incidents/${id(incident.incidentId)}/${action}`,
      body,
    );
  }
  buildSchedule(hubId) {
    return this.mutate(
      '/operator/charging-schedules',
      { hubId },
      'POST',
      false,
    );
  }
  editSlot(proposal, slotId, startAt, endAt) {
    const from = new Date(startAt),
      to = new Date(endAt);
    if (!Number.isFinite(+from) || !Number.isFinite(+to) || to <= from)
      throw new Error('Invalid time window');
    return this.mutate(
      `/operator/charging-schedules/${id(proposal.id)}/slots/${id(slotId)}`,
      {
        revision: proposal.revision,
        startAt: from.toISOString(),
        endAt: to.toISOString(),
      },
      'PUT',
      false,
    );
  }
  confirmSchedule(proposal) {
    return this.mutate(
      `/operator/charging-schedules/${id(proposal.id)}/confirm`,
      { revision: proposal.revision },
      'POST',
      false,
    );
  }
  simulate(scenario) {
    return this.mutate('/operator/simulations', scenario, 'POST', false);
  }
  operation(operationId, signal) {
    return this.read(`/operations/${id(operationId)}`, signal);
  }
}
export const operatorApi = new OperatorApi();
