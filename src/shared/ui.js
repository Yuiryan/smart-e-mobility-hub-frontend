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
export const times = () =>
  `<div class="form-grid">${input('start', t('Bắt đầu', 'Start'), 'datetime-local', localDate(Date.now()), 'required data-draft')}${input('end', t('Kết thúc', 'End'), 'datetime-local', localDate(Date.now() + 7200000), 'required data-draft')}</div>`;
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
