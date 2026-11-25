import {
  operatorAuthShell,
  operatorShell,
  desktopWelcome,
} from './operator-design.js';
const accountFlow = { role: 'STUDENT' };
import { mobileApi as api } from '../shared/api/mobile.js';
import {
  e,
  t,
  input,
  link,
  times,
  applyPreferences,
  preference,
  confirmAction,
  date,
} from '../shared/ui.js';
import {
  welcomePage,
  mobileHeading,
  icon,
  successCard,
} from '../shared/design.js';
import { accountPage } from './account.js';
import { hubsPage } from './student.js';
import { scanCamera, scanFile, stopScanner } from '../shared/scanner.js';

let selected = null;
const go = (path) => {
  location.hash = path;
};
const field = (name, label, value = '', type = 'text', extra = '') =>
  input(name, label, type, value, `required ${extra}`);
const notice = (vi, en) => `<p class="hint">${t(vi, en)}</p>`;
const idLink = (id) => '/student/vehicles/' + encodeURIComponent(id);
const receipts = () => [...api.reservations, ...api.parking, ...api.charging];
function inventory() {
  return api.hubs.flatMap((h) =>
    h.vehicles.map((v) => ({ ...v, hubId: h.hubId, hubName: h.hubName })),
  );
}
function vehicleList() {
  const vehicles = inventory();
  return (
    mobileHeading(t('Phương tiện khả dụng', 'Available vehicles')) +
    (vehicles.length
      ? `<div class="available-vehicles">${vehicles.map((v) => `<a class="available-vehicle" href="#${idLink(v.vehicleId)}">${icon('bike')}<span><strong>${e(v.vehicleId)}</strong><small>${e(v.hubName)}</small><small>${v.batteryPct}% · ${e(v.status)}</small></span>${icon('back', 'forward')}</a>`).join('')}</div>`
      : notice(
          'Chưa có xe khả dụng trong kết quả tìm kiếm.',
          'No available vehicles in search results.',
        )) +
    link(
      '/student/search',
      t('Tìm xe tại vị trí của bạn', 'Search near your location'),
      'button secondary full',
    )
  );
}
function hubSummary(h) {
  return `<p>${h.distanceKm == null ? t('Chưa có khoảng cách', 'Distance unavailable') : e(h.distanceKm.toFixed(2)) + ' km'}${h.freeSpaceCount == null ? '' : ' · ' + e(h.freeSpaceCount) + t(' chỗ đỗ trống', ' free parking spaces')}</p>`;
}
function searchForm(
  loaded = false,
  selectedHubId = null,
  showVehicles = false,
  spaces = null,
) {
  const hub = loaded && api.hubs.find((h) => h.hubId === selectedHubId);
  if (hub)
    return (
      mobileHeading(t('Thông tin Mobility Hub', 'Mobility Hub details')) +
      `<section class="panel"><h2>${icon('pin')} ${e(hub.hubName)}</h2>${hubSummary(hub)}${hub.capacity == null ? '' : `<p>${t('Sức chứa', 'Capacity')}: ${e(hub.capacity)}</p>`}<p>${hub.availableVehicleCount == null ? t('Chưa có dữ liệu xe tại Hub này.', 'Vehicle availability is not available for this Hub.') : e(hub.availableVehicleCount) + t(' xe khả dụng', ' available vehicles')}</p></section>
    <button class="primary full" data-api-action="hub-parking">${t('Đặt chỗ đỗ tại Hub này', 'Book parking at this Hub')}</button>
    ${spaces === null ? '' : parkingAtHub(hub, spaces)}
    <button class="secondary full" data-api-action="hub-vehicles">${t('Xem xe tại Hub này', 'View vehicles at this Hub')}</button>
    ${showVehicles ? `<section class="available-vehicles" aria-live="polite">${hub.vehicles.length ? hub.vehicles.map((v) => link(idLink(v.vehicleId), `${v.vehicleId} · ${v.batteryPct}%`, 'available-vehicle')).join('') : notice(hub.availableVehicleCount === 0 ? 'Hub này hiện không có xe khả dụng. Bạn vẫn có thể xem thông tin Hub hoặc chọn Hub khác.' : 'Chưa có dữ liệu xe khả dụng tại Hub này.', hub.availableVehicleCount === 0 ? 'This Hub currently has no available vehicles. You can still view its information or choose another Hub.' : 'Vehicle availability is not available for this Hub.')}</section>` : ''}
    <button class="secondary full" data-api-action="hub-list">${t('Chọn Hub khác', 'Choose another Hub')}</button>`
    );
  return (
    mobileHeading(t('Chọn Mobility Hub', 'Choose a Mobility Hub')) +
    `<section class="auth-form"><p>${loaded ? t('Chọn một Hub để xem thông tin. Các Hub có khoảng cách được sắp xếp gần trước.', 'Select a Hub to view its information. Hubs with known distances are sorted nearest first.') : t('Cho phép truy cập vị trí để xem các Mobility Hub gần bạn.', 'Allow location access to view Mobility Hubs near you.')}</p><button class="primary full" data-api-action="gps">${icon('pin')}${loaded ? t('Cập nhật vị trí của tôi', 'Update my location') : t('Dùng vị trí của tôi', 'Use my location')}</button></section>` +
    (loaded
      ? `<div class="hub-list">${api.hubs.map((h) => `<button type="button" class="hub-row" data-select-hub="${e(h.hubId)}">${icon('pin')}<span><strong>${e(h.hubName)}</strong><span>${h.distanceKm == null ? t('Chưa có khoảng cách', 'Distance unavailable') : e(h.distanceKm.toFixed(2)) + ' km'}${h.freeSpaceCount == null ? '' : ' · ' + e(h.freeSpaceCount) + t(' chỗ đỗ trống', ' free parking spaces')}</span><small>${t('Xem thông tin Hub', 'View Hub details')}</small></span>${icon('back', 'forward')}</button>`).join('')}</div>`
      : '')
  );
}
function parkingAtHub(hub, spaces) {
  const available = spaces.filter(
    (s) => s.status === 'AVAILABLE' && !s.blocked && !s.pendingOperationId,
  );
  if (!available.length)
    return notice(
      'Hiện không còn chỗ đỗ khả dụng tại Hub này. Hãy cập nhật hoặc chọn Hub khác.',
      'No parking spaces are currently available at this Hub. Refresh or choose another Hub.',
    );
  const destination = `<input type="hidden" name="hubId" value="${e(hub.hubId)}"><fieldset class="parking-options"><legend>${t('Chọn chỗ đỗ', 'Choose a parking space')}</legend>${spaces
    .map((space) => {
      const free = available.includes(space);
      return `<label class="parking-option"><input type="radio" name="spaceId" value="${e(space.spaceId)}" required ${free ? '' : 'disabled'}><span>${icon('parking')}<strong>${e(space.spaceId)}</strong><small>${free ? t('Còn trống', 'Available') : t('Không khả dụng', 'Unavailable')}</small></span></label>`;
    })
    .join('')}</fieldset>`;
  return (
    `<section class="panel"><h2>${t('Đặt chỗ đỗ', 'Book parking')}</h2><form data-api="parking" class="auth-form">${destination}${times()}<button class="primary full">${t('Xác nhận đặt chỗ đỗ', 'Confirm parking reservation')}</button></form></section>` +
    (api.ride?.status === 'ACTIVE'
      ? `<section class="panel"><h2>${t('Trả xe đang sử dụng tại Hub này', 'Return your current vehicle here')}</h2><form data-api="return" class="auth-form"><input type="hidden" name="rideId" value="${e(api.ride.rideId)}">${destination}<button class="primary full">${t('Xác nhận trả xe', 'Confirm return')}</button></form></section>`
      : '') +
    commandPanel()
  );
}
function detailsPage(v) {
  const known = inventory().find((x) => x.vehicleId === v.vehicleId);
  const missing =
    v.batteryLevel == null ||
    !v.updatedAt ||
    !!v.reason ||
    (Array.isArray(v.missingData) ? v.missingData.length > 0 : !!v.missingData);
  const blocked = missing || v.status !== 'AVAILABLE' || v.batteryLevel < 20;
  const currentRide =
    api.ride?.status === 'ACTIVE' && api.ride.vehicleId === v.vehicleId;
  const currentHold = api.reservations.some(
    (r) => r.vehicleId === v.vehicleId && r.status === 'ACTIVE',
  );
  return (
    mobileHeading(
      t('Chi tiết phương tiện', 'Vehicle details'),
      '/student/vehicles',
    ) +
    `<section class="vehicle-hero">${icon('bike')}<div><h2>${e(known?.type || t('Phương tiện', 'Vehicle'))}</h2><p>${e(v.vehicleId)}${known ? ' · ' + e(known.hubName) : ''}</p><span class="badge">${e(v.status)}</span></div></section><div class="battery-readout">${icon('battery')}<strong>${v.batteryLevel == null ? '—' : e(v.batteryLevel) + '%'}</strong><span>${t('Dung lượng pin', 'Battery level')}</span></div><dl class="vehicle-details"><div><dt>${t('Vị trí', 'Location')}</dt><dd>${e(known?.hubName || '—')}</dd></div><div><dt>${t('Trạng thái', 'Status')}</dt><dd>${e(v.status)}</dd></div><div><dt>${t('Cập nhật gần nhất', 'Last updated')}</dt><dd>${v.updatedAt ? e(date(v.updatedAt)) : '—'}</dd></div></dl>${missing ? `<p role="status">${e(v.reason || t('Dữ liệu chưa đủ để đặt xe.', 'Insufficient data to reserve.'))}</p>` : ''}${currentRide ? link('/student/return', t('Trả xe này', 'Return this vehicle'), 'button primary full') : currentHold ? link('/student/pickup', t('Nhận xe đã đặt', 'Pick up reserved vehicle'), 'button primary full') : `<form data-api="reserve" data-id="${e(v.vehicleId)}" class="reservation-form"><details><summary>${t('Chọn thời gian thuê', 'Choose rental times')}</summary>${times()}</details><button class="primary full" ${blocked ? 'disabled' : ''}>${t('Đặt trước xe này', 'Reserve this vehicle')}</button></form>`}${link('/student/vehicles', t('Chọn phương tiện khác', 'Choose another vehicle'), 'button secondary full')}`
  );
}
function bookingCard(r) {
  const id = r.reservationId || r.parkingReservationId || r.chargingRequestId;
  return `<section class="panel"><h2>${e(id)}</h2><p>${e(r.status)} · ${e(r.vehicleId || r.spaceId || r.portId || '')}</p>${r.expiresAt ? `<p>${t('Hết hạn', 'Expires')}: ${e(date(r.expiresAt))}</p>` : ''}${r.scheduledStartAt ? `<p>${e(date(r.scheduledStartAt))} → ${e(date(r.scheduledEndAt))}</p>` : ''}${r.pickupCode && r.status === 'ACTIVE' ? `<p>${t('Mã nhận xe', 'Pickup code')}: <strong>${e(r.pickupCode)}</strong></p>${link('/student/pickup', t('Quét mã để nhận xe', 'Scan to pick up'), 'button primary full')}` : ''}${r.reservationId && r.status === 'ACTIVE' ? `<button class="danger full" data-cancel-api="${e(r.reservationId)}">${t('Hủy đặt xe', 'Cancel vehicle booking')}</button>` : ''}</section>`;
}
function commandPanel() {
  const c = api.command;
  if (!c) return '';
  return `<section class="panel" role="status"><h2>${e(c.state)}</h2><p>${e(c.operationId || c.result?.rideId || '')}</p>${c.state === 'PENDING' ? notice('Đang chờ thiết bị xác nhận. Chưa hoàn tất giao dịch.', 'Waiting for device confirmation. Not completed yet.') + '<button data-api-action="poll">' + t('Kiểm tra trạng thái', 'Check status') + '</button>' : ''}${c.state === 'UNKNOWN' ? notice('Chưa xác định kết quả. Thử lại cùng yêu cầu để đối soát, không tạo lệnh mới.', 'Outcome unknown. Retry the same request to reconcile; do not create another command.') + '<button data-api-action="retry">' + t('Đối soát yêu cầu', 'Reconcile request') + '</button>' : ''}${c.state === 'SUCCEEDED' && c.kind === 'PICKUP' ? link('/student/return', t('Trả phương tiện', 'Return vehicle'), 'button primary full') : ''}</section>`;
}
function pickupForm() {
  const hold = api.reservations.find((r) => r.status === 'ACTIVE');
  return (
    mobileHeading(t('Quét QR để thuê xe', 'Scan QR to rent')) +
    `<h2>${t('Nhận xe bằng mã QR', 'Pick up with QR code')}</h2><p class="hint">${t('Đưa mã QR vào khung để đối chiếu với lượt đặt xe của bạn.', 'Place the QR code in the frame to match your booking.')}</p><section class="scanner panel"><div class="scan-window" aria-hidden="true"><i></i><i></i><i></i><i></i></div><video id="camera" playsinline muted hidden></video><button class="primary full" data-api-action="camera">${t('Quét mã QR', 'Scan QR code')}</button><button type="button" class="secondary full" data-api-action="stop-camera">${t('Dừng camera', 'Stop camera')}</button><label>${t('Chọn ảnh QR', 'Upload QR image')}<input id="api-qr-file" type="file" accept="image/*"></label></section><form data-api="pickup" class="auth-form">${field('vehicleId', t('Mã phương tiện', 'Vehicle ID'), hold?.vehicleId || selected?.vehicleId || '', 'text', hold ? 'readonly' : '')}${input('reservationId', t('Mã đặt xe (nếu có)', 'Reservation ID (optional)'), 'text', hold?.reservationId || '')}${input('pickupCode', t('Mã nhận xe (nếu đã đặt)', 'Pickup code (if reserved)'), 'text', hold?.pickupCode || '')}<button class="primary full">${t('Xác nhận nhận xe', 'Confirm pickup')}</button></form>${commandPanel()}`
  );
}
export function mountMobileApi(root, path) {
  let locationLoaded = api.hubs.length > 0;
  let selectedHubId = null;
  let showHubVehicles = false;
  let hubParkingSpaces = null;
  let disposed = false,
    busy = false,
    timer,
    polls = 0;
  const controller = new AbortController();
  applyPreferences();
  function draw(content) {
    if (disposed) return;
    if (path === '/welcome/pc') {
      root.innerHTML = desktopWelcome();
      return;
    }
    if (
      path.startsWith('/account') &&
      (api.user?.role === 'OPERATOR' || accountFlow.role === 'OPERATOR')
    ) {
      const feedback =
        '<div id="api-feedback" role="alert" tabindex="-1" hidden></div>';
      root.innerHTML = api.user
        ? operatorShell(feedback + content, path).replace(
            'DEMO · ' + t('Dữ liệu mô phỏng', 'Simulated data'),
            'API',
          )
        : operatorAuthShell(feedback + content);
      return;
    }
    root.innerHTML = `<div class="app-frame student-frame"><main><div id="api-feedback" role="alert" tabindex="-1" hidden></div>${content}</main>${
      api.user
        ? `<nav class="bottom-nav" aria-label="${t('Điều hướng chính', 'Main navigation')}">${[
            ['/student/hubs', 'home', t('Trang chủ', 'Home')],
            ['/student/reservations', 'clock', t('Lượt đặt', 'Bookings')],
            ['/account', 'user', t('Tài khoản', 'Account')],
          ]
            .map(
              ([p, i, n]) =>
                `<a href="#${p}" ${p === path ? 'class="active" aria-current="page"' : ''}>${icon(i)}<span>${n}</span></a>`,
            )
            .join('')}</nav>`
        : ''
    }</div>`;
  }
  function message(text, isError = true) {
    if (disposed) return;
    const box = root.querySelector('#api-feedback');
    if (box) {
      box.hidden = false;
      box.className = isError ? 'feedback error' : 'feedback';
      box.textContent = text;
      box.focus();
    }
  }
  function errorText(error) {
    if (error.status === 401)
      return t(
        'Phiên đăng nhập hết hạn hoặc không hợp lệ. Hãy đăng nhập lại.',
        'Session expired or invalid. Please sign in again.',
      );
    if (error.name === 'TypeError' || error.name === 'TimeoutError')
      return t(
        'Không kết nối được backend hoặc quá thời gian chờ. Kiểm tra server và thử lại.',
        'Backend unavailable or timed out. Check the server and retry.',
      );
    const details = Array.isArray(error.details)
      ? error.details
          .map((d) => `${d.field || ''}: ${d.issue || d.message || ''}`)
          .join('; ')
      : '';
    return `${error.code ? error.code + ': ' : ''}${error.message}${details ? ' — ' + details : ''}${error.requestId ? ' (' + error.requestId + ')' : ''}`;
  }
  async function run(action) {
    if (busy || disposed) return;
    busy = true;
    const buttons = [...root.querySelectorAll('button')];
    const disabled = buttons.map((b) => b.disabled);
    buttons.forEach((b) => {
      b.disabled = true;
    });
    try {
      await action();
    } catch (error) {
      if (error.name !== 'AbortError') {
        if (api.command?.state === 'UNKNOWN') render();
        message(errorText(error));
      }
    } finally {
      busy = false;
      const locationButton = root.querySelector('[data-api-action="gps"]');
      if (locationButton) locationButton.disabled = false;
      buttons.forEach((b, i) => {
        if (b.isConnected) b.disabled = disabled[i];
      });
    }
  }
  async function render() {
    if (disposed) return;
    if (path === '/' || path === '/welcome/pc') {
      draw(welcomePage());
      return;
    }
    if (path === '/settings') {
      draw(
        mobileHeading(t('Cài đặt giao diện', 'Display settings'), '/account') +
          `<label>${t('Ngôn ngữ', 'Language')}<select data-api-pref="locale"><option value="vi" ${document.documentElement.lang === 'vi' ? 'selected' : ''}>Tiếng Việt</option><option value="en" ${document.documentElement.lang === 'en' ? 'selected' : ''}>English</option></select></label><label>${t('Giao diện', 'Theme')}<select data-api-pref="theme"><option value="light" ${document.documentElement.dataset.theme === 'light' ? 'selected' : ''}>Light</option><option value="dark" ${document.documentElement.dataset.theme === 'dark' ? 'selected' : ''}>Dark</option></select></label>`,
      );
      return;
    }
    if (path.startsWith('/account')) {
      const mode = path.split('/')[2] || 'login';
      if (mode === 'operator') accountFlow.role = 'OPERATOR';
      if (mode === 'student') accountFlow.role = 'STUDENT';
      let content = accountPage({ user: api.user }, mode, accountFlow);
      if (mode === 'reset')
        content = content.replace(
          '<button class="primary full">',
          field(
            'confirmPassword',
            t('Nhập lại mật khẩu', 'Confirm password'),
            '',
            'password',
          ) + '<button class="primary full">',
        );
      draw(content);
      return;
    }
    const publicRoute =
      ['/student/search', '/student/vehicles'].includes(path) ||
      path.startsWith('/student/vehicles/');
    if (!api.user && !publicRoute) {
      go('/account/student');
      return;
    }
    if (api.user && api.user.role !== 'STUDENT') {
      draw(
        mobileHeading('Mobile') +
          notice(
            'Phần API này dành cho sinh viên.',
            'This API flow is for students.',
          ),
      );
      return;
    }
    if (path === '/student/hubs') {
      draw(
        '<header class="home-heading">Mobility Hub</header>' +
          hubsPage(
            { vehicles: [] },
            {
              bookingPath: '/student/vehicles',
            },
          ).replace(
            /<div class="figma-map">[\s\S]*?<div class="home-quick">/,
            '<div class="home-quick">',
          ),
      );
      return;
    }
    if (path === '/student/search') {
      draw(
        `<div class="hub-browser">${searchForm(locationLoaded, selectedHubId, showHubVehicles, hubParkingSpaces)}</div>`,
      );
      return;
    }
    if (path === '/student/vehicles') {
      draw(vehicleList());
      return;
    }
    if (path.startsWith('/student/vehicles/')) {
      draw(
        mobileHeading(
          t('Chi tiết phương tiện', 'Vehicle details'),
          '/student/vehicles',
        ) +
          '<p role="status">' +
          t('Đang tải…', 'Loading…') +
          '</p>',
      );
      try {
        const v = await api.vehicle(
          decodeURIComponent(path.split('/')[3]),
          controller.signal,
        );
        if (!disposed) {
          selected = v;
          draw(detailsPage(v));
        }
      } catch (error) {
        if (error.name !== 'AbortError') message(errorText(error));
      }
      return;
    }
    if (path === '/student/reservation-confirmed') {
      const receipt = api.reservations[0];
      draw(
        mobileHeading(t('Xác nhận đặt xe', 'Vehicle booking confirmed')) +
          (receipt
            ? successCard(
                t('Đã giữ xe', 'Vehicle reserved'),
                t('Dữ liệu xác nhận từ backend.', 'Confirmed by the backend.'),
              ) + bookingCard(receipt)
            : notice('Chưa có xác nhận đặt xe.', 'No reservation receipt.')),
      );
      return;
    }
    if (path === '/student/reservations') {
      draw(
        mobileHeading(t('Lượt đặt', 'Bookings')) +
          notice(
            'Các lượt đặt được lưu trên tab này; hệ thống kiểm tra trạng thái khi giao dịch.',
            'Bookings saved in this tab. Final status is checked when you perform a transaction.',
          ) +
          (api.ride?.status === 'ACTIVE'
            ? `<section class="panel"><h2>${e(api.ride.vehicleId)}</h2>${link('/student/return', t('Xe đang sử dụng · Trả xe', 'Current vehicle · Return'), 'button primary full')}</section>`
            : '') +
          receipts().map(bookingCard).join(''),
      );
      return;
    }
    if (path === '/student/pickup') {
      draw(pickupForm());
      return;
    }
    if (path === '/student/parking' || path === '/student/return') {
      const returning = path === '/student/return';
      const ride = api.ride?.status === 'ACTIVE' ? api.ride : null;
      if (
        returning &&
        api.command?.kind === 'RETURN' &&
        api.command.state === 'SUCCEEDED'
      ) {
        draw(
          mobileHeading(t('Trả phương tiện', 'Return vehicle')) +
            successCard(
              t('Đã trả xe', 'Vehicle returned'),
              t(
                'Hệ thống đã xác nhận hoàn tất chuyến đi.',
                'The system confirmed that your ride is complete.',
              ),
            ) +
            link(
              '/student/hubs',
              t('Về trang chủ', 'Back home'),
              'button primary full',
            ),
        );
        return;
      }
      if (returning && !ride) {
        draw(
          mobileHeading(t('Trả phương tiện', 'Return vehicle')) +
            notice(
              'Chưa có chuyến đi đang sử dụng. Nhận xe từ lượt đặt của bạn để bắt đầu.',
              'No active ride. Pick up your reserved vehicle to start.',
            ) +
            link(
              '/student/reservations',
              t('Xem lượt đặt', 'View bookings'),
              'button primary full',
            ) +
            commandPanel(),
        );
        return;
      }
      draw(
        mobileHeading(
          returning
            ? t('Trả phương tiện', 'Return vehicle')
            : t('Đặt chỗ đỗ', 'Book parking'),
        ) +
          (ride && returning
            ? `<section class="panel"><h2>${icon('bike')} ${e(ride.vehicleId)}</h2><p>${t('Xe đang sử dụng', 'Your current vehicle')}</p></section>`
            : '') +
          (locationLoaded
            ? `<div class="hub-list">${api.hubs.map((h) => `<button class="hub-row" data-parking-hub="${e(h.hubId)}">${icon('parking')}<span><strong>${e(h.hubName)}</strong>${hubSummary(h)}</span></button>`).join('')}</div>`
            : `<button class="primary full" data-api-action="gps">${t('Tìm chỗ đỗ gần tôi', 'Find parking near me')}</button>`) +
          (hubParkingSpaces && selectedHubId
            ? parkingAtHub(
                api.hubs.find((h) => h.hubId === selectedHubId),
                hubParkingSpaces,
              )
            : '') +
          commandPanel(),
      );
      if (returning)
        root
          .querySelector('[data-api="parking"]')
          ?.closest('section')
          ?.remove();
      return;
    }
    if (path === '/student/charging') {
      draw(
        mobileHeading(t('Đăng ký sạc', 'Register charging')) +
          `<form data-api="charging" class="auth-form">${field('vehicleId', t('Mã xe cá nhân', 'Personal vehicle ID'))}${field('hubId', t('Mã Hub', 'Hub ID'))}${field('target', t('Mức pin mục tiêu (%)', 'Target battery (%)'), 80, 'number', 'min="1" max="100" step="any"')}${field('start', t('Bắt đầu', 'Start'), '', 'datetime-local')}<button class="primary full">${t('Đăng ký sạc', 'Register charging')}</button></form>`,
      );
      return;
    }
    draw(
      mobileHeading(t('Không tìm thấy trang', 'Page not found')) +
        notice(
          'Hãy quay lại trang chủ để tiếp tục.',
          'Return to the home page to continue.',
        ),
    );
  }
  async function submit(event) {
    const form = event.target;
    const kind = form.dataset.api || form.dataset.form;
    if (!kind) return;
    event.preventDefault();
    const f = new FormData(form);
    await run(async () => {
      if (kind === 'login') {
        await api.login(f.get('email'), f.get('password'), f.get('role'));
        if (!disposed)
          go(
            api.user?.role === 'OPERATOR'
              ? '/operator/overview'
              : '/student/hubs',
          );
      } else if (kind === 'forgot') {
        await api.requestRecovery(f.get('email'));
        if (!disposed) go('/account/verify');
      } else if (kind === 'verify') {
        await api.verifyRecovery(f.get('code'));
        if (!disposed) go('/account/reset');
      } else if (kind === 'reset') {
        await api.resetPassword(f.get('password'), f.get('confirmPassword'));
        if (!disposed) go('/account/changed');
      } else if (kind === 'password') {
        await api.changePassword(
          f.get('current'),
          f.get('password'),
          f.get('confirm'),
        );
        if (!disposed) go('/account/changed');
      } else if (kind === 'register') {
        await api.register(
          f.get('studentId'),
          f.get('email'),
          f.get('password'),
          f.get('name'),
        );
        if (!disposed) go('/account/registered');
      } else {
        if (
          !(await confirmAction(
            t(
              'Xác nhận gửi yêu cầu tới hệ thống?',
              'Send this request to the system?',
            ),
          )) ||
          disposed
        )
          return;
        if (kind === 'reserve') {
          await api.reserve(form.dataset.id, f.get('start'), f.get('end'));
          if (!disposed) go('/student/reservation-confirmed');
        }
        if (kind === 'parking') {
          await api.reserveParking(
            f.get('hubId'),
            f.get('spaceId'),
            f.get('start'),
            f.get('end'),
          );
          if (!disposed) go('/student/reservations');
        }
        if (kind === 'charging') {
          await api.charge(
            f.get('vehicleId'),
            f.get('hubId'),
            f.get('target'),
            f.get('start'),
          );
          if (!disposed) go('/student/reservations');
        }
        if (kind === 'pickup') {
          await api.pickup(
            f.get('vehicleId'),
            f.get('reservationId'),
            f.get('pickupCode'),
          );
          if (!disposed) render();
        }
        if (kind === 'return') {
          await api.returnRide(
            f.get('rideId'),
            f.get('hubId'),
            f.get('spaceId'),
          );
          if (!disposed) render();
        }
      }
    });
  }
  function applyQr(text) {
    if (disposed) return;
    // Only decode the vehicle identifier. Never navigate to a scanned URL.
    let id = text.trim();
    try {
      const parsed = JSON.parse(text);
      if (typeof parsed.vehicleId === 'string') id = parsed.vehicleId;
    } catch {
      /* Plain vehicle IDs are supported. */
    }
    const el = root.querySelector('[name="vehicleId"]');
    const hold = api.reservations.find((r) => r.status === 'ACTIVE');
    if (hold && hold.vehicleId !== id) {
      message(
        t(
          'Mã QR không thuộc xe đã đặt. Hãy quét đúng xe.',
          'This QR does not match your reserved vehicle.',
        ),
      );
      return;
    }
    if (el) el.value = id;
  }
  function click(event) {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.hasAttribute('data-parking-hub')) {
      selectedHubId = button.dataset.parkingHub;
      hubParkingSpaces = null;
    }
    if (
      button.dataset.apiAction === 'hub-parking' ||
      button.hasAttribute('data-parking-hub')
    ) {
      run(async () => {
        const hubId = selectedHubId;
        const spaces = await api.hubSpaces(hubId, controller.signal);
        if (!disposed && selectedHubId === hubId) {
          hubParkingSpaces = spaces;
          render();
        }
      });
      return;
    }
    if (button.hasAttribute('data-select-hub')) {
      if (busy || !locationLoaded) return;
      selectedHubId = button.dataset.selectHub;
      hubParkingSpaces = null;
      showHubVehicles = false;
      render();
      return;
    }
    if (button.dataset.apiAction === 'hub-list') {
      selectedHubId = null;
      showHubVehicles = false;
      render();
      return;
    }
    if (button.dataset.apiAction === 'hub-vehicles') {
      showHubVehicles = true;
      render();
      return;
    }
    if (
      button.hasAttribute('data-op-theme') ||
      button.hasAttribute('data-op-locale')
    ) {
      const key = button.hasAttribute('data-op-theme') ? 'theme' : 'locale';
      preference(
        key,
        key === 'theme'
          ? document.documentElement.dataset.theme === 'dark'
            ? 'light'
            : 'dark'
          : document.documentElement.lang === 'en'
            ? 'vi'
            : 'en',
      );
      render();
      return;
    }
    const action = button.dataset.apiAction;
    if (button.dataset.action === 'logout')
      run(async () => {
        try {
          await api.logout();
        } finally {
          if (!disposed) go('/account/student');
        }
      });
    if (button.dataset.cancelApi)
      run(async () => {
        if (
          (await confirmAction(
            t('Hủy lượt đặt này?', 'Cancel this reservation?'),
          )) &&
          !disposed
        ) {
          await api.cancel(button.dataset.cancelApi);
          if (!disposed) render();
        }
      });
    if (action === 'poll')
      run(async () => {
        await api.poll(controller.signal);
        if (!disposed) render();
      });
    if (action === 'retry')
      run(async () => {
        await api.sendCommand();
        if (!disposed) render();
      });
    if (action === 'camera') run(() => scanCamera(applyQr));
    if (action === 'stop-camera') stopScanner();
    if (action === 'privacy-camera')
      run(async () => {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: true,
        });
        stream.getTracks().forEach((track) => track.stop());
        message(t('Đã cho phép camera.', 'Camera access granted.'), false);
      });
    if (action === 'privacy-location')
      run(async () => {
        await new Promise((resolve, reject) =>
          navigator.geolocation.getCurrentPosition(resolve, reject),
        );
        message(t('Đã cho phép vị trí.', 'Location access granted.'), false);
      });
    if (action === 'gps')
      run(async () => {
        locationLoaded = false;
        selectedHubId = null;
        showHubVehicles = false;
        api.hubs = [];
        render();
        const locationButton = root.querySelector('[data-api-action="gps"]');
        if (locationButton) locationButton.disabled = true;
        if (!api.user) {
          go('/account/student');
          return;
        }
        if (!navigator.geolocation)
          throw new Error(
            t(
              'Trình duyệt không hỗ trợ vị trí. Hãy dùng trình duyệt hỗ trợ định vị.',
              'This browser does not support location. Use a location-capable browser.',
            ),
          );
        message(t('Đang lấy vị trí của bạn…', 'Getting your location…'), false);
        const position = await new Promise((resolve, reject) =>
          navigator.geolocation.getCurrentPosition(
            resolve,
            (error) =>
              reject(
                new Error(
                  error.code === 1
                    ? t(
                        'Bạn cần cho phép vị trí để tìm Hub. Mở quyền của trang cạnh thanh địa chỉ, bật Vị trí rồi thử lại.',
                        'Location permission is required. Open site permissions next to the address bar, allow Location and try again.',
                      )
                    : t(
                        'Chưa lấy được vị trí. Hãy bật dịch vụ vị trí của thiết bị và thử lại.',
                        'Location unavailable. Enable device location services and try again.',
                      ),
                ),
              ),
            { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
          ),
        );
        if (disposed) return;
        message(t('Đang tải danh sách Hub…', 'Loading Mobility Hubs…'), false);
        await api.discoverHubs(position.coords, controller.signal);
        if (!disposed) {
          locationLoaded = true;
          render();
          if (!api.hubs.length)
            message(t('Hệ thống chưa có Hub.', 'No Hubs are registered yet.'));
        }
      });
  }

  function change(event) {
    const el = event.target;
    if (el.dataset.apiPref) {
      preference(el.dataset.apiPref, el.value);
      render();
    }
    if (el.id === 'api-qr-file' && el.files[0])
      run(async () => applyQr(await scanFile(el.files[0])));
  }
  root.addEventListener('submit', submit);
  root.addEventListener('click', click);
  root.addEventListener('change', change);
  render();
  timer = setInterval(() => {
    if (
      !busy &&
      api.command?.state === 'PENDING' &&
      api.command.operationId &&
      polls < 30 &&
      [
        '/student/pickup',
        '/student/return',
        '/student/search',
        '/student/parking',
      ].includes(path)
    ) {
      polls++;
      run(async () => {
        await api.poll(controller.signal);
        if (!disposed && api.command?.state !== 'PENDING') render();
      });
    }
  }, 2000);
  return () => {
    disposed = true;
    controller.abort();
    clearInterval(timer);
    stopScanner();
    root.removeEventListener('submit', submit);
    root.removeEventListener('click', click);
    root.removeEventListener('change', change);
  };
}
