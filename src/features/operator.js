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
  rows.length
    ? `<div class="table-scroll"><table class="op-table"><thead><tr>${headers.map((x) => `<th scope="col">${e(x)}</th>`).join('')}</tr></thead><tbody>${rows.map((row) => `<tr>${row.map((x) => `<td>${x}</td>`).join('')}</tr>`).join('')}</tbody></table>`
    : `<p class="empty">${t('Chưa có dữ liệu phù hợp.', 'No matching data yet.')}</p>`;
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
    overview: ['UC08 · Tổng quan mạng lưới', 'UC08 · Network overview'],
    interventions: [
      'UC09 · Can thiệp tài nguyên',
      'UC09 · Resource interventions',
    ],
    alerts: ['UC10 · Cảnh báo quá tải', 'UC10 · Overload alerts'],
    dispatch: ['UC11 · Điều phối phương tiện', 'UC11 · Vehicle dispatch'],
    incidents: ['UC12 · Quản lý sự cố', 'UC12 · Incident management'],
    charging: ['UC13 · Lập lịch sạc', 'UC13 · Charging schedule'],
    simulation: ['UC14 · Mô phỏng What-if', 'UC14 · What-if simulation'],
  };
  if (!names[key]) return title('404');
  let body = '';
  if (key === 'overview') {
    const list = d.metrics.filter(
      (h) => !state.hub || state.hub === 'all' || h.id === state.hub,
    );
    body = `<div class="op-toolbar">${select('overviewHub', t('Lọc Hub', 'Filter Hub'), [['all', t('Tất cả Hub', 'All Hubs')], ...hubs(d)], state.hub || 'all')}${button('refresh', t('Làm mới', 'Refresh'))}<span>${t('Ảnh chụp lúc', 'Snapshot at')} ${e(date(ops.now()))}</span></div><div class="op-metrics">${metric(d.hubs.length, t('Mobility Hub', 'Mobility Hubs'))}${metric(d.vehicles.filter((v) => ops.eligible(v)).length, t('Xe sẵn sàng', 'Ready vehicles'))}${metric(
      d.metrics.reduce((n, h) => n + h.capacity - h.used, 0),
      t('Chỗ đỗ còn lại', 'Free parking spaces'),
    )}${metric(d.incidents.filter((i) => i.status === 'OPEN').length, t('Sự cố đang mở', 'Open incidents'))}</div><div class="op-hub-grid">${list
      .map(
        (h) =>
          `<article class="panel op-hub"><div class="op-heading"><h2>${e(h.name)}</h2>${status(h.severity)}</div><p>${e(h.address)}</p><label>${t('Chỗ đỗ đã sử dụng / giữ', 'Occupied / held parking')} <strong>${h.used}/${h.capacity}</strong><meter min="0" max="${h.capacity}" value="${h.used}">${h.used}/${h.capacity}</meter></label><dl><dt>${t('Phương tiện', 'Vehicles')}</dt><dd>${h.vehicles}</dd><dt>${t('Cổng sạc khả dụng', 'Available ports')}</dt><dd>${h.availablePorts}/${h.ports}</dd><dt>${t('Đang sạc', 'Charging')}</dt><dd>${h.charging}</dd></dl>${h.stale ? `<p class="op-warning">${t('Có telemetry cũ. Kiểm tra từng xe trước khi điều phối.', 'Some telemetry is stale. Check vehicles before dispatch.')}</p>` : ''}<details><summary>${t('Chi tiết tài nguyên', 'Resource details')}</summary>${vehicleTable(
            d.vehicles.filter((v) => v.hubId === h.id),
            ops,
          )}</details></article>`,
      )
      .join('')}</div>`;
  } else if (key === 'interventions') {
    body = `<div class="op-columns">${panel(
      t('Tài nguyên', 'Resources'),
      `<label>${t('Tìm mã tài nguyên hoặc Hub', 'Search resource ID or Hub')}<input type="search" data-resource-search placeholder="E-024, metro-C1…"></label><div class="resource-list">${resourceOptions(
        d,
      )
        .map(
          ([id, text]) =>
            `<div data-resource-row="${e(text.toLowerCase())}"><span>${e(text)}</span>${button('select-resource', t('Chọn', 'Select'), id)}</div>`,
        )
        .join(
          '',
        )}</div><p data-resource-empty hidden>${t('Không tìm thấy tài nguyên.', 'No resources found.')}</p>`,
    )}${panel(
      t('Gửi yêu cầu can thiệp', 'Request intervention'),
      form(
        'command',
        select(
          'resource',
          t('Tài nguyên được chọn', 'Selected resource'),
          resourceOptions(d),
          state.resource,
        ) +
          select(
            'operation',
            t('Thao tác', 'Action'),
            ['LOCK', 'RESTORE', 'RELEASE', 'STOP_CHARGING'].map((x) => [
              x,
              label(x),
            ]),
          ) +
          reason() +
          outcome() +
          submit(t('Kiểm tra và gửi lệnh', 'Validate and send')),
      ) +
        `<p class="muted">${t('Không khóa xe đang chạy. Gửi lệnh chưa đồng nghĩa thiết bị đã thực hiện.', 'Moving vehicles cannot be locked. Dispatching a command is not device confirmation.')}</p>`,
    )}</div>${commandTable(d)}${auditTable(d)}`;
  } else if (key === 'alerts') {
    const rows = d.metrics.filter((h) => h.severity !== 'NORMAL' || h.stale);
    body =
      panel(
        t('Ngưỡng sử dụng', 'Utilization thresholds'),
        `<p>${t('Cảnh báo từ', 'Warning from')} ${d.thresholds.warning * 100}% · ${t('Nghiêm trọng từ', 'Critical from')} ${d.thresholds.critical * 100}%. ${t('Ngưỡng minh họa; dữ liệu cũ được đánh dấu riêng.', 'Illustrative thresholds; stale data is flagged separately.')}</p>`,
      ) +
      table(
        [
          t('Hub', 'Hub'),
          t('Mức độ', 'Severity'),
          t('Chỗ đỗ', 'Parking'),
          t('Sạc', 'Charging'),
          t('Xử lý', 'Actions'),
        ],
        rows.map((h) => [
          e(h.name),
          status(h.severity) + (h.stale ? status('STALE') : ''),
          `${h.used}/${h.capacity}`,
          `${h.charging}/${h.availablePorts}`,
          `<a class="button" href="#/operator/dispatch">${t('Điều phối', 'Dispatch')}</a> <a class="button" href="#/operator/simulation">${t('Mô phỏng', 'Simulate')}</a>`,
        ]),
      );
  } else if (key === 'dispatch') {
    const source = state.source || d.hubs[0].id;
    body =
      panel(
        t('Tạo lệnh điều phối', 'Create dispatch order'),
        form(
          'dispatch',
          `<div class="form-grid">${select('source', t('Hub nguồn', 'Source Hub'), hubs(d), source)}${select('target', t('Hub đích', 'Destination Hub'), hubs(d), state.target || d.hubs[1].id)}</div><fieldset><legend>${t('Chọn xe khả dụng', 'Select available vehicles')}</legend>${d.vehicles
            .filter((v) => v.hubId === source && !v.personal)
            .map(
              (v) =>
                `<label class="op-check"><input type="checkbox" name="vehicles" value="${e(v.id)}" ${ops.eligible(v) ? '' : 'disabled'}><span>${e(v.id)} · ${v.battery}% · ${e(label(v.status))}${ops.now() - v.observedAt > 120000 ? ' · ' + t('Dữ liệu cũ', 'Stale data') : ''}</span></label>`,
            )
            .join(
              '',
            )}</fieldset><p>${t('Chỗ đích được giữ trước khi tạo lệnh. Xe chỉ đổi Hub khi xác nhận đã đến.', 'Destination spaces are held before dispatch. Vehicles change Hub only after arrival confirmation.')}</p>${submit(t('Xem và xác nhận điều phối', 'Review and dispatch'))}`,
        ),
      ) +
      panel(
        t('Lịch sử điều phối', 'Dispatch history'),
        table(
          [
            t('Mã lệnh', 'Order'),
            t('Tuyến', 'Route'),
            t('Xe', 'Vehicles'),
            t('Trạng thái', 'Status'),
            t('Thao tác', 'Actions'),
          ],
          d.orders.map((o) => [
            e(o.id),
            e(hubName(d, o.source) + ' → ' + hubName(d, o.target)),
            e(o.vehicleIds.join(', ')),
            status(o.state),
            o.state === 'IN_TRANSIT'
              ? button(
                  'arrival',
                  t('Xác nhận đã đến', 'Confirm arrival'),
                  o.id,
                ) +
                button('cancel-dispatch', t('Hủy lệnh', 'Cancel order'), o.id)
              : '—',
          ]),
        ),
      );
  } else if (key === 'incidents') {
    body =
      panel(
        t('Ghi nhận sự cố', 'Report incident'),
        form(
          'incident',
          `<div class="form-grid">${select('resource', t('Thiết bị', 'Device'), resourceOptions({ ...d, spaces: [] }))}${select(
            'severity',
            t('Mức độ', 'Severity'),
            ['WARNING', 'CRITICAL'].map((x) => [x, label(x)]),
          )}</div>${reason()}${submit(t('Ghi nhận', 'Record'))}`,
        ),
      ) +
      panel(
        t('Danh sách sự cố', 'Incident list'),
        table(
          [
            t('Sự cố', 'Incident'),
            t('Thiết bị', 'Device'),
            t('Mô tả', 'Description'),
            t('Trạng thái', 'Status'),
            t('Xử lý', 'Actions'),
          ],
          d.incidents.map((i) => [
            e(i.id) + ' ' + status(i.severity),
            e(i.resourceId),
            e(i.reason),
            status(i.status),
            ['OPEN', 'DISMISSED'].includes(i.status)
              ? button(
                  'maintenance',
                  t('Đưa vào bảo trì', 'Send to maintenance'),
                  i.id,
                ) +
                (i.status === 'OPEN'
                  ? button(
                      'dismiss',
                      t('Bỏ qua cảnh báo', 'Dismiss alert'),
                      i.id,
                    )
                  : '')
              : i.status === 'MAINTENANCE'
                ? button(
                    'recover',
                    t('Khôi phục thiết bị', 'Restore device'),
                    i.id,
                  )
                : '—',
          ]),
        ),
      ) +
      `<p class="muted">${t('Bỏ qua cảnh báo không sửa lỗi thiết bị hoặc mở lại tài nguyên. Lệnh bảo trì/khôi phục cần ACK.', 'Dismissing an alert does not repair or reopen a resource. Maintenance and recovery require ACK.')}</p>${commandTable(d)}`;
  } else if (key === 'charging') {
    body = panel(
      t('Tạo đề xuất lịch sạc', 'Build charging proposal'),
      form(
        'schedule',
        `<div class="form-grid">${select('hub', t('Hub', 'Hub'), hubs(d), state.chargingHub || 'campus')}${input('start', t('Bắt đầu', 'Start'), 'datetime-local', localDate(ops.now() + 60000), 'required')}</div><p>${t('Mô hình mẫu: ưu tiên pin thấp, mục tiêu 80%, 1,5 phút cho mỗi 1% pin; mỗi cổng 7 kW. Không phải dự báo tối ưu thực tế.', 'Sample model: lowest battery first, 80% target, 1.5 minutes per 1% charge; 7 kW per port. This is not a production optimizer.')}</p>${submit(t('Tạo đề xuất', 'Build proposal'))}`,
      ),
    );
    const p = d.proposal;
    if (p)
      body += panel(
        t('Đề xuất', 'Proposal') + ' ' + p.id,
        `<p>${e(hubName(d, p.hubId))} ${status(p.state)}</p>${
          p.slots
            .map((s) =>
              form(
                'edit-slot',
                `<strong>${e(s.vehicleId)}</strong><div class="form-grid">${select(
                  'port',
                  t('Cổng', 'Port'),
                  d.ports
                    .filter((x) => x.hubId === p.hubId)
                    .map((x) => [x.id, x.id]),
                  s.portId,
                )}${input('start', t('Bắt đầu', 'Start'), 'datetime-local', localDate(s.start), 'required')}${input('end', t('Kết thúc', 'End'), 'datetime-local', localDate(s.end), 'required')}</div>${p.state === 'DRAFT' ? submit(t('Lưu khung giờ', 'Save slot')) : ''}`,
                s.id,
              ),
            )
            .join('') ||
          `<p>${t('Không có xe đủ điều kiện.', 'No eligible vehicles.')}</p>`
        }${p.queue.length ? `<p>${t('Đang chờ cổng', 'Waiting for a port')}: ${e(p.queue.join(', '))}</p>` : ''}${p.state === 'DRAFT' && p.slots.length ? button('confirm-schedule', t('Kiểm tra lại và xác nhận lịch', 'Revalidate and confirm schedule'), '', 'class="primary"') : ''}`,
      );
    body += panel(
      t('Lịch sạc đang giữ', 'Active charging allocations'),
      table(
        [
          t('Xe', 'Vehicle'),
          t('Cổng', 'Port'),
          t('Bắt đầu', 'Start'),
          t('Kết thúc', 'End'),
        ],
        d.bookings
          .filter((b) => b.kind === 'CHARGING' && b.status === 'ACTIVE')
          .map((b) => [
            e(b.vehicleId),
            e(b.resourceId),
            e(date(b.start)),
            e(date(b.end)),
          ]),
      ),
    );
  } else if (key === 'simulation') {
    body = panel(
      t('Kịch bản What-if', 'What-if scenario'),
      form(
        'simulate',
        `<div class="form-grid">${select('hub', t('Hub', 'Hub'), hubs(d))}${input('hours', t('Khoảng mô phỏng (giờ)', 'Horizon (hours)'), 'number', '4', 'min="1" max="24" step="1" required')}${input('arrivals', t('Tổng xe đến', 'Total arrivals'), 'number', '5', 'min="0" max="100" step="1" required')}${input('departures', t('Tổng yêu cầu đi', 'Total departure requests'), 'number', '1', 'min="0" max="100" step="1" required')}</div><p>${t('Kịch bản dùng bản sao trạng thái, chia đều nhu cầu theo giờ. Chạy mô phỏng không thay đổi tài nguyên.', 'The scenario uses a snapshot and distributes demand evenly by hour. Running it does not change resources.')}</p>${submit(t('Chạy mô phỏng', 'Run simulation'))}`,
      ),
    );
    const s = d.simulation;
    if (s)
      body += panel(
        t('Kết quả mô phỏng', 'Simulation results'),
        `<p>${e(s.id)} · ${e(hubName(d, s.hubId))} · ${e(date(s.at))}</p><div class="op-metrics">${metric(s.peak, t('Nhu cầu chỗ đỗ cao nhất', 'Peak parking demand'))}${metric(s.overflow, t('Chỗ vượt sức chứa', 'Parking overflow'))}${metric(s.failed, t('Yêu cầu đi thiếu xe', 'Unserved departures'))}</div><div class="simulation-chart" role="img" aria-label="${t('Nhu cầu chỗ đỗ theo giờ; số liệu ở bảng bên dưới', 'Hourly parking demand; values are in the following table')}">${s.timeline.map((x) => `<div><span style="height:${Math.max(3, (x.occupancy / Math.max(1, s.peak)) * 120)}px"></span><small>${x.hour}h</small></div>`).join('')}</div>${table(
          [t('Giờ', 'Hour'), t('Nhu cầu chỗ đỗ', 'Parking demand')],
          s.timeline.map((x) => [String(x.hour), String(x.occupancy)]),
        )}${s.plan ? `<p>${t('Gợi ý điều phối', 'Suggested dispatch')}: ${e(hubName(d, s.plan.source))} → ${e(hubName(d, s.plan.target))} · ${e(s.plan.vehicleIds.join(', '))}</p>${s.applied ? status('COMPLETED') : button('apply-simulation', t('Kiểm tra lại và tạo lệnh điều phối', 'Revalidate and create dispatch'), '', 'class="primary"')}` : `<p>${t('Chưa có phương án điều phối khả thi từ dữ liệu mẫu.', 'No feasible dispatch suggestion from the sample data.')}</p>`}`,
      );
  }
  if (key === 'simulation') {
    if (d.simulation)
      body += panel(
        t('Lưu kịch bản', 'Save scenario'),
        form(
          'save-scenario',
          input(
            'name',
            t('Tên kịch bản', 'Scenario name'),
            'text',
            '',
            'required maxlength="100"',
          ) + submit(t('Lưu trong phiên demo', 'Save in demo session')),
        ) +
          button(
            'simulation-charging',
            t(
              'Mở bản nháp lịch sạc cho Hub này',
              'Open charging draft for this Hub',
            ),
          ),
      );
    body += panel(
      t('Kịch bản đã lưu', 'Saved scenarios'),
      table(
        [t('Tên', 'Name'), t('Thời điểm', 'Time'), t('Thao tác', 'Action')],
        d.scenarios.map((s) => [
          e(s.name),
          e(date(s.at)),
          button(
            'load-scenario',
            t('Chạy lại với dữ liệu hiện tại', 'Rerun with current data'),
            s.id,
          ),
        ]),
      ),
    );
  }
  return `<section id="operator-page">${title(t(...names[key]), t('Trung tâm vận hành · Smart E-Mobility Hub', 'Operations center · Smart E-Mobility Hub'))}<div id="op-feedback" role="status" tabindex="-1" hidden></div>${body}</section>`;
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
        ops.dispatch(value('source'), value('target'), v.getAll('vehicles'));
      if (kind === 'schedule') {
        state.chargingHub = value('hub');
        ops.buildSchedule(value('hub'), time('start'));
      }
      if (kind === 'edit-slot')
        ops.editSlot(f.dataset.id, value('port'), time('start'), time('end'));
      if (kind === 'simulate')
        ops.simulate(
          value('hub'),
          Number(value('arrivals')),
          Number(value('departures')),
          Number(value('hours')),
        );
    }, !['schedule', 'edit-slot', 'simulate'].includes(kind));
  });
  section.addEventListener('click', (event) => {
    const b = event.target.closest('[data-op]');
    if (!b) return;
    event.stopPropagation();
    const a = b.dataset.op,
      id = b.dataset.id;
    if (a === 'select-resource') {
      state.resource = id;
      section.querySelector('[name="resource"]').value = id;
      section.querySelector('[name="operation"]').focus();
      return;
    }
    run(() => {
      if (a === 'refresh') ops.poll();
      if (a === 'load-scenario') ops.loadScenario(id);
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
