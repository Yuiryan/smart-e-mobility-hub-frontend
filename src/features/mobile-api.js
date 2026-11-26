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
const receipts = () =>
  [activePickupHold(), ...api.parking, ...api.charging].filter(Boolean);
function inventory() {
  return api.hubs.flatMap((h) =>
    h.vehicles.map((v) => ({
      ...v,
      hubId: h.hubId,
      hubName: h.hubName,
      distanceKm: h.distanceKm,
    })),
  );
}
function vehicleList(filters) {
  const vehicles = inventory()
    .filter(
      (v) =>
        (!filters.hub || v.hubId === filters.hub) &&
        v.batteryPct >= filters.battery,
    )
    .sort((a, b) =>
      filters.sort === 'battery'
        ? b.batteryPct - a.batteryPct
        : (a.distanceKm ?? Infinity) - (b.distanceKm ?? Infinity),
    );
  return (
    mobileHeading(t('Đặt xe', 'Book a vehicle')) +
    `<p class="hint">${t('Chọn phương tiện phù hợp cho chuyến đi của bạn.', 'Choose a vehicle for your trip.')}</p>
    <section class="vehicle-filters"><label>${t('Vị trí và khoảng cách', 'Location and distance')}<select data-vehicle-filter="hub"><option value="">${t('Tất cả Mobility Hub', 'All Mobility Hubs')}</option>${api.hubs.map((h) => `<option value="${e(h.hubId)}" ${filters.hub === h.hubId ? 'selected' : ''}>${e(h.hubName)}</option>`).join('')}</select></label><div class="filter-heading"><strong>${t('Dung lượng pin', 'Battery level')}</strong></div><div class="battery-filters">${[0, 30, 50, 80].map((n) => `<button data-battery-filter="${n}" aria-pressed="${filters.battery === n}">${n ? '≥ ' + n + '%' : t('Tất cả', 'All')}</button>`).join('')}</div></section>
    <div class="filter-heading"><h2>${vehicles.length} ${t('xe khả dụng', 'available vehicles')}</h2><select class="sort-select" data-vehicle-filter="sort" aria-label="${t('Sắp xếp', 'Sort')}"><option value="distance" ${filters.sort === 'distance' ? 'selected' : ''}>${t('Gần nhất', 'Nearest')}</option><option value="battery" ${filters.sort === 'battery' ? 'selected' : ''}>${t('Pin cao nhất', 'Highest battery')}</option></select></div>
    <div class="rental-cards">${vehicles.map((v) => `<article class="rental-card"><div class="vehicle-glyph">${icon('bike')}</div><div class="rental-card-content"><div class="filter-heading"><strong>${e(v.vehicleId)}</strong><span class="battery-value">${e(v.batteryPct)}%</span></div><p>${e(v.hubName)}</p><div class="filter-heading"><span class="distance-value">${v.distanceKm == null ? t('Chưa có khoảng cách', 'Distance unavailable') : e(v.distanceKm.toFixed(2)) + ' km'}</span>${link(idLink(v.vehicleId), t('Chọn xe', 'Select'), 'button secondary')}</div></div></article>`).join('')}</div>
    ${vehicles.length ? '' : `<div class="empty-state">${icon('bike')}<p>${t('Chưa có xe phù hợp. Hãy đổi bộ lọc hoặc tìm tại Hub khác.', 'No matching vehicles. Change filters or search another Hub.')}</p></div>`}
    <button type="button" class="secondary full" data-api-action="nearby-vehicles">${t('Tìm xe gần tôi', 'Find vehicles nearby')}</button>`
  );
}
function telemetryNotice(h) {
  if (!['STALE', 'MISSING'].includes(h.telemetryStatus)) return '';
  return `<p class="hint" role="status">${
    h.telemetryStatus === 'STALE'
      ? t(
          'Dữ liệu pin/trạng thái xe đã quá hạn cập nhật. Xe có dữ liệu cũ tạm thời không được phép thuê.',
          'Vehicle battery/status readings are out of date. Vehicles with stale data cannot be rented yet.',
        )
      : t(
          'Thiếu dữ liệu pin/trạng thái xe. Hệ thống chưa thể xác nhận xe đủ điều kiện thuê.',
          'Battery/status readings are missing. Vehicle eligibility cannot be confirmed.',
        )
  }</p>`;
}
function hubSummary(h) {
  return `<p>${h.distanceKm == null ? t('Chưa có khoảng cách', 'Distance unavailable') : e(h.distanceKm.toFixed(2)) + ' km'}${h.freeSpaceCount == null ? '' : ' · ' + e(h.freeSpaceCount) + t(' chỗ đỗ trống', ' free parking spaces')}</p>`;
}
function searchForm(
  loaded = false,
  selectedHubId = null,
  showVehicles = false,
  spaces = null,
  command = null,
) {
  const hub = loaded && api.hubs.find((h) => h.hubId === selectedHubId);
  if (hub)
    return (
      mobileHeading(t('Thông tin Mobility Hub', 'Mobility Hub details')) +
      `<section class="panel"><h2>${icon('pin')} ${e(hub.hubName)}</h2>${hubSummary(hub)}${telemetryNotice(hub)}${hub.capacity == null ? '' : `<p>${t('Sức chứa', 'Capacity')}: ${e(hub.capacity)}</p>`}<p>${hub.availableVehicleCount == null ? t('Chưa có dữ liệu xe tại Hub này.', 'Vehicle availability is not available for this Hub.') : e(hub.availableVehicleCount) + t(' xe khả dụng', ' available vehicles')}</p></section>
    <button class="primary full" data-api-action="hub-parking">${t('Đặt chỗ đỗ tại Hub này', 'Book parking at this Hub')}</button>
    ${spaces === null ? '' : parkingAtHub(hub, spaces) + (api.ride?.status === 'ACTIVE' ? parkingAtHub(hub, spaces, true) : '') + commandPanel(command)}
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
function vehicleWait(until) {
  const minutes = Math.max(
    1,
    Math.ceil((Date.parse(until) - Date.now()) / 60000),
  );
  const days = Math.floor(minutes / 1440),
    hours = Math.floor((minutes % 1440) / 60),
    mins = minutes % 60;
  return t(
    `Bạn cần chờ thêm ${days} ngày ${hours} giờ ${mins} phút để đổi xe.`,
    `You can change vehicles in ${days}d ${hours}h ${mins}m.`,
  );
}
function vehicleLabel(id) {
  if (!id?.startsWith('private_')) return id;
  const plate = /^private_([0-9]{2})([A-Z][A-Z0-9])([0-9]{4,5})$/.exec(id);
  return plate
    ? `${plate[1]}-${plate[2]} ${plate[3]}`
    : t('Chưa có biển số', 'Plate not provided');
}
function spaceLabel(id) {
  const number = id?.match(/([0-9]+)$/);
  return number ? t('Ô số ', 'Space ') + Number(number[1]) : id;
}
function parkingAtHub(hub, spaces, returning = false) {
  const eligibleVehicles = api.studentVehicles.filter(
    (vehicle) => vehicle.canBookParking,
  );
  if (!returning && !eligibleVehicles.length)
    return notice(
      'Bạn cần có xe đủ điều kiện chưa có ô đỗ để đặt chỗ.',
      'An eligible vehicle without a parking space is required.',
    );
  const owned = (s) =>
    returning &&
    api.parking.some(
      (r) =>
        r.status === 'ACTIVE' &&
        r.vehicleId === api.ride?.vehicleId &&
        r.spaceId === s.spaceId &&
        Date.parse(r.startsAt) <= Date.now() &&
        Date.now() < Date.parse(r.expiresAt),
    );
  const available = spaces.filter(
    (s) =>
      (s.status === 'AVAILABLE' || (s.status === 'RESERVED' && owned(s))) &&
      !s.blocked &&
      !s.pendingOperationId,
  );
  const destination = `<input type="hidden" name="hubId" value="${e(hub.hubId)}">${returning ? '' : vehicleSelect(eligibleVehicles, eligibleVehicles.length === 1 ? eligibleVehicles[0].vehicleId : '')}<fieldset class="parking-options"><legend>${t('Chọn chỗ đỗ', 'Choose a parking space')}</legend><div class="parking-count">${t('Chỗ còn trống', 'Available spaces')} <strong>${available.length} / ${spaces.length}</strong></div>${spaces
    .map((space) => {
      const free = available.includes(space);
      return `<label class="parking-option"><input type="radio" name="spaceId" value="${e(space.spaceId)}" required ${free ? '' : 'disabled'}><span>${icon('parking')}<strong>${e(spaceLabel(space.spaceId))}</strong><small>${owned(space) ? t('Ô bạn đã đặt', 'Your reserved space') : free ? t('Còn trống', 'Available') : t('Không khả dụng', 'Unavailable')}</small></span></label>`;
    })
    .join('')}</fieldset>`;
  return (
    `<section class="panel"><h2>${returning ? t('Trả xe tại Hub này', 'Return vehicle at this Hub') : t('Đặt chỗ đỗ', 'Book parking')}</h2>` +
    (available.length
      ? `<form data-api="${returning ? 'return' : 'parking'}" class="auth-form">${returning ? `<input type="hidden" name="rideId" value="${e(api.ride.rideId)}">` : ''}${destination}${returning ? '' : times()}<button class="primary full">${returning ? t('Xác nhận trả xe', 'Confirm return') : t('Xác nhận đặt chỗ đỗ', 'Confirm parking reservation')}</button></form>`
      : `${spaces.length ? destination : ''}${notice('Hiện không còn chỗ đỗ khả dụng tại Hub này. Hãy cập nhật hoặc chọn Hub khác.', 'No parking spaces are currently available at this Hub. Refresh or choose another Hub.')}`) +
    '</section>'
  );
}
function vehicleSelect(vehicles, selected, attributes = '') {
  return `<label>${t('Phương tiện', 'Vehicle')}<select name="vehicleId" required ${attributes}><option value="">${t('Chọn xe', 'Choose a vehicle')}</option>${vehicles.map((vehicle) => `<option value="${e(vehicle.vehicleId)}" ${vehicle.vehicleId === selected ? 'selected' : ''}>${t(...(vehicle.ownership === 'PRIVATE' ? ['Xe cá nhân', 'Personal vehicle'] : ['Xe đang thuê', 'Current rental']))} · ${e(vehicleLabel(vehicle.vehicleId))}</option>`).join('')}</select></label>`;
}
function detailsPage(v) {
  const known = inventory().find((x) => x.vehicleId === v.vehicleId);
  const taken = ['RESERVED', 'IN_USE'].includes(v.status);
  const statusLabel = taken ? t('Không khả dụng', 'Unavailable') : v.status;
  const missing =
    v.batteryLevel == null ||
    !v.updatedAt ||
    !!v.reason ||
    (Array.isArray(v.missingData) ? v.missingData.length > 0 : !!v.missingData);
  const blocked = missing || v.status !== 'AVAILABLE' || v.batteryLevel < 20;
  const currentRide =
    api.ride?.status === 'ACTIVE' && api.ride.vehicleId === v.vehicleId;
  const currentHold = api.reservations.some(
    (r) =>
      r.vehicleId === v.vehicleId &&
      r.status === 'ACTIVE' &&
      (!r.expiresAt || Date.parse(r.expiresAt) > Date.now()),
  );
  return (
    mobileHeading(
      t('Chi tiết phương tiện', 'Vehicle details'),
      '/student/vehicles',
    ) +
    `<section class="vehicle-hero">${icon('bike')}<div><h2>${e(t('Xe máy điện', 'Electric motorbike'))}</h2><p>${e(vehicleLabel(v.vehicleId))}${known ? ' · ' + e(known.hubName) : ''}</p><span class="badge">${e(statusLabel)}</span></div></section><div class="battery-readout">${icon('battery')}<strong>${v.batteryLevel == null ? '—' : e(Number(v.batteryLevel.toFixed(1))) + '%'}</strong><span>${t('Dung lượng pin', 'Battery level')}</span></div><dl class="vehicle-details"><div><dt>${t('Vị trí', 'Location')}</dt><dd>${e(known?.hubName || '—')}</dd></div><div><dt>${t('Trạng thái', 'Status')}</dt><dd>${e(statusLabel)}</dd></div><div><dt>${t('Cập nhật gần nhất', 'Last updated')}</dt><dd>${v.updatedAt ? e(date(v.updatedAt)) : '—'}</dd></div></dl>${missing ? `<p role="status">${e(v.reason || t('Dữ liệu chưa đủ để đặt xe.', 'Insufficient data to reserve.'))}</p>` : ''}${currentRide ? link('/student/return', t('Trả xe này', 'Return this vehicle'), 'button primary full') : currentHold ? link('/student/pickup', t('Nhận xe đã đặt', 'Pick up reserved vehicle'), 'button primary full') : taken ? notice('Xe này đã được đặt hoặc đang được thuê. Vui lòng chọn xe khác.', 'This vehicle is already reserved or rented. Please select another vehicle.') : `<form data-api="reserve" data-id="${e(v.vehicleId)}" class="reservation-form"><details open><summary>${t('Chọn thời gian thuê', 'Choose rental times')}</summary>${times()}</details><button class="primary full" ${blocked ? 'disabled' : ''}>${t('Đặt trước xe này', 'Reserve this vehicle')}</button></form>`}${link('/student/vehicles', t('Chọn phương tiện khác', 'Choose another vehicle'), 'button secondary full')}`
  );
}
function remainingTime(deadline) {
  const seconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
  if (!seconds) return t('Đã hết thời gian giữ chỗ', 'Hold time ended');
  const minutes = Math.floor(seconds / 60);
  const clock = `${String(minutes % 60).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
  return minutes >= 60 ? `${Math.floor(minutes / 60)}:${clock}` : clock;
}
function chargeCountdown(start, end, deadline, status, now = Date.now()) {
  if (status === 'SCHEDULED' && Number.isFinite(deadline)) {
    if (now < start)
      return [t('Bắt đầu sau', 'Starts in'), remainingTime(start)];
    if (now >= deadline)
      return [
        t('Hạn xác nhận', 'Check-in deadline'),
        t('Đã hết hạn', 'Expired'),
      ];
    return [
      t('Xác nhận bắt đầu sạc trong', 'Confirm charging within'),
      remainingTime(deadline),
    ];
  }
  if (now >= end)
    return [t('Khung giờ sạc', 'Charging window'), t('Đã hết', 'Ended')];
  return now < start
    ? [t('Bắt đầu sau', 'Starts in'), remainingTime(start)]
    : [t('Kết thúc sau', 'Ends in'), remainingTime(end)];
}
function serviceReceipt(receipt) {
  const start = Date.parse(receipt.scheduledStartAt);
  const end = Date.parse(receipt.scheduledEndAt);
  const deadline = Date.parse(receipt.checkInDeadline);
  const [heading, time] = chargeCountdown(start, end, deadline, receipt.status);
  const schedule =
    receipt.status === 'SCHEDULED' || receipt.status === 'CHARGING';
  const resource = receipt.portId || spaceLabel(receipt.spaceId);
  const canCancelCharge =
    receipt.chargingRequestId &&
    ['SCHEDULED', 'CANCEL_PENDING'].includes(receipt.status);
  const checkIn = receipt.status === 'SCHEDULED' && Number.isFinite(deadline);
  const windowOpen = Date.now() >= start && Date.now() < deadline;
  return (
    bookingCard(receipt) +
    (receipt.hubId && resource
      ? `<p class="hint">${e(receipt.hubId)} · ${e(resource)}</p>`
      : '') +
    (schedule && end > start
      ? `<section data-charge-start="${start}" data-charge-end="${end}" data-charge-deadline="${deadline}" data-charge-status="${e(receipt.status)}"><div class="hold-clock"><span>${heading}</span><strong role="timer">${time}</strong></div>${checkIn ? `<button class="primary full" data-confirm-charging="${e(receipt.chargingRequestId)}" ${windowOpen ? '' : 'disabled'}>${t('Xác nhận bắt đầu sạc', 'Confirm charging start')}</button>` : ''}</section>`
      : '') +
    (canCancelCharge
      ? `<button class="danger full" data-cancel-charging="${e(receipt.chargingRequestId)}">${t('Hủy đăng ký sạc', 'Cancel charging booking')}</button>`
      : '')
  );
}
function localDay(date = new Date()) {
  return [date.getFullYear(), date.getMonth() + 1, date.getDate()]
    .map((part) => String(part).padStart(2, '0'))
    .join('-');
}
function chargingForm(vehicles, hubs, choice, options, loading) {
  const ports = options?.ports || [];
  const port = ports.find((item) => item.portId === choice.portId);
  const slot = port?.slots.find(
    (item) => item.startAt === choice.startAt && item.available,
  );
  const preview = slot || port?.slots.find((item) => item.endAt);
  const minutes = preview?.estimatedDurationMinutes || 0;
  const duration = [
    minutes >= 60 ? `${Math.floor(minutes / 60)} ${t('giờ', 'hours')}` : '',
    minutes % 60 ? `${minutes % 60} ${t('phút', 'minutes')}` : '',
  ]
    .filter(Boolean)
    .join(' ');
  const time = (value) =>
    new Date(value).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });
  const rows = port?.slots
    .map(
      (option) =>
        `<label class="charging-slot"><input type="radio" name="startAt" value="${e(option.startAt)}" data-charging-field required ${!option.available ? 'disabled' : ''} ${option === slot ? 'checked' : ''}><span><strong>${e(time(option.startAt))}${option.endAt ? '–' + e(time(option.endAt)) : ''}</strong><small>${option === slot ? t('Đã chọn', 'Selected') : option.available ? t('Còn chỗ', 'Available') : t('Không khả dụng', 'Unavailable')}</small></span></label>`,
    )
    .join('');
  return `<form data-api="charging" class="auth-form charging-form">
    ${vehicleSelect(vehicles, choice.vehicleId, 'data-charging-field')}
    <label>${t('Mức pin hiện tại', 'Current battery')}<output class="charging-value">${options ? `${e(options.currentBatteryPct)}% · ${t('Đã cập nhật', 'Updated')}` : '—'}</output></label>
    <label>${t('Mức pin mong muốn (%)', 'Target battery (%)')}<input type="number" name="target" min="1" max="100" step="1" required value="${e(choice.target)}" data-charging-field></label>
    <label>${t('Thời gian sạc dự kiến', 'Estimated charging time')}<output class="charging-value">${duration || '—'}</output></label>
    <label>Mobility Hub<select name="hubId" required data-charging-field><option value="">${t('Chọn Hub', 'Select Hub')}</option>${hubs.map((hub) => `<option value="${e(hub.hubId)}" ${hub.hubId === choice.hubId ? 'selected' : ''}>${e(hub.hubName)}</option>`).join('')}</select></label>
    <label>${t('Cổng sạc', 'Charging port')}<select name="portId" required data-charging-field ${ports.length ? '' : 'disabled'}><option value="">${t('Chọn cổng sạc', 'Select a charging port')}</option>${ports.map((item) => `<option value="${e(item.portId)}" ${item === port ? 'selected' : ''}>${e(item.portName || item.portId)}</option>`).join('')}</select></label>
    <label>${t('Ngày sạc', 'Charging day')}<input type="date" name="day" min="${localDay()}" value="${e(choice.day)}" required data-charging-field></label>
    ${options && Number(choice.target) <= options.currentBatteryPct ? notice('Mức pin mong muốn phải cao hơn mức hiện tại.', 'The battery target must be higher than the current level.') : ''}
    ${loading ? notice('Đang kiểm tra lịch sạc…', 'Checking charging availability…') : rows ? `<fieldset class="charging-slots"><legend>${t('Khung giờ sạc', 'Charging windows')}</legend>${rows}</fieldset>` : ''}
    <button class="primary full" ${slot && !loading ? '' : 'disabled'}>${t('Đăng ký sạc', 'Register charging')}</button>
  </form>`;
}
function bookingCard(r) {
  const deadline = new Date(r.expiresAt).getTime();
  const heading = r.parkingReservationId
    ? t('Đặt chỗ đỗ', 'Parking reservation')
    : r.chargingRequestId
      ? t('Đăng ký sạc', 'Charging request')
      : t('Đặt xe máy điện', 'Motorbike booking');
  const states = {
    RETURNED: ['Đã trả xe', 'Vehicle returned'],
    USED: ['Đã nhận xe', 'Picked up'],
    PICKED_UP: ['Đã nhận xe', 'Picked up'],
    ACTIVE: ['Đã đặt lịch', 'Booked'],
    CANCELLED: ['Đã hủy', 'Cancelled'],
    EXPIRED: ['Đã hết hạn', 'Expired'],
    CHECKED_IN: ['Đã vào chỗ đỗ', 'Checked in'],
    COMPLETED: ['Đã hoàn tất', 'Completed'],
    PENDING: ['Chờ xác nhận', 'Pending confirmation'],
    WAITING: ['Đang chờ sạc', 'Waiting to charge'],
    SCHEDULED: ['Đã đặt lịch sạc', 'Charging scheduled'],
    CHARGING: ['Đang sạc', 'Charging'],
    CANCEL_PENDING: ['Đang chờ hủy', 'Cancellation pending'],
  };
  const status = t(
    ...(states[r.status] || ['Chưa cập nhật', 'Awaiting update']),
  );
  const start = r.scheduledStartAt || r.startsAt || r.requestedStartAt;
  const end = r.scheduledEndAt || r.endsAt;
  const appointment = Date.parse(start);
  const waiting = Date.now() < appointment;
  return `<section class="panel"><h2>${heading}</h2><p>${status} · ${e(vehicleLabel(r.vehicleId) || spaceLabel(r.spaceId) || r.portId || '')}</p>${r.expiresAt && r.status === 'ACTIVE' ? `<p>${t('Hết hạn', 'Expires')}: ${e(date(r.expiresAt))}</p>${Number.isFinite(deadline) ? `<div class="hold-clock"><span>${waiting ? t('Bắt đầu sau', 'Starts in') : t('Thời gian giữ chỗ còn lại', 'Hold time remaining')}</span><strong class="hold-countdown" role="timer" data-start="${appointment}" data-deadline="${deadline}">${remainingTime(waiting ? appointment : deadline)}</strong></div>` : ''}` : ''}${start ? `<p>${t('Thời gian sử dụng', 'Usage time')}: ${e(date(start))}${end ? ' → ' + e(date(end)) : ''}</p>` : ''}${r.pickupCode && r.status === 'ACTIVE' ? `<p>${t('Mã nhận xe', 'Pickup code')}: <strong>${e(r.pickupCode)}</strong></p>` : ''}${r.reservationId && r.status === 'ACTIVE' ? link('/student/pickup', t('Quét mã để nhận xe', 'Scan to pick up'), 'button primary full') : ''}${r.parkingReservationId && r.status === 'ACTIVE' ? `<button class="danger full" data-cancel-parking="${e(r.parkingReservationId)}">${t('Hủy đặt chỗ đỗ', 'Cancel parking booking')}</button>` : ''}${r.reservationId && r.status === 'ACTIVE' ? `<button class="danger full" data-cancel-api="${e(r.reservationId)}">${t('Hủy đặt xe', 'Cancel vehicle booking')}</button>` : ''}</section>`;
}
function commandPanel(c) {
  if (!c) return '';
  return `<section class="panel" role="status"><h2>${c.state === 'SUCCEEDED' ? (c.kind === 'RETURN' ? t('Đã trả xe thành công', 'Vehicle returned') : t('Đã nhận xe thành công', 'Vehicle picked up')) : c.state === 'FAILED' ? t('Thao tác thất bại', 'Action failed') : t('Đang xử lý', 'Processing')}</h2>${c.state === 'PENDING' ? notice('Đang chờ thiết bị xác nhận. Chưa hoàn tất giao dịch.', 'Waiting for device confirmation. Not completed yet.') + '<button data-api-action="poll">' + t('Kiểm tra trạng thái', 'Check status') + '</button>' : ''}${c.state === 'UNKNOWN' ? notice('Chưa xác định kết quả. Thử lại cùng yêu cầu để đối soát, không tạo lệnh mới.', 'Outcome unknown. Retry the same request to reconcile; do not create another command.') + '<button data-api-action="retry">' + t('Đối soát yêu cầu', 'Reconcile request') + '</button>' : ''}${c.state === 'SUCCEEDED' && c.kind === 'PICKUP' ? link('/student/return', t('Trả phương tiện', 'Return vehicle'), 'button primary full') : ''}</section>`;
}
function activePickupHold() {
  return api.reservations.find(
    (r) =>
      r.status === 'ACTIVE' &&
      (!r.expiresAt || Date.parse(r.expiresAt) > Date.now()),
  );
}
function pickupForm(command) {
  if (api.ride?.status === 'ACTIVE') {
    const vehicle = `${t('Phương tiện', 'Vehicle')} ${api.ride.vehicleId}`;
    return (
      mobileHeading(t('Nhận phương tiện', 'Pick up vehicle')) +
      (command?.state === 'SUCCEEDED'
        ? `<div role="status">${successCard(t('Đã nhận xe thành công', 'Vehicle picked up'), vehicle)}</div>`
        : `<section class="panel"><h2>${t('Xe đang sử dụng', 'Current vehicle')}</h2><p>${e(vehicle)}</p></section>`) +
      link(
        '/student/return',
        t('Trả phương tiện', 'Return vehicle'),
        'button primary full',
      )
    );
  }

  const hold = activePickupHold();
  return (
    mobileHeading(t('Quét mã QR thuê xe', 'Scan QR to rent')) +
    `<section class="pickup-screen"><h2>${t('Nhận xe bằng mã QR', 'Pick up with QR code')}</h2><p class="hint">${t('Đưa mã QR vào khung để đối chiếu với lượt đặt xe của bạn.', 'Place the QR code in the frame to match your booking.')}</p><div class="pickup-viewport"><video id="camera" playsinline muted hidden></video><div class="pickup-reticle" aria-hidden="true"><i></i><i></i><i></i><i></i>${icon('qr')}</div></div><p class="hint">${t('Đảm bảo mã QR trên xe rõ ràng và đủ sáng để quét chính xác.', 'Keep the vehicle QR code clear and well lit for an accurate scan.')}</p><button class="primary full" data-api-action="camera">${t('Quét mã xe', 'Scan vehicle code')}</button><button class="secondary full" data-api-action="pickup-damage">${t('Báo xe gặp sự cố', 'Vehicle has a problem')}</button><div id="pickup-damage-help" class="panel" role="status" hidden><p>${t('Không nhận xe nếu phát hiện hư hỏng. Hãy liên hệ nhân viên tại Hub và cung cấp mã xe. Bạn có thể hủy lượt đặt trong mục Lượt đặt.', 'Do not pick up a damaged vehicle. Contact Hub staff with the vehicle ID. You can cancel your reservation in Bookings.')}</p>${link('/student/reservations', t('Xem lượt đặt', 'View bookings'), 'button secondary full')}</div><details class="pickup-alternatives"><summary>${t('Nhập mã hoặc chọn ảnh QR', 'Enter a code or upload QR')}</summary><button type="button" class="secondary full" data-api-action="stop-camera">${t('Dừng camera', 'Stop camera')}</button><label>${t('Chọn ảnh QR', 'Upload QR image')}<input id="api-qr-file" type="file" accept="image/*"></label><form data-api="pickup" class="auth-form">${field('vehicleId', t('Mã phương tiện', 'Vehicle ID'), hold?.vehicleId || selected?.vehicleId || '', 'text', hold ? 'readonly' : '')}${hold ? field('pickupCode', t('Mã nhận xe', 'Pickup code'), '', 'text', 'autocomplete="off"') : ''}<button class="primary full">${t('Xác nhận nhận xe', 'Confirm pickup')}</button></form></details></section>${commandPanel(command)}`
  );
}
export function mountMobileApi(root, path) {
  let locationLoaded = api.hubs.length > 0;
  const [route, query] = path.split('?');
  path = route;
  let selectedHubId = new URLSearchParams(query).get('hub');
  const previousKey = ['PENDING', 'UNKNOWN'].includes(api.command?.state)
    ? null
    : api.command?.key;
  const currentCommand = (kind) =>
    api.command?.kind === kind && api.command.key !== previousKey
      ? api.command
      : null;
  let showHubVehicles = false;
  let hubParkingSpaces = null;
  let parkingHubs = null;
  let chargingHubs = null;
  let chargingOptions = null;
  let chargingRequest;
  let loadingChargingOptions = false;
  const chargingChoice = {
    vehicleId: '',
    hubId: '',
    portId: '',
    startAt: '',
    day: localDay(),
    target: '80',
  };
  const vehicleFilters = { hub: '', battery: 0, sort: 'distance' };
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
  async function refreshChargingOptions() {
    chargingRequest?.abort();
    chargingOptions = null;
    loadingChargingOptions = false;
    const { vehicleId, hubId, target, day } = chargingChoice;
    const start = new Date(`${day}T00:00:00`);
    if (
      !vehicleId ||
      !hubId ||
      !(Number(target) > 0 && Number(target) <= 100) ||
      !Number.isFinite(+start)
    ) {
      render();
      return;
    }
    const request = new AbortController();
    chargingRequest = request;
    loadingChargingOptions = true;
    render();
    try {
      const data = await api.chargingOptions(
        vehicleId,
        hubId,
        target,
        start.toISOString(),
        request.signal,
      );
      if (disposed || request.signal.aborted) return;
      chargingOptions = data;
      const selectedPort = data.ports.find(
        (port) => port.portId === chargingChoice.portId,
      );
      if (!selectedPort) chargingChoice.portId = '';
      if (
        !selectedPort?.slots.some(
          (slot) => slot.startAt === chargingChoice.startAt && slot.available,
        )
      )
        chargingChoice.startAt = '';
    } catch (error) {
      if (disposed || request.signal.aborted) return;
      loadingChargingOptions = false;
      render();
      message(errorText(error));
      root
        .querySelector('main')
        .insertAdjacentHTML(
          'beforeend',
          `<button class="secondary full" data-api-action="reload-charge-options">${t('Thử tải lại khung giờ', 'Retry loading charging windows')}</button>`,
        );
      return;
    }
    loadingChargingOptions = false;
    render();
  }
  function errorText(error) {
    if (error.code === 'CHARGING_CHECK_IN_NOT_OPEN')
      return t(
        'Chưa đến giờ bắt đầu sạc.',
        'The charging window has not started.',
      );
    if (error.code === 'CHARGING_CHECK_IN_EXPIRED')
      return t(
        'Đã hết thời gian xác nhận bắt đầu sạc.',
        'The charging check-in deadline has passed.',
      );
    if (error.code === 'ACTIVE_PARKING_HOLD_EXISTS')
      return t(
        'Bạn đã có một chỗ đỗ đang giữ. Hãy kiểm tra mục Lượt đặt.',
        'You already have an active parking hold. Check Bookings.',
      );
    if (
      error.code === 'ACTIVE_RIDE_EXISTS' ||
      /student already has an active reservation or ride/i.test(error.message)
    )
      return t(
        'Bạn đang giữ một xe hoặc có chuyến đi chưa kết thúc. Hãy mở Lượt đặt để tiếp tục.',
        'You already have a reservation or an active ride. Open Bookings to continue.',
      );
    if (error.code === 'VEHICLE_ALREADY_RESERVED')
      return t(
        'Xe này đã được đặt hoặc đang được thuê. Vui lòng chọn xe khác.',
        'This vehicle is already reserved or rented. Please select another vehicle.',
      );
    if (error.code === 'VEHICLE_NOT_RENTABLE')
      return t(
        'Xe hiện không khả dụng. Vui lòng chọn xe khác.',
        'Vehicle unavailable. Please select another vehicle.',
      );

    if (error.code === 'INVALID_PLATE_FORMAT')
      return t(
        'Nhập biển số theo mẫu 72-C2 12345 hoặc 72-C2 1234.',
        'Enter the plate as 72-C2 12345 or 72-C2 1234.',
      );
    if (error.code === 'VEHICLE_CHANGE_COOLDOWN')
      return vehicleWait(error.message);
    if (error.code === 'PRIVATE_VEHICLE_EXISTS')
      return t(
        'Hãy hủy đăng ký xe hiện tại trong Thông tin chung trước khi đăng ký xe mới.',
        'Cancel your current vehicle registration in General information first.',
      );
    if (error.code === 'PRIVATE_VEHICLE_IN_USE')
      return t(
        'Xe đang đỗ hoặc có giao dịch đang hoạt động. Hãy kết thúc giao dịch trước khi hủy đăng ký.',
        'The vehicle is parked or has an active booking. Finish it before cancelling registration.',
      );
    if (error.code === 'PLATE_REGISTERED')
      return t(
        'Biển số này đã được đăng ký.',
        'This license plate is already registered.',
      );
    if (error.code === 'OWNER_MISMATCH')
      return t(
        'Thông tin chủ xe chưa khớp với tài khoản sinh viên.',
        'Owner details do not match your student profile.',
      );
    if (error.code === 'INVALID_PICKUP_CODE')
      return t(
        'Sai mã nhận xe. Hãy kiểm tra mã của lượt đặt này.',
        'Incorrect pickup code. Check your booking and try again.',
      );
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
      if (mode === 'general' && api.user?.role === 'STUDENT') {
        const heading = `<h2>${t('Xe cá nhân', 'My vehicles')}</h2>`;
        draw(content + heading + notice('Đang tải xe…', 'Loading vehicles…'));
        try {
          await api.loadJourney(controller.signal);
          if (disposed) return;
          const vehicles = api.studentVehicles.filter(
            (v) => v.ownership === 'PRIVATE',
          );
          content += `<section class="panel">${heading}${
            vehicles.length
              ? vehicles
                  .map(
                    (v) =>
                      `<p>${e(vehicleLabel(v.vehicleId))}</p><button type="button" class="danger full" data-cancel-private="${e(v.vehicleId)}">${t('Hủy đăng ký xe', 'Cancel vehicle registration')}</button>`,
                  )
                  .join('')
              : notice(
                  'Bạn chưa đăng ký xe cá nhân.',
                  'You have no registered personal vehicles.',
                ) +
                link(
                  '/student/register-vehicle',
                  t('Đăng ký xe', 'Register vehicle'),
                  'button secondary full',
                )
          }</section>`;
        } catch (error) {
          if (disposed) return;
          if (error.status === 401) return go('/account/student');
          draw(
            content +
              heading +
              `<button class="secondary full" data-api-action="reload-journey">${t('Thử tải lại danh sách xe', 'Retry loading vehicles')}</button>`,
          );
          message(errorText(error));
          return;
        }
      }
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
    if (
      api.user?.accountId &&
      !api.journeyLoaded &&
      [
        '/student/hubs',
        '/student/reservations',
        '/student/pickup',
        '/student/return',
        '/student/parking',
        '/student/search',
        '/student/charging',
      ].includes(path)
    ) {
      draw(
        notice(
          'Đang tải lượt đặt và chuyến đi…',
          'Loading bookings and current trip…',
        ),
      );
      try {
        await api.loadJourney(controller.signal);
      } catch (error) {
        if (disposed || error.name === 'AbortError') return;
        if (error.status === 401) return go('/account/student');
        draw(
          `<button class="secondary full" data-api-action="reload-journey">${t('Thử tải lại lượt đặt và chuyến đi', 'Retry loading bookings and trip')}</button>`,
        );
        message(errorText(error));
        return;
      }
      if (disposed) return;
    }
    if (path === '/student/register-vehicle') {
      const title = t('Đăng ký xe', 'Register vehicle');
      draw(
        mobileHeading(title, '/student/hubs') +
          `<form class="auth-form panel" data-api="register-vehicle">
        ${field('plate', t('Biển số xe', 'License plate'), '', 'text', 'pattern="[0-9]{2}-[A-Z][A-Z0-9] [0-9]{4,5}" placeholder="72-C2 12345" title="72-C2 12345 / 72-C2 1234" autocapitalize="characters"')}
        ${field('studentId', t('MSSV', 'Student ID'), api.user.studentId || '')}
        ${field('ownerName', t('Tên chủ xe', 'Owner name'), api.user.fullName || '')}
        <button class="primary full">${title}</button></form>`,
      );
      return;
    }
    if (path === '/student/hubs') {
      const journeyLink =
        api.ride?.status === 'ACTIVE'
          ? link(
              '/student/return',
              t('Xe đang sử dụng · Trả xe', 'Current vehicle · Return'),
              'button primary full',
            )
          : activePickupHold()
            ? link(
                '/student/reservations',
                t('Xem lượt đặt đang giữ', 'View active booking'),
                'button primary full',
              )
            : '';
      const home = (pins) =>
        draw(
          '<header class="home-heading">Mobility Hub</header>' +
            journeyLink +
            hubsPage(
              { vehicles: [] },
              { bookingPath: '/student/vehicles', mapPins: pins },
            ),
        );
      home('');
      try {
        const hubs = await api.hubCatalog(controller.signal);
        if (!disposed)
          home(
            hubs
              .map(
                (h, i) =>
                  `<a href="#/student/search?hub=${encodeURIComponent(h.hubId)}" class="map-label pin-${['one', 'two', 'three'][i % 3]}">${icon('pin')}${e(h.hubName.replace(/^Hub /i, ''))}</a>`,
              )
              .join(''),
          );
      } catch (error) {
        if (!disposed) message(errorText(error));
      }
      return;
    }
    if (path === '/student/search') {
      draw(
        `<div class="hub-browser">${searchForm(locationLoaded, selectedHubId, showHubVehicles, hubParkingSpaces, currentCommand('RETURN'))}</div>`,
      );
      return;
    }
    if (path === '/student/vehicles') {
      draw(vehicleList(vehicleFilters));
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
                t('Đã đặt lịch nhận xe', 'Vehicle pickup booked'),
                t('Dữ liệu xác nhận từ backend.', 'Confirmed by the backend.'),
              ) + bookingCard(receipt)
            : notice('Chưa có xác nhận đặt xe.', 'No reservation receipt.')),
      );
      return;
    }
    if (path === '/student/reservations') {
      const bookings = receipts();
      const ride = api.ride?.status === 'ACTIVE' ? api.ride : null;
      draw(
        mobileHeading(t('Lượt đặt', 'Bookings')) +
          (ride
            ? `<section class="panel"><h2>${e(vehicleLabel(ride.vehicleId))}</h2>${link('/student/return', t('Xe đang sử dụng · Trả xe', 'Current vehicle · Return'), 'button primary full')}</section>`
            : '') +
          (bookings.length || ride
            ? bookings.map(serviceReceipt).join('')
            : notice('Bạn chưa có lượt đặt.', 'You have no bookings yet.')),
      );
      return;
    }
    if (
      path === '/student/parking-confirmed' ||
      path === '/student/charging-confirmed'
    ) {
      const charging = path === '/student/charging-confirmed';
      const receipt = (charging ? api.charging : api.parking)[0];
      const confirmed =
        receipt &&
        (charging
          ? ['SCHEDULED', 'CHARGING', 'COMPLETED'].includes(receipt.status)
          : receipt.status === 'ACTIVE' &&
            Date.parse(receipt.expiresAt) > Date.now());
      draw(
        mobileHeading(
          charging
            ? t('Xác nhận đăng ký sạc', 'Charging confirmation')
            : t('Xác nhận đặt chỗ đỗ', 'Parking confirmation'),
        ) +
          (confirmed
            ? successCard(
                charging
                  ? t('Đã đăng ký sạc thành công', 'Charging scheduled')
                  : t('Đã đặt chỗ đỗ thành công', 'Parking booked'),
                charging
                  ? t(
                      'Hệ thống đã xác nhận lịch sạc.',
                      'The system confirmed the charging schedule.',
                    )
                  : t(
                      'Hệ thống đã ghi nhận giờ hẹn cho ô đỗ của bạn.',
                      'Your parking appointment has been recorded.',
                    ),
              ) + serviceReceipt(receipt)
            : receipt
              ? serviceReceipt(receipt)
              : notice('Chưa có biên nhận.', 'No receipt available.')) +
          link(
            '/student/reservations',
            t('Xem lượt đặt', 'View bookings'),
            'button primary full',
          ),
      );
      return;
    }
    if (path === '/student/pickup') {
      draw(pickupForm(currentCommand('PICKUP')));
      return;
    }
    if (path === '/student/parking' || path === '/student/return') {
      const returning = path === '/student/return';
      const ride = api.ride?.status === 'ACTIVE' ? api.ride : null;
      if (
        !returning &&
        !api.studentVehicles.some((vehicle) => vehicle.canBookParking)
      ) {
        draw(
          mobileHeading(t('Đặt chỗ đỗ', 'Book parking')) +
            notice(
              'Bạn cần có xe đủ điều kiện chưa có ô đỗ để đặt chỗ.',
              'An eligible vehicle without a parking space is required.',
            ),
        );
        return;
      }
      if (
        (!returning || ride) &&
        parkingHubs === null &&
        !(
          returning &&
          currentCommand('RETURN') &&
          api.command.state === 'SUCCEEDED'
        )
      ) {
        draw(
          mobileHeading(
            returning
              ? t('Trả phương tiện', 'Return vehicle')
              : t('Đặt chỗ đỗ', 'Book parking'),
          ) +
            (ride && returning
              ? `<section class="panel"><h2>${icon('bike')} ${e(vehicleLabel(ride.vehicleId))}</h2><p>${t('Xe đang sử dụng', 'Your current vehicle')}</p></section>`
              : '') +
            notice('Đang tải danh sách Hub…', 'Loading Hubs…'),
        );
        try {
          parkingHubs = await api.hubCatalog(controller.signal);
          if (disposed) return;
          render();
        } catch (error) {
          if (!disposed && error.name !== 'AbortError') {
            message(errorText(error));
            root
              .querySelector('main')
              .insertAdjacentHTML(
                'beforeend',
                `<button class="secondary full" data-api-action="reload-parking">${t('Thử lại', 'Retry')}</button>`,
              );
          }
        }
        return;
      }

      if (
        returning &&
        currentCommand('RETURN') &&
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
            commandPanel(currentCommand('RETURN')),
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
            ? `<section class="panel"><h2>${icon('bike')} ${e(vehicleLabel(ride.vehicleId))}</h2><p>${t('Xe đang sử dụng', 'Your current vehicle')}</p></section>`
            : '') +
          (parkingHubs.length
            ? `<div class="hub-list">${parkingHubs.map((h) => `<button class="hub-row" data-parking-hub="${e(h.hubId)}">${icon('parking')}<span><strong>${e(h.hubName)}</strong></span></button>`).join('')}</div>`
            : notice('Hệ thống chưa có Hub.', 'No Hubs are registered yet.')) +
          (hubParkingSpaces && selectedHubId
            ? parkingAtHub(
                parkingHubs.find((h) => h.hubId === selectedHubId),
                hubParkingSpaces,
                returning,
              )
            : '') +
          (returning ? commandPanel(currentCommand('RETURN')) : ''),
      );
      return;
    }
    if (path === '/student/charging') {
      if (!api.studentVehicles.length) {
        draw(
          mobileHeading(t('Đăng ký sạc', 'Register charging')) +
            notice(
              'Bạn cần có xe cá nhân hoặc xe thuê đang sử dụng để đăng ký sạc.',
              'A personal vehicle or an active rental is required to book charging.',
            ),
        );
        return;
      }
      if (chargingHubs === null) {
        draw(
          mobileHeading(t('Đăng ký sạc', 'Register charging')) +
            notice('Đang tải danh sách Hub…', 'Loading Hubs…'),
        );
        try {
          chargingHubs = await api.hubCatalog(controller.signal);
          if (!disposed) {
            if (api.studentVehicles.length === 1)
              chargingChoice.vehicleId = api.studentVehicles[0].vehicleId;
            if (chargingHubs.length === 1)
              chargingChoice.hubId = chargingHubs[0].hubId;
            await refreshChargingOptions();
          }
        } catch (error) {
          if (!disposed && error.name !== 'AbortError') {
            message(errorText(error));
            root
              .querySelector('main')
              .insertAdjacentHTML(
                'beforeend',
                `<button class="secondary full" data-api-action="reload-charging">${t('Thử lại', 'Retry')}</button>`,
              );
          }
        }
        return;
      }
      draw(
        mobileHeading(t('Đăng ký sạc', 'Register charging')) +
          chargingForm(
            api.studentVehicles,
            chargingHubs,
            chargingChoice,
            chargingOptions,
            loadingChargingOptions,
          ),
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
      if (kind === 'register-vehicle') {
        await api.call('/student/vehicles', Object.fromEntries(f), {
          signal: controller.signal,
        });
        api.journeyLoaded = false;
        if (!disposed)
          draw(
            mobileHeading(
              t('Đăng ký xe', 'Register vehicle'),
              '/student/hubs',
            ) +
              successCard(
                t('Đã đăng ký xe', 'Vehicle registered'),
                t(
                  'Xe đã được gắn với tài khoản của bạn.',
                  'Your vehicle is now linked to your account.',
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
          const id = form.dataset.id;
          try {
            await api.reserve(id, f.get('start'), f.get('end'));
          } catch (error) {
            if ([409, 422].includes(error.status)) {
              const latest = await api.vehicle(id, controller.signal);
              if (disposed) return;
              if (latest.status !== 'AVAILABLE') {
                selected = latest;
                draw(detailsPage(latest));
                api.removeListedVehicle(id);
                if (['RESERVED', 'IN_USE'].includes(latest.status)) return;
              }
            }
            throw error;
          }
          if (!disposed) go('/student/reservation-confirmed');
        }
        if (kind === 'parking') {
          await api.reserveParking(
            f.get('vehicleId'),
            f.get('hubId'),
            f.get('spaceId'),
            f.get('start'),
            f.get('end'),
          );
          if (!disposed) go('/student/parking-confirmed');
        }
        if (kind === 'charging') {
          try {
            await api.charge(
              f.get('vehicleId'),
              f.get('hubId'),
              f.get('target'),
              f.get('startAt'),
              f.get('portId'),
            );
          } catch (error) {
            if (!disposed && [409, 422].includes(error.status))
              await refreshChargingOptions();
            throw error;
          }
          if (!disposed) go('/student/charging-confirmed');
        }
        if (kind === 'pickup') {
          if (f.has('pickupCode') && !activePickupHold())
            throw new Error(
              t('Lượt giữ xe đã hết hạn.', 'This reservation has expired.'),
            );
          const hold = activePickupHold();
          const code = String(f.get('pickupCode') || '').trim();
          if (hold && hold.pickupCode && String(hold.pickupCode) !== code) {
            throw new Error(
              t(
                'Sai mã nhận xe. Hãy kiểm tra mã của lượt đặt này.',
                'Incorrect pickup code. Check your booking and try again.',
              ),
            );
          }
          await api.pickup(
            hold?.vehicleId || f.get('vehicleId'),
            hold?.reservationId,
            code,
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
    const hold = activePickupHold();
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
    const codeField = root.querySelector('[name="pickupCode"]');
    if (hold?.pickupCode && codeField) codeField.value = hold.pickupCode;
    const details = root.querySelector('.pickup-alternatives');
    if (details) details.open = true;
    el?.focus();
  }
  function click(event) {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.hasAttribute('data-battery-filter')) {
      vehicleFilters.battery = Number(button.dataset.batteryFilter);
      render();
      return;
    }
    if (button.dataset.apiAction === 'pickup-damage') {
      stopScanner();
      root.querySelector('#pickup-damage-help').hidden = false;
      return;
    }

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
    if (button.dataset.cancelPrivate) {
      run(async () => {
        const vehicle = api.studentVehicles.find(
          (v) => v.vehicleId === button.dataset.cancelPrivate,
        );
        if (!vehicle) return;
        if (Date.parse(vehicle.changeAllowedAt) > Date.now()) {
          window.alert(vehicleWait(vehicle.changeAllowedAt));
          return;
        }
        const plate = vehicleLabel(vehicle.vehicleId);
        if (
          !(await confirmAction(
            t(
              `Bạn có muốn hủy đăng ký xe ${plate} không?`,
              `Cancel registration for ${plate}?`,
            ),
          )) ||
          disposed
        )
          return;
        try {
          await api.call(
            '/student/vehicles/' +
              encodeURIComponent(vehicle.vehicleId) +
              '/cancel',
            {},
            { signal: controller.signal },
          );
        } catch (error) {
          if (error.code !== 'VEHICLE_CHANGE_COOLDOWN') throw error;
          if (!disposed) window.alert(vehicleWait(error.message));
        }
        if (!disposed) await render();
      });
      return;
    }
    const action = button.dataset.apiAction;
    if (action === 'reload-charge-options') {
      refreshChargingOptions();
      return;
    }
    if (
      ['reload-charging', 'reload-parking', 'reload-journey'].includes(action)
    ) {
      render();
      return;
    }
    if (button.dataset.action === 'logout')
      run(async () => {
        try {
          await api.logout();
        } finally {
          if (!disposed) go('/account/student');
        }
      });
    const cancelChargingId = button.dataset.cancelCharging;
    const confirmChargingId = button.dataset.confirmCharging;
    if (confirmChargingId) {
      run(async () => {
        await api.confirmCharging(confirmChargingId);
        if (!disposed) render();
      });
      return;
    }
    if (
      button.dataset.cancelApi ||
      button.dataset.cancelParking ||
      cancelChargingId
    )
      run(async () => {
        if (
          (await confirmAction(
            cancelChargingId
              ? t('Hủy đăng ký sạc này?', 'Cancel this charging booking?')
              : t('Hủy lượt đặt này?', 'Cancel this reservation?'),
          )) &&
          !disposed
        ) {
          if (cancelChargingId) await api.cancelCharging(cancelChargingId);
          else if (button.dataset.cancelParking)
            await api.cancelParking(button.dataset.cancelParking);
          else await api.cancel(button.dataset.cancelApi);
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
    if (action === 'gps' || action === 'nearby-vehicles')
      run(async () => {
        const findingVehicles = action === 'nearby-vehicles';
        availabilityRequest?.abort();
        if (!findingVehicles) {
          locationLoaded = false;
          showHubVehicles = false;
          api.hubs = [];
          render();
          root.querySelector('[data-api-action="gps"]').disabled = true;
        }
        if (!findingVehicles && !api.user) {
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
                        'Bạn cần cho phép truy cập vị trí. Mở quyền của trang cạnh thanh địa chỉ, bật Vị trí rồi thử lại.',
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
        if (findingVehicles) {
          message(t('Đang tìm xe gần bạn…', 'Finding nearby vehicles…'), false);
          const { latitude, longitude } = position.coords;
          await api.search(
            { latitude, longitude, radiusKm: 50 },
            controller.signal,
          );
          if (disposed) return;
          api.discoveryOrigin = { latitude, longitude };
          Object.assign(vehicleFilters, {
            hub: '',
            battery: 0,
            sort: 'distance',
          });
          render();
          if (!inventory().length)
            window.alert(
              t(
                'Không có xe nào khả dụng trong bán kính 50 km quanh bạn.',
                'No vehicles are available within 50 km of your location.',
              ),
            );
          return;
        }
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
    if (el.hasAttribute('data-charging-field')) {
      chargingChoice[el.name] = el.value;
      if (el.name === 'startAt' || el.name === 'portId') {
        if (el.name === 'portId') chargingChoice.startAt = '';
        render();
      } else {
        chargingChoice.startAt = '';
        refreshChargingOptions();
      }
      return;
    }
    if (el.dataset.vehicleFilter) {
      vehicleFilters[el.dataset.vehicleFilter] = el.value;
      render();
      return;
    }
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
  let chargingRefreshPending = false;
  async function refreshChargingReceipts(reportError = false) {
    if (disposed || busy || chargingRefreshPending || !api.user?.accountId)
      return;
    chargingRefreshPending = true;
    try {
      const changed = await api.loadChargingReceipts(controller.signal);
      if (!disposed && changed) render();
    } catch (error) {
      if (!disposed && reportError && error.name !== 'AbortError')
        message(errorText(error));
    } finally {
      chargingRefreshPending = false;
    }
  }
  const chargingReceiptView = [
    '/student/reservations',
    '/student/charging-confirmed',
  ].includes(path);
  const chargingRefresh = chargingReceiptView
    ? setInterval(refreshChargingReceipts, 5000)
    : null;
  if (chargingReceiptView) refreshChargingReceipts(true);
  let refreshing = false;
  let availabilityRequest;
  async function refreshAvailableVehicles() {
    if (
      disposed ||
      busy ||
      refreshing ||
      !api.discoveryOrigin ||
      document.hidden
    )
      return;
    refreshing = true;
    try {
      const before = JSON.stringify(api.hubs);
      availabilityRequest = new AbortController();
      if (path === '/student/vehicles')
        await api.search(
          { ...api.discoveryOrigin, radiusKm: 50 },
          availabilityRequest.signal,
        );
      else
        await api.discoverHubs(api.discoveryOrigin, availabilityRequest.signal);
      if (!disposed && !busy && before !== JSON.stringify(api.hubs)) render();
    } catch (error) {
      if (!disposed && error.name !== 'AbortError') message(errorText(error));
    } finally {
      refreshing = false;
    }
  }
  async function refreshDetailStatus() {
    if (busy || refreshing || disposed || document.hidden || !selected) return;
    const id = decodeURIComponent(path.slice('/student/vehicles/'.length));
    if (selected.vehicleId !== id) return;
    refreshing = true;
    try {
      const latest = await api.vehicle(id, controller.signal);
      if (
        disposed ||
        busy ||
        (latest.status === selected.status &&
          latest.batteryLevel === selected.batteryLevel)
      )
        return;
      selected = latest;
      draw(detailsPage(latest));
      if (latest.status !== 'AVAILABLE') api.removeListedVehicle(id);
    } catch (error) {
      if (!disposed && error.name !== 'AbortError') message(errorText(error));
    } finally {
      refreshing = false;
    }
  }
  const listPage = ['/student/vehicles', '/student/search'].includes(path);
  const availabilityTimer = listPage
    ? setInterval(refreshAvailableVehicles, 5000)
    : path.startsWith('/student/vehicles/')
      ? setInterval(refreshDetailStatus, 5000)
      : null;
  if (listPage) refreshAvailableVehicles();
  const countdown = [
    '/student/reservations',
    '/student/reservation-confirmed',
    '/student/parking-confirmed',
    '/student/charging-confirmed',
  ].includes(path)
    ? setInterval(() => {
        for (const el of root.querySelectorAll('[data-deadline]')) {
          const start = Number(el.dataset.start);
          const waiting = Date.now() < start;
          el.previousElementSibling.textContent = waiting
            ? t('Bắt đầu sau', 'Starts in')
            : t('Thời gian giữ chỗ còn lại', 'Hold time remaining');
          el.textContent = remainingTime(
            waiting ? start : Number(el.dataset.deadline),
          );
        }
        for (const el of root.querySelectorAll('[data-charge-start]')) {
          const [label, remaining] = chargeCountdown(
            Number(el.dataset.chargeStart),
            Number(el.dataset.chargeEnd),
            Number(el.dataset.chargeDeadline),
            el.dataset.chargeStatus,
          );
          el.querySelector('span').textContent = label;
          el.querySelector('strong').textContent = remaining;
          const confirm = el.querySelector('[data-confirm-charging]');
          if (confirm)
            confirm.disabled =
              busy ||
              Date.now() < Number(el.dataset.chargeStart) ||
              Date.now() >= Number(el.dataset.chargeDeadline);
        }
      }, 1000)
    : null;
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
    chargingRequest?.abort();
    availabilityRequest?.abort();
    controller.abort();
    clearInterval(timer);
    clearInterval(countdown);
    clearInterval(chargingRefresh);
    clearInterval(availabilityTimer);
    stopScanner();
    root.removeEventListener('submit', submit);
    root.removeEventListener('click', click);
    root.removeEventListener('change', change);
  };
}
