import { e, t, title, input, link } from '../shared/ui.js';
export function accountPage(data, mode = 'login') {
  if (data.user && mode !== 'password')
    return (
      title(t('Tài khoản của tôi', 'My account')) +
      `<section class="panel profile"><div class="avatar">${e(data.user.name.slice(0, 1))}</div><h2>${e(data.user.name)}</h2><p>${e(data.user.email)}</p><p>${e(data.user.role)}</p></section><div class="link-list">${link('/settings', t('Ngôn ngữ & giao diện', 'Language & appearance'))}${link('/account/password', t('Đổi mật khẩu', 'Change password'))}<button data-action="logout">${t('Đăng xuất', 'Sign out')}</button></div>`
    );
  const names = {
    login: t('Chào mừng trở lại', 'Welcome back'),
    register: t('Tạo tài khoản', 'Create account'),
    forgot: t('Khôi phục mật khẩu', 'Recover password'),
    reset: t('Đặt lại mật khẩu', 'Reset password'),
    password: t('Đổi mật khẩu', 'Change password'),
  };
  if (!names[mode]) mode = 'login';
  return `<div class="auth-wrap">${title(names[mode], t('Kết nối hành trình xanh của bạn', 'Connect your greener journey'))}<form data-form="${mode}" class="panel">${mode === 'register' ? input('name', t('Họ tên', 'Full name'), 'text', '', 'required autocomplete="name"') : ''}${mode !== 'password' ? input('email', 'Email', 'email', '', 'required autocomplete="username"') : input('current', t('Mật khẩu hiện tại', 'Current password'), 'password', '', 'required autocomplete="current-password"')}${mode === 'reset' ? input('code', t('Mã khôi phục demo', 'Demo recovery code'), 'text', '', 'required autocomplete="one-time-code"') : ''}${mode !== 'forgot' ? input('password', t('Mật khẩu', 'Password'), 'password', '', 'required minlength="8" autocomplete="' + (mode === 'login' ? 'current-password' : 'new-password') + '"') : ''}${['register', 'reset', 'password'].includes(mode) ? input('confirm', t('Nhập lại mật khẩu', 'Confirm password'), 'password', '', 'required minlength="8" autocomplete="new-password"') : ''}${mode === 'login' ? `<label>${t('Vai trò', 'Role')}<select name="role"><option value="STUDENT">${t('Sinh viên', 'Student')}</option><option value="OPERATOR">${t('Vận hành', 'Operator')}</option></select></label>` : ''}<button class="primary full">${e(names[mode])}</button></form><div class="auth-links">${link('/account', t('Đăng nhập', 'Sign in'), 'text-link')}${link('/account/register', t('Đăng ký', 'Register'), 'text-link')}${link('/account/forgot', t('Quên mật khẩu', 'Forgot password'), 'text-link')}</div><aside class="demo-help"><strong>DEMO</strong><p>student@demo.local / operator@demo.local<br>Demo123!</p><p>${t('Dữ liệu chỉ ở bộ nhớ; tải lại trang sẽ đặt lại. Mã khôi phục: 123456. Không nhập mật khẩu thật.', 'Data is in memory and resets on reload. Recovery code: 123456. Do not enter a real password.')}</p></aside></div>`;
}
