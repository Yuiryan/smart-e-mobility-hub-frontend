import { mountMobileApi } from './features/mobile-api.js';
let disposeLive;
import { OperatorEngine } from './mocks/operator-engine.js';
import { operatorPage, bindOperator } from './features/operator.js';
const operations = new OperatorEngine(engine);
const operatorState = {};
let disposeOperator;
import './index.css';
import './styles/operator.css';
import './styles/figma.css';
import './styles/operator-figma.css';
import {
  operatorShell,
  operatorAuthShell,
  desktopWelcome,
  operatorSettings,
} from './features/operator-design.js';
import { icon, welcomePage } from './shared/design.js';
const accountFlow = { role: 'STUDENT' };
import { engine, call, snapshot, demoMode } from './domain/service.js';
import { validWindow } from './mocks/engine.js';
import { overlaps } from './domain/time.js';
import { features } from './features/catalog.js';
import { accountPage } from './features/account.js';
import {
  hubsPage,
  searchPage,
  availableVehiclesPage,
  reservationConfirmedPage,
  vehiclePage,
  bookingsPage,
  parkingPage,
  chargingPage,
  pickupPage,
  returnPage,
} from './features/student.js';
import {
  e,
  t,
  msg,
  title,
  link,
  date,
  windowFrom,
  confirmAction,
  preference,
  applyPreferences,
} from './shared/ui.js';
import { stopScanner, scanCamera, scanFile } from './shared/scanner.js';
const root = document.querySelector('#root');
let route = '',
  busy = false,
  parkingWindow = null,
  version = 0;
