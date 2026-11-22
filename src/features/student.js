import {
  e,
  t,
  title,
  input,
  link,
  times,
  hubsOptions,
  badge,
  date,
  empty,
  outcome,
} from '../shared/ui.js';
export function hubsPage(d) {
  return (
    title(
      t('Khám phá hành trình xanh', 'Explore your greener journey'),
      t(
        'Tìm xe và Mobility Hub gần bạn',
        'Find vehicles and Mobility Hubs nearby',
      ),
    ) +
    `<div class="campus-map"><svg viewBox="0 0 600 200" aria-hidden="true"><rect width="600" height="200" fill="var(--tint)"/><path d="M0 80H600M130 0V200M400 0V200M0 180L600 20" stroke="var(--surface)" stroke-width="22"/><circle cx="130" cy="80" r="12" fill="var(--primary)"/><circle cx="400" cy="80" r="12" fill="var(--primary)"/></svg><div class="map-pins">${d.hubs.map((h) => `<button data-hub="${e(h.id)}">${e(h.name)}</button>`).join('')}</div><small>${t('Sơ đồ minh họa · không chỉ đường', 'Illustrative map · no navigation')}</small></div><div class="quick-actions">${link('/student/parking', t('Đặt chỗ đỗ', 'Reserve parking'))}${link('/student/charging', t('Đăng ký sạc', 'Book charging'))}${link('/student/pickup', t('Nhận xe QR', 'QR pickup'))}${link('/student/return', t('Trả xe', 'Return vehicle'))}</div><div class="panel">${input('search', t('Tìm Hub hoặc mã xe', 'Search hub or vehicle ID'), 'search', '', 'id="hub-search"')}<button data-action="gps">${t('Dùng vị trí của tôi', 'Use my location')}</button><p id="gps-result" aria-live="polite" class="hint"></p></div><div class="hub-list">${d.hubs
      .map(
        (h) =>
          `<section class="panel hub-section" data-hub-section="${e(h.id)}"><h2>${e(h.name)}</h2><p class="hint">${t('Địa điểm mẫu phục vụ demo', 'Sample location for demo')}</p><div class="cards">${d.vehicles
            .filter((v) => v.hubId === h.id && !v.personal)
            .map(
              (v) =>
                `<article class="vehicle-tile" data-search="${e((h.name + ' ' + v.id).toLowerCase())}"><div class="vehicle-visual" aria-hidden="true">🚲</div><h3>${e(v.id)}</h3><p>${t('Pin', 'Battery')}: ${v.battery}% · ${v.type === 'BIKE' ? t('Xe đạp điện', 'E-bike') : t('Xe máy điện', 'E-scooter')}</p>${badge(Date.now() - v.observedAt > 120000 ? 'STALE' : v.status)}${link('/student/vehicles/' + v.id, t('Xem chi tiết', 'View details'))}</article>`,
            )
            .join('')}</div></section>`,
      )
      .join(
        '',
      )}</div><p id="search-empty" hidden>${t('Không có kết quả phù hợp.', 'No matching results.')}</p>`
  );
}
export function vehiclePage(d, id) {
  const v = d.vehicles.find((v) => v.id === id && !v.personal);
  if (!v) return empty(t('Không tìm thấy xe', 'Vehicle not found'));
  const stale = Date.now() - v.observedAt > 120000;
  return (
    title(t('Chi tiết phương tiện', 'Vehicle details')) +
    `<section class="panel"><div class="vehicle-visual" aria-hidden="true">🚲</div><h2>${e(v.id)}</h2>${badge(stale ? 'STALE' : v.status)}<dl class="details"><div><dt>${t('Pin', 'Battery')}</dt><dd>${v.battery}%</dd></div><div><dt>Hub</dt><dd>${e(d.hubs.find((h) => h.id === v.hubId)?.name)}</dd></div><div><dt>${t('Cập nhật', 'Updated')}</dt><dd>${e(date(v.observedAt))}</dd></div></dl></section><form data-form="reserve" data-id="${e(id)}" class="panel"><h2>${t('Đặt trước xe', 'Reserve vehicle')}</h2>${times()}<p class="hint">${t('Giữ xe tối đa 10 phút trong bản demo.', 'Demo reservation hold: up to 10 minutes.')}</p><button class="primary full" ${stale || v.status !== 'AVAILABLE' || v.battery < 20 ? 'disabled' : ''}>${t('Xác nhận đặt xe', 'Confirm reservation')}</button></form>`
  );
}
export function bookingsPage(d) {
  return (
    title(t('Lượt đặt của tôi', 'My bookings')) +
    (d.bookings.length
      ? `<label>${t('Bộ lọc', 'Filter')}<select id="booking-filter"><option value="ALL">${t('Tất cả', 'All')}</option><option value="ACTIVE">${t('Đang hiệu lực', 'Active')}</option></select></label>` +
        d.bookings
          .map(
            (b) =>
              `<article class="panel booking-card" data-booking-status="${b.status}"><div class="row-between"><h2>${e(b.resourceId)}</h2>${badge(b.status)}</div><p>${e(b.id)} · ${badge(b.kind)}</p><p>${e(date(b.start))} → ${e(date(b.end))}</p>${b.status === 'ACTIVE' ? `<p class="countdown" data-expiry="${b.expiresAt}"></p>${b.kind === 'VEHICLE' ? `<div class="qr-code"><canvas data-qr="${e(b.code)}" aria-label="QR pickup"></canvas><strong>${e(b.code)}</strong></div>${link('/student/pickup', t('Nhận xe', 'Pick up'))}` : ''}<button class="danger" data-cancel="${e(b.id)}">${t('Hủy lượt đặt', 'Cancel booking')}</button>` : ''}</article>`,
          )
          .join('')
      : empty(t('Bạn chưa có lượt đặt nào.', 'You have no bookings.')))
  );
}
export function parkingPage(d) {
  return (
    title(t('Đặt trước chỗ đỗ', 'Reserve a parking space')) +
    `<form class="panel" data-form="parking-search"><label>Hub<select name="hub">${hubsOptions(d)}</select></label><label>${t('Loại xe', 'Vehicle type')}<select name="type"><option value="BIKE">${t('Xe đạp điện', 'E-bike')}</option><option value="SCOOTER">${t('Xe máy điện', 'E-scooter')}</option></select></label>${times()}<button class="primary full">${t('Tìm chỗ khả dụng', 'Find available spaces')}</button></form><section id="parking-results" class="spaces" aria-live="polite"></section>`
  );
}
export function chargingPage(d) {
  return (
    title(
      t('Đăng ký sạc', 'Book charging'),
      t(
        'Xe cá nhân · pin hiện tại 35%',
        'Personal vehicle · current charge 35%',
      ),
    ) +
    `<form class="panel" data-form="charging"><label>Hub<select name="hub">${hubsOptions(d)}</select></label><label>${t('Mức pin mong muốn (%)', 'Target charge (%)')}<input name="target" type="number" min="36" max="100" value="80" required></label><p id="charge-estimate" class="estimate"></p>${times()}<p class="hint">${t('Ước tính demo: 1,5 phút cho mỗi 1% pin.', 'Demo estimate: 1.5 minutes per 1% charge.')}</p><button class="primary full">${t('Xác nhận đăng ký', 'Confirm charging')}</button></form>`
  );
}
export function commandPanel(c) {
  return `<section class="panel command-panel" data-command="${e(c.id)}"><p>${e(c.id)}</p><h2>${badge(c.state)}</h2><p>${t('Đây là phản hồi thiết bị mô phỏng.', 'This is a simulated device response.')}</p>${c.state === 'TIMEOUT' ? `<p>${t('Chưa xác định kết quả. Không gửi lại; demo đặt lại khi tải lại trang.', 'Outcome unknown. Do not resend; reload resets the demo.')}</p>` : ''}${c.state === 'SUCCESS' ? link(c.kind === 'PICKUP' ? '/student/return' : '/student/hubs', c.kind === 'PICKUP' ? t('Xem chuyến đi / trả xe', 'View ride / return') : t('Về trang chủ', 'Back home')) : ''}</section>`;
}
export function pickupPage(d) {
  const active = d.bookings.find(
    (b) => b.kind === 'VEHICLE' && b.status === 'ACTIVE',
  );
  const c = d.commands.find(
    (c) => c.kind === 'PICKUP' && (!active || c.bookingId === active.id),
  );
  if (c && c.state !== 'FAILED')
    return title(t('Nhận xe QR', 'QR pickup')) + commandPanel(c);
  const b = d.bookings.find(
    (b) => b.kind === 'VEHICLE' && b.status === 'ACTIVE',
  );
  if (!b)
    return (
      title(t('Nhận xe QR', 'QR pickup')) +
      empty(
        t(
          'Bạn cần đặt xe trước khi quét mã nhận xe.',
          'Reserve a vehicle before scanning its pickup code.',
        ),
      )
    );
  return (
    title(t('Quét mã nhận xe', 'Scan pickup code')) +
    `<section class="scanner panel"><video id="camera" playsinline muted hidden></video><div class="row-actions"><button data-action="camera">${t('Mở camera', 'Open camera')}</button><button data-action="stop-camera">${t('Dừng camera', 'Stop camera')}</button><label>${t('Chọn ảnh QR', 'Upload QR image')}<input id="qr-file" type="file" accept="image/*"></label></div><p class="hint">${t('Bạn có thể nhập mã bên dưới nếu không dùng camera.', 'You can enter the code below instead of using the camera.')}</p></section><form class="panel" data-form="pickup">${input('code', t('Mã nhận xe', 'Pickup code'), 'text', '', 'required autocomplete="off"')}<button type="button" data-code="${e(b.code)}">${t('Điền mã đặt xe demo', 'Fill demo booking code')}</button>${outcome()}<button class="primary full">${t('Xác nhận mở khóa', 'Confirm unlock')}</button></form>${c ? commandPanel(c) : ''}`
  );
}
export function returnPage(d) {
  const ride = d.rides.find((r) => !r.end);
  const c = d.commands.find(
    (c) => c.kind === 'RETURN' && (!ride || c.rideId === ride.id),
  );
  if (c && c.state !== 'FAILED')
    return title(t('Trả phương tiện', 'Return vehicle')) + commandPanel(c);
  const r = d.rides.find((r) => !r.end);
  if (!r)
    return (
      title(t('Trả phương tiện', 'Return vehicle')) +
      empty(t('Chưa có chuyến đi đang hoạt động.', 'No active ride.'))
    );
  const v = d.vehicles.find((v) => v.id === r.vehicleId);
  return (
    title(t('Chuyến đi hiện tại', 'Current ride')) +
    `<section class="panel"><h2>${e(v.id)}</h2><p>${e(date(r.start))}</p><p>${t('Thời gian đã đi', 'Elapsed')}: <span data-ride-start="${r.start}"></span></p></section><form class="panel" data-form="return"><label>${t('Chỗ đỗ trả xe', 'Return parking space')}<select name="space" required>${d.spaces
      .filter((s) => s.type === v.type && !s.occupied)
      .map(
        (s) =>
          `<option value="${e(s.id)}">${e(d.hubs.find((h) => h.id === s.hubId)?.name)} · ${e(s.id)}</option>`,
      )
      .join(
        '',
      )}</select></label><label class="check-label"><input type="checkbox" name="issue">${t('Báo xe có sự cố', 'Report a vehicle issue')}</label>${outcome()}<button class="primary full">${t('Xác nhận khóa & trả xe', 'Confirm lock & return')}</button></form>${c ? commandPanel(c) : ''}`
  );
}
