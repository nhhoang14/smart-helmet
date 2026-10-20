/**
 * useDeviceState – tải trạng thái ban đầu qua REST,
 * cập nhật realtime qua WebSocket /topic/devices/{id}/state
 */
import { useState, useEffect, useRef } from 'react';
import { getDevice } from '../api/client';
import { subscribe } from '../api/realtime';

const DEVICE_ID = import.meta.env.VITE_DEVICE_ID || '01';

export function useDeviceState() {
  const [state, setState] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Fetch ban đầu
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getDevice(DEVICE_ID)
      .then(data => { if (!cancelled) { setState(data); setLoading(false); } })
      .catch(err  => { if (!cancelled) { setError(err.message); setLoading(false); } });
    return () => { cancelled = true; };
  }, []);

  // Subscribe realtime
  useEffect(() => {
    const unsub = subscribe(`/topic/devices/${DEVICE_ID}/state`, (payload) => {
      setState(prev => ({ ...prev, ...payload }));
    });
    return unsub;
  }, []);

  return { state, loading, error, deviceId: DEVICE_ID };
}
