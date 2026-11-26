import { searchHubs } from './hub-search-api.js';
import {
  e,
  t,
  title,
  input,
  link,
  preference,
  applyPreferences,
} from '../shared/ui.js';
let values = {
  latitude: '',
  longitude: '',
  radiusKm: '3',
  reference: 'origin',
};
export function searchError(error) {
  if (error.message === 'SEARCH_INPUT')
    return t(
      'Nhập vĩ độ từ -90 đến 90, kinh độ từ -180 đến 180 và bán kính lớn hơn 0, tối đa 50 km.',
      'Enter latitude -90 to 90, longitude -180 to 180, and a radius greater than 0 up to 50 km.',
    );
  if (error.message === 'SEARCH_RESPONSE')
    return t(
      'Dữ liệu backend không đúng định dạng UC01. Vui lòng kiểm tra phiên bản backend.',
      'Backend response does not match UC01. Check the backend version.',
    );
  if (error.status === 400)
    return t(
      'Backend từ chối điều kiện tìm kiếm. Kiểm tra tọa độ và giới hạn bán kính của server.',
      'Backend rejected the search. Check coordinates and the server radius limit.',
    );
  if (error.status === 404)
    return t(
      'Không tìm thấy API UC01. Hãy chạy nhánh m1_student của backend.',
      'UC01 endpoint not found. Run the backend m1_student branch.',
    );
  if (error.status === 401 || error.status === 403)
    return t(
      'Backend yêu cầu quyền truy cập. Cơ chế đăng nhập API chưa được tích hợp.',
      'Backend requires authorization. API authentication is not integrated yet.',
    );
  if (error.name === 'TimeoutError')
    return t(
      'Backend phản hồi quá chậm. Vui lòng thử lại.',
      'Backend timed out. Please retry.',
    );
  return t(
    'Không kết nối được backend hoặc server gặp lỗi. Kiểm tra backend cổng 8080 và thử lại.',
    'Cannot reach backend or server error. Check backend port 8080 and retry.',
  );
}
export function searchResults(hubs) {
  if (!hubs.length)
    return `<div class="empty">${t('Không có Hub trong bán kính này. Thử vị trí hoặc bán kính khác.', 'No Hubs within this radius. Try another location or radius.')}</div>`;
  return hubs
    .map(
      (h) =>
        `<article class="panel"><div class="row-between"><h2>${e(h.hubName)}</h2><span class="badge">${e(h.distanceKm.toFixed(2))} km</span></div><p>${t('Xe khả dụng', 'Available vehicles')}: ${h.availableVehicleCount}</p><p>${t('Dữ liệu Hub', 'Hub telemetry')}: <strong>${e({ FRESH: t('Mới', 'Fresh'), STALE: t('Cũ', 'Stale'), MISSING: t('Thiếu dữ liệu', 'Missing data') }[h.telemetryStatus])}</strong></p>${h.telemetryStatus !== 'FRESH' ? `<p class="hint">${t('Một phần dữ liệu Hub đã cũ hoặc bị thiếu. Chỉ liệt kê xe backend trả về; không tự suy ra xe khả dụng.', 'Some Hub telemetry is stale or missing. Only backend-returned vehicles are listed; availability is not inferred.')}</p>` : ''}${h.vehicles.length ? `<ul class="live-vehicles">${h.vehicles.map((v) => `<li><strong>${e(v.vehicleId)}</strong><span>${t('Pin', 'Battery')}: ${Math.round(v.batteryPct)}%</span><span class="badge">${e(v.status)}</span></li>`).join('')}</ul>` : `<p>${t('Chưa có xe đủ điều kiện trong kết quả backend.', 'No eligible vehicles in the backend results.')}</p>`}<p class="hint">${t('API chi tiết và đặt xe chưa sẵn sàng.', 'Vehicle details and booking APIs are not available yet.')}</p></article>`,
    )
    .join('');
}
export function mountLiveSearch(root, path) {
  let disposed = false,
    controller = null;
  const supported = ['/', '/student/hubs'].includes(path);
  function draw() {
    controller?.abort();
    controller = null;
    applyPreferences();
    root.innerHTML = `<div class="app-frame"><header class="app-header"><a href="#/student/hubs" class="brand">◈ Mobility Hub</a><div class="preferences"><label><span class="sr-only">${t('Ngôn ngữ', 'Language')}</span><select data-live-pref="locale"><option value="vi" ${document.documentElement.lang === 'vi' ? 'selected' : ''}>VI</option><option value="en" ${document.documentElement.lang === 'en' ? 'selected' : ''}>EN</option></select></label><label><span class="sr-only">${t('Giao diện', 'Appearance')}</span><select data-live-pref="theme"><option value="light" ${document.documentElement.dataset.theme === 'light' ? 'selected' : ''}>${t('Sáng', 'Light')}</option><option value="dark" ${document.documentElement.dataset.theme === 'dark' ? 'selected' : ''}>${t('Tối', 'Dark')}</option></select></label></div></header><div class="demo-banner">API · ${t('Kết nối backend — UC01', 'Backend connection — UC01')}</div><main>${supported ? title(t('Tìm Mobility Hub', 'Find Mobility Hubs'), t('Kết quả trực tiếp từ backend của nhóm', 'Results directly from the team backend')) + `<form class="panel" id="live-search"><label>${t('Điểm tìm kiếm', 'Search reference')}<select name="reference"><option value="origin" ${values.reference === 'origin' ? 'selected' : ''}>${t('Điểm đi', 'Origin')}</option><option value="destination" ${values.reference === 'destination' ? 'selected' : ''}>${t('Điểm đến', 'Destination')}</option></select></label><div class="form-grid">${input('latitude', t('Vĩ độ', 'Latitude'), 'number', values.latitude, 'required min="-90" max="90" step="any"')}${input('longitude', t('Kinh độ', 'Longitude'), 'number', values.longitude, 'required min="-180" max="180" step="any"')}</div>${input('radiusKm', t('Bán kính (km)', 'Radius (km)'), 'number', values.radiusKm, 'required min="0.01" max="50" step="any"')}<div class="row-actions"><button type="button" data-live-gps>${t('Dùng vị trí của tôi', 'Use my location')}</button><button type="button" data-live-example>${t('Điền tọa độ mẫu H1', 'Fill sample H1 coordinates')}</button></div><p class="hint">${t('Tọa độ chỉ gửi tới backend khi bấm tìm kiếm; không lưu vào trình duyệt. Tọa độ mẫu H1: 10.77, 106.66.', 'Coordinates are sent to backend only when you search, and are not persisted. Sample H1: 10.77, 106.66.')}</p><button class="primary full" type="submit">${t('Tìm Hub', 'Search Hubs')}</button></form><p id="live-status" role="status"></p><div id="live-results" aria-live="polite"></div><aside class="panel"><h2>${t('Phạm vi đang kết nối', 'Connected scope')}</h2><p>${t('UC01 hiện cho phép tìm kiếm không cần đăng nhập. Đăng nhập, đặt xe, nhận/trả xe và sạc đang chờ backend. Không dùng dữ liệu demo trong chế độ API.', 'UC01 currently supports public search. Authentication, reservations, pickup/return and charging await backend implementation. API mode does not use demo data.')}</p></aside>` : title(t('Chức năng API chưa sẵn sàng', 'API feature not available')) + link('/student/hubs', t('Về tìm Hub', 'Back to Hub search'))}</main></div>`;
  }
  function remember() {
    const form = root.querySelector('#live-search');
    if (form) values = Object.fromEntries(new FormData(form));
  }
  function status(text, error = false) {
    const el = root.querySelector('#live-status');
    if (el) {
      el.textContent = text;
      el.className = error ? 'feedback error' : 'hint';
    }
  }
  async function submit(event) {
    if (event.target.id !== 'live-search') return;
    event.preventDefault();
    remember();
    controller?.abort();
    const current = new AbortController();
    controller = current;
    const button = event.target.querySelector('[type="submit"]');
    button.disabled = true;
    root.querySelector('#live-results').innerHTML = '';
    status(t('Đang tìm kiếm…', 'Searching…'));
    try {
      const hubs = await searchHubs(values, current.signal);
      if (disposed || current !== controller) return;
      root.querySelector('#live-results').innerHTML = searchResults(hubs);
      status(
        t('Đã nhận kết quả từ backend.', 'Results received from backend.'),
      );
    } catch (error) {
      if (!disposed && current === controller && error.name !== 'AbortError')
        status(searchError(error), true);
    } finally {
      if (!disposed && current === controller) button.disabled = false;
    }
  }
  function click(event) {
    if (event.target.closest('[data-live-example]')) {
      remember();
      values.latitude = '10.77';
      values.longitude = '106.66';
      draw();
    }
    if (event.target.closest('[data-live-gps]')) {
      if (!navigator.geolocation) {
        status(
          t(
            'Trình duyệt không hỗ trợ vị trí. Nhập tọa độ thủ công.',
            'Location not supported. Enter coordinates manually.',
          ),
          true,
        );
        return;
      }
      status(t('Đang lấy vị trí…', 'Requesting location…'));
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          if (disposed) return;
          remember();
          values.latitude = String(pos.coords.latitude);
          values.longitude = String(pos.coords.longitude);
          draw();
        },
        () => {
          if (!disposed)
            status(
              t(
                'Không lấy được vị trí. Bạn có thể nhập tọa độ thủ công.',
                'Location unavailable. Enter coordinates manually.',
              ),
              true,
            );
        },
        { timeout: 10000 },
      );
    }
  }
  function change(event) {
    if (event.target.dataset.livePref) {
      remember();
      controller?.abort();
      controller = null;
      preference(event.target.dataset.livePref, event.target.value);
      draw();
    }
  }
  draw();
  root.addEventListener('submit', submit);
  root.addEventListener('click', click);
  root.addEventListener('change', change);
  return () => {
    disposed = true;
    controller?.abort();
    root.removeEventListener('submit', submit);
    root.removeEventListener('click', click);
    root.removeEventListener('change', change);
  };
}
