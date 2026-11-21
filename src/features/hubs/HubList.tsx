import { useEffect, useState } from 'react';
import { Bike, MapPin, ParkingCircle } from 'lucide-react';
import { listHubs, type HubSummary } from './api';
import { usePreferences } from '../../shared/preferences';
export default function HubList() {
  const { locale } = usePreferences();
  const vi = locale === 'vi';
  const [data, setData] = useState<HubSummary[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>(
    'loading',
  );
  const [attempt, setAttempt] = useState(0);
  const [search, setSearch] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    listHubs(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setData(result);
          setStatus('ready');
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setStatus('error');
      });
    return () => controller.abort();
  }, [attempt]);
  const filtered = data.filter((h) =>
    h.name.toLowerCase().includes(search.toLowerCase()),
  );
  return (
    <section aria-label={vi ? 'Danh sách Hub' : 'Hub list'}>
      <label className="search">
        {vi ? 'Tìm theo tên Hub' : 'Search by hub name'}
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={vi ? 'Nhập tên Hub…' : 'Enter hub name…'}
        />
      </label>
      {status === 'loading' && (
        <p role="status">{vi ? 'Đang tải…' : 'Loading…'}</p>
      )}
      {status === 'error' && (
        <div role="alert">
          <p>
            {vi
              ? 'Không tải được dữ liệu Hub. Kiểm tra backend và thử lại.'
              : 'Could not load hubs. Check the backend and retry.'}
          </p>
          <button
            onClick={() => {
              setStatus('loading');
              setAttempt((n) => n + 1);
            }}
          >
            {vi ? 'Thử lại' : 'Retry'}
          </button>
        </div>
      )}
      {status === 'ready' && (
        <div className="cards">
          {filtered.map((h) => (
            <article className="card" key={h.id}>
              <MapPin aria-hidden="true" />
              <h2>{h.name}</h2>
              <p>
                <Bike size={18} aria-hidden="true" /> {h.availableVehicles}{' '}
                {vi ? 'xe khả dụng' : 'available vehicles'}
              </p>
              <p>
                <ParkingCircle size={18} aria-hidden="true" />{' '}
                {h.availableParking} {vi ? 'chỗ đỗ trống' : 'parking spaces'}
              </p>
            </article>
          ))}
          {filtered.length === 0 && (
            <p role="status">
              {vi ? 'Không tìm thấy Hub phù hợp.' : 'No matching hubs.'}
            </p>
          )}
        </div>
      )}
    </section>
  );
}
