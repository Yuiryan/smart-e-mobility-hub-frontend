import {
  e,
  t,
  title,
  input,
  localDate,
  date,
  outcome,
  confirmAction,
  msg,
} from '../shared/ui.js';

const labels = {
  NORMAL: ['Bình thường', 'Normal'],
  WARNING: ['Cảnh báo', 'Warning'],
  CRITICAL: ['Nghiêm trọng', 'Critical'],
  STALE: ['Dữ liệu cũ', 'Stale data'],
  AVAILABLE: ['Khả dụng', 'Available'],
  LOW_BATTERY: ['Pin yếu', 'Low battery'],
  RESERVED: ['Đã giữ chỗ', 'Reserved'],
  IN_USE: ['Đang sử dụng', 'In use'],
  DISPATCHING: ['Đang điều phối', 'Dispatching'],
  LOCKED: ['Đã khóa', 'Locked'],
  MAINTENANCE: ['Bảo trì', 'Maintenance'],
  PENDING: ['Chờ ACK', 'Awaiting ACK'],
  SUCCESS: ['Thành công', 'Success'],
  FAILED: ['Thất bại', 'Failed'],
  TIMEOUT: ['Chờ đối soát', 'Reconciliation required'],
  REJECTED: ['Đã từ chối', 'Rejected'],
  IN_TRANSIT: ['Đang vận chuyển', 'In transit'],
  COMPLETED: ['Hoàn thành', 'Completed'],
  CANCELLED: ['Đã hủy', 'Cancelled'],
  OPEN: ['Chưa xử lý', 'Open'],
  DISMISSED: ['Đã bỏ qua', 'Dismissed'],
  RESOLVED: ['Đã khắc phục', 'Resolved'],
  DRAFT: ['Bản nháp', 'Draft'],
  CONFIRMED: ['Đã xác nhận', 'Confirmed'],
  LOCK: ['Khóa', 'Lock'],
  RESTORE: ['Khôi phục', 'Restore'],
  RELEASE: ['Giải phóng giữ chỗ', 'Release reservation'],
  STOP_CHARGING: ['Ngắt sạc khẩn', 'Emergency stop'],
  INCIDENT: ['Ghi nhận sự cố', 'Record incident'],
  DISMISS: ['Bỏ qua cảnh báo', 'Dismiss alert'],
  DISPATCH: ['Điều phối', 'Dispatch'],
  SCHEDULE: ['Lịch sạc', 'Schedule'],
};
const label = (code) => (labels[code] ? t(...labels[code]) : code);
const status = (code) =>
  `<span class="badge op-${e(code.toLowerCase())}">${e(label(code))}</span>`;
const button = (action, text, id = '', extra = '') =>
  `<button type="button" data-op="${action}" data-id="${e(id)}" ${extra}>${e(text)}</button>`;
const table = (headers, rows) =>
  `<div class="table-scroll"><table class="op-table"><thead><tr>${headers.map((x) => `<th scope="col">${e(x)}</th>`).join('')}</tr></thead><tbody>${rows.length ? rows.map((row) => `<tr>${row.map((x) => `<td>${x}</td>`).join('')}</tr>`).join('') : `<tr><td colspan="${headers.length}" class="empty">${t('Chưa có dữ liệu phù hợp.', 'No matching data yet.')}</td></tr>`}</tbody></table></div>`;
const select = (name, text, options, value) =>
  `<label>${e(text)}<select name="${name}">${options.map(([id, text]) => `<option value="${e(id)}" ${id === value ? 'selected' : ''}>${e(text)}</option>`).join('')}</select></label>`;
const hubs = (d) => d.hubs.map((h) => [h.id, h.name]);
const hubName = (d, id) => d.hubs.find((h) => h.id === id)?.name || id;
const panel = (heading, body) =>
  `<section class="panel"><h2>${e(heading)}</h2>${body}</section>`;
const metric = (value, text) =>
  `<article class="op-metric"><span>${e(text)}</span><strong>${e(value)}</strong></article>`;
const reason = () =>
  `<label>${t('Lý do', 'Reason')}<textarea name="reason" required maxlength="500" rows="3"></textarea></label>`;
const submit = (text) =>
  `<button type="submit" class="primary">${e(text)}</button>`;
const form = (action, body, id = '') =>
  `<form data-operator-form="${action}" data-id="${e(id)}" class="op-form">${body}</form>`;
const resourceOptions = (d) =>
  [...d.vehicles, ...d.ports, ...d.spaces].map((r) => [
    r.id,
    `${r.id} · ${hubName(d, r.hubId)} · ${label(r.status || (r.occupied ? 'IN_USE' : 'AVAILABLE'))}`,
  ]);

