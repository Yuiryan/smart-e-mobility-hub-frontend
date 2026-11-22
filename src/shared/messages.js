export const messages = {
  INVALID_TIME: [
    'Chọn thời gian hợp lệ: bắt đầu từ hiện tại và kết thúc sau bắt đầu.',
    'Choose a valid future time window.',
  ],
  UNAUTHORIZED: [
    'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    'Your session expired. Please sign in again.',
  ],
  FORBIDDEN: [
    'Tài khoản không có quyền thực hiện thao tác này.',
    'This account cannot perform this action.',
  ],
  INVALID_CREDENTIALS: [
    'Email, mật khẩu hoặc vai trò chưa đúng.',
    'Incorrect email, password or role.',
  ],
  INVALID_ACCOUNT: [
    'Kiểm tra thông tin và mật khẩu tối thiểu 8 ký tự.',
    'Check your details and use at least 8 password characters.',
  ],
  EMAIL_EXISTS: ['Email đã được đăng ký.', 'This email is already registered.'],
  INVALID_CODE: [
    'Mã không hợp lệ, đã dùng hoặc đã hết hạn.',
    'Code is invalid, already used or expired.',
  ],
  ACTIVE_BOOKING: [
    'Bạn đã có lượt giữ xe hoặc chuyến đi đang hoạt động.',
    'You already have an active reservation or ride.',
  ],
  NOT_FOUND: ['Không tìm thấy tài nguyên.', 'Resource not found.'],
  CONFLICT: [
    'Tài nguyên đã được đặt trong thời gian này. Vui lòng chọn lại.',
    'This resource is booked for that time. Choose another.',
  ],
  UNAVAILABLE: [
    'Tài nguyên hiện không khả dụng.',
    'This resource is currently unavailable.',
  ],
  STALE: [
    'Dữ liệu xe đã cũ; chưa thể xác nhận đặt xe.',
    'Vehicle data is stale; booking cannot be confirmed.',
  ],
  INVALID_TARGET: [
    'Mức pin mục tiêu phải cao hơn hiện tại và không quá 100%.',
    'Target charge must exceed current charge and be at most 100%.',
  ],
  SHORT_WINDOW: [
    'Khoảng thời gian chưa đủ cho mức pin mục tiêu.',
    'The time window is too short for the target charge.',
  ],
  PENDING_COMMAND: [
    'Có lệnh đang chờ hoặc cần đối soát. Không gửi lệnh mới.',
    'A command is pending or needs reconciliation. Do not resend.',
  ],
  TOO_EARLY: ['Chưa đến giờ nhận xe.', 'It is not pickup time yet.'],
  NO_RIDE: [
    'Bạn chưa có chuyến đi đang hoạt động.',
    'You have no active ride.',
  ],
  API_NOT_CONFIGURED: [
    'Backend chưa được tích hợp. Dùng chế độ mock để xem thử frontend.',
    'Backend integration is not configured. Use mock mode to preview the frontend.',
  ],
  UNKNOWN: [
    'Không thực hiện được thao tác. Vui lòng thử lại.',
    'Could not complete this action. Please retry.',
  ],
  BOOKED: ['Đã tạo lượt đặt.', 'Booking created.'],
  CANCELLED: ['Đã hủy lượt đặt.', 'Booking cancelled.'],
  RIDE_STARTED: [
    'Đã xác nhận mở khóa và bắt đầu chuyến đi.',
    'Unlock confirmed. Ride started.',
  ],
  RIDE_COMPLETED: [
    'Đã xác nhận khóa và kết thúc chuyến đi.',
    'Lock confirmed. Ride completed.',
  ],
  FAILED: [
    'Thiết bị từ chối lệnh. Trạng thái tài nguyên chưa thay đổi.',
    'Device rejected the command. Resources were not changed.',
  ],
  TIMEOUT: [
    'Chưa xác định được kết quả lệnh. Cần đối soát trước khi thử lại.',
    'Command outcome is unknown. Reconciliation is required before retrying.',
  ],
  ACTIVE: ['Đang hiệu lực', 'Active'],
  EXPIRED: ['Hết hạn', 'Expired'],
  COMPLETED: ['Hoàn tất', 'Completed'],
  AVAILABLE: ['Khả dụng', 'Available'],
  RESERVED: ['Đã giữ chỗ', 'Reserved'],
  IN_USE: ['Đang sử dụng', 'In use'],
  LOW_BATTERY: ['Pin thấp', 'Low battery'],
  MAINTENANCE: ['Bảo trì', 'Maintenance'],
  PENDING: ['Đang chờ xác nhận thiết bị…', 'Waiting for device confirmation…'],
  SUCCESS: ['Thiết bị đã xác nhận', 'Device confirmed'],
  VEHICLE: ['Đặt xe', 'Vehicle reservation'],
  PARKING: ['Chỗ đỗ', 'Parking'],
  CHARGING: ['Đăng ký sạc', 'Charging'],
};
