import { e, t, link } from '../shared/ui.js';
import { welcomePage } from '../shared/design.js';
export const operatorNavigation = () => [
  ['/operator/overview', t('Tổng quan', 'Overview')],
  ['/operator/resources', t('Tài nguyên Hub', 'Mobility Hub resources')],
  [
    '/operator/interventions',
    t('Can thiệp vận hành', 'Operational intervention'),
  ],
  ['/operator/alerts', t('Cảnh báo quá tải', 'Overload alerts')],
  ['/operator/dispatch', t('Điều phối xe', 'Vehicle dispatch')],
  ['/operator/incidents', t('Sự cố thiết bị', 'Device incidents')],
  ['/operator/charging', t('Lịch sạc thông minh', 'Smart charging')],
  ['/operator/simulation', t('Mô phỏng What-if', 'What-if simulation')],
  ['/settings', t('Giao diện', 'Appearance')],
];
export function operatorShell(content, route, alerts = 0) {
  const dark = document.documentElement.dataset.theme === 'dark';
  return `<div class="app-frame operator-frame figma-operator"><aside class="op-sidebar"><a class="op-brand" href="#/operator/overview">Mobility<br>Hub</a><p>${t('ĐƠN VỊ VẬN HÀNH', 'OPERATIONS')}</p><nav aria-label="${t('Điều hướng vận hành', 'Operator navigation')}">${operatorNavigation()
    .map(
      ([path, name]) =>
        `<a href="#${path}" ${path === route ? 'class="active" aria-current="page"' : ''}>${e(name)}</a>`,
    )
    .join(
      '',
    )}</nav><a class="op-account" href="#/account">${t('Tài khoản', 'Account')}</a></aside><main id="main" tabindex="-1"><header class="op-topbar"><span>${t('Khu đô thị ĐHQG-HCM', 'VNU-HCM University Town')}</span><div class="op-top-actions"><button type="button" data-op-locale>${document.documentElement.lang === 'en' ? 'EN' : 'VI'}</button><span>·</span><button type="button" data-op-theme>${dark ? t('Chuyển sang Sáng', 'Switch to Light') : t('Chuyển sang Tối', 'Switch to Dark')}</button></div>${route === '/operator/overview' ? link('/operator/alerts', t(`Thông báo quá tải (${alerts})`, `Overload alerts (${alerts})`), 'button secondary') : ''}</header><div id="feedback" role="alert" hidden></div>${content}</main></div><aside class="preview-tools"><span>DEMO · ${t('Dữ liệu mô phỏng', 'Simulated data')}</span>${link('/account', t('Tài khoản', 'Account'))}</aside>`;
}
export function operatorAuthShell(content) {
  if (content.includes('data-form="forgot"')) {
    content += link(
      '/account/operator',
      t('Quay lại đăng nhập', 'Back to sign in'),
      'button secondary full',
    );
  }
  return `<div class="pc-auth"><section class="pc-auth-intro"><h2>Mobility Hub</h2><strong>${t('Vận hành hệ thống', 'System operations')}</strong></section><main class="pc-auth-card"><div id="feedback" role="alert" hidden></div>${content.replaceAll('href="#/"', 'href="#/welcome/pc"')}</main></div>`;
}
export function desktopWelcome() {
  return `<div class="pc-welcome"><main>${welcomePage()}</main></div>`;
}
export function operatorSettings() {
  const dark = document.documentElement.dataset.theme === 'dark';
  const en = document.documentElement.lang === 'en';
  const choice = (key, value, text, active) =>
    `<button type="button" data-setting="${key}" data-value="${value}" aria-pressed="${active}">${text}</button>`;
  return `<header class="page-title"><h1>${t('Tùy chọn giao diện', 'Appearance preferences')}</h1><p>${t('Chế độ hiển thị và ngôn ngữ áp dụng ngay cho trang vận hành.', 'Theme and language apply immediately to operations pages.')}</p></header><section class="panel op-settings"><h2>${t('Chế độ hiển thị', 'Display mode')}</h2><div class="op-segment">${choice('theme', 'light', t('Sáng', 'Light'), !dark)}${choice('theme', 'dark', t('Tối', 'Dark'), dark)}</div><h2>${t('Ngôn ngữ', 'Language')}</h2><div class="op-segment">${choice('locale', 'vi', 'Tiếng Việt', !en)}${choice('locale', 'en', 'English', en)}</div></section><p class="op-settings-saved" role="status">${t('Đã lưu tùy chọn giao diện.', 'Appearance preferences saved.')}</p>`;
}