export function operatorPage(ops, route, state = {}) {
  const d = ops.snapshot(),
    key = route.split('/')[2];
  const names = {
    overview: ['Tổng quan mạng lưới', 'Network overview'],
    resources: ['Tài nguyên Hub', 'Mobility Hub resources'],
    interventions: ['Can thiệp vận hành', 'Operational intervention'],
    alerts: ['Cảnh báo quá tải Hub', 'Hub overload alerts'],
    dispatch: ['Điều phối phương tiện', 'Vehicle dispatch'],
    incidents: ['Sự cố thiết bị', 'Device incidents'],
    charging: ['Lập lịch sạc thông minh', 'Smart charging schedule'],
    simulation: ['Mô phỏng kịch bản What-if', 'What-if scenario simulation'],
  };
  const descriptions = {
    overview: t(
      'Theo dõi tài nguyên tại các Mobility Hub',
      'Monitor resources across Mobility Hubs',
    ),
    resources: t('Dữ liệu tài nguyên', 'Resource data'),
    interventions: t(
      'Chọn tài nguyên, nhập lý do và xác nhận trước khi phát lệnh.',
      'Select a resource, enter a reason and confirm before sending a command.',
    ),
    alerts: t(
      'Theo dõi mức sử dụng chỗ đỗ và điểm sạc theo ngưỡng đã cấu hình.',
      'Monitor parking and charging utilization against configured thresholds.',
    ),
    dispatch: t(
      'Chọn xe ở Hub nguồn và kiểm tra sức chứa tại Hub đích.',
      'Select vehicles at the source Hub and check destination capacity.',
    ),
    incidents: t(
      'Khóa thiết bị lỗi để bảo trì hoặc khôi phục sau khi hoàn tất sửa chữa.',
      'Lock faulty devices for maintenance or restore them after repairs.',
    ),
    charging: t(
      'Ưu tiên theo mức pin, nhu cầu dùng xe sắp tới và công suất Hub.',
      'Prioritize battery level, upcoming usage and Hub capacity.',
    ),
    simulation: t(
      'Thử nghiệm trên dữ liệu mạng lưới để đánh giá trước khi áp dụng.',
      'Explore a network snapshot before applying changes.',
    ),
  };
  if (!names[key]) return title('404');
  let heading = t(...names[key]),
    description = descriptions[key],
    body = '';
  const h =
    d.metrics.find((h) => h.id === state.resourceHub) ||
    d.metrics[1] ||
    d.metrics[0];
  const when = new Date(ops.now()).toLocaleTimeString();
  if (key === 'overview') {
    description += ' · ' + t('Cập nhật ', 'Updated ') + when;
    body = `<div class="op-metrics">${metric(d.hubs.length, t('Mobility Hub', 'Mobility Hubs'))}${metric(d.vehicles.filter((v) => ops.eligible(v)).length, t('Xe khả dụng', 'Available vehicles'))}${metric(
      d.metrics.reduce((n, h) => n + h.capacity - h.used, 0),
      t('Chỗ đỗ trống', 'Free parking spaces'),
    )}${metric(
      d.metrics.reduce((n, h) => n + h.availablePorts, 0),
      t('Cổng sạc khả dụng', 'Available chargers'),
    )}</div><div class="op-overview-middle"><div class="op-network-map" aria-label="${t('Sơ đồ Hub minh họa', 'Illustrative Hub map')}"><svg viewBox="0 0 550 260" aria-hidden="true"><rect width="550" height="260" fill="var(--tint)"/><path d="M80 0h390v260H80Z" fill="var(--map-block, #e7f1ee)"/><path d="M180-10l60 290M395-10 280 280M50 165 490 50M65 85l420 155" stroke="var(--surface)" stroke-width="18"/><path d="M180-10l60 290M395-10 280 280M50 165 490 50M65 85l420 155" stroke="var(--border)" stroke-width="1"/><rect x="240" y="25" width="65" height="40" rx="14" fill="#c9dfd6"/><rect x="345" y="190" width="95" height="48" rx="14" fill="#c9dfd6"/></svg>${d.hubs.map((hub, i) => `<a href="#/operator/resources" data-hub-open="${e(hub.id)}" class="op-map-pin pin-${i % 3}">⌖ ${e(hub.name)}</a>`).join('')}</div><section class="op-connection"><h2>${t('Trạng thái kết nối', 'Connection status')}</h2>${status(d.metrics.some((x) => x.stale) ? 'STALE' : 'SUCCESS')}<p>${t('Chọn Hub để xem trạng thái phương tiện, vị trí đỗ và điểm sạc.', 'Select a Hub to view vehicles, parking spaces and charging points.')}</p><a href="#/operator/resources" class="button secondary full">${t('Xem ', 'View ')}${e(h.name)}</a></section></div>${table(
      [
        t('Mobility Hub', 'Mobility Hub'),
        t('Xe khả dụng', 'Available vehicles'),
        t('Chỗ đỗ đang dùng', 'Occupied parking'),
        t('Cổng sạc trống', 'Free chargers'),
      ],
      d.metrics.map((h) => [
        `<a href="#/operator/resources" data-hub-open="${e(h.id)}">${e(h.name)}</a>`,
        String(
          d.vehicles.filter((v) => v.hubId === h.id && ops.eligible(v)).length,
        ),
        `${h.used} / ${h.capacity}`,
        String(h.availablePorts),
      ]),
    )}`;
  } else if (key === 'resources') {
    heading = h.name;
    description += ' · ' + when;
    body = `<div class="op-hub-picker">${select('resourceHub', t('Mobility Hub', 'Mobility Hub'), hubs(d), h.id)}</div><div class="op-metrics">${metric(d.vehicles.filter((v) => v.hubId === h.id && ops.eligible(v)).length, t('Xe khả dụng', 'Available vehicles'))}${metric(`${h.used} / ${h.capacity}`, t('Vị trí đang dùng', 'Occupied spaces'))}${metric(`${h.availablePorts} / ${d.ports.filter((p) => p.hubId === h.id).length}`, t('Cổng sạc khả dụng', 'Available chargers'))}${metric(`${Math.round((h.used / Math.max(1, h.capacity)) * 100)}%`, t('Tỷ lệ sử dụng chỗ đỗ', 'Parking occupancy'))}</div>${vehicleTable(
      d.vehicles.filter((v) => v.hubId === h.id),
      ops,
    )}<div class="op-columns">${table(
      [
        t('Vị trí đỗ', 'Parking space'),
        t('Loại', 'Type'),
        t('Trạng thái', 'Status'),
      ],
      d.spaces
        .filter((s) => s.hubId === h.id)
        .map((s) => [
          e(s.id),
          e(s.type),
          status(
            s.occupied ? 'IN_USE' : s.dispatchId ? 'RESERVED' : 'AVAILABLE',
          ),
        ]),
    )}${table(
      [
        t('Cổng sạc', 'Charging port'),
        t('Phương tiện', 'Vehicle'),
        t('Trạng thái', 'Status'),
      ],
      d.ports
        .filter((p) => p.hubId === h.id)
        .map((p) => [
          e(p.id),
          e(
            d.bookings.find(
              (b) => b.resourceId === p.id && b.status === 'ACTIVE',
            )?.vehicleId || '—',
          ),
          status(p.status),
        ]),
    )}</div><a class="button secondary op-action-width" href="#/operator/interventions">${t('Mở can thiệp vận hành', 'Open operational intervention')}</a>`;
  } else if (key === 'interventions') {
    const resource =
      [...d.vehicles, ...d.ports, ...d.spaces].find(
        (r) => r.id === state.resource,
      ) ||
      d.ports[0] ||
      d.vehicles[0];
    const session = d.bookings.find(
      (b) => b.resourceId === resource.id && b.status === 'ACTIVE',
    );
    body = `<div class="op-columns op-intervention">${panel(
      t('Tài nguyên cần can thiệp', 'Resource to intervene'),
      select(
        'selectedResource',
        t('Tài nguyên cần can thiệp', 'Resource'),
        resourceOptions(d),
        resource.id,
      ) +
        `<label>${t('Vị trí hiện tại', 'Current location')}<input value="${e(hubName(d, resource.hubId))}" readonly></label><dl class="op-details"><div><dt>${t('Kết nối', 'Connection')}</dt><dd>${resource.observedAt && ops.now() - resource.observedAt > 120000 ? t('Dữ liệu cũ', 'Stale data') : t('Đã cập nhật', 'Updated')}</dd></div><div><dt>${t('Trạng thái', 'Status')}</dt><dd>${e(label(resource.status || 'AVAILABLE'))}</dd></div><div><dt>${t('Phiên sạc', 'Charging session')}</dt><dd>${e(session?.id || '—')}</dd></div><div><dt>${t('Vị trí', 'Position')}</dt><dd>${e(resource.id)}</dd></div></dl><details class="op-extra"><summary>${t('Tìm tài nguyên', 'Search resources')}</summary><input type="search" data-resource-search aria-label="${t('Tìm tài nguyên', 'Search resources')}"><div class="resource-list">${resourceOptions(
          d,
        )
          .map(
            ([id, text]) =>
              `<div data-resource-row="${e(text.toLowerCase())}"><span>${e(text)}</span>${button('select-resource', t('Chọn', 'Select'), id)}</div>`,
          )
          .join(
            '',
          )}</div><p data-resource-empty hidden>${t('Không tìm thấy tài nguyên.', 'No resources found.')}</p></details>`,
    )}${panel(
      t('Lệnh can thiệp', 'Intervention command'),
      form(
        'command',
        `<input name="resource" type="hidden" value="${e(resource.id)}">` +
          select(
            'operation',
            t('Hành động', 'Action'),
            ['LOCK', 'RESTORE', 'RELEASE', 'STOP_CHARGING'].map((x) => [
              x,
              label(x),
            ]),
          ) +
          reason() +
          `<p class="hint">${t('Các lệnh theo tài nguyên: khóa/mở khóa xe, ngắt sạc, hủy lượt giữ tài nguyên.', 'Available actions depend on the resource: lock/unlock, stop charging or release a hold.')}</p><div class="op-warning"><strong>${t('Xác nhận từ thiết bị', 'Device confirmation')}</strong><p>${t('Chỉ hiển thị thành công sau phản hồi IoT. Không khóa xe đang chạy chuyến đi.', 'Success appears only after IoT acknowledgement. Never lock a vehicle during a ride.')}</p></div>` +
          submit(
            t('Kiểm tra và xác nhận lệnh', 'Validate and confirm command'),
          ) +
          outcome(),
      ),
    )}</div>${d.operatorCommands.length ? commandTable(d) : ''}${auditTable(d)}`;
  } else if (key === 'alerts') {
    const rows = d.metrics.filter((h) => h.severity !== 'NORMAL' || h.stale);
    body = `<div class="op-warning"><strong>${t('Theo dõi nguy cơ quá tải', 'Monitor overload risk')}</strong><p>${t('Ngưỡng cảnh báo', 'Warning threshold')} ${d.thresholds.warning * 100}% · ${t('Nghiêm trọng', 'Critical')} ${d.thresholds.critical * 100}%</p></div>${table(
      [
        t('Hub', 'Hub'),
        t('Sử dụng chỗ đỗ', 'Parking usage'),
        t('Sử dụng cổng sạc', 'Charging usage'),
        t('Mức cảnh báo', 'Alert level'),
      ],
      rows.map((h) => [
        e(h.name),
        `${h.used} / ${h.capacity} · ${Math.round((h.used / Math.max(1, h.capacity)) * 100)}%`,
        `${h.charging} / ${d.ports.filter((p) => p.hubId === h.id).length}`,
        status(h.severity) + (h.stale ? status('STALE') : ''),
      ]),
    )}<div class="op-action-row"><a class="button primary" href="#/operator/dispatch">${t('Điều phối phương tiện', 'Dispatch vehicles')}</a><a class="button secondary" href="#/operator/simulation">${t('Mô phỏng phương án', 'Simulate options')}</a><a class="button secondary" href="#/operator/resources">${t('Xem chi tiết Hub', 'View Hub details')}</a></div>`;
  } else if (key === 'dispatch') {
    const order = d.orders.find((o) => o.id === state.order);
    if (order) {
      heading = t('Lệnh điều phối ', 'Dispatch order ') + order.id;
      description = t('Đã khởi tạo · ', 'Created · ') + date(order.at);
      const target = d.metrics.find((h) => h.id === order.target);
      body =
        status(order.state) +
        `<div class="op-metrics">${metric(order.vehicleIds.length, t('Phương tiện', 'Vehicles'))}${metric(hubName(d, order.source), t('Hub nguồn', 'Source Hub'))}${metric(hubName(d, order.target), t('Hub đích', 'Destination Hub'))}${metric(target.capacity - target.used, t('Chỗ đỗ còn lại', 'Remaining spaces'))}</div>${table(
          [
            t('Xe', 'Vehicle'),
            t('Hub đi', 'From'),
            t('Hub đến', 'To'),
            t('Trạng thái', 'Status'),
          ],
          order.vehicleIds.map((id) => [
            e(id),
            e(hubName(d, order.source)),
            e(hubName(d, order.target)),
            e(label(order.state)),
          ]),
        )}<div class="op-action-row"><a class="button secondary" href="#/operator/overview">${t('Về giám sát mạng lưới', 'Back to network monitoring')}</a>${button('new-dispatch', t('Tạo lệnh khác', 'New dispatch'))}${order.state === 'IN_TRANSIT' ? button('arrival', t('Xác nhận đã đến', 'Confirm arrival'), order.id) + button('cancel-dispatch', t('Hủy lệnh', 'Cancel order'), order.id) : ''}</div>`;
    } else {
      const source = state.source || d.hubs[0].id,
        target = state.target || d.hubs[1].id;
      body =
        form(
          'dispatch',
          `<div class="op-dispatch-fields">${select('source', t('Hub nguồn', 'Source Hub'), hubs(d), source)}${select('target', t('Hub đích', 'Destination Hub'), hubs(d), target)}<label>${t('Số xe đã chọn', 'Selected vehicles')}<input data-dispatch-count value="0" readonly></label></div>${table(
            [
              t('Chọn', 'Select'),
              t('Mã xe', 'Vehicle ID'),
              t('Pin', 'Battery'),
              t('Trạng thái', 'Status'),
              t('Đủ điều kiện', 'Eligible'),
            ],
            d.vehicles
              .filter((v) => v.hubId === source && !v.personal)
              .map((v) => [
                `<input type="checkbox" name="vehicles" value="${e(v.id)}" aria-label="${e(v.id)}" ${state.proposedVehicles?.includes(v.id) ? 'checked' : ''} ${ops.eligible(v) ? '' : 'disabled'}>`,
                e(v.id),
                `${v.battery}%`,
                e(label(v.status)),
                ops.eligible(v) ? t('Có', 'Yes') : t('Không', 'No'),
              ]),
          )}<div class="op-capacity" role="status" data-capacity></div>${submit(t('Xác nhận tạo lệnh điều phối', 'Confirm dispatch order'))}`,
        ) +
        `<details class="op-extra"><summary>${t('Lịch sử điều phối', 'Dispatch history')}</summary>${table(
          [
            t('Mã lệnh', 'Order'),
            t('Tuyến', 'Route'),
            t('Trạng thái', 'Status'),
          ],
          d.orders.map((o) => [
            button('view-order', o.id, o.id),
            e(hubName(d, o.source) + ' → ' + hubName(d, o.target)),
            status(o.state),
          ]),
        )}</details>`;
    }
  } else if (key === 'incidents') {
    const incident =
      d.incidents.find((i) => i.id === state.incident) ||
      d.incidents.find((i) => i.status === 'OPEN') ||
      d.incidents[0];
    body = table(
      [
        t('Thiết bị', 'Device'),
        t('Hub', 'Hub'),
        t('Cảnh báo', 'Alert'),
        t('Trạng thái', 'Status'),
      ],
      d.incidents.map((i) => [
        button('view-incident', i.resourceId, i.id),
        e(
          hubName(
            d,
            [...d.vehicles, ...d.ports].find((r) => r.id === i.resourceId)
              ?.hubId,
          ),
        ),
        e(i.reason),
        e(label(i.status)),
      ]),
    );
    if (incident)
      body += `<div class="op-columns op-incident-columns">${panel(incident.resourceId + ' · ' + incident.reason, `<dl class="op-details"><div><dt>${t('Nguồn cảnh báo', 'Alert source')}</dt><dd>${t('Hệ thống demo', 'Demo system')}</dd></div><div><dt>${t('Trạng thái', 'Status')}</dt><dd>${e(label(incident.status))}</dd></div></dl><p class="hint">${t('Đưa vào bảo trì sẽ khóa thiết bị và loại xe khỏi danh sách có thể đặt sau khi IoT xác nhận.', 'Maintenance locks the device and removes it from availability after IoT acknowledgement.')}</p>${['OPEN', 'DISMISSED'].includes(incident.status) ? button('maintenance', t('Đưa vào bảo trì', 'Send to maintenance'), incident.id, 'class="primary full"') : ''}`)}${panel(
        t('Thao tác theo tình trạng', 'Actions by status'),
        d.incidents
          .filter((i) => i.status === 'MAINTENANCE')
          .map((i) =>
            button(
              'recover',
              t('Khôi phục ', 'Restore ') + i.resourceId,
              i.id,
              'class="secondary full"',
            ),
          )
          .join('') +
          (incident.status === 'OPEN'
            ? button(
                'dismiss',
                t('Bỏ qua cảnh báo nhẹ', 'Dismiss minor alert'),
                incident.id,
                'class="secondary full"',
              )
            : ''),
      )}</div>`;
    body += `<details class="op-extra"><summary>${t('Ghi nhận sự cố', 'Report incident')}</summary>${form(
      'incident',
      select(
        'resource',
        t('Thiết bị', 'Device'),
        resourceOptions({ ...d, spaces: [] }),
      ) +
        select(
          'severity',
          t('Mức độ', 'Severity'),
          ['WARNING', 'CRITICAL'].map((x) => [x, label(x)]),
        ) +
        reason() +
        submit(t('Ghi nhận', 'Record')),
    )}</details>${d.operatorCommands.length ? commandTable(d) : ''}`;
  } else if (key === 'charging') {
    const p = d.proposal;
    body = form(
      'schedule',
      `<div class="op-schedule-fields">${select('hub', 'Mobility Hub', hubs(d), state.chargingHub || 'campus')}${submit(t('Tạo / cập nhật đề xuất', 'Create / update proposal'))}</div><details class="op-extra"><summary>${t('Thời gian bắt đầu', 'Start time')}</summary>${input('start', t('Bắt đầu', 'Start'), 'datetime-local', localDate(ops.now() + 60000), 'required')}</details>`,
    );
    if (p) {
      body += table(
        [
          t('Ưu tiên', 'Priority'),
          t('Xe / Pin', 'Vehicle / Battery'),
          t('Nhu cầu sử dụng', 'Usage demand'),
          t('Cổng sạc', 'Charging port'),
          t('Thời gian', 'Time'),
        ],
        [
          ...p.slots.map((s, i) => [
            String(i + 1),
            `${e(s.vehicleId)} · ${d.vehicles.find((v) => v.id === s.vehicleId)?.battery}%`,
            t('Sạc đến 80%', 'Charge to 80%'),
            e(s.portId),
            e(date(s.start) + ' – ' + date(s.end)),
          ]),
          ...p.queue.map((id, i) => [
            String(p.slots.length + i + 1),
            e(id),
            '—',
            t('Hàng đợi', 'Queue'),
            '—',
          ]),
        ],
      );
      if (p.queue.length)
        body += `<div class="op-warning"><strong>${p.queue.length} ${t('xe đang chờ phân bổ', 'vehicles waiting for allocation')}</strong><p>${t('Công suất hiện tại chưa đáp ứng toàn bộ nhu cầu. Có thể điều phối bớt xe sang Hub lân cận.', 'Current capacity cannot meet all demand. Consider dispatching vehicles to a nearby Hub.')}</p></div>`;
      body += `<div class="op-action-row">${button('toggle-slots', t('Điều chỉnh khung giờ', 'Adjust time slots'), '', 'class="secondary"')}<a href="#/operator/dispatch" class="button secondary">${t('Điều phối xe sang Hub khác', 'Dispatch to another Hub')}</a>${p.state === 'DRAFT' && p.slots.length ? button('confirm-schedule', t('Xác nhận và lưu lịch', 'Confirm and save schedule'), '', 'class="primary"') : status(p.state)}</div>`;
      if (state.editSlots)
        body += p.slots
          .map((s) =>
            panel(
              s.vehicleId,
              form(
                'edit-slot',
                select(
                  'port',
                  t('Cổng', 'Port'),
                  d.ports
                    .filter((x) => x.hubId === p.hubId)
                    .map((x) => [x.id, x.id]),
                  s.portId,
                ) +
                  input(
                    'start',
                    t('Bắt đầu', 'Start'),
                    'datetime-local',
                    localDate(s.start),
                    'required',
                  ) +
                  input(
                    'end',
                    t('Kết thúc', 'End'),
                    'datetime-local',
                    localDate(s.end),
                    'required',
                  ) +
                  (p.state === 'DRAFT'
                    ? submit(t('Lưu khung giờ', 'Save slot'))
                    : ''),
                s.id,
              ),
            ),
          )
          .join('');
    } else
      body += `<div class="empty">${t('Chọn Hub và tạo đề xuất để xem lịch sạc.', 'Choose a Hub and create a proposal to see charging slots.')}</div>`;
  } else if (key === 'simulation') {
    const sim = d.simulation;
    if (sim && !state.newSimulation) {
      heading = t('Kết quả mô phỏng', 'Simulation results');
      description =
        hubName(d, sim.hubId) + ' · ' + sim.hours + t(' giờ', ' hours');
      const m = d.metrics.find((h) => h.id === sim.hubId);
      body = `<div class="op-metrics">${metric(sim.failed ? 1 : 0, t('Hub có nguy cơ thiếu xe', 'Hubs at risk of shortage'))}${metric(sim.failed, t('Lượt không đặt được xe', 'Unserved requests'))}${metric(Math.round((sim.peak / Math.max(1, m.capacity)) * 100) + '%', t('Mức dùng chỗ đỗ cao nhất', 'Peak parking usage'))}${metric((sim.timeline.find((x) => x.occupancy === sim.peak)?.hour ?? 0) + 'h', t('Thời điểm cao điểm', 'Peak time'))}</div>${table([t('Phương án đề xuất', 'Proposed option'), t('Tài nguyên', 'Resources'), t('Tác động dự kiến', 'Expected impact')], sim.plan ? [[t('Điều chuyển ', 'Dispatch ') + sim.plan.vehicleIds.length + t(' xe', ' vehicles'), e(hubName(d, sim.plan.source) + ' → ' + hubName(d, sim.plan.target)), t('Tăng khả năng đáp ứng nhu cầu', 'Improve availability')]] : [])}<p class="hint">${t('Chọn phương án để mở màn hình xử lý với thông tin đã điền sẵn.', 'Select a proposal to open its form with prefilled information.')}</p><div class="op-action-row">${button('save-current-scenario', t('Lưu kịch bản', 'Save scenario'), '', 'class="secondary"')}${sim.plan && !sim.applied ? button('prepare-simulation', t('Áp dụng điều phối đề xuất', 'Apply proposed dispatch'), '', 'class="primary"') : ''}${button('simulation-charging', t('Áp dụng lịch sạc đề xuất', 'Apply proposed charging'), '', 'class="secondary"')}</div><div class="op-action-row">${button('new-simulation', t('Kịch bản mới', 'New scenario'))}</div>`;
    } else {
      const hub = state.simulationHub || d.hubs[0].id,
        m = d.metrics.find((x) => x.id === hub);
      body = `<div class="op-columns">${panel(t('Thiết lập nhu cầu giả định', 'Configure hypothetical demand'), form('simulate', select('hub', 'Mobility Hub', hubs(d), hub) + input('hours', t('Khoảng thời gian mô phỏng (giờ)', 'Simulation horizon (hours)'), 'number', '2', 'min="1" max="24" step="1" required') + input('arrivals', t('Số xe đến giả định', 'Hypothetical arrivals'), 'number', '5', 'min="0" max="100" step="1" required') + input('departures', t('Số lượt cần xe giả định', 'Hypothetical departure demand'), 'number', '4', 'min="0" max="100" step="1" required') + submit(t('Bắt đầu mô phỏng', 'Start simulation'))))}${panel(t('Dữ liệu nền', 'Baseline data'), `<dl class="op-details"><div><dt>${t('Cập nhật lúc', 'Updated at')}</dt><dd>${e(when)}</dd></div><div><dt>${t('Xe khả dụng', 'Available vehicles')}</dt><dd>${d.vehicles.filter((v) => v.hubId === hub && ops.eligible(v)).length}</dd></div><div><dt>${t('Chỗ đỗ trống', 'Free parking spaces')}</dt><dd>${m.capacity - m.used}</dd></div><div><dt>${t('Cổng sạc khả dụng', 'Available chargers')}</dt><dd>${m.availablePorts}</dd></div></dl><p class="hint">${t('Các tham số thay đổi chỉ phục vụ kịch bản mô phỏng.', 'Changed parameters apply only to the simulation.')}</p>`)}</div>`;
    }
    if (d.scenarios.length)
      body += `<details class="op-extra"><summary>${t('Kịch bản đã lưu', 'Saved scenarios')}</summary>${table(
        [t('Tên', 'Name'), t('Thời điểm', 'Time'), t('Thao tác', 'Action')],
        d.scenarios.map((s) => [
          e(s.name),
          e(date(s.at)),
          button('load-scenario', t('Chạy lại', 'Rerun'), s.id),
        ]),
      )}</details>`;
  }
  return `<section id="operator-page" data-view="${e(key)}">${title(heading, description)}<div id="op-feedback" role="status" tabindex="-1" hidden></div>${body}</section>`;
}

