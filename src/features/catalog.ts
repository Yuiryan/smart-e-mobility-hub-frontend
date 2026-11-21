export type Locale = 'vi' | 'en';
export type Area = 'student' | 'operator' | 'shared';
export interface Feature {
  id: string;
  path: string;
  area: Area;
  owner: string;
  title: Record<Locale, string>;
}
export const features: Feature[] = [
  {
    id: 'UC00',
    path: '/account',
    area: 'shared',
    owner: 'Quân',
    title: { vi: 'Tài khoản', en: 'Account' },
  },
  {
    id: 'UC01',
    path: '/student/hubs',
    area: 'student',
    owner: 'Phúc',
    title: { vi: 'Tìm Hub', en: 'Find hubs' },
  },
  {
    id: 'UC02',
    path: '/student/vehicles/:vehicleId',
    area: 'student',
    owner: 'Sơn',
    title: { vi: 'Chi tiết xe', en: 'Vehicle details' },
  },
  {
    id: 'UC03',
    path: '/student/reservations',
    area: 'student',
    owner: 'Phúc',
    title: { vi: 'Đặt xe', en: 'Reserve a vehicle' },
  },
  {
    id: 'UC04',
    path: '/student/parking',
    area: 'student',
    owner: 'Sơn',
    title: { vi: 'Đặt chỗ đỗ', en: 'Reserve parking' },
  },
  {
    id: 'UC05',
    path: '/student/pickup',
    area: 'student',
    owner: 'Phúc',
    title: { vi: 'Nhận xe QR', en: 'QR pickup' },
  },
  {
    id: 'UC06',
    path: '/student/return',
    area: 'student',
    owner: 'Sơn',
    title: { vi: 'Trả xe', en: 'Return vehicle' },
  },
  {
    id: 'UC07',
    path: '/student/charging',
    area: 'student',
    owner: 'Quân',
    title: { vi: 'Đăng ký sạc', en: 'Request charging' },
  },
  {
    id: 'UC08',
    path: '/operator/overview',
    area: 'operator',
    owner: 'Quân',
    title: { vi: 'Tổng quan mạng lưới', en: 'Network overview' },
  },
  {
    id: 'UC09',
    path: '/operator/interventions',
    area: 'operator',
    owner: 'Thịnh',
    title: { vi: 'Can thiệp tài nguyên', en: 'Resource intervention' },
  },
  {
    id: 'UC10',
    path: '/operator/alerts',
    area: 'operator',
    owner: 'Thịnh',
    title: { vi: 'Cảnh báo quá tải', en: 'Overload alerts' },
  },
  {
    id: 'UC11',
    path: '/operator/dispatch',
    area: 'operator',
    owner: 'Bảo',
    title: { vi: 'Điều phối xe', en: 'Vehicle dispatch' },
  },
  {
    id: 'UC12',
    path: '/operator/incidents',
    area: 'operator',
    owner: 'Quân',
    title: { vi: 'Quản lý sự cố', en: 'Incident management' },
  },
  {
    id: 'UC13',
    path: '/operator/charging',
    area: 'operator',
    owner: 'Thịnh',
    title: { vi: 'Lập lịch sạc', en: 'Charging schedule' },
  },
  {
    id: 'UC14',
    path: '/operator/simulation',
    area: 'operator',
    owner: 'Bảo',
    title: { vi: 'Mô phỏng What-if', en: 'What-if simulation' },
  },
  {
    id: 'UC15',
    path: '/settings',
    area: 'shared',
    owner: 'Bảo',
    title: { vi: 'Cài đặt giao diện', en: 'Display settings' },
  },
];
