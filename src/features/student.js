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
import { icon, mobileHeading, successCard } from '../shared/design.js';
export function availableVehicles(d, now = Date.now()) {
  return d.vehicles.filter(
    (v) =>
      !v.personal &&
      v.status === 'AVAILABLE' &&
      v.battery >= 20 &&
      !v.fault &&
      !v.operatorPending &&
      now - v.observedAt <= 120000,
  );
}
export function hubsPage(d, options = {}) {
  const suggested = availableVehicles(d)[0];
  const bookingPath =
    options.bookingPath ||
    (suggested
      ? '/student/vehicles/' + encodeURIComponent(suggested.id)
      : '/student/vehicles');
  const service = (path, name, glyph) =>
    `<a href="#${path}" class="home-service"><span>${icon(glyph)}</span><small>${e(name)}</small></a>`;
  return `<a href="#/student/search" class="home-search">${icon('search')}<span>${t('Tìm xe hoặc Mobility Hub', 'Find a vehicle or Mobility Hub')}</span></a><div class="figma-map"><svg viewBox="0 0 342 128" aria-hidden="true"><rect width="342" height="128" fill="var(--tint)"/><path d="M75 0h190v128H75Z" fill="var(--background)"/><path d="M60 20h40v28H60ZM160 12h30v20h-30ZM215 95h40v24h-40Z" fill="var(--map-block)"/><path d="m65-10 70 150M235-10l-50 150M0 85l342-70M35 25l270 90" stroke="var(--surface)" stroke-width="10"/><path d="m65-10 70 150M235-10l-50 150M0 85l342-70M35 25l270 90" stroke="var(--border)" stroke-width="1"/></svg>${options.mapPins ?? `<a href="#/student/search" class="map-label pin-one">${icon('pin')}Metro</a><a href="#/student/search" class="map-label pin-two">${icon('pin')}Dorm B</a><a href="#/student/search" class="map-label pin-three">${icon('pin')}HCMUT</a>`}</div><div class="home-quick"><a href="#${bookingPath}">${t('Đặt xe', 'Book a vehicle')}${icon('bike')}</a><a href="#/student/search">${t('Tìm Mobility Hub', 'Find a Mobility Hub')}${icon('pin')}</a></div><h2 class="services-title">${t('Dịch vụ khác', 'Other services')}</h2><div class="home-services">${service('/student/parking', t('Đặt chỗ đỗ', 'Book parking'), 'parking')}${service('/student/charging', t('Đăng ký sạc', 'Register charging'), 'charge')}${service('/student/return', t('Trả xe', 'Return vehicle'), 'return')}${service('/student/pickup', t('Nhận xe', 'Pick up vehicle'), 'qr')}${service('/student/register-vehicle', t('Đăng ký xe', 'Register vehicle'), 'bike')}</div>`;
}
export function searchPage(d) {
  return (
    mobileHeading(t('Chọn Mobility Hub', 'Choose a Mobility Hub')) +
    input(
      'search',
      t('Điểm đến', 'Destination'),
      'search',
      '',
      'id="hub-search" placeholder="' +
        t('Tìm Hub hoặc mã xe', 'Find a Hub or vehicle') +
        '"',
    ) +
    `<h2>${t('Mobility Hub cho hành trình', 'Mobility Hubs for your trip')}</h2><div class="hub-list">${d.hubs
      .map(
        (h) =>
          `<section class="hub-section" data-hub-section="${e(h.id)}"><details class="hub-choice"><summary>${icon('pin')}<span><strong>${e(h.name)}</strong><small>${d.vehicles.filter((v) => v.hubId === h.id && !v.personal && v.status === 'AVAILABLE').length} ${t('xe khả dụng', 'available vehicles')} · ${d.spaces.filter((s) => s.hubId === h.id && !s.occupied).length} ${t('chỗ đỗ', 'parking spaces')} · ${h.ports} ${t('cổng sạc', 'chargers')}</small></span></summary><div class="vehicle-options">${d.vehicles
            .filter((v) => v.hubId === h.id && !v.personal)
            .map(
              (v) =>
                `<a class="vehicle-option" data-search="${e((h.name + ' ' + v.id).toLowerCase())}" href="#/student/vehicles/${e(v.id)}">${icon('bike')}<span><strong>${e(v.id)}</strong><small>${Math.round(v.battery)}% · ${badge(Date.now() - v.observedAt > 120000 ? 'STALE' : v.status)}</small></span>${icon('back', 'forward')}</a>`,
            )
            .join('')}</div></details></section>`,
      )
      .join(
        '',
      )}</div><p id="search-empty" hidden>${t('Không có kết quả phù hợp.', 'No matching results.')}</p><button class="secondary full" data-action="gps">${icon('pin')}${t('Dùng vị trí của tôi', 'Use my location')}</button><p id="gps-result" class="hint" aria-live="polite"></p>`
  );
}
export function availableVehiclesPage(d) {
  const vehicles = availableVehicles(d);
  return (
    mobileHeading(t('Phương tiện khả dụng', 'Available vehicles')) +
    (vehicles.length
      ? `<div class="available-vehicles">${vehicles
          .map((v) => {
            const hub = d.hubs.find((h) => h.id === v.hubId);
            return `<a class="available-vehicle" href="#/student/vehicles/${encodeURIComponent(v.id)}">${icon('bike')}<span><strong>${t('Xe máy điện', 'Electric motorbike')}</strong><small>${e(v.id)} · ${e(hub?.name || '')}</small><small>${t('Dung lượng pin', 'Battery level')}: ${Math.round(v.battery)}%</small>${badge(v.status)}</span>${icon('back', 'forward')}</a>`;
          })
          .join('')}</div>`
      : `<div class="empty" role="status">${t('Hiện chưa có phương tiện khả dụng. Vui lòng thử lại sau.', 'No vehicles are available right now. Please try again later.')}</div>`)
  );
}
export function vehiclePage(d, id) {
  const v = d.vehicles.find((v) => v.id === id && !v.personal);
  if (!v) return empty(t('Không tìm thấy xe', 'Vehicle not found'));
  const stale = Date.now() - v.observedAt > 120000,
    blocked =
      stale ||
      v.status !== 'AVAILABLE' ||
      v.battery < 20 ||
      v.fault ||
      v.operatorPending;
  return (
    mobileHeading(
      t('Chi tiết phương tiện', 'Vehicle details'),
      '/student/vehicles',
    ) +
    `<section class="vehicle-hero">${icon('bike')}<div><h2>${t('Xe máy điện', 'Electric motorbike')}</h2><p>${e(v.id)} · ${e(d.hubs.find((h) => h.id === v.hubId)?.name)}</p>${badge(stale ? 'STALE' : v.status)}</div></section><div class="battery-readout">${icon('battery')}<strong>${Math.round(v.battery)}%</strong><span>${t('Dung lượng pin', 'Battery level')}</span></div><dl class="vehicle-details"><div><dt>${t('Vị trí', 'Location')}</dt><dd>${e(d.hubs.find((h) => h.id === v.hubId)?.name)}</dd></div><div><dt>${t('Trạng thái', 'Status')}</dt><dd>${badge(stale ? 'STALE' : v.status)}</dd></div><div><dt>${t('Cập nhật gần nhất', 'Last updated')}</dt><dd>${e(new Date(v.observedAt).toLocaleTimeString())}</dd></div></dl><form data-form="reserve" data-id="${e(id)}" class="reservation-form"><details><summary>${t('Chọn thời gian thuê', 'Choose rental times')}</summary>${times()}</details><button class="primary full" ${blocked ? 'disabled' : ''}>${t('Đặt trước xe này', 'Reserve this vehicle')}</button></form>${link('/student/vehicles', t('Chọn phương tiện khác', 'Choose another vehicle'), 'button secondary full')}`
  );
}
export function reservationConfirmedPage(d) {
  const b = d.bookings.find(
    (b) => b.kind === 'VEHICLE' && b.status === 'ACTIVE',
  );
  if (!b) return bookingsPage(d);
  return (
    mobileHeading(t('Đã xác nhận đặt xe', 'Vehicle booking confirmed')) +
    successCard(
      t('Đã giữ xe', 'Vehicle reserved'),
      t(
        'Đến Mobility Hub và quét mã QR trên xe để nhận xe.',
        'Go to the Mobility Hub and scan the vehicle QR code to pick it up.',
      ),
    ) +
    `<section class="reservation-timer"><span>${t('Thời gian giữ xe còn lại', 'Reservation time remaining')}</span><strong data-countdown="${b.expiresAt}"></strong></section><dl class="vehicle-details"><div><dt>${t('Mã nhận xe', 'Pickup code')}</dt><dd>${e(b.code)}</dd></div><div><dt>${t('Phương tiện', 'Vehicle')}</dt><dd>${e(b.resourceId)}</dd></div><div><dt>${t('Vị trí', 'Location')}</dt><dd>${e(d.hubs.find((h) => h.id === b.hubId)?.name)}</dd></div></dl>${link('/student/pickup', t('Quét mã để nhận xe', 'Scan to pick up'), 'button primary full')}<button class="danger full" data-cancel="${e(b.id)}">${t('Hủy đặt xe', 'Cancel vehicle booking')}</button>`
  );
}
export function bookingsPage(d) {
  return (
    mobileHeading(t('Lượt đặt của tôi', 'My bookings')) +
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
    mobileHeading(t('Đặt trước chỗ đỗ', 'Reserve a parking space')) +
    `<form class="panel" data-form="parking-search"><label>Hub<select name="hub">${hubsOptions(d)}</select></label><input type="hidden" name="type" value="E_BIKE">${times()}<button class="primary full">${t('Tìm chỗ khả dụng', 'Find available spaces')}</button></form><section id="parking-results" class="spaces" aria-live="polite"></section>`
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
  return (
    mobileHeading(t('Quét QR để thuê xe', 'Scan QR to rent')) +
    `<h2>${t('Quét mã QR trên xe tại Mobility Hub', 'Scan the QR code on a vehicle at a Mobility Hub')}</h2><p class="hint">${t('Đặt mã QR của xe vào khung để xem chi tiết và nhận xe.', 'Place the vehicle QR code inside the scanner to view its details and rent it.')}</p><section class="scanner panel"><div class="scan-window" aria-hidden="true"><i></i><i></i><i></i><i></i></div><video id="camera" playsinline muted hidden></video><div class="row-actions"><button data-action="camera">${t('Mở camera', 'Open camera')}</button><button data-action="stop-camera">${t('Dừng camera', 'Stop camera')}</button><label>${t('Chọn ảnh QR', 'Upload QR image')}<input id="qr-file" type="file" accept="image/*"></label></div><p class="hint">${t('Bạn có thể nhập mã bên dưới nếu không dùng camera.', 'You can enter the code below instead of using the camera.')}</p></section><form class="panel" data-form="${b ? 'pickup' : 'scan-vehicle'}">${input('code', t('Mã nhận xe', 'Pickup code'), 'text', '', 'required autocomplete="off"')}<button type="button" data-code="${e(b?.code || 'E-024')}">${t('Điền mã demo', 'Fill demo code')}</button>${b ? outcome() : ''}<button class="primary full">${b ? t('Xác nhận mở khóa', 'Confirm unlock') : t('Xem chi tiết xe', 'View vehicle details')}</button></form>${c ? commandPanel(c) : ''}`
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