function vehicleTable(vehicles, ops) {
  return table(
    [
      t('Xe', 'Vehicle'),
      t('Pin', 'Battery'),
      t('Trạng thái', 'Status'),
      t('Dữ liệu', 'Data'),
    ],
    vehicles.map((v) => [
      e(v.id),
      v.battery + '%',
      status(v.status),
      ops.now() - v.observedAt > 120000
        ? status('STALE')
        : e(date(v.observedAt)),
    ]),
  );
}
function commandTable(d) {
  return panel(
    t('Theo dõi lệnh thiết bị', 'Device command tracking'),
    table(
      [
        t('Mã lệnh', 'Command'),
        t('Tài nguyên', 'Resource'),
        t('Thao tác', 'Action'),
        t('Trạng thái', 'Status'),
        t('Đối soát demo', 'Demo reconciliation'),
      ],
      d.operatorCommands.map((c) => [
        e(c.id),
        e(c.resourceId),
        e(label(c.action)),
        status(c.state),
        c.state === 'TIMEOUT'
          ? button(
              'reconcile-success',
              t('Giả lập ACK thành công', 'Simulate success ACK'),
              c.id,
            ) +
            button(
              'reconcile-failed',
              t('Giả lập ACK thất bại', 'Simulate failed ACK'),
              c.id,
            )
          : '—',
      ]),
    ),
  );
}
function auditTable(d) {
  return panel(
    t('Nhật ký can thiệp', 'Intervention audit'),
    table(
      [
        t('Thời điểm', 'Time'),
        t('Người thực hiện', 'Actor'),
        t('Tài nguyên', 'Resource'),
        t('Thao tác', 'Action'),
        t('Kết quả', 'Result'),
        t('Lý do', 'Reason'),
      ],
      d.audit
        .slice(0, 50)
        .map((a) => [
          e(date(a.at)),
          e(a.actor),
          e(a.resource),
          e(label(a.action)),
          status(a.result),
          e(a.reason),
        ]),
    ),
  );
}

