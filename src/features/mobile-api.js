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

let searchValues = {
  latitude: '',
  longitude: '',
  radiusKm: '3',
  reference: 'origin',
};
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
function searchForm() {
  return (
    mobileHeading(t('Chọn Mobility Hub', 'Choose a Mobility Hub')) +
    `<form data-api="search" class="auth-form">${notice('Chọn vị trí hoặc nhập tọa độ để tìm xe từ backend.', 'Choose your location or enter coordinates to search the backend.')}${field('latitude', t('Vĩ độ', 'Latitude'), searchValues.latitude, 'number', 'step="any" min="-90" max="90"')}${field('longitude', t('Kinh độ', 'Longitude'), searchValues.longitude, 'number', 'step="any" min="-180" max="180"')}${field('radiusKm', t('Bán kính (km)', 'Radius (km)'), searchValues.radiusKm, 'number', 'step="any" min="0.01" max="50"')}<button type="button" class="secondary full" data-api-action="gps">${t('Dùng vị trí của tôi', 'Use my location')}</button><button class="primary full">${t('Tìm Hub', 'Search Hubs')}</button></form><div class="hub-list">${api.hubs.map((h) => `<section class="panel"><h2>${e(h.hubName)}</h2><p>${h.availableVehicleCount} ${t('xe khả dụng', 'available vehicles')} · ${e(h.distanceKm)} km</p>${h.vehicles.map((v) => link(idLink(v.vehicleId), `${v.vehicleId} · ${v.batteryPct}%`, 'vehicle-option')).join('')}</section>`).join('')}</div>`
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
  return (
    mobileHeading(
      t('Chi tiết phương tiện', 'Vehicle details'),
      '/student/vehicles',
    ) +
    `<section class="vehicle-hero">${icon('bike')}<div><h2>${e(known?.type || t('Phương tiện', 'Vehicle'))}</h2><p>${e(v.vehicleId)}${known ? ' · ' + e(known.hubName) : ''}</p><span class="badge">${e(v.status)}</span></div></section><div class="battery-readout">${icon('battery')}<strong>${v.batteryLevel == null ? '—' : e(v.batteryLevel) + '%'}</strong><span>${t('Dung lượng pin', 'Battery level')}</span></div><dl class="vehicle-details"><div><dt>${t('Vị trí', 'Location')}</dt><dd>${e(known?.hubName || '—')}</dd></div><div><dt>${t('Trạng thái', 'Status')}</dt><dd>${e(v.status)}</dd></div><div><dt>${t('Cập nhật gần nhất', 'Last updated')}</dt><dd>${v.updatedAt ? e(date(v.updatedAt)) : '—'}</dd></div></dl>${missing ? `<p role="status">${e(v.reason || t('Dữ liệu chưa đủ để đặt xe.', 'Insufficient data to reserve.'))}</p>` : ''}<form data-api="reserve" data-id="${e(v.vehicleId)}" class="reservation-form"><details><summary>${t('Chọn thời gian thuê', 'Choose rental times')}</summary>${times()}</details><button class="primary full" ${blocked ? 'disabled' : ''}>${t('Đặt trước xe này', 'Reserve this vehicle')}</button></form>${link('/student/vehicles', t('Chọn phương tiện khác', 'Choose another vehicle'), 'button secondary full')}`
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
    `<section class="scanner panel"><div class="scan-window" aria-hidden="true"><i></i><i></i><i></i><i></i></div><video id="camera" playsinline muted hidden></video><button class="primary full" data-api-action="camera">${t('Quét mã QR', 'Scan QR code')}</button><label>${t('Chọn ảnh QR', 'Upload QR image')}<input id="api-qr-file" type="file" accept="image/*"></label></section><form data-api="pickup" class="auth-form">${field('vehicleId', t('Mã phương tiện', 'Vehicle ID'), hold?.vehicleId || selected?.vehicleId || '')}${input('reservationId', t('Mã đặt xe (nếu có)', 'Reservation ID (optional)'), 'text', hold?.reservationId || '')}${input('pickupCode', t('Mã nhận xe (nếu đã đặt)', 'Pickup code (if reserved)'), 'text', hold?.pickupCode || '')}<button class="primary full">${t('Xác nhận nhận xe', 'Confirm pickup')}</button></form>${commandPanel()}`
  );
}
function destinationFields() {
  return (
    field('hubId', t('Mã Hub', 'Hub ID')) +
    field('spaceId', t('Mã chỗ đỗ', 'Parking space ID'))
  );
}
export function mountMobileApi(root, path) {
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
    root.innerHTML = `<div class="app-frame student-frame"><div class="design-status" aria-hidden="true"><span>18:36</span><span>▣ Wi-Fi　67% ⌖</span></div><main><div id="api-feedback" role="alert" tabindex="-1" hidden></div>${content}</main>${
      api.user
        ? `<nav class="bottom-nav" aria-label="${t('Điều hướng chính', 'Main navigation')}">${[
            ['/student/hubs', 'home', t('Trang chủ', 'Home')],
            ['/student/reservations', 'clock', t('Lượt đặt', 'Bookings')],
            ['/student/notifications', 'bell', t('Thông báo', 'Notifications')],
            ['/account', 'user', t('Tài khoản', 'Account')],
          ]
            .map(
              ([p, i, n]) =>
                `<a href="#${p}" ${p === path ? 'class="active" aria-current="page"' : ''}>${icon(i)}<span>${n}</span></a>`,
            )
            .join('')}</nav>`
        : ''
    }</div><aside class="preview-tools"><span>API</span>${link('/settings', t('Giao diện / ngôn ngữ', 'Appearance / language'))}</aside>`;
  }
  function message(text) {
    if (disposed) return;
    const box = root.querySelector('#api-feedback');
    if (box) {
      box.hidden = false;
      box.className = 'feedback error';
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
    return `${error.code ? error.code + ': ' : ''}${error.message}${error.requestId ? ' (' + error.requestId + ')' : ''}`;
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
        mobileHeading(t('Cài đặt giao diện', 'Display settings')) +
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
      const first = inventory()[0];
      draw(
        '<header class="home-heading">Mobility Hub</header>' +
          hubsPage(
            { vehicles: [] },
            {
              bookingPath: first
                ? idLink(first.vehicleId)
                : '/student/vehicles',
            },
          ),
      );
      return;
    }
    if (path === '/student/search') {
      draw(searchForm());
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
            'Các lượt đặt nhận được trong phiên này. Backend chưa có API tải lịch sử.',
            'Receipts received in this session. The backend has no history endpoint yet.',
          ) +
          receipts().map(bookingCard).join(''),
      );
      return;
    }
    if (path === '/student/pickup') {
      draw(pickupForm());
      return;
    }
    if (path === '/student/parking') {
      draw(
        mobileHeading(t('Đặt chỗ đỗ', 'Book parking')) +
          notice(
            'Nhập mã chỗ đỗ được cung cấp tại Hub. Chưa có API danh sách chỗ đỗ khả dụng.',
            'Enter a parking space ID provided at the Hub. No available-space listing API exists yet.',
          ) +
          `<form data-api="parking" class="auth-form">${destinationFields()}${times()}<button class="primary full">${t('Đặt chỗ đỗ', 'Reserve parking')}</button></form>`,
      );
      return;
    }
    if (path === '/student/return') {
      draw(
        mobileHeading(t('Trả phương tiện', 'Return vehicle')) +
          `<form data-api="return" class="auth-form">${field('rideId', t('Mã chuyến đi', 'Ride ID'), api.ride?.status === 'ACTIVE' ? api.ride.rideId : '')}${destinationFields()}<button class="primary full">${t('Xác nhận trả xe', 'Confirm return')}</button></form>${commandPanel()}`,
      );
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
      mobileHeading(t('Thông báo', 'Notifications')) +
        notice(
          'Chưa có API cho màn hình này.',
          'No API is available for this screen yet.',
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
        await api.login(f.get('email'), f.get('password'));
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
        );
        if (!disposed) go('/account/registered');
      } else if (kind === 'search') {
        searchValues = { ...searchValues, ...Object.fromEntries(f) };
        await api.search(searchValues, controller.signal);
        if (!disposed) {
          render();
          if (!api.hubs.length)
            message(t('Không có Hub phù hợp.', 'No matching Hubs.'));
        }
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
    if (el) el.value = id;
  }
  function click(event) {
    const button = event.target.closest('button');
    if (!button) return;
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
    if (action === 'gps') {
      if (!navigator.geolocation) {
        message(t('Trình duyệt không hỗ trợ GPS.', 'GPS is not supported.'));
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          if (disposed) return;
          searchValues.latitude = String(pos.coords.latitude);
          searchValues.longitude = String(pos.coords.longitude);
          render();
        },
        () =>
          message(
            t(
              'Không lấy được vị trí. Hãy nhập tọa độ.',
              'Location unavailable. Enter coordinates.',
            ),
          ),
      );
    }
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
      ['/student/pickup', '/student/return'].includes(path)
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
