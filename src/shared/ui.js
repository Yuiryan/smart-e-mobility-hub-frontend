import { messages } from './messages.js';
export const e = (value = '') =>
  String(value).replace(
    /[&<>"']/g,
    (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[
        c
      ],
  );
let prefs = { locale: 'vi', theme: 'light' };
try {
  const saved = JSON.parse(localStorage.getItem('emh-preferences'));
  if (['vi', 'en'].includes(saved?.locale)) prefs.locale = saved.locale;
  if (['light', 'dark'].includes(saved?.theme)) prefs.theme = saved.theme;
} catch {
  /* Storage can be disabled. */
}
export const t = (vi, en) => (prefs.locale === 'vi' ? vi : en);
export const locale = () => prefs.locale;
export function preference(key, value) {
  if (
    (key === 'locale' && ['vi', 'en'].includes(value)) ||
    (key === 'theme' && ['light', 'dark'].includes(value))
  ) {
    prefs[key] = value;
    try {
      localStorage.setItem('emh-preferences', JSON.stringify(prefs));
    } catch {
      /* Session-only preference. */
    }
  }
  applyPreferences();
}
export function applyPreferences() {
  document.documentElement.lang = prefs.locale;
  document.documentElement.dataset.theme = prefs.theme;
}
export const msg = (code) => t(...(messages[code] || messages.UNKNOWN));
export const date = (value) =>
  (
    typeof value === 'string'
      ? Number.isFinite(Date.parse(value))
      : Number.isFinite(value)
  )
    ? new Date(value).toLocaleString(prefs.locale === 'vi' ? 'vi-VN' : 'en-GB')
    : '—';
export const localDate = (value) => {
  const d = new Date(value);
  return new Date(value - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
};
export const title = (name, desc = '') =>
  `<div class="page-title"><p class="eyebrow">SMART E-MOBILITY HUB</p><h1>${e(name)}</h1><p>${e(desc)}</p></div>`;
export const link = (path, label, cls = 'button') =>
  `<a class="${cls}" href="#${path}">${e(label)}</a>`;
export const badge = (code) =>
  `<span class="badge status-${e(code.toLowerCase())}">${e(msg(code))}</span>`;
export const empty = (text) =>
  `<div class="empty"><p>${e(text)}</p>${link('/student/hubs', t('Tìm xe', 'Find a vehicle'))}</div>`;
export const input = (name, label, type = 'text', value = '', extra = '') =>
  `<label>${e(label)}<input name="${name}" type="${type}" value="${e(value)}" ${extra}></label>`;
function timeField(name, label, timestamp) {
  const value = localDate(Math.ceil(timestamp / 300000) * 300000);
  const options = (count, increment, selected) =>
    Array.from({ length: count }, (_, i) => {
      const n = String(i * increment).padStart(2, '0');
      return `<option ${selected === n ? 'selected' : ''}>${n}</option>`;
    }).join('');
  return `<fieldset class="booking-time"><legend>${label}</legend><input type="hidden" name="${name}" value="${value}"><input type="date" aria-label="${label} · ${t('Ngày', 'Date')}" required value="${value.slice(0, 10)}"><div><select aria-label="${label} · ${t('Giờ', 'Hour')}">${options(24, 1, value.slice(11, 13))}</select><span>:</span><select aria-label="${label} · ${t('Phút', 'Minute')}">${options(12, 5, value.slice(14, 16))}</select></div></fieldset>`;
}
if (typeof document !== 'undefined')
  document.addEventListener(
    'change',
    (event) => {
      const field = event.target.closest?.('.booking-time');
      if (!field) return;
      const day = field.querySelector('[type=date]').value;
      const [hour, minute] = field.querySelectorAll('select');
      field.querySelector('[type=hidden]').value = day
        ? `${day}T${hour.value}:${minute.value}`
        : '';
    },
    true,
  );
export const times = (parkingOnly = false) => {
  const now = Date.now();
  return `<div class="form-grid">${timeField('start', parkingOnly ? t('Giờ đến đỗ', 'Parking arrival') : t('Bắt đầu', 'Start'), now)}${parkingOnly ? '' : timeField('end', t('Kết thúc', 'End'), now + 7200000)}</div>`;
};
export const windowFrom = (form) => ({
  start: new Date(form.get('start')).getTime(),
  end: new Date(form.get('end')).getTime(),
});
export const hubsOptions = (data) =>
  data.hubs
    .map((h) => `<option value="${e(h.id)}">${e(h.name)}</option>`)
    .join('');
export const outcome = () =>
  `<details class="demo-tools"><summary>${t('Kịch bản thiết bị mô phỏng', 'Simulated device outcome')}</summary><label>${t('Kết quả ACK', 'ACK result')}<select name="outcome"><option value="SUCCESS">${t('Thành công', 'Success')}</option><option value="FAILED">${t('Thất bại', 'Failure')}</option><option value="TIMEOUT">${t('Quá hạn / cần đối soát', 'Timeout / reconciliation')}</option></select></label></details>`;
export function confirmAction(text) {
  return new Promise((resolve) => {
    const d = document.createElement('dialog');
    d.innerHTML = `<h2>${t('Xác nhận', 'Confirm')}</h2><p>${e(text)}</p><div class="row-actions"><button data-no>${t('Quay lại', 'Back')}</button><button class="primary" data-yes>${t('Xác nhận', 'Confirm')}</button></div>`;
    document.body.append(d);
    const close = (value) => {
      d.close();
      d.remove();
      resolve(value);
    };
    d.querySelector('[data-no]').onclick = () => close(false);
    d.querySelector('[data-yes]').onclick = () => close(true);
    d.oncancel = (event) => {
      event.preventDefault();
      close(false);
    };
    d.showModal();
  });
}
