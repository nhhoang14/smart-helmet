/**
 * HistoryPage – biểu đồ lịch sử nồng độ khí lấy trực tiếp từ database MySQL theo khoảng thời gian
 */
import { useState, useEffect } from 'react';
import { getTelemetry } from '../api/client';
import { GasChart } from '../components/GasChart';
import { toDatetimeLocal } from '../utils/format';

const DEVICE_ID = import.meta.env.VITE_DEVICE_ID || '01';

export function HistoryPage() {
  const now   = new Date();
  const hour  = new Date(now.getTime() - 60 * 60 * 1000);

  const [from, setFrom]       = useState(toDatetimeLocal(hour));
  const [to, setTo]           = useState(toDatetimeLocal(now));
  const [samples, setSamples] = useState([]);
  const [loading, setLoading] = useState(false);
  const [fetched, setFetched] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const data = await getTelemetry(DEVICE_ID, new Date(from).toISOString(), new Date(to).toISOString());
      const list = Array.isArray(data) ? data : (data.content || []);
      const mapped = list.map(t => ({
        gas: Number(t.gasPpm ?? t.gas ?? 0),
        ts: t.recordedAt ? Math.floor(new Date(t.recordedAt).getTime() / 1000) : (t.ts ?? 0),
      }));
      setSamples(mapped);
      setFetched(true);
    } catch (e) {
      alert(`Lỗi tải dữ liệu lịch sử: ${e.message}`);
    } finally {
      setLoading(false);
    }
  };

  // Auto fetch khi vào trang
  useEffect(() => { fetchData(); }, []);

  const maxPpm = samples.length ? Math.max(...samples.map(s => s.gas)) : 0;
  const minPpm = samples.length ? Math.min(...samples.map(s => s.gas)) : 0;
  const avgPpm = samples.length
    ? samples.reduce((acc, s) => acc + s.gas, 0) / samples.length
    : 0;

  return (
    <div className="page animate-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">Lịch sử nồng độ khí</h1>
          <div className="page-sub">Xem biểu đồ lịch sử dữ liệu cảm biến lưu trong MySQL</div>
        </div>
      </div>

      {/* Date range picker */}
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-title">📅 Chọn khoảng thời gian</div>
        <div style={{ display: 'flex', gap: 16, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Từ</label>
            <input
              type="datetime-local"
              className="date-input"
              value={from}
              onChange={e => setFrom(e.target.value)}
            />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Đến</label>
            <input
              type="datetime-local"
              className="date-input"
              value={to}
              onChange={e => setTo(e.target.value)}
            />
          </div>
          {/* Quick select */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {[
              { label: '15p', ms: 15 * 60 * 1000 },
              { label: '1h',  ms: 60 * 60 * 1000 },
              { label: '6h',  ms: 6 * 60 * 60 * 1000 },
              { label: '24h', ms: 24 * 60 * 60 * 1000 },
              { label: '7d',  ms: 7 * 24 * 60 * 60 * 1000 },
            ].map(({ label, ms }) => (
              <button
                key={label}
                className="btn btn-ghost btn-sm"
                onClick={() => {
                  const currentNow = new Date();
                  setFrom(toDatetimeLocal(new Date(currentNow.getTime() - ms)));
                  setTo(toDatetimeLocal(currentNow));
                }}
              >{label}</button>
            ))}
          </div>
          <button
            className="btn btn-primary"
            onClick={fetchData}
            disabled={loading}
          >
            {loading ? <><div className="spinner" style={{ width: 16, height: 16 }} /> Đang tải...</> : '🔍 Xem'}
          </button>
        </div>
      </div>

      {/* Stats */}
      {fetched && samples.length > 0 && (
        <div className="stats-row" style={{ marginBottom: 16 }}>
          <div className="stat-card">
            <div className="stat-icon">📈</div>
            <div className="stat-value">{maxPpm.toFixed(0)}</div>
            <div className="stat-label">ppm cao nhất</div>
          </div>
          <div className="stat-card">
            <div className="stat-icon">📉</div>
            <div className="stat-value">{minPpm.toFixed(0)}</div>
            <div className="stat-label">ppm thấp nhất</div>
          </div>
          <div className="stat-card">
            <div className="stat-icon">📊</div>
            <div className="stat-value">{avgPpm.toFixed(0)}</div>
            <div className="stat-label">ppm trung bình</div>
          </div>
          <div className="stat-card">
            <div className="stat-icon">🔢</div>
            <div className="stat-value">{samples.length}</div>
            <div className="stat-label">mẫu trong DB</div>
          </div>
        </div>
      )}

      {/* Chart */}
      {loading ? (
        <div className="card" style={{ padding: 60, display: 'flex', justifyContent: 'center' }}>
          <div className="spinner" style={{ width: 40, height: 40 }} />
        </div>
      ) : samples.length === 0 && fetched ? (
        <div className="card" style={{ padding: 40, textAlign: 'center', color: 'var(--c-text-muted)' }}>
          Không có mẫu dữ liệu nào trong khoảng thời gian đã chọn.
        </div>
      ) : (
        <GasChart
          samples={samples}
          warnThreshold={400}
          dangerThreshold={600}
          title={`Nồng độ khí trong cơ sở dữ liệu (${samples.length} mẫu)`}
          height={350}
        />
      )}
    </div>
  );
}
