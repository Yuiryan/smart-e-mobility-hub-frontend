import { e, t, input, link } from '../shared/ui.js';
import { mobileHeading, successCard } from '../shared/design.js';
export function accountPage(data, mode = 'login', flow = {}) {
  if (data.user && mode !== 'password')
    return (
      mobileHeading(t('Tài khoản', 'Account')) +
      `<section class="panel profile"><div class="avatar">${e((data.user.name || data.user.email).slice(0, 1))}</div><h2>${e(data.user.name)}</h2><p>${e(data.user.email)}</p><p>${data.user.role === 'OPERATOR' ? t('Đơn vị vận hành', 'Operator') : t('Sinh viên', 'Student')}</p></section><div class="link-list">${link('/settings', t('Ngôn ngữ & giao diện', 'Language & appearance'))}${link('/account/password', t('Đổi mật khẩu', 'Change password'))}<button data-action="logout">${t('Đăng xuất', 'Sign out')}</button></div>`
    );
  if (mode === 'registered' || mode === 'changed')
    return (
      mobileHeading(
        mode === 'registered'
          ? t('Đăng ký thành công', 'Registration successful')
          : t('Đã đổi mật khẩu', 'Password changed'),
        '/account',
      ) +
      successCard(
        mode === 'registered'
          ? t('Tài khoản đã được tạo', 'Account created')
          : t('Cập nhật thành công', 'Updated successfully'),
        mode === 'registered'
          ? t(
              'Đăng nhập để bắt đầu sử dụng Mobility Hub.',
              'Sign in to start using Mobility Hub.',
            )
          : t(
              'Bạn có thể đăng nhập bằng mật khẩu mới.',
              'You can now sign in with your new password.',
            ),
      ) +
      link(
        '/account',
        t('Quay lại đăng nhập', 'Back to sign in'),
        'button primary full',
      )
    );
  const names = {
    login: t('Đăng nhập', 'Sign in'),
    student: t('Đăng nhập', 'Sign in'),
    operator: t('Đăng nhập', 'Sign in'),
    register: t('Đăng ký sinh viên', 'Student registration'),
    forgot: t('Quên mật khẩu', 'Forgot password'),
    verify: t('Xác nhận email', 'Verify email'),
    reset: t('Đặt lại mật khẩu', 'Reset password'),
    password: t('Đổi mật khẩu', 'Change password'),
  };
  if (!names[mode]) mode = 'login';
  const login = ['login', 'student', 'operator'].includes(mode),
    role = mode === 'operator' ? 'OPERATOR' : flow.role || 'STUDENT';
  const field = (name, label, type, placeholder, extra = '') =>
    input(
      name,
      label,
      type,
      '',
      `required placeholder="${e(placeholder)}" ${extra}`,
    );
  let fields = '';
  if (login)
    fields =
      field(
        'email',
        'Email',
        'email',
        t('Nhập email của bạn', 'Enter your email'),
        'autocomplete="username"',
      ) +
      field(
        'password',
        t('Mật khẩu', 'Password'),
        'password',
        '••••••••',
        'autocomplete="current-password"',
      ) +
      `<input type="hidden" name="role" value="${role}">`;
  if (mode === 'register')
    fields =
      field(
        'name',
        t('Họ tên', 'Full name'),
        'text',
        t('Nhập họ tên của bạn', 'Enter your full name'),
        'autocomplete="name"',
      ) +
      field(
        'studentId',
        t('Mã số sinh viên', 'Student ID'),
        'text',
        t('Nhập mã số sinh viên', 'Enter your student ID'),
      ) +
      field(
        'email',
        'Email',
        'email',
        t('Nhập email của bạn', 'Enter your email'),
        'autocomplete="email"',
      ) +
      field(
        'password',
        t('Mật khẩu', 'Password'),
        'password',
        t('Nhập mật khẩu của bạn', 'Enter your password'),
        'minlength="8" autocomplete="new-password"',
      );
  if (mode === 'forgot')
    fields =
      `<p class="auth-description">${t('Nhập email đã đăng ký để nhận mã xác nhận.', 'Enter your registered email to receive a verification code.')}</p>` +
      field(
        'email',
        'Email',
        'email',
        t('Nhập email của bạn', 'Enter your email'),
        'autocomplete="email"',
      );
  if (mode === 'verify')
    fields =
      `<p class="auth-description">${t('Nhập mã xác nhận đã gửi đến email của bạn.', 'Enter the verification code sent to your email.')}</p>` +
      field(
        'code',
        t('Mã xác nhận', 'Verification code'),
        'text',
        t('Nhập mã xác nhận', 'Enter verification code'),
        'autocomplete="one-time-code" inputmode="numeric"',
      );
  if (mode === 'reset')
    fields = field(
      'password',
      t('Mật khẩu mới', 'New password'),
      'password',
      t('Nhập mật khẩu mới', 'Enter your new password'),
      'minlength="8" autocomplete="new-password"',
    );
  if (mode === 'password')
    fields =
      field(
        'current',
        t('Mật khẩu hiện tại', 'Current password'),
        'password',
        '••••••••',
        'autocomplete="current-password"',
      ) +
      field(
        'password',
        t('Mật khẩu mới', 'New password'),
        'password',
        '••••••••',
        'minlength="8" autocomplete="new-password"',
      ) +
      field(
        'confirm',
        t('Nhập lại mật khẩu', 'Confirm password'),
        'password',
        '••••••••',
        'minlength="8" autocomplete="new-password"',
      );
  const action = login
    ? names[mode]
    : mode === 'register'
      ? t('Tạo tài khoản', 'Create account')
      : mode === 'forgot'
        ? t('Gửi mã xác nhận', 'Send verification code')
        : mode === 'verify'
          ? t('Xác nhận mã', 'Verify code')
          : t('Lưu mật khẩu mới', 'Save new password');
  return `<div class="auth-wrap">${mobileHeading(names[mode], login ? '/' : '/account')}<div id="auth-error" role="alert" hidden></div>${login ? '<h2 class="auth-brand">Mobility Hub</h2>' : ''}<form data-form="${login ? 'login' : mode}" class="auth-form">${fields}<button class="primary full">${e(action)}</button></form><div class="auth-actions">${login ? link('/account/forgot', t('Quên mật khẩu', 'Forgot password'), 'button secondary full') + (role === 'STUDENT' ? link('/account/register', t('Đăng ký tài khoản', 'Create a student account'), 'button secondary full') : '') : mode === 'register' ? link('/account', t('Đã có tài khoản? Đăng nhập', 'Already registered? Sign in'), 'button secondary full') : ''}</div></div>`;
}
