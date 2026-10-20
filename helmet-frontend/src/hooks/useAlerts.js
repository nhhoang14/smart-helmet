/**
 * useAlerts – tải danh sách cảnh báo từ MySQL, nhận realtime qua WebSocket, xác nhận xử lý
 */
import { useState, useEffect, useCallback, useRef } from 'react';
import { getAlerts, ackAlert } from '../api/client';
import { subscribe } from '../api/realtime';
import { playAlert } from '../utils/alertSound';

export function useAlerts() {
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const seenIds = useRef(new Set());

  // Tải danh sách cảnh báo từ backend
  const loadAlerts = useCallback(() => {
    setLoading(true);
    getAlerts({ status: '', page: 0, size: 20 })
      .then(data => {
        const list = Array.isArray(data) ? data : (data.content || []);
        setAlerts(list);
        list.forEach(a => seenIds.current.add(a.id));
        setLoading(false);
      })
      .catch(err => {
        setError(err.message);
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    loadAlerts();
  }, [loadAlerts]);

  // Lắng nghe cảnh báo realtime qua WebSocket STOMP
  useEffect(() => {
    const unsub = subscribe('/topic/alerts', (alert) => {
      setAlerts(prev => {
        // Cập nhật nếu đã có trong danh sách, ngược lại chèn vào đầu
        const exists = prev.find(a => a.id === alert.id);
        if (exists) return prev.map(a => a.id === alert.id ? alert : a);
        return [alert, ...prev];
      });

      // Phát âm báo cho cảnh báo mới
      if (!seenIds.current.has(alert.id)) {
        if (alert.severity === 'DANGER') {
          playAlert(alert.type === 'SOS' ? 'sos' : 'danger');
        } else if (alert.severity === 'WARNING') {
          playAlert('warning');
        }
        seenIds.current.add(alert.id);
      }
    });
    return unsub;
  }, []);

  // Xác nhận xử lý cảnh báo (gọi API POST /api/alerts/{id}/ack)
  const acknowledge = useCallback(async (id) => {
    try {
      const updated = await ackAlert(id);
      setAlerts(prev => prev.map(a =>
        a.id === id ? { ...a, ...(updated || {}), status: 'ACKNOWLEDGED', acknowledgedAt: new Date().toISOString() } : a
      ));
    } catch (e) {
      console.error('Lỗi xác nhận cảnh báo:', e);
    }
  }, []);

  const newCount = alerts.filter(a => a.status === 'NEW').length;

  return { alerts, loading, error, acknowledge, newCount, reload: loadAlerts };
}
