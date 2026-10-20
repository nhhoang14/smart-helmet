/**
 * AlertsPage – lịch sử cảnh báo truy vấn trực tiếp từ MySQL qua REST API với phân trang và lọc
 */
import { useState, useEffect } from 'react';
import { getAlerts, ackAlert } from '../api/client';
import { AlertList } from '../components/AlertList';
import { alertTypeLabel } from '../utils/format';

const ALL_TYPES = ['', 'GAS_WARNING', 'GAS_DANGER', 'FALL', 'SOS', 'OFFLINE'];
const PAGE_SIZE = 10;

export function AlertsPage() {
  const [alerts, setAlerts]         = useState([]);
  const [loading, setLoading]       = useState(true);
  const [filterType, setType]       = useState('');
  const [filterStatus, setStatus]   = useState('');
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const fetchAlerts = () => {
    setLoading(true);
    getAlerts({ type: filterType, status: filterStatus, page, size: PAGE_SIZE })
      .then(data => {
        if (Array.isArray(data)) {
          setAlerts(data);
          setTotalPages(1);
          setTotalElements(data.length);
        } else if (data && data.content) {
          setAlerts(data.content);
          setTotalPages(data.totalPages || 1);
          setTotalElements(data.totalElements || 0);
        } else {
          setAlerts([]);
          setTotalPages(1);
          setTotalElements(0);
        }
        setLoading(false);
      })
      .catch(err => {
        console.error('Lỗi tải danh sách cảnh báo:', err);
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchAlerts();
  }, [filterType, filterStatus, page]);

  const handleAck = async (id) => {
    try {
      await ackAlert(id);
      setAlerts(prev => prev.map(a =>
        a.id === id ? { ...a, status: 'ACKNOWLEDGED', acknowledgedAt: new Date().toISOString() } : a
      ));
    } catch (e) {
      alert(`Lỗi xác nhận cảnh báo: ${e.message}`);
    }
  };

  const newCount = alerts.filter(a => a.status === 'NEW').length;

  return (
    <div className="page animate-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">Lịch sử cảnh báo</h1>
          <div className="page-sub">Xem và xác nhận tất cả cảnh báo lưu trong cơ sở dữ liệu MySQL</div>
        </div>
        {newCount > 0 && (
          <span className="badge badge-danger" style={{ fontSize: '0.9rem', padding: '6px 14px' }}>
            {newCount} cảnh báo chưa xử lý
          </span>
        )}
      </div>

      {/* Filter bar */}
      <div className="filter-bar">
        <label style={{ fontSize: '0.85rem', color: 'var(--c-text-muted)' }}>Loại:</label>
        <select
          className="filter-select"
          value={filterType}
          onChange={e => { setType(e.target.value); setPage(0); }}
        >
          <option value="">Tất cả</option>
          {ALL_TYPES.slice(1).map(t => (
            <option key={t} value={t}>{alertTypeLabel(t)}</option>
          ))}
        </select>

        <label style={{ fontSize: '0.85rem', color: 'var(--c-text-muted)' }}>Trạng thái:</label>
        <select
          className="filter-select"
          value={filterStatus}
          onChange={e => { setStatus(e.target.value); setPage(0); }}
        >
          <option value="">Tất cả</option>
          <option value="NEW">Chưa xử lý</option>
          <option value="ACKNOWLEDGED">Đã xử lý</option>
        </select>

        <div style={{ marginLeft: 'auto', fontSize: '0.82rem', color: 'var(--c-text-muted)' }}>
          Tổng cộng: {totalElements} cảnh báo
        </div>
      </div>

      <div className="card">
        {loading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
            <div className="spinner" style={{ width: 32, height: 32 }} />
          </div>
        ) : alerts.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 40, color: 'var(--c-text-muted)' }}>
            Không có cảnh báo nào phù hợp với bộ lọc.
          </div>
        ) : (
          <AlertList alerts={alerts} onAck={handleAck} showAll />
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="pagination">
          <button
            className="page-btn"
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
          >‹</button>
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i}
              className={`page-btn ${page === i ? 'active' : ''}`}
              onClick={() => setPage(i)}
            >{i + 1}</button>
          ))}
          <button
            className="page-btn"
            onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
            disabled={page === totalPages - 1}
          >›</button>
        </div>
      )}
    </div>
  );
}