const go = (path) => (location.hash = path);
function feedback(text, error = true) {
  const box =
    document.querySelector('#auth-error') ||
    document.querySelector('#feedback');
  if (box) {
    box.hidden = false;
    box.className = 'feedback ' + (error ? 'error' : 'success');
    box.textContent = text;
    box.focus();
  }
}
function clearDrafts() {
  try {
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith('emh-draft:'))
      .forEach((k) => sessionStorage.removeItem(k));
  } catch {
    /* Optional storage. */
  }
}
function draftKey() {
  return 'emh-draft:' + snapshot().user?.id + ':' + route;
}
function restoreDraft() {
  try {
    const x = JSON.parse(sessionStorage.getItem(draftKey()));
    if (x && Date.now() - x.at < 900000)
      document.querySelectorAll('[data-draft]').forEach((el) => {
        if (typeof x.values[el.name] === 'string') el.value = x.values[el.name];
      });
  } catch {
    /* Ignore malformed drafts. */
  }
}
function saveDraft() {
  try {
    const values = {};
    document
      .querySelectorAll('[data-draft]')
      .forEach((el) => (values[el.name] = el.value));
    sessionStorage.setItem(
      draftKey(),
      JSON.stringify({ at: Date.now(), values }),
    );
  } catch {
    /* Optional cache. */
  }
}
function settings() {
  return (
    title(t('Cài đặt giao diện', 'Display settings')) +
    `<section class="panel"><label>${t('Ngôn ngữ', 'Language')}<select data-pref="locale"><option value="vi" ${document.documentElement.lang === 'vi' ? 'selected' : ''}>Tiếng Việt</option><option value="en" ${document.documentElement.lang === 'en' ? 'selected' : ''}>English</option></select></label><label>${t('Giao diện', 'Appearance')}<select data-pref="theme"><option value="light" ${document.documentElement.dataset.theme === 'light' ? 'selected' : ''}>${t('Sáng', 'Light')}</option><option value="dark" ${document.documentElement.dataset.theme === 'dark' ? 'selected' : ''}>${t('Tối', 'Dark')}</option></select></label><p>${t('Chỉ lưu ngôn ngữ, giao diện và thời gian form. Không lưu mật khẩu hoặc phiên đăng nhập.', 'Only language, appearance and time fields are cached. Passwords and sessions are never persisted.')}</p><button data-action="clear-drafts">${t('Xóa form lưu tạm', 'Clear saved forms')}</button></section>`
  );
}
function render() {
  disposeOperator?.();
  disposeOperator = null;
  disposeLive?.();
  disposeLive = null;
  if (!demoMode) {
    disposeLive = mountMobileApi(root, location.hash.slice(1) || '/');
    return;
  }
  stopScanner();
  version++;
  parkingWindow = null;
  applyPreferences();
  route = location.hash.slice(1) || '/';
  let d;
  try {
    d = snapshot();
  } catch (err) {
    root.innerHTML = `<main>${title('Smart E-Mobility Hub')}<p role="alert">${e(msg(err.code))}</p></main>`;
    return;
  }
  if (
    (route.startsWith('/student') || route.startsWith('/operator')) &&
    !d.user
  ) {
    go('/account');
    return;
  }
  if (
    (route.startsWith('/operator') && d.user?.role !== 'OPERATOR') ||
    (route.startsWith('/student') && d.user?.role !== 'STUDENT')
  ) {
    go(d.user.role === 'OPERATOR' ? '/operator/overview' : '/student/hubs');
    return;
  }
  let content;
  if (route === '/') content = welcomePage();
  else if (route.startsWith('/account')) {
    if (route === '/account/operator') accountFlow.role = 'OPERATOR';
    if (route === '/account/student') accountFlow.role = 'STUDENT';
    content = accountPage(d, route.split('/')[2], accountFlow);
  } else if (route === '/welcome/pc') content = '';
  else if (route === '/settings')
    content = d.user?.role === 'OPERATOR' ? operatorSettings() : settings();
  else if (route === '/student/hubs') content = hubsPage(d);
  else if (route === '/student/search') content = searchPage(d);
  else if (route === '/student/reservation-confirmed')
    content = reservationConfirmedPage(d);
  else if (route === '/student/vehicles') content = availableVehiclesPage(d);
  else if (route.startsWith('/student/vehicles/'))
    content = vehiclePage(d, decodeURIComponent(route.split('/')[3]));
  else if (route === '/student/reservations') content = bookingsPage(d);
  else if (route === '/student/parking') content = parkingPage(d);
  else if (route === '/student/charging') content = chargingPage(d);
  else if (route === '/student/pickup') content = pickupPage(d);
  else if (route === '/student/return') content = returnPage(d);
  else if (route === '/student/notifications')
    content =
      title(t('Thông báo', 'Notifications')) +
      (d.notices.length
        ? d.notices
            .map(
              (n) =>
                `<article class="panel"><h2>${e(msg(n.code))}</h2><p>${e(date(n.createdAt))}</p></article>`,
            )
            .join('')
        : `<div class="empty">${t('Chưa có thông báo', 'No notifications yet')}</div>`);
  else {
    const f =
      route === '/operator/resources' ||
      features.find((f) => f.area === 'operator' && f.path === route);
    content = f
      ? operatorPage(operations, route, operatorState)
      : title('404', t('Không tìm thấy trang', 'Page not found')) +
        link('/', t('Về trang chủ', 'Home'));
  }
  const nav =
    d.user?.role === 'OPERATOR'
      ? [
          ...features
            .filter((f) => f.area === 'operator')
            .map((f) => [f.path, t(f.title.vi, f.title.en)]),
          ['/account', t('Tài khoản', 'Account')],
        ]
      : [
          ['/student/hubs', t('Trang chủ', 'Home')],
          ['/student/reservations', t('Lượt đặt', 'Bookings')],
          ['/student/notifications', t('Thông báo', 'Alerts')],
          ['/account', t('Tài khoản', 'Account')],
        ];
  const operator = d.user?.role === 'OPERATOR';
  const home = route === '/student/hubs';
  const showNav =
    !!d.user &&
    (operator ||
      [
        '/student/hubs',
        '/student/search',
        '/student/reservations',
        '/student/notifications',
        '/account',
        '/settings',
      ].includes(route));
  const navIcons = ['home', 'clock', 'bell', 'user'];
  const operatorAuth =
    !d.user && accountFlow.role === 'OPERATOR' && route.startsWith('/account');
  root.innerHTML = operator
    ? operatorShell(
        content,
        route,
        operations.snapshot().metrics.filter((h) => h.severity !== 'NORMAL')
          .length,
      )
    : operatorAuth
      ? operatorAuthShell(content)
      : route === '/welcome/pc'
        ? desktopWelcome()
        : `<div class="app-frame ${operator ? 'operator-frame' : 'student-frame'} ${route === '/' ? 'welcome-frame' : ''}">${operator ? `<header class="app-header"><a href="#/operator/overview" class="brand">Mobility Hub</a>${link('/settings', t('Cài đặt', 'Settings'), 'text-link')}</header>` : `<div class="design-status" aria-hidden="true"><span>18:36</span><span>▣ Wi-Fi　67% ⌖</span></div>`}<main id="main" tabindex="-1"><div id="feedback" role="alert" tabindex="-1" hidden></div>${home ? '<header class="home-heading">Mobility Hub</header>' : ''}${content}</main>${showNav ? `<nav class="${operator ? 'operator-nav' : 'bottom-nav'}" aria-label="${t('Điều hướng chính', 'Main navigation')}">${nav.map(([path, text], i) => `<a href="#${path}" ${route === path ? 'aria-current="page" class="active"' : ''}>${operator ? '' : icon(navIcons[i])}<span>${e(text)}</span></a>`).join('')}</nav>` : ''}</div><aside class="preview-tools"><span>DEMO</span>${link('/settings', t('Giao diện / ngôn ngữ', 'Appearance / language'), 'text-link')}<details><summary>${t('Tài khoản thử', 'Demo accounts')}</summary><p>student@demo.local<br>operator@demo.local<br>Demo123!<br>${t('Mã khôi phục: 123456. Dữ liệu mẫu đặt lại khi tải lại trang. Thanh trạng thái là mẫu Figma.', 'Recovery code: 123456. Sample data resets on reload. Status bar is a Figma mockup.')}</p></details></aside>`;
  restoreDraft();
  if (route.startsWith('/operator'))
    disposeOperator = bindOperator(
      root,
      operations,
      route,
      operatorState,
      render,
    );
  document.querySelector('main')?.focus({ preventScroll: true });
  document.querySelectorAll('[data-qr]').forEach(async (canvas) => {
    const { default: QR } = await import('qrcode');
    if (canvas.isConnected)
      QR.toCanvas(canvas, canvas.dataset.qr, { width: 180, margin: 2 }).catch(
        () => {},
      );
  });
  updateClock();
  updateEstimate();
}
function updateEstimate() {
  const target = document.querySelector('[name="target"]'),
    el = document.querySelector('#charge-estimate');
  if (target && el)
    el.textContent =
      t('Thời gian ước tính: ', 'Estimated time: ') +
      Math.ceil((Number(target.value) - 35) * 1.5) +
      t(' phút', ' minutes');
}
function updateClock() {
  document.querySelectorAll('[data-countdown]').forEach((el) => {
    const left = Math.max(
      0,
      Math.ceil((Number(el.dataset.countdown) - Date.now()) / 1000),
    );
    el.textContent =
      String(Math.floor(left / 60)).padStart(2, '0') +
      ':' +
      String(left % 60).padStart(2, '0');
  });
  document.querySelectorAll('[data-expiry]').forEach((el) => {
    const left = Math.max(
      0,
      Math.ceil(
        (Number(el.dataset.expiry || el.dataset.countdown) - Date.now()) / 1000,
      ),
    );
    el.textContent =
      t('Còn hiệu lực: ', 'Expires in: ') +
      `${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`;
  });
  document
    .querySelectorAll('[data-ride-start]')
    .forEach(
      (el) =>
        (el.textContent =
          Math.floor((Date.now() - Number(el.dataset.rideStart)) / 60000) +
          t(' phút', ' minutes')),
    );
}
async function action(fn) {
  if (busy) return;
  busy = true;
  const buttons = [...document.querySelectorAll('button')].filter(
    (b) => !b.disabled,
  );
  buttons.forEach((b) => (b.disabled = true));
  try {
    await fn();
  } catch (err) {
    feedback(msg(err.code));
    if (err.code === 'UNAUTHORIZED') go('/account');
  } finally {
    busy = false;
    buttons.filter((b) => b.isConnected).forEach((b) => (b.disabled = false));
  }
}
root.addEventListener('submit', (event) => {
  if (!demoMode) return;
  event.preventDefault();
  const form = event.target,
    f = new FormData(form),
    kind = form.dataset.form;
  if (!kind) return;
  action(async () => {
    if (kind === 'password' && f.get('password') !== f.get('confirm')) {
      feedback(t('Mật khẩu nhập lại không khớp.', 'Passwords do not match.'));
      return;
    }
    if (kind === 'login') {
      await call('login', f.get('email'), f.get('password'), f.get('role'));
      clearDrafts();
      go(f.get('role') === 'OPERATOR' ? '/operator/overview' : '/student/hubs');
    } else if (kind === 'register') {
      await call(
        'register',
        f.get('name'),
        f.get('email'),
        f.get('password'),
        f.get('studentId'),
      );
      go('/account/registered');
    } else if (kind === 'forgot') {
      await call('requestReset', f.get('email'));
      accountFlow.email = f.get('email');
      go('/account/verify');
    } else if (kind === 'verify') {
      await call('verifyReset', accountFlow.email || '', f.get('code'));
      accountFlow.code = f.get('code');
      go('/account/reset');
    } else if (kind === 'reset') {
      await call(
        'resetPassword',
        accountFlow.email || '',
        accountFlow.code || '',
        f.get('password'),
      );
      delete accountFlow.email;
      delete accountFlow.code;
      go('/account/changed');
    } else if (kind === 'password') {
      await call('changePassword', f.get('current'), f.get('password'));
      go('/account');
    } else if (kind === 'parking-search') {
      const w = windowFrom(f);
      validWindow(w, Date.now());
      parkingWindow = w;
      const d = snapshot();
      document.querySelector('#parking-results').innerHTML = d.spaces
        .filter((s) => s.hubId === f.get('hub') && s.type === f.get('type'))
        .map((s) => {
          const blocked =
            s.occupied ||
            d.bookings.some(
              (b) =>
                b.kind === 'PARKING' &&
                b.resourceId === s.id &&
                b.status === 'ACTIVE' &&
                overlaps(b, w),
            );
          return `<button class="space" data-space="${e(s.id)}" ${blocked ? 'disabled' : ''}>${e(s.id)}<small>${blocked ? t('Không khả dụng', 'Unavailable') : t('Chọn chỗ', 'Select space')}</small></button>`;
        })
        .join('');
    } else if (kind === 'reserve' || kind === 'charging') {
      const w = windowFrom(f);
      validWindow(w, Date.now());
      if (
        !(await confirmAction(
          t(
            'Xác nhận lượt đặt mô phỏng cho thời gian đã chọn?',
            'Confirm the simulated booking for the selected time?',
          ),
        ))
      )
        return;
      if (kind === 'reserve') await call('reserveVehicle', form.dataset.id, w);
      else
        await call(
          'bookCharging',
          'PERSONAL-01',
          f.get('hub'),
          Number(f.get('target')),
          w,
        );
      clearDrafts();
      go(
        kind === 'reserve'
          ? '/student/reservation-confirmed'
          : '/student/reservations',
      );
    } else if (kind === 'scan-vehicle') {
      const id = String(f.get('code')).trim();
      if (!snapshot().vehicles.some((v) => v.id === id && !v.personal))
        throw Object.assign(new Error('INVALID_QR'), { code: 'INVALID_QR' });
      go('/student/vehicles/' + encodeURIComponent(id));
    } else if (kind === 'pickup' || kind === 'return') {
      if (
        !(await confirmAction(
          t(
            'Gửi lệnh thiết bị mô phỏng? Chỉ cập nhật chuyến đi sau ACK.',
            'Send simulated device command? Ride changes only after ACK.',
          ),
        ))
      )
        return;
      if (kind === 'pickup')
        await call('pickup', f.get('code'), f.get('outcome'));
      else
        await call(
          'returnVehicle',
          f.get('space'),
          f.has('issue'),
          f.get('outcome'),
        );
      render();
    }
  });
});
root.addEventListener('click', (event) => {
  if (!demoMode) return;
  const b = event.target.closest('button');
  if (!b) return;
  if (b.dataset.code) {
    document.querySelector('[name="code"]').value = b.dataset.code;
    return;
  }
  if (b.dataset.hub) {
    document
      .querySelector(`[data-hub-section="${b.dataset.hub}"]`)
      ?.scrollIntoView({ behavior: 'smooth' });
    return;
  }
  if (
    b.hasAttribute('data-op-theme') ||
    b.hasAttribute('data-op-locale') ||
    b.dataset.setting
  ) {
    const key =
      b.dataset.setting ||
      (b.hasAttribute('data-op-theme') ? 'theme' : 'locale');
    const value =
      b.dataset.value ||
      (key === 'theme'
        ? document.documentElement.dataset.theme === 'dark'
          ? 'light'
          : 'dark'
        : document.documentElement.lang === 'en'
          ? 'vi'
          : 'en');
    preference(key, value);
    render();
    return;
  }
  const a = b.dataset.action;
  if (a === 'stop-camera') {
    stopScanner();
    return;
  }
  if (a === 'camera') {
    action(async () => {
      try {
        await scanCamera((code) => {
          const input = document.querySelector('[name="code"]');
          if (input) input.value = code;
        });
      } catch {
        stopScanner();
        feedback(
          t(
            'Không mở được camera. Cho phép quyền truy cập hoặc nhập mã/chọn ảnh QR.',
            'Camera unavailable. Allow camera access or enter a code/upload QR.',
          ),
        );
      }
    });
    return;
  }
  if (a === 'gps') {
    if (!navigator.geolocation) {
      feedback(
        t('Trình duyệt không hỗ trợ vị trí.', 'Location is not supported.'),
      );
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const el = document.querySelector('#gps-result');
        if (!el) return;
        const nearest = [...snapshot().hubs].sort(
          (a, b) =>
            Math.hypot(
              a.lat - pos.coords.latitude,
              a.lng - pos.coords.longitude,
            ) -
            Math.hypot(
              b.lat - pos.coords.latitude,
              b.lng - pos.coords.longitude,
            ),
        )[0];
        el.textContent =
          t(
            'Gần nhất theo tọa độ mẫu: ',
            'Nearest using sample coordinates: ',
          ) + nearest.name;
      },
      () =>
        feedback(
          t(
            'Không lấy được vị trí. Bạn vẫn có thể chọn Hub thủ công.',
            'Location unavailable. You can still select a Hub manually.',
          ),
        ),
      { timeout: 10000 },
    );
    return;
  }
  action(async () => {
    if (a === 'logout') {
      await call('logout');
      clearDrafts();
      for (const key of Object.keys(operatorState)) delete operatorState[key];
      go('/');
    } else if (a === 'clear-drafts') {
      clearDrafts();
      feedback(t('Đã xóa dữ liệu form tạm.', 'Saved forms cleared.'), false);
    } else if (b.dataset.cancel) {
      if (await confirmAction(t('Hủy lượt đặt này?', 'Cancel this booking?'))) {
        await call('cancelBooking', b.dataset.cancel);
        render();
      }
    } else if (b.dataset.space && parkingWindow) {
      if (
        await confirmAction(
          t('Giữ chỗ đỗ ', 'Reserve parking ') + b.dataset.space + '?',
        )
      ) {
        await call('reserveParking', b.dataset.space, parkingWindow);
        clearDrafts();
        go('/student/reservations');
      }
    }
  });
});
root.addEventListener('change', async (event) => {
  if (!demoMode) return;
  const el = event.target;
  if (el.dataset.pref) {
    saveDraft();
    preference(el.dataset.pref, el.value);
    render();
  }
  if (el.matches('[data-draft]') || ['hub', 'type'].includes(el.name)) {
    saveDraft();
    parkingWindow = null;
    const results = document.querySelector('#parking-results');
    if (results) results.innerHTML = '';
  }
  if (el.id === 'booking-filter')
    document
      .querySelectorAll('[data-booking-status]')
      .forEach(
        (b) =>
          (b.hidden =
            el.value !== 'ALL' && b.dataset.bookingStatus !== el.value),
      );
  if (el.id === 'qr-file') {
    const current = version;
    try {
      const code = await scanFile(el.files[0]);
      if (current === version)
        document.querySelector('[name="code"]').value = code;
    } catch {
      if (current === version)
        feedback(
          t(
            'Không đọc được mã QR; chọn ảnh dưới 5 MB hoặc nhập mã.',
            'Cannot read QR; use an image under 5 MB or enter the code.',
          ),
        );
    }
  }
});
root.addEventListener('input', (event) => {
  if (!demoMode) return;
  const el = event.target;
  if (el.name === 'target') updateEstimate();
  if (el.id === 'hub-search') {
    const query = el.value.trim().toLowerCase();
    document
      .querySelectorAll('[data-search]')
      .forEach((card) => (card.hidden = !card.dataset.search.includes(query)));
    let count = 0;
    document.querySelectorAll('.hub-section').forEach((section) => {
      section.hidden = ![...section.querySelectorAll('[data-search]')].some(
        (c) => !c.hidden,
      );
      if (!section.hidden) count++;
    });
    document.querySelector('#search-empty').hidden = count > 0;
  }
});
window.addEventListener('hashchange', () => {
  render();
  window.scrollTo(0, 0);
});
for (const name of ['pointerdown', 'keydown'])
  window.addEventListener(
    name,
    () => {
      if (demoMode) engine.touch();
    },
    { passive: true },
  );
setInterval(async () => {
  if (!demoMode || busy) return;
  try {
    const before = snapshot();
    if (
      !before.user &&
      (route.startsWith('/student') || route.startsWith('/operator'))
    ) {
      go('/account');
      return;
    }
    let changed = false;
    for (const c of before.commands.filter(
      (c) => c.state === 'PENDING' && before.user?.role === 'STUDENT',
    )) {
      await call('pollCommand', c.id);
      if (snapshot().commands.find((x) => x.id === c.id)?.state !== c.state)
        changed = true;
    }
    if (
      ['/student/reservations', '/student/reservation-confirmed'].includes(
        route,
      ) &&
      [...document.querySelectorAll('[data-expiry], [data-countdown]')].some(
        (el) => Number(el.dataset.expiry || el.dataset.countdown) <= Date.now(),
      )
    )
      changed = true;
    if (changed) render();
    else updateClock();
  } catch {
    /* Expired session redirects on the next snapshot. */
  }
}, 500);
render();
