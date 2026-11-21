import {
  BrowserRouter,
  Link,
  NavLink,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom';
import { Bike, LayoutDashboard, Moon, Sun, ArrowUpRight } from 'lucide-react';
import { features, type Feature } from './features/catalog';
import HubList from './features/hubs/HubList';
import { isMock } from './features/hubs/api';
import { setPreferences, usePreferences } from './shared/preferences';
import './index.css';
const figma =
  'https://www.figma.com/design/exswez5Rtk8mrmEgxqbNBS/Final?node-id=0-1';
function Preferences() {
  const { locale, theme } = usePreferences();
  return (
    <div className="preferences">
      <label>
        <span className="sr-only">
          {locale === 'vi' ? 'Ngôn ngữ' : 'Language'}
        </span>
        <select
          value={locale}
          onChange={(e) =>
            setPreferences({ locale: e.target.value === 'en' ? 'en' : 'vi' })
          }
        >
          <option value="vi">Tiếng Việt</option>
          <option value="en">English</option>
        </select>
      </label>
      <button
        className="icon-button"
        aria-label={locale === 'vi' ? 'Đổi sáng/tối' : 'Toggle theme'}
        aria-pressed={theme === 'dark'}
        onClick={() =>
          setPreferences({ theme: theme === 'light' ? 'dark' : 'light' })
        }
      >
        {theme === 'light' ? <Moon size={20} /> : <Sun size={20} />}
      </button>
    </div>
  );
}
function FeaturePage({ feature }: { feature: Feature }) {
  const { locale } = usePreferences();
  const vi = locale === 'vi';
  return (
    <>
      <p className="eyebrow">{feature.id}</p>
      <h1>{feature.title[locale]}</h1>
      {feature.id === 'UC01' ? (
        <HubList />
      ) : feature.id === 'UC15' ? (
        <section className="card">
          <h2>{vi ? 'Tùy chọn hiển thị' : 'Display preferences'}</h2>
          <p>
            {vi
              ? 'Cài đặt được lưu trên trình duyệt này.'
              : 'Preferences are saved in this browser.'}
          </p>
          <Preferences />
        </section>
      ) : (
        <section className="card">
          <span className="badge">
            {vi ? 'Khung màn hình' : 'Screen scaffold'}
          </span>
          <h2>{vi ? 'Sẵn sàng để triển khai' : 'Ready for implementation'}</h2>
          <p>
            {vi
              ? 'Màn hình này chưa có luồng nghiệp vụ. Hoàn thiện giao diện theo frame Figma và kết nối API đã được nhóm thống nhất.'
              : 'This screen has no business flow yet. Implement the Figma frame and connect the agreed API contract.'}
          </p>
          <a href={figma} target="_blank" rel="noreferrer">
            {vi ? 'Mở thiết kế Figma' : 'Open Figma design'}{' '}
            <ArrowUpRight size={16} />
          </a>
        </section>
      )}
    </>
  );
}
function Home() {
  const { locale } = usePreferences();
  const vi = locale === 'vi';
  return (
    <>
      <p className="eyebrow">SMART E-MOBILITY HUB</p>
      <h1>{vi ? 'Kết nối hành trình xanh.' : 'Connect your green journey.'}</h1>
      <p className="lead">
        {vi
          ? 'Bộ khung phát triển giao diện web · Submission 4'
          : 'Web frontend starter · Submission 4'}
      </p>
      <div className="cards">
        <Link className="card entry" to="/student/hubs">
          <Bike size={32} />
          <h2>{vi ? 'Sinh viên' : 'Student'}</h2>
          <p>
            {vi
              ? 'Tra cứu Hub, đặt xe, nhận và trả phương tiện.'
              : 'Find hubs, reserve, pick up and return vehicles.'}
          </p>
          <span>
            {vi ? 'Xem giao diện sinh viên' : 'Open student workspace'} →
          </span>
        </Link>
        <Link className="card entry" to="/operator/overview">
          <LayoutDashboard size={32} />
          <h2>{vi ? 'Đơn vị vận hành' : 'Operator'}</h2>
          <p>
            {vi
              ? 'Giám sát, điều phối và quản lý mạng lưới.'
              : 'Monitor, dispatch and manage the network.'}
          </p>
          <span>
            {vi ? 'Xem giao diện vận hành' : 'Open operations workspace'} →
          </span>
        </Link>
      </div>
      <p className="muted">
        {vi
          ? 'Lựa chọn giao diện không thay thế đăng nhập hoặc phân quyền.'
          : 'Workspace selection is not authentication or authorization.'}
      </p>
    </>
  );
}
function Shell() {
  const { locale } = usePreferences();
  const vi = locale === 'vi';
  const location = useLocation();
  const area = location.pathname.startsWith('/operator')
    ? 'operator'
    : 'student';
  const visible = features.filter(
    (f) => f.area === area || f.area === 'shared',
  );
  return (
    <>
      <a className="skip" href="#main">
        {vi ? 'Đến nội dung' : 'Skip to content'}
      </a>
      <header>
        <Link className="brand" to="/">
          <Bike aria-hidden="true" /> E-Mobility <span>Hub</span>
        </Link>
        <Preferences />
      </header>
      <div className="notice" role="status">
        {isMock
          ? vi
            ? 'BẢN PHÁT TRIỂN · Dữ liệu mẫu, chưa kết nối backend'
            : 'DEVELOPMENT · Sample data, backend not connected'
          : vi
            ? 'BẢN PHÁT TRIỂN · API thử nghiệm, chưa hoàn thiện xác thực'
            : 'DEVELOPMENT · Experimental API, authentication not implemented'}
      </div>
      <div className="workspace">
        <aside>
          <nav aria-label={vi ? 'Khu vực' : 'Workspace'} className="area-links">
            <Link to="/student/hubs">{vi ? 'Sinh viên' : 'Student'}</Link>
            <Link to="/operator/overview">{vi ? 'Vận hành' : 'Operator'}</Link>
          </nav>
          <nav aria-label={vi ? 'Chức năng' : 'Features'}>
            {visible.map((f) => (
              <NavLink
                key={f.id}
                to={f.path.replace(':vehicleId', 'demo-vehicle')}
              >
                <span>{f.id}</span>
                {f.title[locale]}
              </NavLink>
            ))}
          </nav>
        </aside>
        <main id="main" tabIndex={-1} key={location.pathname}>
          <Routes>
            <Route path="/" element={<Home />} />
            {features.map((f) => (
              <Route
                key={f.id}
                path={f.path}
                element={<FeaturePage feature={f} />}
              />
            ))}
            <Route
              path="*"
              element={
                <>
                  <h1>404</h1>
                  <p>{vi ? 'Không tìm thấy trang.' : 'Page not found.'}</p>
                  <Link to="/">{vi ? 'Về trang chủ' : 'Go home'}</Link>
                </>
              }
            />
          </Routes>
        </main>
      </div>
      <footer>Smart E-Mobility Hub · Submission 4</footer>
    </>
  );
}
export default function App() {
  return (
    <BrowserRouter>
      <Shell />
    </BrowserRouter>
  );
}
