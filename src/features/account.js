import { e, t, input, link } from '../shared/ui.js';
import { mobileHeading, successCard, icon } from '../shared/design.js';
export function accountPage(data, mode = 'login', flow = {}) {
  if (data.user && !['password', 'forgot', 'verify', 'reset'].includes(mode)) {
    const user = data.user;
    const name =
      user.fullName || user.name || t('Chưa có thông tin', 'Not provided');
    const heading = (vi, en) => mobileHeading(t(vi, en), '/account');
    if (mode === 'general')
      return (
        heading('Thông tin chung', 'General information') +
        `<dl class="vehicle-details">${[
          [t('Họ tên', 'Full name'), name],
          ['Email', user.email],
          [t('Mã số sinh viên', 'Student ID'), user.studentId],
          [
            t('Vai trò', 'Role'),
            user.role === 'OPERATOR'
              ? t('Đơn vị vận hành', 'Operator')
              : t('Sinh viên', 'Student'),
          ],
        ]
          .map(
            ([label, value]) =>
              `<div><dt>${e(label)}</dt><dd>${e(value || t('Chưa có thông tin', 'Not provided'))}</dd></div>`,
          )
          .join('')}</dl>`
      );
    if (mode === 'privacy')
      return (
        heading('Quyền riêng tư', 'Privacy') +
        `<h2>${t('Quyền truy cập của ứng dụng', 'App permissions')}</h2><p class="hint">${t('Bạn kiểm soát quyền dùng vị trí và camera. Có thể thu hồi quyền trong cài đặt trang web của trình duyệt.', 'You control location and camera access. Revoke access in your browser site settings.')}</p><section class="panel"><h3>${icon('pin')} ${t('Vị trí', 'Location')}</h3><p>${t('Tìm Mobility Hub gần bạn.', 'Find Mobility Hubs nearby.')}</p><button class="secondary full" data-api-action="privacy-location">${t('Cho phép vị trí', 'Allow location')}</button></section><section class="panel"><h3>${icon('qr')} ${t('Camera', 'Camera')}</h3><p>${t('Quét mã QR để nhận xe.', 'Scan QR codes to pick up a vehicle.')}</p><button class="secondary full" data-api-action="privacy-camera">${t('Cho phép camera', 'Allow camera')}</button></section>`
      );
    if (mode === 'country')
      return (
        heading('Quốc gia', 'Country') +
        `<section class="panel"><h2>${t('Việt Nam', 'Vietnam')}</h2><p>${t('Khu vực phục vụ: ĐHQG-HCM.', 'Service area: VNU-HCM.')}</p></section>`
      );
    if (mode === 'security')
      return (
        heading('Đổi mật khẩu', 'Change password') +
        link(
          '/account/password',
          t('Đổi mật khẩu', 'Change password'),
          'button primary full',
        ) +
        link(
          '/account/forgot',
          t('Quên mật khẩu', 'Forgot password'),
          'button primary full',
        ) +
        `<p class="hint">${t('Đặt lại bằng mật khẩu hiện tại; quên mật khẩu thì xác nhận qua email đã đăng ký.', 'Change using your current password, or recover through your registered email.')}</p>`
      );
    const menu = [
      ['/account/general', 'user', t('Thông tin chung', 'General information')],
      [
        '/account/privacy',
        'qr',
        t('Kiểm soát quyền riêng tư', 'Privacy controls'),
      ],
      ['/account/country', 'pin', t('Quốc gia', 'Country')],
      ['/settings', 'pin', t('Ngôn ngữ & Giao diện', 'Language & appearance')],
      ['/account/password', 'qr', t('Đổi mật khẩu', 'Change password')],
    ];
    return (
      mobileHeading(t('Tài khoản', 'Account')) +
      `<section class="account-summary"><div class="avatar">${icon('user')}</div><div><h2>${e(name)}</h2><p>${e(user.studentId || '')}${user.studentId ? ' · ' : ''}${e(user.email || '')}</p></div></section><section class="panel premium-card"><h3>${icon('bike')} ${t('Donate để mở khóa Premium', 'Donate to unlock Premium')}</h3><p>${t('Không giới hạn số lượt thuê xe', 'Unlimited vehicle rentals')}</p><small>${t('Chưa hỗ trợ đăng ký Premium.', 'Premium registration is not available yet.')}</small></section><div class="account-menu">${menu.map(([path, glyph, label]) => `<a href="#${path}">${icon(glyph)}<span>${e(label)}</span>${icon('back', 'forward')}</a>`).join('')}</div><button class="secondary full" data-action="logout">${t('Đăng xuất', 'Sign out')}</button>`
    );
  }
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
      input(
        'email',
        'Email',
        'email',
        data.user?.email || '',
        `required autocomplete="email" placeholder="${e(t('Nhập email của bạn', 'Enter your email'))}"`,
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
  return `<div class="auth-wrap">${mobileHeading(names[mode], login ? '/' : '/account')}<div id="auth-error" role="alert" hidden></div>${login ? '<h2 class="auth-brand">Mobility Hub</h2>' : ''}<form data-form="${login ? 'login' : mode}" class="auth-form">${fields}<button class="primary full">${e(action)}</button></form><div class="auth-actions">${login ? link('/account/forgot', t('Quên mật khẩu', 'Forgot password'), 'button secondary full') + (role === 'STUDENT' ? link('/account/register', t('Đăng ký tài khoản', 'Create a student account'), 'button secondary full') : '') : mode === 'password' ? link('/account/forgot', t('Quên mật khẩu', 'Forgot password'), 'button secondary full') : mode === 'register' ? link('/account', t('Đã có tài khoản? Đăng nhập', 'Already registered? Sign in'), 'button secondary full') : ''}</div></div>`;
}
