import {
  operatorApi as api,
  interventionParameters,
} from '../shared/api/operator.js';
import { mobileApi } from '../shared/api/mobile.js';
import {
  e,
  t,
  title,
  input,
  localDate,
  preference,
  applyPreferences,
  confirmAction,
} from '../shared/ui.js';
import { operatorShell, operatorSettings } from './operator-design.js';
const list = (response) => {
  if (!Array.isArray(response?.items)) throw new Error('Invalid list response');
  return response.items;
};
const value = (v) => e(v == null ? '—' : v);
const table = (headers, rows) =>
  `<div class="table-scroll"><table class="op-table"><thead><tr>${headers.map((h) => `<th>${e(h)}</th>`).join('')}</tr></thead><tbody>${rows.length ? rows.map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join('')}</tr>`).join('') : `<tr><td colspan="${headers.length}">${t('Chưa có dữ liệu.', 'No data.')}</td></tr>`}</tbody></table></div>`;
const select = (name, label, options, current) =>
  `<label>${e(label)}<select name="${name}">${options.map(([id, name]) => `<option value="${e(id)}" ${id === current ? 'selected' : ''}>${e(name)}</option>`).join('')}</select></label>`;
const field = (name, label, type = 'text', initial = '', extra = 'required') =>
  input(name, label, type, initial, extra);
const button = (action, text, id = '') =>
  `<button type="button" class="secondary" data-live-op="${action}" data-id="${e(id)}">${e(text)}</button>`;
const form = (name, body, text) =>
  `<form data-live-form="${name}" class="op-form">${body}<button class="primary">${e(text)}</button></form>`;
const metric = (n, label) =>
  `<article class="op-metric"><span>${e(label)}</span><strong>${value(n)}</strong></article>`;
let sessionKey,
  state = {};
export function mountOperatorApi(root, path) {
  if (sessionKey !== mobileApi.session?.sessionToken) {
    state = {};
    sessionKey = mobileApi.session?.sessionToken;
    api.pending = null;
  }
  const mountToken = mobileApi.session?.sessionToken;
  const dialogs = new Set();
  let disposed = false,
    busy = false,
    loading = false,
    generation = 0,
    timer,
    polls = 0;
  const abort = new AbortController(),
    signal = abort.signal;
  const screen = path.split('/')[2];
  let data = { vehicles: [], spaces: [], ports: [] };
  applyPreferences();
  function draw(content) {
    if (disposed) return;
    root.innerHTML = operatorShell(content, path).replace(
      'DEMO · ' + t('Dữ liệu mô phỏng', 'Simulated data'),
      'API',
    );
    const box = root.querySelector('#feedback');
    box.tabIndex = -1;
    if (state.operation) {
      const op = state.operation;
      box.hidden = false;
      box.className = 'feedback';
      box.textContent = `${t('Lệnh thiết bị', 'Device operation')} ${op.operationId}: ${op.status}${op.error?.message ? ' · ' + op.error.message : ''}`;
      box.insertAdjacentHTML(
        'beforeend',
        button('poll', t('Kiểm tra trạng thái', 'Check status')),
      );
    }
    if (api.pending) {
      box.hidden = false;
      box.className = 'feedback error';
      box.textContent = t(
        'Chưa xác định kết quả yêu cầu trước. Không gửi thao tác mới.',
        'The previous request outcome is unknown. Do not send a new operation.',
      );
      if (api.pending.key)
        box.insertAdjacentHTML(
          'beforeend',
          button(
            'retry-mutation',
            t('Đối soát bằng yêu cầu cũ', 'Retry the original request'),
          ),
        );
      else
        box.insertAdjacentHTML(
          'beforeend',
          button(
            'reconcile-schedule',
            t('Tải lại và đối soát', 'Refresh and reconcile'),
          ),
        );
    }
  }
  function fail(error) {
    if (disposed || error.name === 'AbortError') return;
    const box = root.querySelector('#feedback');
    if (!box) return;
    box.hidden = false;
    box.className = 'feedback error';
    box.textContent = `${error.code ? error.code + ': ' : ''}${error.message}${error.requestId ? ' · ' + error.requestId : ''}`;
    box.insertAdjacentHTML(
      'beforeend',
      button('refresh', t('Tải lại', 'Refresh')),
    );
    if (api.pending?.key)
      box.insertAdjacentHTML(
        'beforeend',
        button(
          'retry-mutation',
          t('Đối soát yêu cầu cũ', 'Retry original request'),
        ),
      );
    if (api.pending && !api.pending.key)
      box.insertAdjacentHTML(
        'beforeend',
        button(
          'reconcile-schedule',
          t('Đối soát trạng thái', 'Reconcile state'),
        ),
      );
    if (error.status === 401)
      box.insertAdjacentHTML(
        'beforeend',
        `<a class="button" href="#/account/operator">${t('Đăng nhập lại', 'Sign in again')}</a>`,
      );
    box.focus();
  }
  const hubOptions = () =>
    data.hubs.map((h) => [h.hubId, h.hubName || h.name || h.hubId]);
  const hubName = (id) => data.hubs?.find((h) => h.hubId === id)?.hubName || id;
  function operationResult(result) {
    if (mountToken !== mobileApi.session?.sessionToken) return;
    if (result?.operationId) {
      state.operation = result;
      polls = 0;
    }
    if (result?.dispatchOrderId) state.order = result;
    if (result?.slots) state.proposal = result;
    if (result?.simulationId) state.simulation = result;
    if (result?.confirmedAt) {
      state.schedule = result;
      if (state.proposal) state.proposal.confirmedScheduleId = result.id;
    }
  }
  function contents() {
    if (path === '/settings') return operatorSettings();
    const refresh = button('refresh', t('Cập nhật dữ liệu', 'Refresh data'));
    if (screen === 'overview') {
      const hubs = data.network.hubs;
      return (
        title(
          t('Tổng quan mạng lưới', 'Network overview'),
          data.network.generatedAt,
        ) +
        `<div class="op-metrics">${metric(hubs.length, 'Mobility Hub')}${metric(
          hubs.reduce(
            (n, h) => n + (h.vehicleCountsByStatus?.AVAILABLE || 0),
            0,
          ),
          t('Xe khả dụng', 'Available vehicles'),
        )}${metric(
          hubs.reduce((n, h) => n + h.freeSpaceCount, 0),
          t('Chỗ đỗ trống', 'Free parking'),
        )}${metric(
          hubs.reduce(
            (n, h) => n + (h.chargingPortsByStatus?.AVAILABLE || 0),
            0,
          ),
          t('Cổng sạc khả dụng', 'Available chargers'),
        )}</div>` +
        table(
          [
            'Mobility Hub',
            t('Chỗ đỗ trống', 'Free spaces'),
            t('Tỷ lệ sử dụng', 'Occupancy'),
            t('Cảnh báo', 'Severity'),
            t('Dữ liệu', 'Data'),
          ],
          hubs.map((h) => [
            button('hub', h.hubName, h.hubId),
            value(h.freeSpaceCount),
            value(h.occupancyPct == null ? null : h.occupancyPct + '%'),
            value(h.severity),
            value(h.dataQuality),
          ]),
        ) +
        refresh
      );
    }
    if (screen === 'resources')
      return (
        title(hubName(state.hub) || t('Tài nguyên Hub', 'Hub resources')) +
        select('hub', 'Mobility Hub', hubOptions(), state.hub) +
        table(
          [
            t('Phương tiện', 'Vehicle'),
            t('Pin', 'Battery'),
            t('Vị trí', 'Space'),
            t('Trạng thái', 'Status'),
            t('Kết nối', 'Connection'),
          ],
          data.vehicles.map((v) => [
            value(v.vehicleId),
            value(v.batteryPct == null ? null : v.batteryPct + '%'),
            value(v.spaceId),
            value(v.status),
            value(v.connectionStatus),
          ]),
        ) +
        `<div class="op-columns">${table(
          [
            t('Chỗ đỗ', 'Parking space'),
            t('Xe', 'Vehicle'),
            t('Trạng thái', 'Status'),
          ],
          data.spaces.map((s) => [
            value(s.spaceId),
            value(s.vehicleId),
            value(s.status),
          ]),
        )}${table(
          [
            t('Cổng sạc', 'Charger'),
            t('Xe', 'Vehicle'),
            t('Trạng thái', 'Status'),
          ],
          data.ports.map((p) => [
            value(p.portId),
            value(p.vehicleId),
            value(p.status),
          ]),
        )}</div>` +
        `<a class="button secondary" href="#/operator/interventions">${t('Mở can thiệp vận hành', 'Open intervention')}</a>`
      );
    if (screen === 'alerts')
      return (
        title(t('Cảnh báo quá tải Hub', 'Hub overload alerts')) +
        table(
          [
            'Hub',
            t('Mức cảnh báo', 'Severity'),
            t('Nội dung', 'Message'),
            t('Cập nhật', 'Updated'),
          ],
          data.alerts.map((a) => [
            value(a.hubId),
            value(a.severity),
            value(a.message),
            value(a.observedAt),
          ]),
        ) +
        `<div class="op-action-row"><a class="button primary" href="#/operator/dispatch">${t('Điều phối xe', 'Dispatch vehicles')}</a><a class="button secondary" href="#/operator/simulation">${t('Mô phỏng phương án', 'Simulate options')}</a></div>` +
        refresh
      );
    if (screen === 'interventions') {
      const r = data.resource;
      return (
        title(t('Can thiệp vận hành', 'Operational intervention')) +
        `<div class="op-columns"><section class="panel">${form(
          'resource',
          select(
            'resourceType',
            t('Loại tài nguyên', 'Resource type'),
            [
              ['VEHICLE', t('Phương tiện', 'Vehicle')],
              ['CHARGING_PORT', t('Cổng sạc', 'Charging port')],
              ['PARKING_SPACE', t('Chỗ đỗ', 'Parking space')],
            ],
            state.resourceType,
          ) +
            field(
              'resourceId',
              t('Mã tài nguyên', 'Resource ID'),
              'text',
              state.resourceId || '',
            ),
          t('Xem tài nguyên', 'View resource'),
        )}${r ? table([t('Trạng thái', 'Status'), t('Kết nối', 'Connection'), t('Phiên bản', 'Version')], [[value(r.status), value(r.connectionStatus), value(r.version)]]) : ''}</section><section class="panel">${
          r
            ? form(
                'intervene',
                select(
                  'action',
                  t('Hành động', 'Action'),
                  r.allowedActions
                    .filter((a) => a !== 'LOCK' && a !== 'UNLOCK')
                    .map((a) => [a, a]),
                ) +
                  field('reason', t('Lý do can thiệp', 'Reason')) +
                  field(
                    'batteryPct',
                    t(
                      'Pin mới (%) — chỉ cho SET_BATTERY',
                      'New battery (%) — SET_BATTERY only',
                    ),
                    'number',
                    '',
                    'min="0" max="100"',
                  ) +
                  `<p>${t('Kết quả chỉ được xác nhận sau phản hồi từ thiết bị.', 'The result is confirmed only after device acknowledgement.')}</p>`,
                t('Kiểm tra và xác nhận lệnh', 'Validate and confirm'),
              )
            : t(
                'Chọn tài nguyên để xem các thao tác được phép.',
                'Select a resource to see allowed actions.',
              )
        }</section></div>` +
        table(
          [
            t('Thời điểm', 'Time'),
            t('Tài nguyên', 'Resource'),
            t('Thao tác', 'Action'),
            t('Kết quả', 'Outcome'),
          ],
          data.audit.map((a) => [
            value(a.createdAt || a.timestamp),
            value(a.resourceId),
            value(a.action),
            value(a.outcome),
          ]),
        )
      );
    }
    if (screen === 'dispatch') {
      if (state.order) {
        const o = state.order;
        return (
          title(
            t('Lệnh điều phối ', 'Dispatch order ') + o.dispatchOrderId,
            o.status,
          ) +
          table(
            [
              t('Xe', 'Vehicle'),
              t('Hub nguồn', 'Source'),
              t('Hub đích', 'Destination'),
            ],
            o.vehicleIds.map((v) => [
              value(v),
              value(o.sourceHubId),
              value(o.destinationHubId),
            ]),
          ) +
          `<div class="op-action-row">${o.status === 'IN_TRANSIT' ? button('arrival', t('Xác nhận đã đến', 'Confirm arrival')) + button('cancel', t('Hủy lệnh', 'Cancel order')) : ''}${button('new-order', t('Lệnh mới', 'New order'))}</div>`
        );
      }
      const c = data.candidates;
      return (
        title(t('Điều phối phương tiện', 'Vehicle dispatch')) +
        form(
          'dispatch',
          `<div class="op-dispatch-fields">${select('sourceHubId', t('Hub nguồn', 'Source Hub'), hubOptions(), state.source)}${select(
            'destinationHubId',
            t('Hub đích', 'Destination Hub'),
            (c?.destinationOptions || []).map((h) => [
              h.hubId,
              h.hubName +
                ' · ' +
                h.freeSpaceCount +
                t(' chỗ trống', ' free spaces'),
            ]),
            state.target,
          )}${select(
            'transportMode',
            t('Hình thức vận chuyển', 'Transport mode'),
            [
              ['TRUCK', t('Xe vận chuyển', 'Truck')],
              ['SELF_DRIVE', t('Tự chạy', 'Self drive')],
            ],
            state.transportMode || 'TRUCK',
          )}</div>` +
            table(
              [
                t('Chọn', 'Select'),
                t('Xe', 'Vehicle'),
                t('Pin', 'Battery'),
                t('Trạng thái', 'Status'),
              ],
              (c?.items || []).map((v) => [
                `<input type="checkbox" name="vehicleIds" value="${e(v.vehicleId)}" aria-label="${e(v.vehicleId)}" ${v.dispatchModes.includes(state.transportMode || 'TRUCK') ? '' : 'disabled'}>`,
                value(v.vehicleId),
                value(v.batteryPct),
                value(v.status),
              ]),
            ) +
            field('reason', t('Lý do', 'Reason')),
          t('Xác nhận tạo lệnh điều phối', 'Confirm dispatch order'),
        ) +
        table(
          [t('Mã lệnh', 'Order'), t('Trạng thái', 'Status')],
          data.orders.map((o) => [
            button('order', o.dispatchOrderId, o.dispatchOrderId),
            value(o.status),
          ]),
        )
      );
    }
    if (screen === 'incidents')
      return (
        title(t('Sự cố thiết bị', 'Device incidents')) +
        table(
          [
            t('Thiết bị', 'Device'),
            'Hub',
            t('Cảnh báo', 'Description'),
            t('Trạng thái', 'Status'),
            t('Thao tác', 'Actions'),
          ],
          data.incidents.map((i) => [
            value(i.resourceId),
            value(i.hubId),
            value(i.description),
            value(i.status),
            i.allowedActions
              .map((a) =>
                button('incident-' + a.toLowerCase(), a, i.incidentId),
              )
              .join(' '),
          ]),
        ) +
        `<details class="op-extra"><summary>${t('Ghi nhận sự cố', 'Report incident')}</summary>${form(
          'incident',
          select('resourceType', t('Loại thiết bị', 'Device type'), [
            ['VEHICLE', t('Phương tiện', 'Vehicle')],
            ['CHARGING_PORT', t('Cổng sạc', 'Charger')],
          ]) +
            field('resourceId', t('Mã thiết bị', 'Device ID')) +
            select('severity', t('Mức độ', 'Severity'), [
              ['LOW', t('Thấp', 'Low')],
              ['MEDIUM', t('Trung bình', 'Medium')],
              ['HIGH', t('Cao', 'High')],
            ]) +
            field('description', t('Mô tả', 'Description')),
          t('Ghi nhận', 'Report'),
        )}</details>${refresh}`
      );
    if (screen === 'charging') {
      const p = state.proposal;
      return (
        title(t('Lập lịch sạc thông minh', 'Smart charging schedule')) +
        form(
          'schedule',
          select('hubId', 'Mobility Hub', hubOptions(), state.hub),
          t('Tạo / cập nhật đề xuất', 'Create / update proposal'),
        ) +
        (p
          ? table(
              [
                t('Xe', 'Vehicle'),
                t('Cổng', 'Port'),
                t('Bắt đầu', 'Start'),
                t('Kết thúc', 'End'),
                t('Thao tác', 'Action'),
              ],
              p.slots.map((s) => [
                value(s.vehicleId),
                value(s.portId),
                value(s.startAt),
                value(s.endAt),
                !p.confirmedScheduleId
                  ? button('edit-slot', t('Điều chỉnh', 'Edit'), s.id)
                  : '',
              ]),
            ) +
            table(
              [t('Xe chờ', 'Queued vehicle'), t('Lý do', 'Reason')],
              p.queue.map((q) => [value(q.vehicleId), value(q.reason)]),
            ) +
            p.violations
              .map((v) => `<p class="feedback error">${e(v)}</p>`)
              .join('') +
            (p.confirmedScheduleId
              ? `<p>${t('Lịch đã xác nhận: ', 'Confirmed schedule: ')}${e(p.confirmedScheduleId)}</p>`
              : !p.violations.length
                ? button(
                    'confirm-schedule',
                    t('Xác nhận và lưu lịch', 'Confirm and save schedule'),
                  )
                : '')
          : t('Chưa có đề xuất.', 'No proposal yet.'))
      );
    }
    if (screen === 'simulation') {
      const s = state.simulation;
      return (
        title(t('Mô phỏng kịch bản What-if', 'What-if scenario simulation')) +
        (s
          ? `<div class="op-metrics">${metric(s.projectedOverloadHubs.length, t('Hub có nguy cơ quá tải', 'Hubs at risk'))}${metric(s.failedBookingsCount, t('Lượt không đặt được xe', 'Failed bookings'))}${metric(s.failedReturnsCount, t('Lượt không trả được xe', 'Failed returns'))}${metric(s.status, t('Trạng thái', 'Status'))}</div><p>${e(s.disclaimer)}</p>` +
            table(
              [
                t('Hub nguồn', 'Source'),
                t('Hub đích', 'Destination'),
                t('Số xe', 'Vehicles'),
                t('Thao tác', 'Action'),
              ],
              s.recommendedDispatch.map((p) => [
                value(p.fromHub),
                value(p.toHub),
                value(p.vehicleCount),
                button(
                  'proposal',
                  t('Mở điều phối', 'Prepare dispatch'),
                  p.proposalId,
                ),
              ]),
            ) +
            s.missingData
              .map((x) => `<p class="op-warning">${e(x)}</p>`)
              .join('') +
            button('new-simulation', t('Kịch bản mới', 'New scenario'))
          : form(
              'simulate',
              `<div class="panel">${field('scenarioName', t('Tên kịch bản', 'Scenario name'))}${field('duration', t('Thời gian mô phỏng (giờ)', 'Simulation duration (hours)'), 'number', '2', 'required min="0.1" step="0.1"')}${field('multiplier', t('Hệ số nhu cầu (1.5 = tăng 50%)', 'Rental multiplier (1.5 = +50%)'), 'number', '1.5', 'required min="0" step="0.1"')}${select('hubOutageId', t('Hub ngừng hoạt động giả định', 'Hypothetical Hub outage'), [['', t('Không có', 'None')], ...hubOptions()])}</div>`,
              t('Bắt đầu mô phỏng', 'Start simulation'),
            ))
      );
    }
    return title('404');
  }
  async function load() {
    if (disposed) return;
    const current = ++generation;
    loading = true;
    draw(title(t('Đang tải dữ liệu…', 'Loading data…')));
    try {
      if (!mobileApi.user || mobileApi.user.role !== 'OPERATOR')
        throw new Error(
          t(
            'Vui lòng đăng nhập bằng tài khoản vận hành.',
            'Please sign in with an operator account.',
          ),
        );
      if (path === '/settings') {
        draw(contents());
        return;
      }
      if (screen === 'overview') data.network = await api.network(signal);
      if (
        ['resources', 'dispatch', 'charging', 'simulation'].includes(screen)
      ) {
        data.hubs = list(await api.hubs(signal));
        state.hub ||= data.hubs[0]?.hubId;
        state.source ||= data.hubs[0]?.hubId;
      }
      if (screen === 'resources' && state.hub)
        [data.vehicles, data.spaces, data.ports] = (
          await Promise.all([
            api.vehicles(state.hub, signal),
            api.spaces(state.hub, signal),
            api.ports(state.hub, signal),
          ])
        ).map(list);
      if (screen === 'alerts') data.alerts = list(await api.alerts(signal));
      if (screen === 'interventions') {
        data.audit = list(await api.audit(signal));
        if (state.resourceId)
          data.resource = await api.resource(
            state.resourceType,
            state.resourceId,
            signal,
          );
      }
      if (screen === 'dispatch') {
        data.orders = list(await api.orders(signal));
        if (state.order)
          state.order = await api.order(state.order.dispatchOrderId, signal);
        else if (state.source)
          data.candidates = await api.candidates(
            state.source,
            state.transportMode || 'TRUCK',
            signal,
          );
      }
      if (screen === 'incidents')
        data.incidents = list(await api.incidents(signal));
      if (screen === 'charging' && state.proposal)
        state.proposal = await api.proposal(state.proposal.id, signal);
      if (!disposed && current === generation) draw(contents());
    } catch (error) {
      if (current === generation) fail(error);
    } finally {
      if (current === generation) loading = false;
    }
  }
  async function run(fn, confirm = false) {
    if (busy || loading || disposed) return;
    busy = true;
    try {
      if (
        confirm &&
        !(await confirmAction(
          t(
            'Xác nhận gửi yêu cầu tới hệ thống?',
            'Confirm sending this request to the system?',
          ),
        ))
      )
        return;
      if (disposed) return;
      const result = await fn();
      if (disposed) {
        operationResult(result);
        return;
      }
      operationResult(result);
      await load();
    } catch (error) {
      fail(error);
    } finally {
      busy = false;
    }
  }
  async function submit(event) {
    const kind = event.target.dataset.liveForm;
    if (!kind) return;
    event.preventDefault();
    event.stopPropagation();
    const f = new FormData(event.target),
      v = Object.fromEntries(f);
    if (kind === 'resource') {
      state.resourceId = v.resourceId;
      state.resourceType = v.resourceType;
      return load();
    }
    await run(async () => {
      if (kind === 'dispatch')
        return api.dispatch({ ...v, vehicleIds: f.getAll('vehicleIds') });
      if (kind === 'incident') return api.createIncident(v);
      if (
        kind === 'intervene' &&
        (v.action === 'LOCK' ||
          v.action === 'UNLOCK' ||
          !data.resource.allowedActions.includes(v.action))
      )
        throw new Error('Action not permitted');
      if (kind === 'intervene') {
        return api.intervene({
          resourceType: state.resourceType,
          resourceId: state.resourceId,
          action: v.action,
          reason: v.reason,
          expectedVersion: data.resource.version,
          parameters: interventionParameters(
            data.resource,
            v.action,
            v.batteryPct,
          ),
        });
      }
      if (kind === 'schedule') return api.buildSchedule(v.hubId);
      if (kind === 'simulate')
        return api.simulate({
          scenarioName: v.scenarioName,
          parameters: {
            simulatedDurationHours: Number(v.duration),
            rentalMultiplier: Number(v.multiplier),
            hubOutageId: v.hubOutageId || null,
          },
        });
    }, !['schedule', 'simulate'].includes(kind));
  }
  async function click(event) {
    const b = event.target.closest('button');
    if (!b) return;
    if (
      b.hasAttribute('data-op-theme') ||
      b.hasAttribute('data-op-locale') ||
      b.dataset.setting
    ) {
      const key =
        b.dataset.setting ||
        (b.hasAttribute('data-op-theme') ? 'theme' : 'locale');
      preference(
        key,
        b.dataset.value ||
          (key === 'theme'
            ? document.documentElement.dataset.theme === 'dark'
              ? 'light'
              : 'dark'
            : document.documentElement.lang === 'en'
              ? 'vi'
              : 'en'),
      );
      draw(contents());
      return;
    }
    const a = b.dataset.liveOp;
    if (!a || busy || loading) return;
    event.stopPropagation();
    if (a === 'refresh') return load();
    if (a === 'hub') {
      state.hub = b.dataset.id;
      location.hash = '/operator/resources';
      return;
    }
    if (a === 'new-order') {
      state.order = null;
      return load();
    }
    if (a === 'new-simulation') {
      state.simulation = null;
      return load();
    }
    if (a === 'proposal') {
      const p = state.simulation.recommendedDispatch.find(
        (p) => p.proposalId === b.dataset.id,
      );
      state.source = p.fromHub;
      state.target = p.toHub;
      state.order = null;
      location.hash = '/operator/dispatch';
      return;
    }
    if (a === 'edit-slot') {
      const s = state.proposal.slots.find((s) => s.id === b.dataset.id);
      const d = document.createElement('dialog');
      d.innerHTML =
        form(
          'slot-dialog',
          field(
            'startAt',
            t('Bắt đầu', 'Start'),
            'datetime-local',
            localDate(Date.parse(s.startAt)),
          ) +
            field(
              'endAt',
              t('Kết thúc', 'End'),
              'datetime-local',
              localDate(Date.parse(s.endAt)),
            ),
          t('Lưu khung giờ', 'Save slot'),
        ) + button('close-dialog', t('Hủy', 'Cancel'));
      dialogs.add(d);
      document.body.append(d);
      d.showModal();
      d.querySelector('[data-live-op=close-dialog]').onclick = () => d.remove();
      d.addEventListener('cancel', () => d.remove());
      d.onsubmit = (event) => {
        event.preventDefault();
        const f = new FormData(event.target);
        d.remove();
        if (!disposed)
          run(() =>
            api.editSlot(
              state.proposal,
              s.id,
              f.get('startAt'),
              f.get('endAt'),
            ),
          );
      };
      return;
    }
    if (a.startsWith('incident-')) {
      const action = a.slice(9),
        incidentId = b.dataset.id;
      const d = document.createElement('dialog');
      d.innerHTML =
        form(
          'note',
          field('note', t('Lý do / ghi chú', 'Reason / note')),
          t('Xác nhận', 'Confirm'),
        ) + button('close-dialog', t('Hủy', 'Cancel'));
      dialogs.add(d);
      document.body.append(d);
      d.showModal();
      d.querySelector('[data-live-op=close-dialog]').onclick = () => d.remove();
      d.addEventListener('cancel', () => d.remove());
      d.onsubmit = (event) => {
        event.preventDefault();
        const note = new FormData(event.target).get('note');
        d.remove();
        if (!disposed)
          run(async () => {
            const incident = await api.incident(incidentId, signal);
            return api.incidentAction(incident, action, note);
          });
      };
      return;
    }
    await run(async () => {
      if (a === 'order') {
        state.order = await api.order(b.dataset.id, signal);
        return;
      }
      if (a === 'arrival' || a === 'cancel')
        return api.dispatchAction(state.order, a);
      if (a === 'confirm-schedule') return api.confirmSchedule(state.proposal);
      if (a === 'poll') {
        state.operation = await api.operation(
          state.operation.operationId,
          signal,
        );
        polls = 0;
        return;
      }
      if (a === 'retry-mutation') return api.retry();
      if (a === 'reconcile-schedule') {
        if (state.proposal) {
          state.proposal = await api.proposal(state.proposal.id, signal);
          api.pending = null;
        } else if (
          await confirmAction(
            t(
              'Không có mã đề xuất để truy vấn. Yêu cầu trước có thể đã tạo bản nháp/kết quả mô phỏng. Bỏ trạng thái chờ để tiếp tục?',
              'No proposal ID is available to query. The previous request may have created a draft/simulation result. Clear the pending state to continue?',
            ),
          )
        )
          api.pending = null;
      }
    }, ['arrival', 'cancel', 'confirm-schedule'].includes(a));
  }
  function change(event) {
    const v = event.target.value,
      n = event.target.name;
    if (n === 'hub') {
      state.hub = v;
      load();
    }
    if (n === 'sourceHubId' || n === 'transportMode') {
      state.source = root.querySelector('[name=sourceHubId]').value;
      state.transportMode = root.querySelector('[name=transportMode]').value;
      state.target = root.querySelector('[name=destinationHubId]')?.value;
      load();
    }
  }
  root.addEventListener('click', click);
  root.addEventListener('submit', submit);
  root.addEventListener('change', change);
  load();
  timer = setInterval(async () => {
    if (
      disposed ||
      busy ||
      loading ||
      polls >= 30 ||
      !state.operation ||
      !['PENDING', 'RUNNING', 'RECONCILING'].includes(state.operation.status)
    )
      return;
    busy = true;
    polls++;
    try {
      const result = await api.operation(state.operation.operationId, signal);
      if (!disposed) {
        state.operation = result;
        if (!['PENDING', 'RUNNING', 'RECONCILING'].includes(result.status))
          await load();
      }
    } catch (error) {
      fail(error);
    } finally {
      busy = false;
    }
  }, 2000);
  return () => {
    disposed = true;
    abort.abort();
    dialogs.forEach((d) => d.remove());
    clearInterval(timer);
    root.removeEventListener('click', click);
    root.removeEventListener('submit', submit);
    root.removeEventListener('change', change);
  };
}