export function bindOperator(root, ops, route, state, refresh) {
  const section = root.querySelector('#operator-page');
  if (!section) return () => {};
  let pending = false;
  let discardDraft = false;
  state.drafts ||= {};
  const draftKey = route;
  const saveDraft = () => {
    if (discardDraft) return;
    state.drafts[draftKey] = [...section.querySelectorAll('form')].map((f) => ({
      key: f.dataset.operatorForm + ':' + f.dataset.id,
      fields: [...f.elements]
        .filter((el) => el.name)
        .map((el) => ({ name: el.name, value: el.value, checked: el.checked })),
    }));
  };
  for (const saved of state.drafts[draftKey] || []) {
    const f = [...section.querySelectorAll('form')].find(
      (f) => f.dataset.operatorForm + ':' + f.dataset.id === saved.key,
    );
    if (!f) continue;
    for (const field of saved.fields)
      for (const el of f.elements)
        if (el.name === field.name) {
          if (el.type === 'checkbox') {
            if (el.value === field.value && !el.disabled)
              el.checked = field.checked;
          } else el.value = field.value;
        }
  }
  const feedback = (text, error = false) => {
    const box = section.querySelector('#op-feedback');
    if (!box) return;
    box.hidden = false;
    box.className = 'feedback ' + (error ? 'error' : 'success');
    box.textContent = text;
    box.focus();
  };
  const run = async (fn, confirmation = true) => {
    if (pending) return;
    pending = true;
    try {
      if (
        confirmation &&
        !(await confirmAction(
          t(
            'Xác nhận thao tác trên dữ liệu mô phỏng? Điều kiện tài nguyên sẽ được kiểm tra lại.',
            'Confirm this operation on simulated data? Resource conditions will be rechecked.',
          ),
        ))
      )
        return;
      if (!section.isConnected) return;
      await fn();
      discardDraft = true;
      delete state.drafts[draftKey];
      refresh();
      const box = root.querySelector('#op-feedback');
      if (box) {
        box.hidden = false;
        box.className = 'feedback success';
        box.textContent = t(
          'Đã xử lý yêu cầu. Kiểm tra trạng thái bên dưới.',
          'Request processed. Check the status below.',
        );
      }
    } catch (err) {
      feedback(msg(err.code), true);
    } finally {
      pending = false;
    }
  };
  section.addEventListener('submit', (event) => {
    event.preventDefault();
    event.stopPropagation();
    const f = event.target,
      kind = f.dataset.operatorForm;
    if (!kind) return;
    const v = new FormData(f),
      value = (name) => v.get(name),
      time = (name) => new Date(value(name)).getTime();
    run(() => {
      if (kind === 'save-scenario') ops.saveScenario(value('name'));
      if (kind === 'command') {
        state.resource = value('resource');
        ops.command(
          value('resource'),
          value('operation'),
          value('reason'),
          value('outcome'),
        );
      }
      if (kind === 'incident')
        ops.recordIncident(
          value('resource'),
          value('reason'),
          value('severity'),
        );
      if (kind === 'dispatch')
        state.order = ops.dispatch(
          value('source'),
          value('target'),
          v.getAll('vehicles'),
        ).id;
      if (kind === 'schedule') {
        state.chargingHub = value('hub');
        ops.buildSchedule(value('hub'), time('start'));
      }
      if (kind === 'edit-slot')
        ops.editSlot(f.dataset.id, value('port'), time('start'), time('end'));
      if (kind === 'simulate') {
        state.newSimulation = false;
        ops.simulate(
          value('hub'),
          Number(value('arrivals')),
          Number(value('departures')),
          Number(value('hours')),
        );
      }
    }, !['schedule', 'edit-slot', 'simulate'].includes(kind));
  });
  const updateCapacity = () => {
    const count = section.querySelector('[data-dispatch-count]');
    if (!count) return;
    const selected = section.querySelectorAll('[name=vehicles]:checked').length;
    count.value = String(selected);
    const target = ops
      .snapshot()
      .metrics.find(
        (h) => h.id === section.querySelector('[name=target]').value,
      );
    const remaining = target.capacity - target.used - selected;
    const box = section.querySelector('[data-capacity]');
    box.classList.toggle('error', remaining < 0);
    box.textContent =
      remaining >= 0
        ? t(
            `Hub đích còn ${remaining} chỗ sau khi tiếp nhận ${selected} xe.`,
            `Destination has ${remaining} spaces after receiving ${selected} vehicles.`,
          )
        : t('Hub đích không đủ chỗ đỗ.', 'Insufficient destination parking.');
  };
  updateCapacity();
  section.addEventListener('click', (event) => {
    const hubLink = event.target.closest('[data-hub-open]');
    if (hubLink) state.resourceHub = hubLink.dataset.hubOpen;
    const b = event.target.closest('[data-op]');
    if (!b) return;
    event.stopPropagation();
    const a = b.dataset.op,
      id = b.dataset.id;
    if (a === 'select-resource') {
      state.resource = id;
      discardDraft = true;
      delete state.drafts[draftKey];
      refresh();
      return;
    }
    if (
      [
        'view-order',
        'new-dispatch',
        'view-incident',
        'toggle-slots',
        'new-simulation',
        'prepare-simulation',
      ].includes(a)
    ) {
      if (a === 'view-order') state.order = id;
      if (a === 'new-dispatch') {
        state.order = null;
        state.proposedVehicles = [];
      }
      if (a === 'view-incident') state.incident = id;
      if (a === 'toggle-slots') state.editSlots = !state.editSlots;
      if (a === 'new-simulation') state.newSimulation = true;
      if (a === 'prepare-simulation') {
        const plan = ops.snapshot().simulation?.plan;
        if (!plan) return;
        state.source = plan.source;
        state.target = plan.target;
        state.proposedVehicles = [...plan.vehicleIds];
        state.order = null;
        delete state.drafts['/operator/dispatch'];
        location.hash = '/operator/dispatch';
      }
      refresh();
      return;
    }
    run(() => {
      if (a === 'save-current-scenario')
        ops.saveScenario(t('Kịch bản ', 'Scenario ') + date(ops.now()));
      if (a === 'refresh') ops.poll();
      if (a === 'load-scenario') {
        ops.loadScenario(id);
        state.newSimulation = false;
      }
      if (a === 'simulation-charging') {
        state.chargingHub = ops.snapshot().simulation.hubId;
        ops.buildSchedule(state.chargingHub, ops.now() + 60000);
        location.hash = '/operator/charging';
      }
      if (a === 'arrival' || a === 'cancel-dispatch')
        ops.finishDispatch(id, a === 'cancel-dispatch');
      if (a === 'dismiss') ops.dismiss(id);
      if (a === 'maintenance' || a === 'recover') {
        const i = ops.snapshot().incidents.find((i) => i.id === id);
        ops.command(
          i.resourceId,
          a === 'maintenance' ? 'MAINTENANCE' : 'RESTORE',
          i.reason,
          'SUCCESS',
          id,
        );
      }
      if (a.startsWith('reconcile-'))
        ops.reconcile(id, a === 'reconcile-success' ? 'SUCCESS' : 'FAILED');
      if (a === 'confirm-schedule') ops.confirmSchedule();
      if (a === 'apply-simulation') ops.applySimulation();
    }, a !== 'refresh');
  });
  section.addEventListener('change', (event) => {
    saveDraft();
    updateCapacity();
    if (event.target.name === 'resourceHub') {
      state.resourceHub = event.target.value;
      refresh();
    }
    if (event.target.name === 'selectedResource') {
      state.resource = event.target.value;
      discardDraft = true;
      delete state.drafts[draftKey];
      refresh();
    }
    if (event.target.name === 'hub' && route === '/operator/simulation') {
      state.simulationHub = event.target.value;
      refresh();
    }
    if (event.target.name === 'overviewHub') {
      state.hub = event.target.value;
      refresh();
    }
    if (event.target.name === 'source') {
      discardDraft = true;
      delete state.drafts[draftKey];
      state.source = event.target.value;
      state.target = section.querySelector('[name="target"]').value;
      refresh();
    }
    if (event.target.name === 'target') state.target = event.target.value;
  });
  section.addEventListener('input', (event) => {
    saveDraft();
    if (!event.target.matches('[data-resource-search]')) return;
    const query = event.target.value.trim().toLowerCase();
    let visible = 0;
    section.querySelectorAll('[data-resource-row]').forEach((row) => {
      row.hidden = !row.dataset.resourceRow.includes(query);
      if (!row.hidden) visible++;
    });
    section.querySelector('[data-resource-empty]').hidden = visible > 0;
  });
  const timer = setInterval(() => {
    if (pending || !section.isConnected) return;
    try {
      if (
        ops.poll() &&
        ['/operator/interventions', '/operator/incidents'].includes(route)
      )
        refresh();
    } catch {
      /* Session guard is handled by the main router. */
    }
  }, 500);
  return () => {
    saveDraft();
    clearInterval(timer);
  };
}
