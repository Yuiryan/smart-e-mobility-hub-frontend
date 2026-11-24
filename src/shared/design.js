import { e, t, link } from './ui.js';
const paths = {
  back: '<path d="m15 5-7 7 7 7"/>',
  search: '<circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/>',
  home: '<path d="m3 10 9-7 9 7v11h-6v-8H9v8H3Z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 3"/>',
  bell: '<path d="M5 16V9a7 7 0 0 1 14 0v7l2 3H3ZM9 22h6"/>',
  user: '<circle cx="12" cy="7" r="4"/><path d="M5 22v-4a7 7 0 0 1 14 0v4"/>',
  pin: '<path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/>',
  bike: '<circle cx="5" cy="17" r="4"/><circle cx="19" cy="17" r="4"/><path d="m5 17 5-9 6 9M8 8h5M12 3h3l4 14M10 8l9 9"/>',
  parking:
    '<circle cx="12" cy="12" r="10"/><path d="M9 19V6h5a4 4 0 0 1 0 8H9"/>',
  charge: '<path d="m13 2-7 11h6l-1 9 7-13h-6ZM4 7v4M20 13v4"/>',
  return: '<path d="M3 6h17l-4-4M21 18H4l4 4M20 6l-4 4M4 18l4-4"/>',
  qr: '<path d="M2 2h7v7H2ZM15 2h7v7h-7ZM2 15h7v7H2ZM15 15h3v3h-3ZM21 15v7h-6M12 2v3M2 12h4M12 9v5M9 12h8M12 18v4"/>',
  check: '<circle cx="12" cy="12" r="10"/><path d="m7 12 3 3 7-7"/>',
  battery:
    '<path d="M3 7H1v10h2M21 7h2v10h-2M7 4H5v16h2M17 4h2v16h-2m-5-14-4 10h5l-2 8"/>',
};
export const icon = (name, cls = '') =>
  `<svg class="ui-icon ${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.pin}</svg>`;
export const mobileHeading = (text, back = '/student/hubs') =>
  `<header class="mobile-heading"><a href="#${back}" aria-label="${t('Quay lại', 'Back')}">${icon('back')}</a><h1>${e(text)}</h1></header>`;
export function welcomePage() {
  return `<section class="figma-welcome"><img class="welcome-logo" src="/assets/mobility-logo-${document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'}.png" alt="Mobility Hub" width="112" height="112"><div class="welcome-message">WELCOME TO MOBILITY HUB</div><p class="welcome-tagline">${t('Giải pháp Xanh vì Tương lai Xanh', 'Green solutions for a green future')}</p><div class="welcome-choices">${link('/account/student', t('Sinh viên', 'Student'), 'button primary full')}${link('/account/operator', 'Admin', 'button primary full')}</div></section>`;
}
export const successCard = (heading, description) =>
  `<section class="success-card">${icon('check')}<h2>${e(heading)}</h2><p>${e(description)}</p></section>`;
